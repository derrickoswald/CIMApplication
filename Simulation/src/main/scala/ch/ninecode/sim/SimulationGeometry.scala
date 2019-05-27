package ch.ninecode.sim

import ch.ninecode.gl.GLMNode
import com.datastax.driver.core.ConsistencyLevel
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import com.datastax.spark.connector._
import com.datastax.spark.connector.writer.WriteConf

case class SimulationGeometry (session: SparkSession, keyspace: String)
{
    // the Id is a concatenation of simulation and mRID: "simulation_mrid"
    type Id = String
    type Key = String
    type Value = String
    type KeyValue = (Key, Value)
    type KeyValueList = Iterable[KeyValue]
    type Properties = (Id, KeyValueList)
    type Simulation = String
    type Transformer = String

    /**
     * Gather key-value pairs into groups keyed by simulation_mrid.
     *
     * Note that this inverts the relationship to make the query name the key of the key value pair.
     * For example, the Cassandra table contains:
     *  simulation | query  | key  | value
     * ------------+--------+------+-------
     *  sim1       | ratedI | KLE2 | 100.0
     * which produces:
     * ("sim1_KLE2", Iterator[("ratedI", "100.0")])
     *
     * @return the PairRDD with simulation_mrid as the key and a list of key-value pairs as the value
     */
    def query_extra: RDD[Properties] =
    {
        val rdd: RDD[Row] = session.read.format ("org.apache.spark.sql.cassandra")
            .options (Map ("keyspace" -> keyspace, "table" -> "key_value"))
            .load ()
            .select ("simulation", "key", "query", "value")
            .rdd
        rdd.map (row ⇒ Tuple2[Id, KeyValue] (row.getString (0) + "_" + row.getString (1), (row.getString (2), row.getString (3)))).groupByKey
    }

    /**
     * Store the GeoJSON point coordinates for equipment in each simulation
     *
     * @param trafos the simulation details for each transformer service area
     * @param extra the extra properties that are to be stored with each point
     */
    def store_geojson_points (trafos: RDD[SimulationTrafoKreis], extra: RDD[Properties]): Unit =
    {
        val nodes: RDD[(Id, (Simulation, Transformer, SimulationNode))] =
            trafos.flatMap (
                trafo ⇒
                    trafo.nodes.map (
                        node ⇒
                            (
                                trafo.simulation + "_" + node.equipment,
                                (trafo.simulation, trafo.transformer.transformer_name, node)
                            )
                    )
            )
        val jsons = nodes.leftOuterJoin (extra).values
            .flatMap (
                (x: ((Simulation, Transformer, SimulationNode), Option[KeyValueList])) ⇒
                {
                    val node = x._1._3
                    val world = if (null != node.world_position)
                    {
                        val geometry = ("Point", List (node.world_position._1, node.world_position._2))
                        val properties = x._2.orNull
                        Some ((x._1._1, node.equipment, "wgs84", geometry, properties, x._1._2, "Feature"))
                    }
                    else
                        None
                    val schematic =  if (null != node.schematic_position)
                    {
                        val geometry = ("Point", List (node.schematic_position._1, node.schematic_position._2))
                        val properties = x._2.orNull
                        Some ((x._1._1, node.equipment, "pseudo_wgs84", geometry, properties, x._1._2, "Feature"))
                    }
                    else
                        None
                    (world :: schematic :: Nil).flatten
                }
            )
        val cc = WriteConf.fromSparkConf (session.sparkContext.getConf)
        val dd = cc.copy (consistencyLevel = ConsistencyLevel.ANY)
        val ee = dd.copy (parallelismLevel = 1)
        val j2 = jsons.repartition (1)
        j2.saveToCassandra (keyspace, "geojson_points", SomeColumns ("simulation", "mrid", "coordinate_system", "geometry", "properties", "transformer", "type"), ee)
    }

    def store_geojson_lines (trafos: RDD[SimulationTrafoKreis], extra: RDD[Properties]): Unit =
    {
        val edges: RDD[(Id, (Simulation, Transformer, SimulationEdge))] =
            trafos.flatMap (
                trafo ⇒
                    trafo.edges.map (
                        edge ⇒
                            (
                                trafo.simulation + "_" + edge.rawedge.id,
                                (trafo.simulation, trafo.transformer.transformer_name, edge)
                            )
                    )
            )

        val jsons = edges.leftOuterJoin (extra).values
            .flatMap (
                (x: ((Simulation, Transformer, SimulationEdge), Option[KeyValueList])) ⇒
                {
                    val edge = x._1._3
                    val world = if (null != edge.world_position)
                    {
                        val coordinates = edge.world_position.map (p ⇒ List (p._1, p._2)).toList
                        val geometry = ("LineString", coordinates)
                        Some ((x._1._1, edge.rawedge.id, "wgs84", geometry, x._2.orNull, x._1._2, "Feature"))
                    }
                    else
                        None
                    val schematic = if (null != edge.schematic_position)
                    {
                        val coordinates = edge.schematic_position.map (p ⇒ List (p._1, p._2)).toList
                        val geometry = ("LineString", coordinates)
                        Some ((x._1._1, edge.rawedge.id, "pseudo_wgs84", geometry, x._2.orNull, x._1._2, "Feature"))
                    }
                    else
                        None
                    (world :: schematic :: Nil).flatten
                }
            )
        jsons.saveToCassandra (keyspace, "geojson_lines", SomeColumns ("simulation", "mrid", "coordinate_system", "geometry", "properties", "transformer", "type"), WriteConf.fromSparkConf (session.sparkContext.getConf).copy (consistencyLevel = ConsistencyLevel.ANY))
    }

    def get_world_points (trafo: SimulationTrafoKreis): Iterable[(Double, Double)] =
    {
        var points =
            for
            {
                raw ← trafo.nodes
                sim_node = raw.asInstanceOf [SimulationNode]
                if null != sim_node.world_position
            }
                yield sim_node.world_position
        for
        {
            raw ← trafo.edges
            sim_edge = raw.asInstanceOf [SimulationEdge]
            if null != sim_edge.world_position
        }
            points = points ++ sim_edge.world_position
        points
    }

    def get_schematic_points (trafo: SimulationTrafoKreis): Iterable[(Double, Double)] =
    {
        var points =
            for
            {
                raw ← trafo.nodes
                sim_node = raw.asInstanceOf [SimulationNode]
                if null != sim_node.schematic_position
            }
                yield sim_node.schematic_position
        for
        {
            raw ← trafo.edges
            sim_edge = raw.asInstanceOf [SimulationEdge]
            if null != sim_edge.schematic_position
        }
            points = points ++ sim_edge.schematic_position
        points
    }

    def store_geojson_polygons (trafos: RDD[SimulationTrafoKreis], extra: RDD[Properties]): Unit =
    {
        val jsons = trafos.keyBy (trafo ⇒ trafo.simulation + "_" + trafo.transformer.transformer_name).leftOuterJoin (extra).values
            .flatMap (
                (x: (SimulationTrafoKreis, Option[KeyValueList])) ⇒
                {
                    val trafo = x._1
                    val world_points = get_world_points (trafo).toList
                    val world = if (world_points.nonEmpty)
                    {
                        val hull = Hull.scan (world_points.toList).map (p ⇒ List (p._1, p._2))
                        val coordinates: List[List[List[Double]]] = List (hull)
                        val geometry = ("Polygon", coordinates)
                        val properties = x._2.orNull
                        Some ((trafo.simulation, trafo.transformer.transformer_name, "wgs84", geometry, properties, "Feature"))
                    }
                    else
                        None
                    val schematic_points = get_schematic_points (trafo).toList
                    val schematic = if (schematic_points.nonEmpty)
                    {
                        val hull = Hull.scan (schematic_points.toList).map (p ⇒ List (p._1, p._2))
                        val coordinates: List[List[List[Double]]] = List (hull)
                        val geometry = ("Polygon", coordinates)
                        val properties = x._2.orNull
                        Some ((trafo.simulation, trafo.transformer.transformer_name, "pseudo_wgs84", geometry, properties, "Feature"))
                    }
                    else
                        None
                    (world :: schematic :: Nil).flatten
                }
        )
        jsons.saveToCassandra (keyspace, "geojson_polygons", SomeColumns ("simulation", "mrid", "coordinate_system", "geometry", "properties", "type"), WriteConf.fromSparkConf (session.sparkContext.getConf).copy (consistencyLevel = ConsistencyLevel.ANY))
    }

    def storeGeometry (trafos: RDD[SimulationTrafoKreis]): Unit =
    {
        val extra = query_extra
        store_geojson_points (trafos, extra)
        store_geojson_lines (trafos, extra)
        store_geojson_polygons (trafos, extra)
    }
}

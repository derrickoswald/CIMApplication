package ch.ninecode.sim

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
//import scala.collection.JavaConverters._

import com.datastax.spark.connector._

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
            .map (
                (x: ((Simulation, Transformer, SimulationNode), Option[KeyValueList])) ⇒
                {
                    val node = x._1._3
                    val geometry = ("Point", List (node.position._1, node.position._2))
                    val properties = x._2.orNull
                    (x._1._1, node.equipment, geometry, properties, x._1._2, "Feature")
                }
            )
        jsons.saveToCassandra (keyspace, "geojson_points")
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
            .map (
                (x: ((Simulation, Transformer, SimulationEdge), Option[KeyValueList])) ⇒
                {
                    val edge = x._1._3
                    val coordinates = edge.position.map (p ⇒ List (p._1, p._2)).toList
                    val geometry = ("LineString", coordinates)
                    val properties = x._2.orNull
                    (x._1._1, edge.rawedge.id, geometry, properties, x._1._2, "Feature")
                }
            )
        jsons.saveToCassandra (keyspace, "geojson_lines")
    }

    def get_points (trafo: SimulationTrafoKreis): Iterable[(Double, Double)] =
    {
        var points =
            for (raw <- trafo.nodes)
                yield raw.asInstanceOf [SimulationNode].position
        for (raw <- trafo.edges)
            points = points ++ raw.asInstanceOf [SimulationEdge].position.toIterable
        points
    }

    def store_geojson_polygons (trafos: RDD[SimulationTrafoKreis], extra: RDD[Properties]): Unit =
    {
        val jsons = trafos.keyBy (trafo ⇒ trafo.simulation + "_" + trafo.transformer.transformer_name).leftOuterJoin (extra).values
            .map (
                (x: (SimulationTrafoKreis, Option[KeyValueList])) ⇒
                {
                    val trafo = x._1
                    val hull = Hull.scan (get_points (trafo).toList).map (p ⇒ List (p._1, p._2))
                    val coordinates: List[List[List[Double]]] = List (hull)
                    val geometry = ("Polygon", coordinates)
                    val properties = x._2.orNull
                    (trafo.simulation, trafo.transformer.transformer_name, geometry, properties, "Feature")
                }
        )
        jsons.saveToCassandra (keyspace, "geojson_polygons")
    }

    def storeGeometry (trafos: RDD[SimulationTrafoKreis]): Unit =
    {
        val extra = query_extra
        store_geojson_points (trafos, extra)
        store_geojson_lines (trafos, extra)
        store_geojson_polygons (trafos, extra)
    }
}

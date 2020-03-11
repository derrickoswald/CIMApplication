package ch.ninecode.sim

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.datastax.driver.core.ConsistencyLevel
import com.datastax.spark.connector._
import com.datastax.spark.connector.writer.WriteConf

import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.LineEdge
import ch.ninecode.model._

case class SimulationGeometry (session: SparkSession, keyspace: String) extends CIMRDD
{
    implicit val log: Logger = LoggerFactory.getLogger (getClass)
    implicit val spark: SparkSession = session

    type mRID = String
    type StationmRID = String
    type TargetmRID = String
    type DiagrammRID =String
    type NodemRID = String
    type EquipmentmRID = String
    type IslandmRID = String
    // the Id is a concatenation of simulation and mRID: "simulation_mrid"
    type Id = String
    type Key = String
    type Value = String
    type KeyValue = (Key, Value)
    type KeyValueList = Iterable[KeyValue]
    type Properties = (Id, KeyValueList)
    type Simulation = String
    type Transformer = String
    type CoordinateSystem = String

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
     * @param simulation the simulation id to query extra properties for
     * @return the PairRDD with mrid as the key and a list of key-value pairs as the value
     */
    def query_extra (simulation: String): RDD[Properties] =
    {
        val df = session.read.format ("org.apache.spark.sql.cassandra")
            .options (Map ("keyspace" -> keyspace, "table" -> "key_value"))
            .load
            .where (s"simulation == '$simulation'")
        if (!df.isEmpty)
        {
            df.select ("key", "query", "value").rdd
                .map (row ⇒ (row.getString (0), (row.getString (1), row.getString (2))))
                .groupByKey
        }
        else
            session.sparkContext.emptyRDD
    }

    /**
     * Store the GeoJSON point coordinates for EnergyConsumer in each simulation
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
                                node.equipment,
                                (trafo.simulation, trafo.transformer.transformer_name, node)
                            )
                    )
            )
        val consumers = nodes.join (getOrElse[EnergyConsumer].keyBy (_.id)).values.map (x ⇒ (x._1._3.equipment, x._1))
        val jsons = consumers.leftOuterJoin (extra).values
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
                                edge.rawedge.id,
                                (trafo.simulation, trafo.transformer.transformer_name, edge)
                            )
                    )
            )

        val jsons = edges.leftOuterJoin (extra).values
            .flatMap (
                (x: ((Simulation, Transformer, SimulationEdge), Option[KeyValueList])) ⇒
                {
                    val edge = x._1._3
                    edge.rawedge match
                    {
                        case _: LineEdge =>
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
                        case _ =>
                            List ()
                    }
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
                sim_node = raw.asInstanceOf[SimulationNode]
                if null != sim_node.world_position
            }
                yield sim_node.world_position
        for
        {
            raw ← trafo.edges
            sim_edge = raw.asInstanceOf[SimulationEdge]
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
                sim_node = raw.asInstanceOf[SimulationNode]
                if null != sim_node.schematic_position
            }
                yield sim_node.schematic_position
        for
        {
            raw ← trafo.edges
            sim_edge = raw.asInstanceOf[SimulationEdge]
            if null != sim_edge.schematic_position
        }
            points = points ++ sim_edge.schematic_position
        points
    }

    def store_geojson_polygons (trafos: RDD[SimulationTrafoKreis], extra: RDD[Properties]): Unit =
    {
        val jsons = trafos.keyBy (_.transformer.transformer_name).leftOuterJoin (extra).values
            .flatMap (
                (x: (SimulationTrafoKreis, Option[KeyValueList])) ⇒
                {
                    val trafo = x._1
                    val world_points = get_world_points (trafo).toList
                    val world = if (world_points.nonEmpty)
                    {
                        val hull = Hull.scan (world_points).map (p ⇒ List (p._1, p._2))
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
                        val hull = Hull.scan (schematic_points).map (p ⇒ List (p._1, p._2))
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

    def store_geojson_transformers (trafos: RDD[SimulationTrafoKreis], extra: RDD[Properties]): Unit =
    {
        // get the few thousand transformer names with transformer id(s) and location
        val transformers = trafos.flatMap (trafo ⇒ trafo.transformer.transformers.map (
            x ⇒ (trafo.name, x.transformer.id, x.transformer.ConductingEquipment.Equipment.PowerSystemResource.Location)))
        // get the world positions
        val location_trafo = transformers.map (x ⇒ (x._3, x._1)).collectAsMap
        val world_positions: RDD[(String, (Double, Double))] = get[PositionPoint].flatMap (
            point ⇒
            {
                val location = point.Location
                if (location_trafo.contains (location))
                {
                    val trafo = location_trafo(location)
                    val x = point.xPosition.toDouble
                    val y = point.yPosition.toDouble
                    Some (trafo, (x, y))
                }
                else
                    None
            }
        )
        val transformer_trafo = transformers.map (x ⇒ (x._2, x._1)).collectAsMap
        val diagram_trafo = getOrElse[DiagramObject].flatMap (
            obj ⇒
            {
                val id = obj.IdentifiedObject_attr
                if (transformer_trafo.contains (id))
                {
                    val trafo = transformer_trafo(id)
                    Some (obj.id, trafo)
                }
                else
                    None
            }
        ).collectAsMap
        val schematic_points: RDD[(String, (Double, Double))] = getOrElse[DiagramObjectPoint].flatMap (
            point ⇒
            {
                val obj = point.DiagramObject
                if (diagram_trafo.contains (obj))
                {
                    val trafo = diagram_trafo(obj)
                    val x = point.xPosition.toDouble
                    val y = point.yPosition.toDouble
                    Some (trafo, (x, y))
                }
                else
                    None
            }
        )
        def make_trafo (stuff: ((SimulationTrafoKreis, Option[KeyValueList]), Option[((Double, Double), Option[(Double, Double)])])):
            List[(Simulation, String, Value, Set[Transformer], String, (Value, List[Double]), KeyValueList)] =
        {
            stuff._2 match
            {
                case Some (x) ⇒
                    val trafo = stuff._1._1
                    val geometry = ("Point", List (x._1._1, x._1._2))
                    val properties = stuff._1._2.orNull
                    val trafos = trafo.transformer.transformers.map (_.transformer.id).toSet
                    val one = (trafo.simulation, "wgs84", trafo.transformer.transformer_name, trafos, "Feature", geometry, properties)
                    val two = x._2 match
                    {
                        case Some (coords) ⇒
                            val geometry2 = ("Point", List (coords._1, coords._2))
                            Some (trafo.simulation, "pseudo_wgs84", trafo.transformer.transformer_name, trafos, "Feature", geometry2, properties)
                        case None ⇒
                            None
                    }
                    (Some(one) :: two :: Nil).flatten
                case None ⇒ List()
            }
        }
        val with_properties = trafos.keyBy (_.transformer.transformer_name).leftOuterJoin (extra).values
        val jsons = with_properties.keyBy (_._1.name).leftOuterJoin (world_positions.leftOuterJoin (schematic_points)).values.flatMap (make_trafo)
        jsons.saveToCassandra (keyspace, "geojson_transformers", SomeColumns ("simulation", "coordinate_system", "mrid", "transformers", "type", "geometry", "properties"), WriteConf.fromSparkConf (session.sparkContext.getConf).copy (consistencyLevel = ConsistencyLevel.ANY))
    }

    def store_geojson_stations (trafos: RDD[SimulationTrafoKreis], extra: RDD[Properties]): Unit =
    {
        // get a map of islands to Simulation & Transformer
        val transformers: RDD[(NodemRID, (Simulation, Transformer))] = trafos.map (kreis ⇒ (kreis.transformer.node1, (kreis.simulation, kreis.transformer.transformer_name)))
        val nodes_islands: RDD[(NodemRID, IslandmRID)] = getOrElse[TopologicalNode].map (node ⇒ (node.id, node.TopologicalIsland))
        val islands_transformers: RDD[(IslandmRID, (Simulation, Transformer))] = nodes_islands.join (transformers).values

        // get a map of equipment containers and islands
        val terminals = getOrElse[Terminal]
        val psrtypes = Array ("PSRType_Substation", "PSRType_TransformerStation", "PSRType_DistributionBox")
        val containers: RDD[Substation] = getOrElse[Substation].filter (station ⇒ psrtypes.contains (station.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType))
        val equipment_islands: RDD[(EquipmentmRID, IslandmRID)] = terminals.map (terminal ⇒ (terminal.TopologicalNode, terminal.ConductingEquipment)).join (nodes_islands).values
        val equipment_containers: RDD[(EquipmentmRID, StationmRID)] = getOrElse[Equipment].map (equipment ⇒ (equipment.id, equipment.EquipmentContainer))
        val islands_containers: RDD[(IslandmRID, StationmRID)] = equipment_islands.join (equipment_containers).values.distinct

        val stations: RDD[(StationmRID, (Simulation, Transformer))] = islands_containers.join (islands_transformers).values
        val diagramObjects: RDD[(TargetmRID, DiagramObject)] = getOrElse[DiagramObject].keyBy (_.IdentifiedObject_attr)
        val diagramObjectsPoints: RDD[(DiagrammRID, Iterable[DiagramObjectPoint])] = getOrElse[DiagramObjectPoint].keyBy (_.DiagramObject).groupByKey
        val station_diagram: RDD[(StationmRID, ((Simulation, Transformer), DiagramObject))] = stations.join (diagramObjects)

        val diagramid_station: RDD[(DiagrammRID, (StationmRID, Simulation, Transformer))] = station_diagram.map (x ⇒ (x._2._2.id, (x._1, x._2._1._1, x._2._1._2)))
        val stations_with_geometry: RDD[((StationmRID, Simulation, Transformer), Iterable[DiagramObjectPoint])] = diagramid_station.join (diagramObjectsPoints).values
        val stations_with_geometry_keyed: RDD[(String, (StationmRID, Simulation, Transformer, Iterable[DiagramObjectPoint]))] = stations_with_geometry.map (x ⇒ (x._1._1, (x._1._1, x._1._2, x._1._3, x._2)))
        val stations_with_everything: RDD[((StationmRID, Simulation, Transformer, Iterable[DiagramObjectPoint]), Option[KeyValueList])] = stations_with_geometry_keyed.leftOuterJoin (extra).values
        val rearranged2: RDD[(Simulation, StationmRID, Transformer, Iterable[DiagramObjectPoint], Option[KeyValueList])] = stations_with_everything.map (x ⇒ (x._1._2, x._1._1, x._1._3, x._1._4, x._2))

        val geojson_station: RDD[(Simulation, CoordinateSystem, StationmRID, Transformer, String, (String, List[List[List[Double]]]), KeyValueList)] = rearranged2.map (
            x ⇒
            {
                val simulation = x._1
                val station = x._2
                val transformer = x._3
                val coords = x._4
                val coordinates = List (coords.toList.sortWith (_.sequenceNumber < _.sequenceNumber).map (y ⇒ List (y.xPosition, y.yPosition)))
                val geometry = ("Polygon", coordinates)
                val properties = x._5.orNull
                (simulation, "pseudo_wgs84", station, transformer, "Feature", geometry, properties)
            }
        )
        geojson_station.saveToCassandra (keyspace, "geojson_stations", SomeColumns ("simulation", "coordinate_system", "mrid", "transformer", "type", "geometry", "properties"), WriteConf.fromSparkConf (session.sparkContext.getConf).copy (consistencyLevel = ConsistencyLevel.ANY))
    }

    def storeGeometry (simulation: String, trafos: RDD[SimulationTrafoKreis]): Unit =
    {
        val extra = query_extra (simulation)
        store_geojson_points (trafos, extra)
        store_geojson_lines (trafos, extra)
        store_geojson_polygons (trafos, extra)
        store_geojson_transformers (trafos, extra)
        store_geojson_stations (trafos, extra)
    }
}

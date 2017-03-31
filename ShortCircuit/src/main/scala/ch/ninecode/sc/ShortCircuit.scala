package ch.ninecode.sc

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.HashMap
import java.util.Map

import scala.Iterator
import scala.reflect.runtime.universe
import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.Graph.graphToGraphOps
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.{StructType, StructField, StringType, IntegerType, DoubleType};
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim._
import ch.ninecode.model._

// define case classes that need to be pattern matched
// due to a flaw in Scala pattern matching with classes defined within another class
// we have to define these classes external to the main class,
// otherwise we have to pattern match on Any and then cast it asInstanceOf[whatever]

// create a holder for pre-computed transformer power availabaility
case class ShortCircuitData (mRID: String, Sk: Double, Ikw: Double, valid: Boolean)

// create a basket to hold all transformer data
case class TransformerData (transformer: PowerTransformer, end1: PowerTransformerEnd, terminal1: Terminal, v1: Double, end2: PowerTransformerEnd, terminal2: Terminal, v2: Double, substation: Substation, short_circuit: ShortCircuitData)

// define the message class
// this is used in the VertexData class to avoid reallocating another object that stores the same information as the message
case class Message (transformer: String, r: Double, x: Double, r0: Double, x0: Double, fuses: List[Tuple2[String,Double]], valid: Boolean) extends Serializable

// define the data attached to each vertex
case class VertexData (val id: String, val name: String, val container: String, val start: TransformerData, val stop: Boolean, var message: Message, var valid: Boolean)

class ShortCircuit extends Serializable
{
    var _StorageLevel = StorageLevel.MEMORY_ONLY

    // name of file containing short circuit Ikw and Sk values for medium voltage transformers
    // e.g.
    //
    // "","Fehlerort","Un","Ikw...RST.","Sk..RST.","Beschreibung..SAP.Nr..","Abgang","NIS.ID","NIS.Name"
    // "1","Scheidbach Turbach",16,-37.34,89.733,20444,"SAA Lauenen","STA2040","Scheidbach"
    // "2","Bachegg",16,-36.22,83.805,20468,"SAA Lauenen","STA9390","Bachegg"
    //
    // this should only be needed until the medium voltage network is fully described and  calculations can
    // be done from the high voltage network "slack bus" connections
    var csv = "hdfs://sandbox:8020/data/KS_Leistungen.csv"

    // define the augmented edge class
    case class EdgePlus (id_seq_1: String, id_seq_2: String, id_equ: String, clazz: String, name: String, aliasName: String, container: String, length: Double, voltage: String, normalOpen: Boolean, ratedCurrent: Double, x1: String, y1: String, x2: String, y2: String, r: Double, x: Double, r0: Double, x0: Double, valid: Boolean)

    // class to return the transformer id values
    case class TransformerName (id: String)

    case class HouseConnection (mRID: String, node: String, transformer: TransformerData, r: Double, x: Double, r0: Double, x0: Double, fuses: List[Tuple2[String,Double]], wires_valid: Boolean, ik: Double = 0.0, ik3pol: Double = 0.0, ip: Double = 0.0, fuse_valid: Boolean = false)

    case class Result (mRID: String, node: String, transformer: String, r: Double, x: Double, r0: Double, x0: Double, fuses: String, ik: Double = 0.0, ik3pol: Double = 0.0, ip: Double = 0.0, wires_valid:Boolean, transformer_valid: Boolean, fuse_valid: Boolean, location_x: String = "0.0", location_y: String = "0.0")

    def dv (v: BaseVoltage) =
    {
        (v.id, v.nominalVoltage)
    }

    def vertex_id (string: String): VertexId =
    {
        string.hashCode().asInstanceOf[VertexId]
    }

    def get (name: String, context: SparkContext): RDD[Element] =
    {
        val rdds = context.getPersistentRDDs
        for (key <- rdds.keys)
        {
            val rdd = rdds (key)
            if (rdd.name == name)
                return (rdd.asInstanceOf[RDD[Element]])
        }
        return (null)
    }

    def read_csv (sqlContext: SQLContext): RDD[ShortCircuitData] =
    {
        val customSchema = StructType (
            Array
            (
                StructField ("id", StringType, true),
                StructField ("Fehlerort", StringType, true),
                StructField ("Un", DoubleType, true),
                StructField ("Ikw...RST.", DoubleType, true),
                StructField ("Sk..RST.", DoubleType, true),
                StructField ("Beschreibung..SAP.Nr..", StringType, true),
                StructField ("Abgang", StringType, true),
                StructField ("NIS.ID", StringType, true),
                StructField ("NIS.Name", StringType, true)
            )
        )

        val df = sqlContext.read
            .format ("csv")
            .option ("header", "true")
            .schema (customSchema)
            .csv (csv)

        import sqlContext.implicits._
        df.map ( r => ShortCircuitData (r.getString (7), r.getDouble (4), r.getDouble (3), true) ).rdd
    }

    def preparation (sc: SparkContext, sqlContext: SQLContext, args: String): DataFrame  =
    {
        val arguments = args.split (",").map (
            (s) =>
                {
                    val pair = s.split ("=")
                    if (2 == pair.length)
                        (pair(0), pair(1))
                    else
                        (pair(0), "")
                }
        ).toMap

        // gather the set of voltages
        // usage: voltages.getOrElse ("BaseVoltage_400", 0.0)  yields 0.4 as a Double
        val voltages = get ("BaseVoltage", sc).asInstanceOf[RDD[ch.ninecode.model.BaseVoltage]].map (dv).collectAsMap ()

        // get the name of the csv file
        csv = arguments.getOrElse ("csv", csv)

        // get all transformers in substations
        val transformers = get ("PowerTransformer", sc).asInstanceOf[RDD[PowerTransformer]]
        val substation_transformers = transformers.filter ((t: PowerTransformer) => { (t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name != "Messen_Steuern") })

        // get an RDD of substations by filtering out distribution boxes
        val stations = get ("Substation", sc).asInstanceOf[RDD[Substation]].filter (_.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType == "PSRType_TransformerStation")

        // the Equipment container for a transformer could be a Bay, VoltageLevel or Station... the first two of which have a reference to their station
        def station_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                case (key: String, (t: PowerTransformer, station: Substation)) =>
                {
                    (station.id, t)
                }
                case (key: String, (t: PowerTransformer, bay: Bay)) =>
                {
                    (bay.Substation, t)
                }
                case (key: String, (t: PowerTransformer, level: VoltageLevel)) =>
                {
                    (level.Substation, t)
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        // create an RDD of transformer-container pairs, e.g. { (TRA13730,KAB8526), (TRA4425,STA4551), ... }
        val elements = get ("Elements", sc).asInstanceOf[RDD[Element]]
        val tpairs = substation_transformers.keyBy(_.ConductingEquipment.Equipment.EquipmentContainer).join (elements.keyBy (_.id)).map (station_fn)
//        tpairs.count
//        tpairs.first

        val short_circuit = read_csv (sqlContext)
//        short_circuit.count
//        short_circuit.first

        // only keep the pairs where the transformer is in a substation we have
        val transformers_stations = tpairs.join (stations.keyBy (_.id)).values
//        transformers_stations.count
//        transformers_stations.first

        def transformer_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                case (key: String, ((a: PowerTransformer, b: Substation), Some (c: ShortCircuitData))) =>
                {
                    (a, b, c)
                }
                case (key: String, ((a: PowerTransformer, b: Substation), None)) =>
                {
                    (a, b, ShortCircuitData (b.id, 200, -70, false))
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        val transformers_short_circuit = transformers_stations.keyBy (_._2.id).leftOuterJoin (short_circuit.keyBy (_.mRID)).map (transformer_fn)

        // get all transformers with their end data

        // get the ends
        val ends = get ("PowerTransformerEnd", sc).asInstanceOf[RDD[PowerTransformerEnd]]
        // Note: if the end count does not equal twice the number of transformers, we have a three (or more) terminal transformer and hence a problem

        // get the terminals
        val terminals = get ("Terminal", sc).asInstanceOf[RDD[Terminal]]

        // keep only terminals for transformer ends
        val transformer_terminals = terminals.keyBy (_.id).join (ends.keyBy (_.TransformerEnd.Terminal)).values

        // get the list of transformers, with their ends, terminals and voltage
        def transformer_end_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                case (key: String, (iterator: Iterable[Any], (transformer: PowerTransformer, substation: Substation, short_circuit: ShortCircuitData))) =>
                {
                    val i1 = iterator.head.asInstanceOf[(Terminal, PowerTransformerEnd)]
                    val i2 = iterator.last.asInstanceOf[(Terminal, PowerTransformerEnd)] // ToDo: three (or more) terminal transformer
                    val v_a = voltages.getOrElse (i1._2.TransformerEnd.BaseVoltage, 0.0) * 1000.0
                    val v_b = voltages.getOrElse (i2._2.TransformerEnd.BaseVoltage, 0.0) * 1000.0
                    if (v_a > v_b)
                        TransformerData (transformer, i1._2, i1._1, v_a, i2._2, i2._1, v_b, substation, short_circuit)
                    else
                        TransformerData (transformer, i2._2, i2._1, v_b, i1._2, i1._1, v_a, substation, short_circuit)
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        val transformer_and_transformer_terminals = transformer_terminals.keyBy (_._1.ConductingEquipment).groupByKey.join (transformers_short_circuit.keyBy (_._1.id)).map (transformer_end_fn)

        // get the low voltage (niederspannung) transformers
        val ns_transformers = transformer_and_transformer_terminals.filter ((t: TransformerData) => { 400.0 == t.v2 })

        // persist RDD so later execution can get at it
        ns_transformers.setName ("graph_transformers")
        ns_transformers.persist (_StorageLevel)

        def tx_fn (t: TransformerData) =
        {
            TransformerName (t.transformer.id)
        }
        val tx = ns_transformers.map (tx_fn)

        return (sqlContext.createDataFrame (tx))
    }

    def get_vertices (ns_transformers: RDD[TransformerData], context: SparkContext): RDD[(VertexId, VertexData)] =
    {
        // convert CIM nodes into Graphx vertices as RDD of (key, value) pairs

        // get the list of nodes
        val nodes = get ("ConnectivityNode", context).asInstanceOf[RDD[ConnectivityNode]]

        def node_function (x: Tuple2[String, Any]) =
        {
            x match
            {
                case (key: String, (n: ConnectivityNode, Some (t: TransformerData) )) =>
                {
                    (vertex_id (key), VertexData (key, n.IdentifiedObject.name, n.ConnectivityNodeContainer, t, t.v1 > 400.0, Message (null, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, List(), true), true))
                }
                case (key: String, (n: ConnectivityNode, None)) =>
                {
                    (vertex_id (key), VertexData (key, n.IdentifiedObject.name, n.ConnectivityNodeContainer, null, false, Message (null, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, List(), true), true))
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        // get starting nodes identified by non-null transformer data
        val vertices = nodes.keyBy (_.id).leftOuterJoin (ns_transformers.keyBy (_.terminal2.ConnectivityNode)).map (node_function)
        vertices.setName ("graph_vertices")
        vertices.persist (_StorageLevel)
    }

    def get_edges (context: SparkContext): RDD[Edge[EdgePlus]] =
    {
        val cim_edges = get ("Edges", context).asInstanceOf[RDD[PostEdge]]
//        cim_edges.count
//        cim_edges.first

        // keep only non-self connected and non-singly connected edges
        val someedges =  cim_edges.filter ((e: PostEdge) => { (e.id_seq_1 != e.id_seq_2) && e.id_seq_1 != null && e.id_seq_2 != null && e.id_seq_1 != "" && e.id_seq_2 != "" })
//        someedges.count
//        someedges.first

        // get the wires
        val segments = get ("ACLineSegment", context).asInstanceOf[RDD[ACLineSegment]]
//        segments.count
//        segments.first

        def fn (x: Tuple2[String, Any]) =
        {
            val ep = x match
            {
                case (key: String, (e: PostEdge, Some(wire: ACLineSegment))) =>
                {
                    // default line impedance: R=0.124 Ohms/km, R0=0.372 Ohms/km, X=0.61 Ohms/km, X0=0.204 Ohms/km
                    if (0.0 != wire.r)
                        EdgePlus (e.id_seq_1, e.id_seq_2, e.id_equ, e.clazz, e.name, e.aliasName, e.container, e.length, e.voltage, e.normalOpen, e.ratedCurrent, e.x1, e.y1, e.x2, e.y2, wire.r, wire.x, wire.r0, wire.x0, true)
                    else
                        EdgePlus (e.id_seq_1, e.id_seq_2, e.id_equ, e.clazz, e.name, e.aliasName, e.container, e.length, e.voltage, e.normalOpen, e.ratedCurrent, e.x1, e.y1, e.x2, e.y2, 0.124, 0.61, 0.372, 0.204, false)
                }
                case (key: String, (e: PostEdge, None)) =>
                {
                    EdgePlus (e.id_seq_1, e.id_seq_2, e.id_equ, e.clazz, e.name, e.aliasName, e.container, e.length, e.voltage, e.normalOpen, e.ratedCurrent, e.x1, e.y1, e.x2, e.y2, 0.0, 0.0, 0.0, 0.0, false)
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
            Edge (vertex_id (ep.id_seq_1), vertex_id (ep.id_seq_2), ep)
        }

        // convert CIM edges into GraphX edges
        val edges = someedges.keyBy(_.id_equ).leftOuterJoin (segments.keyBy (_.id)).map (fn)
        edges.setName ("graph_edges")
        edges.persist (_StorageLevel)
    }

    def stuff (sc: SparkContext, sqlContext: SQLContext, args: String): DataFrame =
    {
        val arguments = args.split (",").map (
            (s) =>
                {
                    val pair = s.split ("=")
                    if (2 == pair.length)
                        (pair(0), pair(1))
                    else
                        (pair(0), "")
                }
        ).toMap

        var ns_transformers = get ("graph_transformers", sc).asInstanceOf[RDD[TransformerData]]
        if (null == ns_transformers)
        {
            preparation (sc, sqlContext, args);
            ns_transformers = get ("graph_transformers", sc).asInstanceOf[RDD[TransformerData]]
        }

        val vertices = get_vertices (ns_transformers, sc)
        val edges = get_edges (sc)

        // get the specified transformer
        val tran = arguments.getOrElse ("transformer", "")

        // filter out all but the specified transformer
        def filterx (v: Tuple2[VertexId, VertexData]): Tuple2[VertexId, VertexData] =
        {
            return ((v._1, VertexData (v._2.id, v._2.name, v._2.container, if ((null != v._2.start) && (v._2.start.transformer.id == tran)) v._2.start else null, v._2.stop, v._2.message, v._2.valid)))
        }
        var some_vertices: RDD[(VertexId, VertexData)] = null
        if ((tran != "") && (tran.toLowerCase () != "all"))
            some_vertices = vertices.map (filterx)
        else
            some_vertices = vertices

        // construct the initial graph from the augmented elements (vertices) and edges
        val default = VertexData ("", "", "", null, true, Message (null, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, List[Tuple2[String,Double]] (), false), false)
        val initial = Graph.apply[VertexData, EdgePlus] (some_vertices, edges, default)

        // do the Pregel algorithm
        def vprog (id: VertexId, v: VertexData, message: Message): VertexData =
        {
            var ret = v

            if (null == message) // initial message
            {
                if (null != v.start) // is this node a low voltage transformer terminal
                    ret = VertexData (v.id, v.name, v.container, v.start, v.stop, Message (v.start.transformer.id, 0.0, 0.0, 0.0, 0.0, List (), true), v.valid)
            }
            else // subsequent messages
                ret = VertexData (v.id, v.name, v.container, v.start, v.stop, message, v.valid)

            return (ret)
        }

        def sendMessage (triplet: EdgeTriplet[VertexData, EdgePlus]): Iterator[(VertexId, Message)] =
        {
            var ret:Iterator[(VertexId, Message)] = Iterator.empty

            // check for normalOpen switch
            if (!triplet.attr.normalOpen)
            {
                val km = triplet.attr.length / 1000.0
                // compute the impedences to the downstream vertex
                if ((Double.PositiveInfinity != triplet.srcAttr.message.r)
                    && (   (Double.PositiveInfinity == triplet.dstAttr.message.r) // dst has not yet recieved a message
                        || (triplet.srcAttr.message.r + km * triplet.attr.r < triplet.dstAttr.message.r))) // handle mesh network, note r may be 0.0 if it's a source bus
                {
                    // check for high voltage terminal
                    if (!triplet.dstAttr.stop)
                    {
                        val r = triplet.srcAttr.message.r + km * triplet.attr.r
                        val x = triplet.srcAttr.message.x + km * triplet.attr.x
                        val r0 = triplet.srcAttr.message.r0 + km * triplet.attr.r0
                        val x0 = triplet.srcAttr.message.x0 + km * triplet.attr.x0
                        var fuses = triplet.srcAttr.message.fuses
                        if (triplet.attr.clazz.endsWith ("Fuse"))
                            fuses = (triplet.attr.id_equ, triplet.attr.ratedCurrent) :: fuses
                        val m = Message (triplet.srcAttr.message.transformer, r, x, r0, x0, fuses, triplet.srcAttr.message.valid && triplet.srcAttr.valid)
                        ret = Iterator ((triplet.dstId, m))
                    }
                }
                // else compute the impedences to the upstream vertex
                else if ((Double.PositiveInfinity != triplet.dstAttr.message.r)
                    && (   (Double.PositiveInfinity == triplet.srcAttr.message.r)
                        || (triplet.dstAttr.message.r + km * triplet.attr.r < triplet.srcAttr.message.r)))
                {
                    // check for high voltage terminal
                    if (!triplet.srcAttr.stop)
                    {
                        val r = triplet.dstAttr.message.r + km * triplet.attr.r
                        val x = triplet.dstAttr.message.x + km * triplet.attr.x
                        val r0 = triplet.dstAttr.message.r0 + km * triplet.attr.r0
                        val x0 = triplet.dstAttr.message.x0 + km * triplet.attr.x0
                        var fuses = triplet.dstAttr.message.fuses
                        if (triplet.attr.clazz.endsWith ("Fuse"))
                            fuses = (triplet.attr.id_equ, triplet.attr.ratedCurrent) :: fuses
                        val m = Message (triplet.dstAttr.message.transformer, r, x, r0, x0, fuses, triplet.dstAttr.message.valid && triplet.dstAttr.valid)
                        ret = Iterator ((triplet.srcId, m))
                    }
                }
            }

            return (ret)
        }

        def mergeMessage (a: Message, b: Message): Message =
        {

            var ret: Message = null
            // throw new Exception ("" + a + " " + b)
            // ToDo: handle multiple sources
            if ((null != a) && (null != b))
                if (a.r < b.r)
                    ret = a
                else
                    ret = b
            return (ret)
        }

        val graph = initial.pregel (null.asInstanceOf[Message], 50 /* Int.MaxValue */, EdgeDirection.Either) (vprog, sendMessage, mergeMessage)

        // get the leaf nodes with their data
        val has = graph.vertices.filter (
            (v: Tuple2[VertexId, VertexData]) =>
            {
                (v._2.name.startsWith ("HAS") || v._2.name.startsWith ("HAK")) && v._2.message.r != Double.PositiveInfinity
            }
        ).values
        has.setName ("house_connections")
        has.persist (_StorageLevel)

        // link them to the supplying transformer
        def house_transformer_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                case (key: String, (v: VertexData, t: TransformerData)) =>
                {
                    HouseConnection (v.name, v.id, t, v.message.r, v.message.x, v.message.r0, v.message.x0, v.message.fuses, v.message.valid)
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }
        val houses = has.keyBy (_.message.transformer).join (ns_transformers.keyBy (_.transformer.id)).map (house_transformer_fn)

        // Rated current, Breaking capacity [A]
        val breaking_capacity: Array[(Double, Double)] =
            Array (
                (0, 0),
                (6, 17),
                (10, 28),
                (13, 60),
                (15, 39),
                (16, 40),
                (20, 55),
                (25, 70),
                (32, 93),
                (35, 95),
                (40, 120),
                (50, 160),
                (60, 187),
                (63, 190),
                (75, 150),
                (80, 230),
                (100, 305),
                (125, 380),
                (150, 320),
                (160, 490),
                (200, 690),
                (250, 820),
                (300, 650),
                (315, 1150),
                (350, 1250),
                (355, 1250),
                (400, 1350),
                (500, 1900),
                (600, 1300),
                (630, 2500)
            )
        def current_max (amps: Double): Double =
        {
            val less = breaking_capacity.filter (_._1 < amps)
            if (1 <= less.length)
                less.last._2
            else
                amps * 1e6
        }

        // compute the house connection short-circuit values
        def calculate_short_circuit (house: HouseConnection): HouseConnection =
        {
            val c = 0.90
            val cmin = 0.90
            val v1 = house.transformer.v1
            val v2 = house.transformer.v2
            val sk = house.transformer.short_circuit.Sk * 1e+6
            val turns_ratio = v2 / v1
            val zqt = c * v1 * v1 / sk * (turns_ratio * turns_ratio)
            val wik = house.transformer.short_circuit.Ikw
            val wik_radians = Math.PI / 180.0 * wik
            val netz_r1 = zqt * Math.cos (wik_radians)
            val netz_x1 = zqt * Math.sin (wik_radians)

            val r_total = house.r + house.transformer.end2.r + netz_r1 // ToDo: check transformer secondary resistance, that is end2
            val x_total = house.x + house.transformer.end2.x + Math.abs (netz_x1) // ToDo: check why abs is necessary
            val r_secondary = house.r + house.transformer.end2.r
            val x_secondary = house.x + house.transformer.end2.x
            val impedance = Math.sqrt (r_total * r_total + x_total * x_total)

            // Einpoligen Kurzschlussstrom berechnen
            val ik = Math.sqrt (3.0)  * cmin * v2 / (2.0 * impedance + Math.sqrt (r_secondary * r_secondary + x_secondary * x_secondary))

            // Dreipoligen Kurzschlussstrom berechnen
            val ik3pol = 1.1 * v2 / (Math.sqrt (3.0) * impedance)

            // Stosskurzschlussstrom berechnen
            val ip = (1.02 + 0.98 * Math.exp (-3.0 * r_total / x_total)) * Math.sqrt (2) * ik3pol

            // apply rules for fuses
            var fuse_valid = false
            if (2 <= house.fuses.length)
                fuse_valid = house.fuses.tail.head._2 / house.fuses.head._2 > 1.6
            else if (1 <= house.fuses.length)
                fuse_valid = ik > current_max (house.fuses.head._2)

            HouseConnection (house.mRID, house.node, house.transformer, house.r, house.x, house.r0, house.x0, house.fuses, house.wires_valid, ik, ik3pol, ip, fuse_valid)
        }

        val result = houses.map (calculate_short_circuit)

        def localize (x: Tuple2 [HouseConnection, PositionPoint]) =
        {
            val house = x._1
            val point = x._2
            Result (house.mRID, house.node, house.transformer.transformer.id, house.r, house.x, house.r0, house.x0, house.fuses.toString (), house.ik, house.ik3pol, house.ip, house.wires_valid, house.transformer.short_circuit.valid, house.fuse_valid, point.xPosition, point.yPosition)
        }
        val consumers = get ("Fuse", sc).asInstanceOf[RDD[Fuse]]
        val locs = consumers.map ((f: Fuse) => { (f.id, f.Switch.ConductingEquipment.Equipment.PowerSystemResource.Location) })
        val points = get ("PositionPoint", sc).asInstanceOf[RDD[PositionPoint]]
        val final_result = locs.join (result.keyBy (_.mRID)).values.join (points.keyBy (_.Location)).values.map (localize)

        return (sqlContext.createDataFrame (final_result))
    }
}

object ShortCircuit
{
    def jarForObject (obj: Object): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = obj.getClass.getProtectionDomain ().getCodeSource ().getLocation ().getPath ()
        try
        {
            ret = URLDecoder.decode (ret, "UTF-8")
        }
        catch
        {
            case e: UnsupportedEncodingException => e.printStackTrace ()
        }
        if (!ret.toLowerCase ().endsWith (".jar"))
        {
            // as an aid to debugging, make jar in tmp and pass that name
            val name = "/tmp/" + Random.nextInt (99999999) + ".jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (ret + "ch/")), "ch/")
            writer.close ()
            ret = name
        }

        return (ret)
    }

    def main (args: Array[String])
    {
        val shortcircuit = new ShortCircuit ()

        val filename = if (args.length > 0)
            args (0)
        else
            "hdfs://sandbox:8020/data/" + "NIS_CIM_Export_sias_current_20160816_Kiental_V10" + ".rdf"

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("ShortCircuit")
        configuration.setMaster ("spark://sandbox:7077")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "4g")
        // get the necessary jar files to send to the cluster
        val s1 = jarForObject (new DefaultSource ())
        val s2 = jarForObject (shortcircuit)
        configuration.setJars (Array (s1, s2))

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[PostEdge]))
        // register short circuit classes
        configuration.registerKryoClasses (Array (classOf[ShortCircuitData], classOf[TransformerData], classOf[Message], classOf[VertexData]))
        // register short circuit inner classes
        configuration.registerKryoClasses (Array (classOf[ShortCircuit#EdgePlus], classOf[ShortCircuit#TransformerName], classOf[ShortCircuit#HouseConnection], classOf[ShortCircuit#Result]))

        // create a Sprk session
        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val start = System.nanoTime ()
        val files = filename.split (",")
        val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER");
        options.put ("ch.ninecode.cim.make_edges", "true"); // backwards compatibility
        options.put ("ch.ninecode.cim.do_join", "false");
        val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        val count = elements.count

        val read = System.nanoTime ()

        shortcircuit._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER
        shortcircuit.preparation (session.sparkContext, session.sqlContext, "csv=hdfs://sandbox:8020/data/KS_Leistungen.csv")

        val prep = System.nanoTime ()

        val rdd = shortcircuit.stuff (session.sparkContext, session.sqlContext, "transformer=all") // TRA5401

        val graph = System.nanoTime ()

        val results = rdd.collect

        val fetch = System.nanoTime ()

        println (s"""
        id,Name,ik,ik3pol,ip,Transformer,r,x,r0,x0,wires_valid,trafo_valid,fuse_valid,x,y,fuses""")
        for (i <- 0 until results.length)
        {
            val h = results (i)
            println (h.getString(0) + "," + h.getString(1) + "," + h.getDouble(8) + "," + h.getDouble(9) + "," + h.getDouble(10) + "," + h.getString(2) + "," + h.getDouble(3) + "," + h.getDouble(4) + "," + h.getDouble(5) + "," + h.getDouble(6) + "," + h.getBoolean(11) + "," + h.getBoolean(12) + "," + h.getBoolean(13) + "," + h.getString(14) + "," + h.getString(15) + "," + h.getString(7))
        }

        println ("" + count + " elements")
        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("prep : " + (prep - read) / 1e9 + " seconds")
        println ("graph: " + (graph - prep) / 1e9 + " seconds")
        println ("fetch: " + (fetch - graph) / 1e9 + " seconds")
        println ("print: " + (System.nanoTime () - fetch) / 1e9 + " seconds")
        println ();
    }
}

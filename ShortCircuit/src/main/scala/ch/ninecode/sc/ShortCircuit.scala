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
import org.apache.spark.sql.types.{StructType, StructField, StringType, IntegerType, DoubleType}
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim._
import ch.ninecode.model._

case class ShortCircuitOptions (
    verbose: Boolean = true,
    cim_reader_options: Iterable[(String, String)] = new scala.collection.mutable.HashMap[String, String] (),
    three: Boolean = false,
    trafos: String = "",
    csv_file: String = "",
    transformer: String = "",
    workdir: String = "",
    files: Seq[String] = Seq()
)

// define case classes that need to be pattern matched
// due to a flaw in Scala pattern matching with classes defined within another class
// we have to define these classes external to the main class,
// otherwise we have to pattern match on Any and then cast it asInstanceOf[whatever]

/**
 * Station short circuit power availability (pre-computed).
 * The available power and phase from the mid-voltage network if the primary is shorted.
 * @param mRID CIM MRID of the station.
 * @param Sk Available short circuit power available at the primary of the transformer (MW).
 * @param Ikw Short circuit phase angle at the primary of the transformer (degrees).
 * @param valid Indicates if the station was found in the pre-computed list, default values otherwise (and thus not valid).
 */
case class ShortCircuitData (
    mRID: String,
    Sk: Double,
    Ikw: Double,
    valid: Boolean)

//// create a basket to hold all transformer data
//case class TransformerData (transformer: PowerTransformer, end1: PowerTransformerEnd, terminal1: Terminal, v1: Double, end2: PowerTransformerEnd, terminal2: Terminal, v2: Double, substation: Substation, short_circuit: ShortCircuitData)
/**
 * Transformer data.
 * @param transformer The PowerTransformer object.
 * @param station The Substation object where the transformer is located.
 * @param shortcircuit The ShortCircuit object with the available short circuit power and phase at the primary.
 * @param voltage0 The voltage for the transformer high voltage end (kV).
 * @param end0 The high voltage PowerTransformerEnd.
 * @param terminal0 The high voltage Terminal.
 * @param voltage1 The voltage for the transformer low voltage end (kV).
 * @param end1 The low voltage PowerTransformerEnd.
 * @param terminal1 The low voltage Terminal.
 */
case class TData (
    transformer: PowerTransformer,
    station: Substation,
    shortcircuit: ShortCircuitData,
    end0: PowerTransformerEnd,
    voltage0: Double,
    terminal0: Terminal,
    node0: String,
    end1: PowerTransformerEnd,
    voltage1: Double,
    terminal1: Terminal,
    node1: String)


// define the message class
// this is used in the VertexData class to avoid reallocating another object that stores the same information as the message
case class Message (transformer: String, r: Double, x: Double, r0: Double, x0: Double, fuses: List[(String,Double)], valid: Boolean) extends Serializable

// define the data attached to each vertex
case class VertexData (
    id: String,
    name: String,
    container: String,
    start: Array[TData],
    stop: Boolean,
    var message: Message,
    var valid: Boolean)

// define the augmented edge class
case class EdgePlus (id_seq_1: String, id_seq_2: String, id_equ: String, clazz: String, name: String, aliasName: String, container: String, length: Double, voltage: String, normalOpen: Boolean, ratedCurrent: Double, x1: String, y1: String, x2: String, y2: String, r: Double, x: Double, r0: Double, x0: Double, valid: Boolean)

// class to return the transformer id values
case class TransformerName (id: String)

case class HouseConnection (mRID: String, node: String, transformer: Array[TData], r: Double, x: Double, r0: Double, x0: Double, fuses: List[(String,Double)], wires_valid: Boolean, ik: Double = 0.0, ik3pol: Double = 0.0, ip: Double = 0.0, fuse_valid: Boolean = false)

case class Result (mRID: String, node: String, transformer: String, r: Double, x: Double, r0: Double, x0: Double, fuses: String, ik: Double = 0.0, ik3pol: Double = 0.0, ip: Double = 0.0, wires_valid:Boolean, transformer_valid: Boolean, fuse_valid: Boolean, location_x: String = "0.0", location_y: String = "0.0")

case class ShortCircuit (session: SparkSession, storage_level: StorageLevel, options: ShortCircuitOptions) extends Serializable
{
    // name of file containing short circuit Ikw and Sk values for medium voltage transformers
    // e.g.
    //
    // "","Fehlerort","Un","Ikw...RST.","Sk..RST.","Beschreibung..SAP.Nr..","Abgang","NIS.ID","NIS.Name"
    // "1","Scheidbach Turbach",16,-37.34,89.733,20444,"SAA Lauenen","STA2040","Scheidbach"
    // "2","Bachegg",16,-36.22,83.805,20468,"SAA Lauenen","STA9390","Bachegg"
    //
    // this should only be needed until the medium voltage network is fully described and  calculations can
    // be done from the high voltage network "slack bus" connections
    // var csv = "hdfs://sandbox:8020/data/KS_Leistungen.csv"

    def dv (v: BaseVoltage) =
    {
        (v.id, v.nominalVoltage)
    }

    def vertex_id (string: String): VertexId =
    {
        var h = 2166136261l;
        for (c ← string)
            h = (h * 16777619) ^ c
        h.asInstanceOf[VertexId]
    }

    def get (name: String): RDD[Element] =
    {
        val rdds = session.sparkContext.getPersistentRDDs
        for (key <- rdds.keys)
        {
            val rdd = rdds (key)
            if (rdd.name == name)
                return (rdd.asInstanceOf[RDD[Element]])
        }
        return (null)
    }

    def read_csv (): RDD[ShortCircuitData] =
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

        val df = session.sqlContext.read
            .format ("csv")
            .option ("header", "true")
            .schema (customSchema)
            .csv (options.csv_file)

        import session.sqlContext.implicits._
        df.map ( r => ShortCircuitData (r.getString (7), r.getDouble (4), r.getDouble (3), true) ).rdd
    }

    def preparation (): DataFrame  =
    {
        // gather the set of voltages
        // usage: voltages.getOrElse ("BaseVoltage_400", 0.0)  yields 0.4 as a Double
        val voltages = get ("BaseVoltage").asInstanceOf[RDD[ch.ninecode.model.BaseVoltage]].map (dv).collectAsMap ()

        // get all transformers in substations
        val transformers = get ("PowerTransformer").asInstanceOf[RDD[PowerTransformer]]
        val substation_transformers = transformers.filter ((t: PowerTransformer) => { (t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name != "Messen_Steuern") })

        // get an RDD of substations by filtering out distribution boxes
        val stations = get ("Substation").asInstanceOf[RDD[Substation]].filter (_.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType == "PSRType_TransformerStation")

        // the Equipment container for a transformer could be a Bay, VoltageLevel or Station... the first two of which have a reference to their station
        def station_fn (x: (String, Any)) =
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
        val elements = get ("Elements").asInstanceOf[RDD[Element]]
        val tpairs = substation_transformers.keyBy(_.ConductingEquipment.Equipment.EquipmentContainer).join (elements.keyBy (_.id)).map (station_fn)

        val short_circuit = read_csv ()

        // only keep the pairs where the transformer is in a substation we have
        val transformers_stations = tpairs.join (stations.keyBy (_.id)).values

        def transformer_fn (x: (String, Any)) =
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
        val ends = get ("PowerTransformerEnd").asInstanceOf[RDD[PowerTransformerEnd]]
        // Note: if the end count does not equal twice the number of transformers, we have a three (or more) terminal transformer and hence a problem

        // get the terminals
        val terminals = get ("Terminal").asInstanceOf[RDD[Terminal]]

        // keep only terminals for transformer ends
        val transformer_terminals = terminals.keyBy (_.id).join (ends.keyBy (_.TransformerEnd.Terminal)).values

        // get the list of transformers, with their ends, terminals and voltage
        def transformer_end_fn (x: (String, Any)) =
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
                        TData (transformer, substation, short_circuit, i1._2, v_a, i1._1, i1._1.ConnectivityNode, i2._2, v_b, i2._1, i2._1.ConnectivityNode)
                    else
                        TData (transformer, substation, short_circuit, i2._2, v_b, i2._1, i2._1.ConnectivityNode, i1._2, v_a, i1._1, i1._1.ConnectivityNode)
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        val transformer_and_transformer_terminals = transformer_terminals.keyBy (_._1.ConductingEquipment).groupByKey.join (transformers_short_circuit.keyBy (_._1.id)).map (transformer_end_fn)

        // get the low voltage (niederspannung) transformers
        val ns_transformers = transformer_and_transformer_terminals.filter (t => { 400.0 == t.voltage1 })

        // persist RDD so later execution can get at it
        ns_transformers.setName ("graph_transformers")
        ns_transformers.persist (storage_level)

        def tx_fn (t: TData) =
        {
            TransformerName (t.transformer.id)
        }
        val tx = ns_transformers.map (tx_fn)

        session.sqlContext.createDataFrame (tx)
    }

    def get_vertices (ns_transformers: RDD[TData]): RDD[(VertexId, VertexData)] =
    {
        // convert CIM nodes into Graphx vertices as RDD of (key, value) pairs

        // get the list of nodes
        val nodes = get ("ConnectivityNode").asInstanceOf[RDD[ConnectivityNode]]

        def node_function (x: (String, (ConnectivityNode, Option[TData]))): (VertexId, VertexData) =
        {
            x match
            {
                case (key: String, (n: ConnectivityNode, Some (t: TData) )) =>
                {
                    // ToDo: fix TData Array
                    (vertex_id (key), VertexData (key, n.IdentifiedObject.name, n.ConnectivityNodeContainer, Array(t), t.voltage0 > 400.0, Message (null, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, List(), true), true))
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
        val vertices = nodes.keyBy (_.id).leftOuterJoin (ns_transformers.keyBy (_.terminal1.ConnectivityNode)).map (node_function)
        vertices.setName ("graph_vertices")
        vertices.persist (storage_level)
    }

    def get_edges (): RDD[Edge[EdgePlus]] =
    {
        val cim_edges = get ("Edges").asInstanceOf[RDD[PostEdge]]

        // keep only non-self connected and non-singly connected edges
        val someedges =  cim_edges.filter ((e: PostEdge) => { (e.id_seq_1 != e.id_seq_2) && e.id_seq_1 != null && e.id_seq_2 != null && e.id_seq_1 != "" && e.id_seq_2 != "" })

        // get the wires
        val segments = get ("ACLineSegment").asInstanceOf[RDD[ACLineSegment]]

        def fn (x: (String, Any)) =
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
        edges.persist (storage_level)
    }

    def run (): DataFrame =
    {
        val tx = get ("graph_transformers").asInstanceOf[RDD[TData]]
        val ns_transformers = if (null == tx)
        {
            preparation ()
            get ("graph_transformers").asInstanceOf[RDD[TData]]
        }
        else
            tx

        val vertices = get_vertices (ns_transformers)
        val edges = get_edges ()

        // get the specified transformer
        val tran = options.transformer

        // filter out all but the specified transformer
        def filterx (v: (VertexId, VertexData)): (VertexId, VertexData) =
        {
            // ToDo: fix Array TData
            (v._1, VertexData (v._2.id, v._2.name, v._2.container, if ((null != v._2.start) && (v._2.start(0).transformer.id == tran)) v._2.start else null, v._2.stop, v._2.message, v._2.valid))
        }
        val some_vertices = if ((tran != "") && (tran.toLowerCase () != "all"))
            vertices.map (filterx)
        else
            vertices

        // construct the initial graph from the augmented elements (vertices) and edges
        val default = VertexData ("", "garbage", "", null, true, Message (null, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, List[(String,Double)] (), false), false)
        val initial = Graph.apply[VertexData, EdgePlus] (some_vertices, edges, default)

        // do the Pregel algorithm
        def vprog (id: VertexId, v: VertexData, message: Message): VertexData =
        {
            if (null == message) // initial message
                if (null != v.start) // is this node a low voltage transformer terminal
                    // ToDo: fix Array TData
                    VertexData (v.id, v.name, v.container, v.start, v.stop, Message (v.start(0).transformer.id, 0.0, 0.0, 0.0, 0.0, List (), true), v.valid)
                else
                    v
            else // subsequent messages
                VertexData (v.id, v.name, v.container, v.start, v.stop, message, v.valid)
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

            ret
        }

        def mergeMessage (a: Message, b: Message): Message =
        {
            // throw new Exception ("" + a + " " + b)
            // ToDo: handle multiple sources
            if ((null != a) && (null != b))
                if (a.r < b.r)
                    a
                else
                    b
            else
                null
        }

        val graph = initial.pregel (null.asInstanceOf[Message], 50 /* Int.MaxValue */, EdgeDirection.Either) (vprog, sendMessage, mergeMessage)

        // get the leaf nodes with their data
        val has = graph.vertices.filter (
            (v: (VertexId, VertexData)) =>
            {
                (v._2.id.startsWith ("HAS") || v._2.id.startsWith ("HAK")) && v._2.message.r != Double.PositiveInfinity
            }
        ).values
        has.setName ("house_connections")
        has.persist (storage_level)

        // link them to the supplying transformer
        def house_transformer_fn (x: (String, Any)) =
        {
            x match
            {
                case (key: String, (v: VertexData, t: TData)) =>
                {
                    // ToDo: fix Array TData
                    HouseConnection (v.name, v.id, Array(t), v.message.r, v.message.x, v.message.r0, v.message.x0, v.message.fuses, v.message.valid)
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
            // ToDo: fix Array TData
            val v1 = house.transformer(0).voltage0
            val v2 = house.transformer(0).voltage1
            val sk = house.transformer(0).shortcircuit.Sk * 1e+6
            val turns_ratio = v2 / v1
            val zqt = c * v1 * v1 / sk * (turns_ratio * turns_ratio)
            val wik = house.transformer(0).shortcircuit.Ikw
            val wik_radians = Math.PI / 180.0 * wik
            val netz_r1 = zqt * Math.cos (wik_radians)
            val netz_x1 = zqt * Math.sin (wik_radians)

            val r_total = house.r + house.transformer(0).end1.r + netz_r1 // ToDo: check transformer secondary resistance, that is end2
            val x_total = house.x + house.transformer(0).end1.x + Math.abs (netz_x1) // ToDo: check why abs is necessary
            val r_secondary = house.r + house.transformer(0).end1.r
            val x_secondary = house.x + house.transformer(0).end1.x
            val impedance = Math.sqrt (r_total * r_total + x_total * x_total)

            // Einpoligen Kurzschlussstrom berechnen
            val ik = Math.sqrt (3.0)  * cmin * v2 / (2.0 * impedance + Math.sqrt (r_secondary * r_secondary + x_secondary * x_secondary))

            // Dreipoligen Kurzschlussstrom berechnen
            val ik3pol = 1.1 * v2 / (Math.sqrt (3.0) * impedance)

            // Stosskurzschlussstrom berechnen
            val ip = (1.02 + 0.98 * Math.exp (-3.0 * r_total / x_total)) * Math.sqrt (2) * ik3pol

            // apply rules for fuses
            val fuse_valid = if (2 <= house.fuses.length)
                house.fuses.tail.head._2 / house.fuses.head._2 > 1.6
            else if (1 <= house.fuses.length)
                ik > current_max (house.fuses.head._2)
            else
                false

            HouseConnection (house.mRID, house.node, house.transformer, house.r, house.x, house.r0, house.x0, house.fuses, house.wires_valid, ik, ik3pol, ip, fuse_valid)
        }

        val result = houses.map (calculate_short_circuit)

        def localize (x: (HouseConnection, PositionPoint)): Result =
        {
            val house = x._1
            val point = x._2
            // ToDo: fix Array TData
            Result (house.mRID, house.node, house.transformer(0).transformer.id, house.r, house.x, house.r0, house.x0, house.fuses.toString (), house.ik, house.ik3pol, house.ip, house.wires_valid, house.transformer(0).shortcircuit.valid, house.fuse_valid, point.xPosition, point.yPosition)
        }
        val consumers = get ("Fuse").asInstanceOf[RDD[Fuse]]
        val locs = consumers.map ((f: Fuse) => { (f.id, f.Switch.ConductingEquipment.Equipment.PowerSystemResource.Location) })
        val points = get ("PositionPoint").asInstanceOf[RDD[PositionPoint]]
        val final_result = locs.join (result.keyBy (_.mRID)).values.join (points.keyBy (_.Location)).values.map (localize)

        session.sqlContext.createDataFrame (final_result)
    }
}

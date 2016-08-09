package ch.ninecode.cim

import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext
import org.apache.spark.graphx._
import org.apache.spark.rdd._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext

import ch.ninecode._
import ch.ninecode.cim._
import ch.ninecode.model._

class ShortCircuit extends Serializable
{

    case class ShortCircuitData (mRID: String, Sk: Double, Ikw: Double, valid: Boolean)

    // create a basket to hold all transformer data
    case class TransformerData (transformer: ch.ninecode.model.PowerTransformer, end1: ch.ninecode.model.PowerTransformerEnd, terminal1: ch.ninecode.model.Terminal, end2: ch.ninecode.model.PowerTransformerEnd, terminal2: ch.ninecode.model.Terminal, substation: ch.ninecode.model.Substation, short_circuit: ShortCircuitData)

    case class HouseConnection (mRID: String, node: String, transformer: TransformerData, r: Double, x: Double, r0: Double, x0: Double, fuses: List[Tuple2[String,Double]], wires_valid: Boolean, ik: Double = 0.0, ik3pol: Double = 0.0, ip: Double = 0.0, fuse_valid: Boolean = false)

    case class Result (mRID: String, node: String, transformer: String, r: Double, x: Double, r0: Double, x0: Double, fuses: String, ik: Double = 0.0, ik3pol: Double = 0.0, ip: Double = 0.0, wires_valid:Boolean, transformer_valid: Boolean, fuse_valid: Boolean, location_x: String = "0.0", location_y: String = "0.0")

    def dv (v: BaseVoltage) =
    {
        (v.id, v.nominalVoltage)
    }

    def stuff (sc: SparkContext, sqlContext: SQLContext): DataFrame =
    {
        // paragraph 1

        //val rdd = sqlContext.read.format ("ch.ninecode.cim").load ("hdfs://sandbox:9000/data/20160803-16_NIS_CIM_Export_b4_Bruegg.rdf")
        //val rdd = sqlContext.read.format ("ch.ninecode.cim").load ("hdfs://sandbox:9000/data/20160804-09_NIS_CIM_Export_b4_Guemligen.rdf")
        //rdd.count

        def get (name: String): RDD[Element] =
        {
            val rdds = sc.getPersistentRDDs
            for (key <- rdds.keys)
            {
                val rdd = rdds (key)
                if (rdd.name == name)
                    return (rdd.asInstanceOf[RDD[Element]])
            }
            return (null)
        }
        // gather the set of voltages
        // usage: voltages.getOrElse ("BaseVoltage_400", 0.0)  yields 0.4 as a Double
        val voltages = get ("BaseVoltage").asInstanceOf[RDD[ch.ninecode.model.BaseVoltage]].map (dv).collectAsMap ()

        // paragraph 2

        // read the csv as a text file (could also install com.databricks.spark.csv, see https://github.com/databricks/spark-csv)
        val csv = sc.textFile ("hdfs://sandbox:9000/data/KS_Leistungen.csv")
//        csv.count
//        csv.first

        // create a function to trim off the double quotes
        def trimplus (s: String): String =
        {
            var ret = s.trim
            if (ret.startsWith ("\"") && ret.endsWith ("\""))
                ret = ret.substring (1, ret.length - 1)
            return (ret)
        }
        // split / clean data
        val headerAndRows = csv.map (line => line.split(",").map (trimplus (_)))
        // get header
        val header = headerAndRows.first
        // filter out header (eh. just check if the first val matches the first header name)
        val data = headerAndRows.filter (_(0) != header (0))
        // splits to map (header/value pairs)
        val short_circuit_power = data.map (splits => header.zip (splits).toMap)

        short_circuit_power.count
        short_circuit_power.first

        // keep only the relevant information
        val short_circuit = short_circuit_power.map ((record: scala.collection.immutable.Map[String,String]) => { ShortCircuitData (record("NIS.ID"), record("Sk..RST.").toDouble, record("Ikw...RST.").toDouble, true) })
//        short_circuit.count
//        short_circuit.first

        // paragraph 3

        // get all transformers in substations with their short circuit data

        val transformers = get ("PowerTransformer").asInstanceOf[RDD[ch.ninecode.model.PowerTransformer]]
//        transformers.count
//        transformers.first

        val substation_transformers = transformers.filter ((t: PowerTransformer) => { (t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name != "Messen_Steuern") }) // (t.ConductingEquipment.Equipment.PowerSystemResource.PSRType == "PSRType_Substation") &&
//        substation_transformers.count
//        substation_transformers.first

        // get an RDD of substations by filtering out distribution boxes
        val stations = get ("Substation").asInstanceOf[RDD[ch.ninecode.model.Substation]].filter (_.ConnectivityNodeContainer.PowerSystemResource.PSRType == "PSRType_TransformerStation")
//        stations.count
//        stations.first

        // the Equipment container for a transformer could be a Bay, VoltageLevel or Station... the first two of which have a reference to their station
        def station_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                case (key: String, (t: ch.ninecode.model.PowerTransformer, Some (station: ch.ninecode.model.Substation))) =>
                {
                    (station.id, t)
                }
                case (key: String, (t: ch.ninecode.model.PowerTransformer, Some (bay: ch.ninecode.model.Bay))) =>
                {
                    (bay.Substation, t)
                }
                case (key: String, (t: ch.ninecode.model.PowerTransformer, Some (level: ch.ninecode.model.VoltageLevel))) =>
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
        val elements = get ("Elements").asInstanceOf[RDD[ch.ninecode.model.Element]]
        val tpairs = substation_transformers.keyBy(_.ConductingEquipment.Equipment.EquipmentContainer).leftOuterJoin (elements.keyBy (_.id)).map (station_fn)
//        tpairs.count
//        tpairs.first

        // only keep the pairs where the transformer is in a substation we have
        val transformers_stations = tpairs.join (stations.keyBy (_.id)).values
//        transformers_stations.count
//        transformers_stations.first

        def transformer_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                // due to a flaw in Zeppelin pattern matching with user defined types we have to use Any and then cast it asInstanceOf[ShortCircuitData]
                case (key: String, ((a: ch.ninecode.model.PowerTransformer, b: ch.ninecode.model.Substation), Some (c: Any))) =>
                {
                    (a, b, c.asInstanceOf[ShortCircuitData])
                }
                case (key: String, ((a: ch.ninecode.model.PowerTransformer, b: ch.ninecode.model.Substation), None)) =>
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
//        transformers_short_circuit.count
//        transformers_short_circuit.first

        // paragraph 5

        // get all transformers with their end data

        // get the ends
        val ends = get ("PowerTransformerEnd").asInstanceOf[RDD[ch.ninecode.model.PowerTransformerEnd]]
        // Note: if the end count does not equal twice the number of transformers, we have a three (or more) terminal transformer and hence a problem
//        ends.count
//        ends.first

        // get the terminals
        val terminals = get ("Terminal").asInstanceOf[RDD[ch.ninecode.model.Terminal]]
//        terminals.count
//        terminals.first

        // keep only terminals for transformer ends
        val transformer_terminals = terminals.keyBy (_.id).join (ends.keyBy (_.TransformerEnd.Terminal)).values
//        transformer_terminals.count
//        transformer_terminals.first

        // get the list of tranformers, with their ends, and terminals
        def transformer_end_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                // due to a flaw in Zeppelin pattern matching with user defined types we have to use Any and then cast it asInstanceOf[ShortCircuitData]
                case (key: String, (iterator: Iterable[Any], (transformer: ch.ninecode.model.PowerTransformer, substation: ch.ninecode.model.Substation, short_circuit: Any))) =>
                {
                    val i1 = iterator.head.asInstanceOf[(ch.ninecode.model.Terminal, ch.ninecode.model.PowerTransformerEnd)]
                    val i2 = iterator.last.asInstanceOf[(ch.ninecode.model.Terminal, ch.ninecode.model.PowerTransformerEnd)] // ToDo: three (or more) terminal transformer
                    if (voltages.getOrElse (i1._2.TransformerEnd.BaseVoltage, 0.0) > voltages.getOrElse (i2._2.TransformerEnd.BaseVoltage, 0.0))
                        TransformerData (transformer, i1._2, i1._1, i2._2, i2._1, substation, short_circuit.asInstanceOf[ShortCircuitData])
                    else
                        TransformerData (transformer, i2._2, i2._1, i1._2, i1._1, substation, short_circuit.asInstanceOf[ShortCircuitData])
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        val transformer_and_transformer_terminals = transformer_terminals.keyBy (_._1.ConductingEquipment).groupByKey.join (transformers_short_circuit.keyBy (_._1.id)).map (transformer_end_fn)
//        transformer_and_transformer_terminals.count
//        transformer_and_transformer_terminals.first

        // paragraph 6

        // convert CIM nodes into Graphx vertices as RDD of (key, value) pairs

        // define the message class
        // this is used in the VertexData class to avoid realocating another object that stores the same information as the message
        case class Message (transformer: String, r: Double, x: Double, r0: Double, x0: Double, fuses: List[Tuple2[String,Double]], valid: Boolean) extends Serializable

        // get the list of nodes
        val nodes = get ("ConnectivityNode").asInstanceOf[RDD[ch.ninecode.model.ConnectivityNode]]
        //nodes.count
        //nodes.first

        // get the low voltage (niederspannung) transformers
        val ns_transformers = transformer_and_transformer_terminals.filter ((t: TransformerData) => { 0.4 == voltages.getOrElse (t.end2.TransformerEnd.BaseVoltage, 0.0) })
        //ns_transformers.count
        //ns_transformers.first

        // define the data attached to each vertex
        case class VertexData (val id: String, name: String, val container: String, val start: TransformerData, var message: Message, var valid: Boolean)

        // (k, (v, Some(w))) or (k, (v, None))
        def node_function (x: Tuple2[String, Any]) =
        {
            x match
            {
                // due to a flaw in Zeppelin pattern matching with user defined types we have to use Any and then cast it asInstanceOf[ShortCircuitData]
                case (key: String, (a: Any, None)) =>
                {
                    val n = a.asInstanceOf[ConnectivityNode]
                    VertexData (key, n.IdentifiedObject.name, n.ConnectivityNodeContainer, null, Message (null, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, List(), true), true)
                }
                case (key: String, (a: Any, Some (b: Any) )) =>
                {
                    val n = a.asInstanceOf[ConnectivityNode]
                    val t = b.asInstanceOf[TransformerData]
                    VertexData (key, n.IdentifiedObject.name, n.ConnectivityNodeContainer, t, Message (t.transformer.id, 0.0, 0.0, 0.0, 0.0, List(), true), true)
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        // due to a bug in Zeppelin, this keyBy has to be done first, otherwise only partial results are used
        val keyed = ns_transformers.keyBy (_.terminal2.ConnectivityNode)
//        keyed.count
//        keyed.first

        val vertices = nodes.keyBy (_.id).leftOuterJoin (keyed).map (node_function).keyBy (_.name.hashCode().asInstanceOf[VertexId])
//        vertices.count
//        vertices.first

//        val starting_set = vertices.filter (_._2.start != null)
//        starting_set.count
//        starting_set.first

        // paragraph 7

        // define the augmented edge class
        case class EdgePlus (id_seq_1: String, id_seq_2: String, id_equ: String, container: String, length: Double, voltage: String, typ: String, normalOpen: Boolean, ratedCurrent: Double, x1: String, y1: String, x2: String, y2: String, r: Double, x: Double, r0: Double, x0: Double, valid: Boolean)

        val cim_edges = get ("Edges").asInstanceOf[RDD[ch.ninecode.cim.Edge]]
//        cim_edges.count
//        cim_edges.first

        // keep only non-self connected and non-singly connected edges
        val someedges =  cim_edges.filter ((e: ch.ninecode.cim.Edge) => { (e.id_seq_1 != e.id_seq_2) && e.id_seq_1 != null && e.id_seq_2 != null && e.id_seq_1 != "" && e.id_seq_2 != "" })
//        someedges.count
//        someedges.first

        // get the wires
        val segments = get ("ACLineSegment").asInstanceOf[RDD[ch.ninecode.model.ACLineSegment]]
//        segments.count
//        segments.first

        def fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                case (key: String, (e: ch.ninecode.cim.Edge, Some(wire: ch.ninecode.model.ACLineSegment))) =>
                {
                    // default line impedance: R=0.124 Ohms/km, R0=0.372 Ohms/km, X=0.61 Ohms/km, X0=0.204 Ohms/km
                    if (0.0 != wire.r)
                        EdgePlus (e.id_seq_1, e.id_seq_2, e.id_equ, e.container, e.length, e.voltage, e.typ, e.normalOpen, e.ratedCurrent, e.x1, e.y1, e.x2, e.y2, wire.r, wire.x, wire.r0, wire.x0, true)
                    else
                        EdgePlus (e.id_seq_1, e.id_seq_2, e.id_equ, e.container, e.length, e.voltage, e.typ, e.normalOpen, e.ratedCurrent, e.x1, e.y1, e.x2, e.y2, 0.124, 0.61, 0.372, 0.204, false)
                }
                case (key: String, (e: ch.ninecode.cim.Edge, None)) =>
                {
                    EdgePlus (e.id_seq_1, e.id_seq_2, e.id_equ, e.container, e.length, e.voltage, e.typ, e.normalOpen, e.ratedCurrent, e.x1, e.y1, e.x2, e.y2, 0.0, 0.0, 0.0, 0.0, false)
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        val eplus = someedges.keyBy(_.id_equ).leftOuterJoin (segments.keyBy (_.id)).map (fn)
//        eplus.count
//        eplus.first

        // convert CIM edges into GraphX edges
        val edges = eplus.map (
            (ep: EdgePlus) =>
            {
                org.apache.spark.graphx.Edge (ep.id_seq_1.hashCode(), ep.id_seq_2.hashCode(), ep)
            }
        )
//        edges.count
//        edges.first

        // paragraph 8

        // construct the initial graph from the augmented elements (vertices) and edges
        val default = VertexData ("", "", "", null, Message (null, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, List (), false), false)
        val initial = Graph.apply[VertexData, EdgePlus] (vertices, edges, default)
//        initial.vertices.count
//        initial.edges.count

        // paragraph 9

        // do the Pregel algorithm

        def vprog (id: VertexId, v: VertexData, message: Message): VertexData =
        {
            var ret = v

            if (null == message) // initial message
            {
                if (null != v.start) // is this node a low voltage transformer terminal
                    ret = VertexData (v.id, v.name, v.container, v.start, Message (v.start.transformer.id, 0.0, 0.0, 0.0, 0.0, List (), true), v.valid)
            }
            else // subsequent messages
                ret = VertexData (v.id, v.name, v.container, v.start, message, v.valid)

            return (ret)
        }

        def sendMessage (triplet: EdgeTriplet[VertexData, EdgePlus]): Iterator[(VertexId, Message)] =
        {
            var ret:Iterator[(VertexId, Message)] = Iterator.empty

            // check for normalOpen switch
            if (!triplet.attr.normalOpen)
            {
                // compute the impedences to the downstream vertex
                if ((Double.PositiveInfinity != triplet.srcAttr.message.r)
                    && (   (Double.PositiveInfinity == triplet.dstAttr.message.r) // dst has not yet recieved a message
                        || (0.0 == triplet.dstAttr.message.r) // not sure if this ever occurs
                        || (triplet.srcAttr.message.r + triplet.attr.length * triplet.attr.r / 1000.0 < triplet.dstAttr.message.r))) // handle mesh netweork
                {
                    val r = triplet.srcAttr.message.r + triplet.attr.length * triplet.attr.r / 1000.0
                    val x = triplet.srcAttr.message.x + triplet.attr.length * triplet.attr.x / 1000.0
                    val r0 = triplet.srcAttr.message.r0 + triplet.attr.length * triplet.attr.r0 / 1000.0
                    val x0 = triplet.srcAttr.message.x0 + triplet.attr.length * triplet.attr.x0 / 1000.0
                    var fuses = triplet.srcAttr.message.fuses
                    if (triplet.attr.id_equ.startsWith ("SIG"))
                        fuses = (triplet.attr.id_equ, triplet.attr.ratedCurrent) :: fuses
                    val m = Message (triplet.srcAttr.message.transformer, r, x, r0, x0, fuses, triplet.srcAttr.message.valid && triplet.srcAttr.valid)
                    ret = Iterator ((triplet.dstId, m))
                }
                // else compute the impedences to the upstream vertex
                else if ((Double.PositiveInfinity != triplet.dstAttr.message.r)
                    && (   (Double.PositiveInfinity == triplet.srcAttr.message.r)
                        || (0.0 == triplet.srcAttr.message.r)
                        || (triplet.dstAttr.message.r + triplet.attr.length * triplet.attr.r / 1000.0 < triplet.srcAttr.message.r)))
                {
                    val r = triplet.dstAttr.message.r + triplet.attr.length * triplet.attr.r / 1000.0
                    val x = triplet.dstAttr.message.x + triplet.attr.length * triplet.attr.x / 1000.0
                    val r0 = triplet.dstAttr.message.r0 + triplet.attr.length * triplet.attr.r0 / 1000.0
                    val x0 = triplet.dstAttr.message.x0 + triplet.attr.length * triplet.attr.x0 / 1000.0
                    var fuses = triplet.dstAttr.message.fuses
                    if (triplet.attr.id_equ.startsWith ("SIG"))
                        fuses = (triplet.attr.id_equ, triplet.attr.ratedCurrent) :: fuses
                    val m = Message (triplet.dstAttr.message.transformer, r, x, r0, x0, fuses, triplet.dstAttr.message.valid && triplet.dstAttr.valid)
                    ret = Iterator ((triplet.srcId, m))
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
//        graph.vertices.first
//        graph.edges.first

        // paragraph 10

        val has =  graph.vertices.filter ( (v: Tuple2[VertexId, VertexData]) => { v._2.name.startsWith ("HAS") } )
//        has.count
//        has.first

        val test =  graph.vertices.filter ((v: Tuple2[VertexId, VertexData]) => { v._2.name.startsWith ("HAS") && v._2.message.r != Double.PositiveInfinity})
//        val size = test.count
//        if (size != 0)
//            test.first

        // paragraph 11

        // compute the house connection short-circuit values

        def house_transformer_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                // due to a flaw in Zeppelin pattern matching with user defined types we have to use Any and then cast it
                case (key: String, (v: Any, t: Any)) =>
                {
                    val vertex = v.asInstanceOf[VertexData]
                    val transformer = t.asInstanceOf[TransformerData]
                    HouseConnection (vertex.name, vertex.id, transformer, vertex.message.r, vertex.message.x, vertex.message.r0, vertex.message.x0, vertex.message.fuses, vertex.message.valid)
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }
        val houses = test.values.keyBy (_.message.transformer).join (ns_transformers.keyBy (_.transformer.id)).map (house_transformer_fn)
//        houses.count
//        houses.first

        // paragraph 12

        // Rated current, Breaking capacity [A]
        val breaking_capacity: Array[(Double, Double)] =
            Array (
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
            breaking_capacity.filter (_._1 < amps).last._2
        }

        def calculate_short_circuit (house: HouseConnection): HouseConnection =
        {
            val c = 0.90
            val cmin = 0.90
            val v1 = voltages.getOrElse (house.transformer.end1.TransformerEnd.BaseVoltage, 0.0) * 1000.0
            val v2 = voltages.getOrElse (house.transformer.end2.TransformerEnd.BaseVoltage, 0.0) * 1000.0
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

        val final_result = houses.map (calculate_short_circuit)
//        final_result.count
//        final_result.first

        def localize (x: Tuple2 [HouseConnection, PositionPoint]) =
        {
            val house = x._1
            val point = x._2
            Result (house.mRID, house.node, house.transformer.transformer.id, house.r, house.x, house.r0, house.x0, house.fuses.toString (), house.ik, house.ik3pol, house.ip, house.wires_valid, house.transformer.short_circuit.valid, house.fuse_valid, point.xPosition, point.yPosition)
        }
        val consumers = get ("EnergyConsumer").asInstanceOf[RDD[EnergyConsumer]]
        val locs = consumers.map ((c: EnergyConsumer) => { (c.id, c.ConductingEquipment.Equipment.PowerSystemResource.Location) })
        val points = get ("PositionPoint").asInstanceOf[RDD[PositionPoint]]
        val final_final_result = locs.join (final_result.keyBy (_.mRID)).values.join (points.keyBy (_.Location)).values.map (localize)

        val final_final_final_result = sqlContext.createDataFrame (final_final_result)
        return (final_final_final_result)
    }
}

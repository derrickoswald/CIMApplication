package ch.ninecode.sc

import scala.collection.Map
import org.slf4j.LoggerFactory

import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.{ StructType, StructField, StringType, DoubleType }
import org.apache.spark.storage.StorageLevel

import ch.ninecode.model._

case class ShortCircuitOptions(verbose: Boolean = true, csv_file: String = "", transformer: String = "", workdir: String = "")

/**
 * Station short circuit power availability (pre-computed).
 * The available power and phase from the mid-voltage network if the primary is shorted.
 * @param mRID CIM MRID of the station.
 * @param Sk Available short circuit power available at the primary of the transformer (MW).
 * @param Ikw Short circuit phase angle at the primary of the transformer (degrees).
 * @param valid Indicates if the station was found in the pre-computed list, default values otherwise (and thus not valid).
 */
case class ShortCircuitData(mRID: String, Sk: Double, Ikw: Double, valid: Boolean)

case class LineDetails(r_km: Double, r0_km: Double, x_km: Double, x0_km: Double)

case class StartingTrafos(osPin: VertexId, nsPin: VertexId, trafo_id: Array[TData], r: Double, ratedS: Double) extends Serializable

trait Graphable {
    /**
     * Compute the vertex id.
     * @param string The CIM MRID.
     */
    def vertex_id(string: String): VertexId =
        {
            var h = 2166136261l;
            for (c ← string)
                h = (h * 16777619) ^ c
            h.asInstanceOf[VertexId]
        }
}

case class ScNode(
    id_seq: String,
    voltage: Double,
    source_obj: StartingTrafos,
    sum_x: Double,
    sum_x0: Double,
    sum_r: Double,
    sum_r0: Double) extends Graphable

case class ScEdge(
    id_seq_1: String,
    id_cn_1: String,
    v1: Double,
    id_seq_2: String,
    id_cn_2: String,
    v2: Double,
    id_equ: String,
    equipment: ConductingEquipment,
    element: Element) extends Graphable with Serializable

case class HouseConnection(
        mRID: String, 
        node: String, 
        transformer: Array[TData], 
        r: Double, 
        x: Double, 
        r0: Double, 
        x0: Double, 
        ik: Double = 0.0, 
        ik3pol: Double = 0.0, 
        ip: Double = 0.0)

case class ShortCircuit(session: SparkSession, storage_level: StorageLevel, options: ShortCircuitOptions) extends Serializable {

    val log = LoggerFactory.getLogger (getClass)
    val default_node = ScNode ("", 0.0, null.asInstanceOf[StartingTrafos], Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity)

    def has(string: String): String =
        {
            string.substring (0, string.indexOf ("_"))
        }

    def trafokreis_key(transformers: Array[TData]): String =
        {
            transformers.map(_.transformer.id).sortWith(_ < _).mkString("_")
        }

    def get(name: String): RDD[Element] =
        {
            val rdds = session.sparkContext.getPersistentRDDs
            for (key ← rdds.keys) {
                val rdd = rdds (key)
                if (rdd.name == name)
                    return (rdd.asInstanceOf[RDD[Element]])
            }
            return (null)
        }

    def read_csv(csv: String): RDD[ShortCircuitData] =
        {
            val customSchema = StructType (
                Array (
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
                .csv (csv)

            import session.sqlContext.implicits._
            df.map (r ⇒ ShortCircuitData (r.getString (7), r.getDouble (4), r.getDouble (3), true)).rdd
        }

    def shouldContinue(element: Element): Boolean =
        {
            val clazz = element.getClass.getName
            val cls = clazz.substring (clazz.lastIndexOf (".") + 1)
            val ret = cls match {
                case "Switch" ⇒
                    !element.asInstanceOf[Switch].normalOpen
                case "Cut" ⇒
                    !element.asInstanceOf[Cut].Switch.normalOpen
                case "Disconnector" ⇒
                    !element.asInstanceOf[Disconnector].Switch.normalOpen
                case "Fuse" ⇒
                    !element.asInstanceOf[Fuse].Switch.normalOpen
                case "GroundDisconnector" ⇒
                    !element.asInstanceOf[GroundDisconnector].Switch.normalOpen
                case "Jumper" ⇒
                    !element.asInstanceOf[Jumper].Switch.normalOpen
                case "ProtectedSwitch" ⇒
                    !element.asInstanceOf[ProtectedSwitch].Switch.normalOpen
                case "Sectionaliser" ⇒
                    !element.asInstanceOf[Sectionaliser].Switch.normalOpen
                case "Breaker" ⇒
                    !element.asInstanceOf[Breaker].ProtectedSwitch.Switch.normalOpen
                case "LoadBreakSwitch" ⇒
                    !element.asInstanceOf[LoadBreakSwitch].ProtectedSwitch.Switch.normalOpen
                case "Recloser" ⇒
                    !element.asInstanceOf[Recloser].ProtectedSwitch.Switch.normalOpen
                case "PowerTransformer" ⇒
                    false
                case _ ⇒
                    true
            }
            return (ret)
        }

    def make_graph_vertices(v: ScNode): Tuple2[VertexId, ScNode] =
        {
            (v.vertex_id(v.id_seq), v)
        }

    def make_graph_edges(e: ScEdge): Edge[ScEdge] =
        {
            Edge(e.vertex_id(e.id_cn_1), e.vertex_id(e.id_cn_2), e)
        }

    def topological_node_operator(arg: Tuple2[Tuple2[TopologicalNode, Terminal], ScEdge]): ScNode =
        {
            val node = arg._1._1
            val term = arg._1._2
            val edge = arg._2
            ScNode (node.id, if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2, null.asInstanceOf[StartingTrafos], Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity)
        }

    def edge_operator(voltages: Map[String, Double])(arg: Tuple2[Tuple2[Element, Option[Iterable[PowerTransformerEnd]]], Iterable[Terminal]]): List[ScEdge] =
        {
            var ret = List[ScEdge]()

            val e = arg._1._1
            val pte_op = arg._1._2
            val t_it = arg._2

            // get the ConductingEquipment
            var c = e
            while ((null != c) && !c.getClass().getName().endsWith(".ConductingEquipment"))
                c = c.sup

            if (null != c) {
                // sort terminals by sequence number (and hence the primary is index 0)
                val terminals = t_it.toArray.sortWith(_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)
                // get the equipment
                val equipment = c.asInstanceOf[ConductingEquipment]
                // make a list of voltages
                val volt = 1000.0 * voltages.getOrElse(equipment.BaseVoltage, 0.0)
                val volts =
                    pte_op match {
                        case Some(x: Iterable[PowerTransformerEnd]) ⇒
                            // sort ends by end number
                            // ToDo: handle the case where terminal sequence and end sequence aren't the same
                            val tends = x.toArray.sortWith(_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
                            tends.map(e ⇒ 1000.0 * voltages.getOrElse(e.TransformerEnd.BaseVoltage, 0.0))
                        case None ⇒
                            Array[Double](volt, volt)
                    }
                // Note: we eliminate 230V edges because transformer information doesn't exist and
                // see also NE-51 NIS.CIM: Export / Missing 230V connectivity
                if (!volts.contains(230.0))
                    // make a pre-edge for each pair of terminals
                    ret = terminals.length match {
                        case 1 ⇒
                            ret :+
                                new ScEdge(
                                    terminals(0).ACDCTerminal.id,
                                    terminals(0).TopologicalNode,
                                    volts(0),
                                    "",
                                    "",
                                    volts(0),
                                    terminals(0).ConductingEquipment,
                                    equipment,
                                    e)
                        case _ ⇒
                            {
                                for (i ← 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                                {
                                    ret = ret :+ new ScEdge(
                                        terminals(0).ACDCTerminal.id,
                                        terminals(0).TopologicalNode,
                                        volts(0),
                                        terminals(i).ACDCTerminal.id,
                                        terminals(i).TopologicalNode,
                                        volts(i),
                                        terminals(0).ConductingEquipment,
                                        equipment,
                                        e)
                                }
                                ret
                            }
                    }
            }
            //else // shouldn't happen, terminals always reference ConductingEquipment, right?

            return (ret)
        }

    def trafo_mapping(tdata: Array[TData]): StartingTrafos =
        {
            val pn = default_node
            val v0 = pn.vertex_id (tdata(0).terminal0.TopologicalNode)
            val v1 = pn.vertex_id (tdata(0).terminal1.TopologicalNode)
            val ratedS = tdata.map(_.end1.ratedS).sum
            val r = if (tdata.length > 1) {
                // ToDo: handle 3 or more transformers ganged together
                val r1 = tdata(0).end1.r
                val r2 = tdata(1).end1.r
                (r1 * r2) / (r1 + r2)
            }
            else
                tdata(0).end1.r
            StartingTrafos (v0, v1, tdata, r, ratedS)
        }

    def get_inital_graph(): Graph[ScNode, ScEdge] =
        {
            // get a map of voltages
            val voltages = get("BaseVoltage").asInstanceOf[RDD[BaseVoltage]].map((v) ⇒ (v.id, v.nominalVoltage)).collectAsMap()

            // get the terminals
            val terminals = get("Terminal").asInstanceOf[RDD[Terminal]].filter(null != _.ConnectivityNode)

            // get the terminals keyed by equipment
            val terms = terminals.groupBy(_.ConductingEquipment)

            // get all elements
            val elements = get("Elements").asInstanceOf[RDD[Element]]

            // get the transformer ends keyed by transformer
            val ends = get("PowerTransformerEnd").asInstanceOf[RDD[PowerTransformerEnd]].groupBy(_.PowerTransformer)

            // handle transformers specially, by attaching all PowerTransformerEnd objects to the elements
            val elementsplus = elements.keyBy(_.id).leftOuterJoin(ends)

            // map the terminal 'pairs' to edges
            val edges = elementsplus.join(terms).flatMapValues (edge_operator (voltages)).values

            // eliminate edges with only one connectivity node, or the same connectivity node
            val real_edges = edges.filter(x ⇒ null != x.id_cn_1 && null != x.id_cn_2 && "" != x.id_cn_1 && "" != x.id_cn_2 && x.id_cn_1 != x.id_cn_2)

            // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
            val tv = edges.keyBy(_.id_seq_1).union(edges.keyBy(_.id_seq_2)).distinct

            // get the nodes RDD
            val tnodes = get("TopologicalNode").asInstanceOf[RDD[TopologicalNode]]
            // map the topological nodes to prenodes with voltages
            val nodes = tnodes.keyBy(_.id).join(terminals.keyBy(_.TopologicalNode)).values.keyBy(_._2.id).join(tv).values.map(topological_node_operator).distinct

            // persist edges and nodes to avoid recompute
            val xedges = real_edges.map(make_graph_edges)
            val xnodes = nodes.map(make_graph_vertices)
            val e = xedges.count
            xedges.name = "xedges"
            xedges.persist(storage_level)
            val n = xnodes.count
            xnodes.name = "xnodes"
            xnodes.persist(storage_level)
            session.sparkContext.getCheckpointDir match {
                case Some(dir) ⇒
                    xedges.checkpoint()
                    xnodes.checkpoint()
                case None ⇒
            }

            Graph.apply[ScNode, ScEdge] (xnodes, xedges, default_node, storage_level, storage_level)
        }

    def trace(initial: Graph[ScNode, ScEdge]): Graph[ScNode, ScEdge] =
        {
                def line_details(edge: ScEdge): LineDetails =
                    {
                        if (edge.element.isInstanceOf[ACLineSegment]) {
                            val lineSeg = edge.element.asInstanceOf[ACLineSegment]
                            val dist_km = lineSeg.Conductor.len / 1000.0
                            LineDetails(lineSeg.r * dist_km, lineSeg.r0 * dist_km, lineSeg.x * dist_km, lineSeg.x0 * dist_km)
                        }
                        else
                            LineDetails(0.0, 0.0, 0.0, 0.0)
                    }

                // do the Pregel algorithm
                def vprog(id: VertexId, v: ScNode, message: ScNode): ScNode =
                    {
                        if (message.sum_r < v.sum_r)
                            message
                        else
                            v
                    }

                def sendMessage(triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScNode)] =
                    {
                        if ((null != triplet.srcAttr.source_obj) || (null != triplet.dstAttr.source_obj))
                            if (shouldContinue (triplet.attr.element)) {
                                val ld = line_details (triplet.attr)
                                if (triplet.srcAttr.source_obj != null && triplet.dstAttr.source_obj == null) {
                                    val sum_r = triplet.srcAttr.sum_r + ld.r_km
                                    val sum_r0 = triplet.srcAttr.sum_r + ld.r0_km
                                    val sum_x = triplet.srcAttr.sum_r + ld.x_km
                                    val sum_x0 = triplet.srcAttr.sum_r + ld.x0_km
                                    Iterator ((triplet.dstId, ScNode (triplet.dstAttr.id_seq, triplet.dstAttr.voltage, triplet.srcAttr.source_obj, sum_x, sum_x0, sum_r, sum_r0)))
                                }
                                else if (triplet.srcAttr.source_obj == null && triplet.dstAttr.source_obj != null) {
                                    val sum_r = triplet.dstAttr.sum_r + ld.r_km
                                    val sum_r0 = triplet.dstAttr.sum_r + ld.r0_km
                                    val sum_x = triplet.dstAttr.sum_r + ld.x_km
                                    val sum_x0 = triplet.dstAttr.sum_r + ld.x0_km
                                    Iterator ((triplet.srcId, ScNode (triplet.srcAttr.id_seq, triplet.srcAttr.voltage, triplet.dstAttr.source_obj, sum_x, sum_x0, sum_r, sum_r0)))
                                }
                                else
                                    Iterator.empty
                            }
                            else
                                Iterator.empty
                        else
                            Iterator.empty
                    }

                def mergeMessage(a: ScNode, b: ScNode): ScNode =
                    {
                        a
                    }

            initial.pregel (default_node, 10000, EdgeDirection.Either) (vprog, sendMessage, mergeMessage)
        }

    // compute the house connection short-circuit values
    def calculate_short_circuit(house: ScNode): HouseConnection =
        {
            val c = 1.0
            val cmin = 0.90
            val ratioZ0Z1 = 4
            val ratioX0R0 = 10
            // ToDo: fix Array TData
            val tData = house.source_obj.trafo_id(0)
            val v1 = tData.voltage0
            val v2 = tData.voltage1
            val sk = tData.shortcircuit.Sk * 1e+6
            val wik = tData.shortcircuit.Ikw

            val turns_ratio = v2 / v1
            val zqt = c * v1 * v1 / (sk * (turns_ratio * turns_ratio))
            val zqt0 = zqt * ratioZ0Z1

            val wik_radians = Math.PI / 180.0 * wik
            val netz_r1 = zqt * Math.cos (0.017453292519943 * wik_radians)
            val netz_x1 = zqt * Math.sin (0.017453292519943 * wik_radians)
            val netz_r0 = zqt0 * Math.cos(Math.abs(Math.atan(ratioX0R0)))
            val netz_x0 = zqt0 * Math.sin(Math.abs(Math.atan(ratioX0R0)))

            val has_r1 = house.sum_r
            val has_r0 = house.sum_r0
            val has_x1 = house.sum_x
            val has_x0 = house.sum_x0
            val trafo_r = tData.end1.r
            val trafo_r0 = tData.end1.r0
            val trafo_x = tData.end1.x
            val trafo_x0 = tData.end1.x0

            // Einpoligen Kurzschlussstrom berechnen
            val ik = Math.sqrt (3.0) * cmin * v2 / Math.sqrt(Math.pow(2 * has_r1 * trafo_r + has_r0 + trafo_r0 + 2 * netz_r1 + netz_r0, 2) + Math.pow(2 * has_x1 + trafo_x + has_x0 + trafo_x0 + 2 * Math.abs(netz_x1) + netz_x0, 2))

            // Dreipoligen Kurzschlussstrom berechnen
            val ik3pol = 1.1 * v2 / (Math.sqrt (3.0) * Math.sqrt(Math.pow(trafo_r + netz_r1, 2) + Math.pow(trafo_x + Math.abs(netz_x1), 2)))

            // Stosskurzschlussstrom berechnen
            val ip = (1.02 + 0.98 * Math.exp (-3.0 * (trafo_r + netz_r1) / (trafo_x + Math.abs(netz_x1)))) * Math.sqrt (2) * ik3pol

            HouseConnection (house.id_seq, has(house.id_seq), house.source_obj.trafo_id, has_r1, has_x1, has_r0, has_x0, ik, ik3pol, ip)
        }

    def run(): RDD[HouseConnection] =
        {
            val _transformers = new Transformers(this, session, storage_level)
            val tdata = _transformers.getTransformerData(true, options.csv_file)

            val transformers = if (null != options.transformer && "" != options.transformer && "all" != options.transformer) {
                val selected = tdata.filter(t ⇒ { options.transformer.split(",").contains(t.transformer.id) })
                selected.groupBy (t ⇒ t.terminal1.TopologicalNode).values.map (_.toArray).collect
            }
            else {
                // do all low voltage power transformers
                // ToDo: fix this 1kV multiplier on the voltages
                val niederspannug = tdata.filter ((td) ⇒ td.voltage0 != 0.4 && td.voltage1 == 0.4)
                niederspannug.groupBy (t ⇒ t.terminal1.TopologicalNode).values.map (_.toArray).collect
            }

            val starting_nodes = transformers.map (trafo_mapping)

                // create the initial Graph with ScNode vertices
                def starting_map(starting_nodes: Array[StartingTrafos])(id: VertexId, v: ScNode): ScNode =
                    {
                        starting_nodes.find (s ⇒ s.nsPin == id) match {
                            case Some(node) ⇒
                                ScNode (v.id_seq, v.voltage, node, 0.0, 0.0, 0.0, 0.0)
                            case None ⇒
                                ScNode (v.id_seq, v.voltage, null.asInstanceOf[StartingTrafos], Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity)
                        }
                    }

            val initial = get_inital_graph()
            val initial_with_starting_nodes = initial.mapVertices (starting_map (starting_nodes))
            val graph = trace(initial_with_starting_nodes)

            // get the leaf nodes with their data
            val has = graph.vertices.filter (
                (v: (VertexId, ScNode)) ⇒
                    {
                        (v._2.id_seq.startsWith ("HAS") || v._2.id_seq.startsWith ("HAK")) && v._2.sum_r != Double.PositiveInfinity
                    }
            ).values
            has.setName ("house_connections")
            has.persist (storage_level)

            has.map (calculate_short_circuit)
        }
}
package ch.ninecode.gl

import scala.collection.Map
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.Bay
import ch.ninecode.model.Element
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Substation
import ch.ninecode.model.Terminal
import ch.ninecode.model.VoltageLevel

class Transformers (session: SparkSession, storage_level: StorageLevel) extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Handle a join of power transformers and stations with the power transformer ends.
     * NOTE: Skips transformers with unusual numbers of ends.
     * @param voltages - a map of voltage name and voltage value (kV)
     * @param x - the list of transformers and stations keyed by transformer id, joined with transformer ends
     * @return a list of a pair of transformer name and a four-tuple of transformer, station, high side end with voltage and low side end with voltage
     */
    def addEnds (voltages: Map[String, Double]) (x: (String, ((PowerTransformer, Substation), Option[Iterable[PowerTransformerEnd]]))): List[(String, (PowerTransformer, Substation, (PowerTransformerEnd, Double), (PowerTransformerEnd, Double)))] =
    {
        val ends = x._2._2 match
        {
            case Some(y) => // Some(Iterable[PowerTransformerEnd])
                y.toArray.sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
            case None =>
                Array[PowerTransformerEnd]()
        }
        if (ends.length > 2)
            log.warn ("more than two transformer ends for %s".format (x._1))
        val ret = if (ends.length == 0)
        {
            log.error ("no transformer ends for %s".format (x._1))
            List[(String, (PowerTransformer, Substation, (PowerTransformerEnd, Double), (PowerTransformerEnd, Double)))]()
        }
        else if (ends.length == 1)
        {
            log.error ("less than two transformer ends for %s".format (x._1))
            List[(String, (PowerTransformer, Substation, (PowerTransformerEnd, Double), (PowerTransformerEnd, Double)))]()
        }
        else
        {
            val hv: String = ends(0).TransformerEnd.BaseVoltage
            val v0 = voltages.getOrElse (hv, 0.0)
            if (0.0 == v0)
                log.error ("no high voltage for %s %s".format (hv, x._1))
            val lv: String = ends(1).TransformerEnd.BaseVoltage
            val v1 = voltages.getOrElse (lv, 0.0)
            if (0.0 == v1)
                log.error ("no low voltage for %s %s".format (lv, x._1))
            List((x._1, (x._2._1._1, x._2._1._2, (ends(0), v0), (ends(1), v1))))
        }
        ret
    }

    /**
     * Handle a join of power transformers, stations and transformer ends with terminals.
     * NOTE: Skips transformers with terminals not matching the transformer ends
     * @return a list of four-tuples of transformer, station, and two three-tuples of end, voltage and terminal
     */
    def addTerminals (x: (String, ((PowerTransformer, Substation, (PowerTransformerEnd, Double), (PowerTransformerEnd, Double)), Option[Iterable[Terminal]]))): List[(PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal))] =
    {
        val terminals = x._2._2 match
        {
            case Some(y) => // Some(Iterable[Terminal])
                y.toArray.sortWith (_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)
            case None =>
                Array[Terminal]()
        }
        // match terminals to ends
        val end1 = x._2._1._3
        val match1 = terminals.find ((x) => x.id == end1._1.TransformerEnd.Terminal)
        val end2 = x._2._1._4
        val match2 = terminals.find ((x) => x.id == end2._1.TransformerEnd.Terminal)
        val ret = match1 match
        {
            case Some(t1) =>
                match2 match
                {
                    case Some (t2) =>
                        List((x._2._1._1, x._2._1._2, (end1._1, end1._2, t1), (end2._1, end2._2, t2)))
                    case None =>
                        log.error ("terminal not found for %s".format (end2._1.id))
                        List[(PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal))]()
                }
            case None =>
                match1 match
                {
                    case Some (_) =>
                        log.error ("terminal not found for %s".format (end1._1.id))
                        List[(PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal))]()
                    case None =>
                        log.error ("no terminals not found for %s".format (x._2._1._1.id))
                        List[(PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal))]()
                }
        }
        ret
    }

    def addSC (arg: ((PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal)), Option[ShortCircuitData])): (PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal), ShortCircuitData) =
    {
        arg._2 match
        {
            case Some (sc) => (arg._1._1, arg._1._2, arg._1._3, arg._1._4, sc)
            case None => (arg._1._1, arg._1._2, arg._1._3, arg._1._4, ShortCircuitData (arg._1._2.id, 200, -70, valid = false))
        }
    }

    def getTransformerData (topological_nodes: Boolean = true, shortcircuitdata: String = null): RDD[TData] =
    {
        /**
         * The name of the node associated with a terminal.
         * @param t The terminal object to get the node for.
         * @return The name of the TopologicalNode or ConnectivityNode.
         */
        def node_name (t: Terminal): String =
        {
            if (topological_nodes) t.TopologicalNode else t.ConnectivityNode
        }

        // get all transformers in substations
        val transformers = get[PowerTransformer]
        val substation_transformers = transformers.filter (_.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name != "Messen_Steuern")

        // get an RDD of substations by filtering out distribution boxes
        val stations = get[Substation].filter (_.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType != "PSRType_DistributionBox")

        // the equipment container for a transformer could be a Bay, VoltageLevel or Station... the first two of which have a reference to their station
        def station_fn (x: (String, Any)) =
        {
            x match
            {
                case (_, (t: PowerTransformer, station: Substation)) => (station.id, t)
                case (_, (t: PowerTransformer, bay: Bay)) => (bay.Substation, t)
                case (_, (t: PowerTransformer, level: VoltageLevel)) => (level.Substation, t)
                case _ => throw new Exception ("unknown container type for %s".format (x._1))
            }
        }

        // create an RDD of container-transformer pairs, e.g. { (KAB8526,TRA13730), (STA4551,TRA4425), ... }
        val elements = get[Element]("Elements")
        val tpairs = substation_transformers.keyBy(_.ConductingEquipment.Equipment.EquipmentContainer).join (elements.keyBy (_.id)).map (station_fn)

        // only keep the pairs where the transformer is in a substation we have
        val transformers_stations = tpairs.join (stations.keyBy (_.id)).values

        // get the transformer ends keyed by transformer
        val ends = get[PowerTransformerEnd].groupBy (_.PowerTransformer)

        // get a map of voltages
        val voltages = get[BaseVoltage].map ((v) => (v.id, v.nominalVoltage)).collectAsMap ()

        // attach PowerTransformerEnd elements
        val transformers_stations_plus_ends = transformers_stations.keyBy (_._1.id).leftOuterJoin (ends).flatMap (addEnds (voltages))

        // get the terminals keyed by transformer
        val terms = get[Terminal].groupBy (_.ConductingEquipment)

        // attach Terminal elements
        val transformers_stations_plus_ends_plus_terminals = transformers_stations_plus_ends.leftOuterJoin (terms).flatMap (addTerminals)

        // optionally read in the short circuit data
        val short_circuit = 
            if ((null != shortcircuitdata) && ("" != shortcircuitdata))
            {
                val sc = new ShortCircuit (session, storage_level)
                sc.read_csv (shortcircuitdata)
            }
            else
                session.sparkContext.parallelize (List[ShortCircuitData] ())

        val transformers_stations_plus_ends_plus_terminals_plus_sc =
            transformers_stations_plus_ends_plus_terminals.keyBy (_._2.id).leftOuterJoin (short_circuit.keyBy (_.mRID)).values.map (addSC)

        // convert to TData
        val transformer_data = transformers_stations_plus_ends_plus_terminals_plus_sc.map (
            (x) => TData (x._1, x._2, x._5, x._3._1, x._3._2, x._3._3, node_name (x._3._3), x._4._1, x._4._2, x._4._3, node_name (x._4._3))
            )
        transformer_data.persist (storage_level)
        session.sparkContext.getCheckpointDir match
        {
            case Some (_) => transformer_data.checkpoint ()
            case None =>
        }

        transformer_data
    }
}


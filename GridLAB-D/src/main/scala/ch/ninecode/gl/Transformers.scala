package ch.ninecode.gl

import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.BasicElement
import ch.ninecode.model.Bay
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element
import ch.ninecode.model.Equipment
import ch.ninecode.model.EquivalentEquipment
import ch.ninecode.model.EquivalentInjection
import ch.ninecode.model.IdentifiedObject
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Substation
import ch.ninecode.model.Terminal
import ch.ninecode.model.VoltageLevel

/**
 * Get information about transformers.
 * Joins PowerTransformer, PowerTransformerEnd and BaseVoltage objects to form complete details about transformers.
 *
 * @param session       the Spark session
 * @param storage_level specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level</a> used to persist and serialize the objects
 */
class Transformers (session: SparkSession, storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER")) extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    def getTransformers
    (
        default_supply_network_short_circuit_power_max: Double = 200.0e6,
        default_supply_network_short_circuit_impedance_max: Complex = Complex (0.437785783, -1.202806555),
        default_supply_network_short_circuit_power_min: Double = 100.0e6,
        default_supply_network_short_circuit_impedance_min: Complex = Complex (0.437785783, -1.202806555),
        transformer_filter: PowerTransformer ⇒ Boolean = _.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name != "Messen_Steuern",
        substation_filter: Substation ⇒ Boolean = _.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType != "PSRType_DistributionBox"
    ): RDD[TransformerData] =
    {
        // get ends and terminals
        val ends_terminals = getOrElse [PowerTransformerEnd].keyBy (_.TransformerEnd.Terminal).join (getOrElse [Terminal].keyBy (_.id)).values

        // get a map of voltages
        // ToDo: fix this 1kV multiplier on the voltages
        val voltages = getOrElse [BaseVoltage].map (v => (v.id, v.nominalVoltage * 1000.0)).collectAsMap ()

        // attach them to the ends
        val ends_terminals_voltages: RDD[(PowerTransformerEnd, Terminal, (String, Double))] = ends_terminals.map (
            x ⇒
            {
                val base_voltage = x._1.TransformerEnd.BaseVoltage
                val voltage = voltages.getOrElse (base_voltage, 0.0)
                (x._1, x._2, (base_voltage, voltage))
            }
        )

        def to_transformer_data (trafo: (PowerTransformer, Iterable[(PowerTransformerEnd, Terminal, (String, Double))])): TransformerData =
        {
            val ends = trafo._2.toArray.sortWith (_._1.TransformerEnd.endNumber < _._1.TransformerEnd.endNumber)
            TransformerData (trafo._1, ends.map (_._1), ends.map (_._2), ends.map (_._3), null, null)
        }

        // get the transformers of interest and join to end information (filter out transformers with less than 2 ends)
        val ends = ends_terminals_voltages.keyBy (_._1.PowerTransformer).groupByKey.filter (_._2.size >= 2)
        val transformers = getOrElse [PowerTransformer].filter (transformer_filter)
            .keyBy (_.id).join (ends)
            .values.map (to_transformer_data)

        // the equipment container for a transformer could be a Bay, VoltageLevel or Station... the first two of which have a reference to their station
        def station_fn (container: Option[Element]): String =
        {
            container match
            {
                case Some (element) ⇒
                    element match
                    {
                        case station: Substation => station.id
                        case bay: Bay => bay.Substation
                        case level: VoltageLevel => level.Substation
                        case _ => throw new Exception ("unknown container type (%s) for transformer".format (element.getClass.toString))
                    }
                case None ⇒ ""
            }
        }

        // add station if any
        // ToDo: should we invent a dummy station?
        val substations_by_id = getOrElse [Substation].filter (substation_filter).keyBy (_.id)
        val transformers_stations = transformers.keyBy (_.transformer.ConductingEquipment.Equipment.EquipmentContainer)
            .leftOuterJoin (get [Element]("Elements").keyBy (_.id)).values
            .map (x ⇒ (station_fn (x._2), x._1))
            .leftOuterJoin (substations_by_id).values
            .map (x ⇒ x._1.copy (station = x._2.orNull))

        // add equivalent injection, or default
        def default_injection (transformer: String, station: String, voltage: (String, Double)): EquivalentInjection =
        {
            val mRID = "EquivalentInjection_" + transformer
            val description = "default equivalent generation injection"
            val element = BasicElement (null, mRID)
            element.bitfields = Array (Integer.parseInt ("1", 2))
            val obj = IdentifiedObject (element, null, description, mRID, null, null, null)
            obj.bitfields = Array (Integer.parseInt ("110", 2))
            val psr = PowerSystemResource (obj, null, null, null, null, null, null, null, null, null, null, null)
            psr.bitfields = Array (0)
            val equipment = Equipment (psr, false, true, List (), List (), station, List (), List (), List (), List (), List (), List (), List (), List (), List ())
            equipment.bitfields = Array (Integer.parseInt ("10010", 2))
            val conducting = ConductingEquipment (equipment, voltage._1, null, null, List (), List (), null, List ())
            conducting.bitfields = Array (Integer.parseInt ("1", 2))
            val equivalent = EquivalentEquipment (conducting, null)
            equivalent.bitfields = Array (0)
            // decompose sk values into P & Q
            val maxP = default_supply_network_short_circuit_power_max * Math.cos (default_supply_network_short_circuit_impedance_max.angle)
            val maxQ = default_supply_network_short_circuit_power_max * Math.sin (default_supply_network_short_circuit_impedance_max.angle)
            val minP = default_supply_network_short_circuit_power_min * Math.cos (default_supply_network_short_circuit_impedance_min.angle)
            val minQ = default_supply_network_short_circuit_power_min * Math.sin (default_supply_network_short_circuit_impedance_min.angle)
            val injection = EquivalentInjection (equivalent, maxP, maxQ, minP, minQ, 0.0, 0.0, default_supply_network_short_circuit_impedance_max.re, default_supply_network_short_circuit_impedance_max.re, 0.0, false, false, 0.0, default_supply_network_short_circuit_impedance_max.im, default_supply_network_short_circuit_impedance_max.im, 0.0, null)
            // note: use RegulationStatus to indicate this is a default value, and not a real value
            injection.bitfields = Array (Integer.parseInt ("0001010001001111", 2))
            injection
        }

        val injections_by_node = getOrElse [EquivalentInjection].keyBy (_.id)
            .join (getOrElse [Terminal].keyBy (_.ConductingEquipment)).values
            .map (x ⇒ (x._2.TopologicalNode, x._1))
            .groupByKey.mapValues (_.head)
        transformers_stations.keyBy (_.node0)
            .leftOuterJoin (injections_by_node).values
            .map (x ⇒ x._1.copy (shortcircuit = x._2.getOrElse (default_injection (x._1.transformer.id, if (null != x._1.station) x._1.station.id else "", x._1.voltages (x._1.primary)))))
            .persist (storage_level)
    }
}

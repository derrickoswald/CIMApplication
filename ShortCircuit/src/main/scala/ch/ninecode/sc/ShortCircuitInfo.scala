package ch.ninecode.sc

import scala.collection.Map
import scala.language.existentials

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CHIM
import ch.ninecode.cim.CIMSubsetter
import ch.ninecode.cim.ClassInfo
import ch.ninecode.model.ACDCTerminal
import ch.ninecode.model.BasicElement
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element
import ch.ninecode.model.Equipment
import ch.ninecode.model.EquivalentEquipment
import ch.ninecode.model.EquivalentInjection
import ch.ninecode.model.IdentifiedObject
import ch.ninecode.model.Location
import ch.ninecode.model.PositionPoint
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.Terminal

/**
 * Generate the RDD of available short circuit power and angle at each station.
 * Reads a CSV (in a particular format) to extract the ShortCircuitData information.
 *
 * @param session the Spark session
 * @param storage_level specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level</a> used to persist and serialize the objects
 */
case class ShortCircuitInfo (session: SparkSession, storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER")) extends Serializable
{
    import session.sqlContext.implicits._
    val log: Logger = LoggerFactory.getLogger (getClass)

    // get a map of voltages
    val voltage_map: Map[Double, String] =
    {
        // ToDo: fix this 1000V multiplier
        val voltages = session.sql ("select IdentifiedObject.mRID, nominalVoltage * 1000.0 voltage from BaseVoltage")
        voltages.rdd.map ((v) ⇒ (v.getDouble (1), v.getString (0))).collectAsMap ()
    }

    def read_csv (csv: String): RDD[EquivalentInjection] =
    {
        val customSchema = StructType (
            Array
            (
                StructField ("id", StringType),
                StructField ("Fehlerort", StringType),
                StructField ("Un", DoubleType),
                StructField ("Ikw...RST.", DoubleType),
                StructField ("Sk..RST.", DoubleType),
                StructField ("Beschreibung..SAP.Nr..", StringType),
                StructField ("Abgang", StringType),
                StructField ("NIS.ID", StringType),
                StructField ("NIS.Name", StringType)
            )
        )

        val df = session.sqlContext.read
            .format ("csv")
            .option ("header", "true")
            .schema (customSchema)
            .csv (csv)

        def toEquivalentInjection (voltages: Map[Double, String]) (row: Row): EquivalentInjection =
        {
            val id = row.getString (0)
            val ort = row.getString (1)
            val v1 = row.getDouble (2) * 1e3
            val wik = row.getDouble (3)
            val sk = row.getDouble (4) * 1e6
            val sap = row.getString (5)
            val feeder = row.getString (6)
            val station = row.getString (7)
            val name = row.getString (8)

            val c = 1.0
            //val ratioZ0Z1 = 4
            //val ratioX0R0 = 10
            val zqt = (c * v1 * v1) / sk
            //val zqt0 = zqt * ratioZ0Z1
            val wik_radians = Math.PI / 180.0 * wik
            val netz_r1 = zqt * Math.cos (wik_radians)
            val netz_x1 = zqt * Math.sin (wik_radians)
            val netz_r0 = 0.0 // zqt0 * Math.cos (Math.abs (Math.atan (ratioX0R0)))
            val netz_x0 = 0.0 // zqt0 * Math.sin (Math.abs (Math.atan (ratioX0R0)))

            val voltage = voltages.getOrElse (v1, "BaseVoltage_Unknown_%s".format (v1))
            val mRID = "EquivalentInjection_" + id
            val description = "equivalent generation injection at %s from %s".format (ort, feeder)
            val element = BasicElement (null, mRID)
            element.bitfields = Array (Integer.parseInt ("1", 2))
            val obj = IdentifiedObject (element, sap, description, mRID, name + " equivalent injection", null, null)
            obj.bitfields = Array (Integer.parseInt ("1111", 2))
            val psr = PowerSystemResource (obj, null, null, null, null, null, null, null, null, null, null, null)
            psr.bitfields = Array (0)
            val equipment = Equipment (psr, false, true, List(), List(), station, List(), List(), List(), List(), List(), List(), List(), List(), List())
            equipment.bitfields = Array (Integer.parseInt ("10010", 2))
            val conducting = ConductingEquipment (equipment, voltage, null, null, List(), List(), null, List())
            conducting.bitfields = Array (Integer.parseInt ("1", 2))
            val equivalent = EquivalentEquipment (conducting, null)
            equivalent.bitfields = Array (0)
            val injection = EquivalentInjection (equivalent, sk, 0.0, 0.0, 0.0, 0.0, 0.0, netz_r1, netz_r0, netz_r1, false, true, 0.0, netz_x1, netz_x0, netz_x1, null)
            // note: exclude r0, x0, r2, x2 since we don't really know them and they aren't used
            // note: use RegulationStatus to indicate this is a real value and not a default
            injection.bitfields = Array (Integer.parseInt ("0001010001000001", 2))
            injection
        }
        val sc = df.map (toEquivalentInjection (voltage_map)).rdd

        sc
    }

    def table_exists (name: String): Boolean = session.catalog.tableExists (name)

    def toTerminalsAndLocations (pair: (EquivalentInjection, TransformerDetails)): List[Element] =
    {
        val eq_inj = pair._1
        val details = pair._2

        // only keep transformers with matching primary voltage
        if (eq_inj.EquivalentEquipment.ConductingEquipment.BaseVoltage == details.voltage)
        {
            val mRID = details.transformer + "_equivalent_injection"

            // create the location object
            val loc_element = BasicElement (null, mRID + "_location")
            loc_element.bitfields = Array (Integer.parseInt ("1", 2))
            val loc_id_obj = IdentifiedObject (loc_element, null, null, mRID + "_location", null, null, null)
            loc_id_obj.bitfields = Array (Integer.parseInt ("100", 2))
            val location = Location (loc_id_obj, null, null, null, null, null, null, null, null, "geographic", List (), List (), "wgs84", List (), List (), List (), List (), List (), List (), List ())
            location.bitfields = Array (Integer.parseInt ("100100000000", 2))

            // change the mRID and insert the location into the EquivalentInjection
            val old_obj = eq_inj.EquivalentEquipment.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject
            val obj = IdentifiedObject (BasicElement (null, mRID), eq_inj.id, old_obj.description, mRID, old_obj.name, old_obj.DiagramObjects, old_obj.Names)
            val psr = PowerSystemResource (obj, null, null, null, null, location.id, null, null, null, null, null, null)
            psr.bitfields = Array (Integer.parseInt ("10000", 2))
            val equipment = Equipment (psr, false, true, List (), List (), details.station, List (), List (), List (), List (), List (), List (), List (), List (), List ())
            equipment.bitfields = Array (Integer.parseInt ("10010", 2))
            val conducting = ConductingEquipment (equipment, eq_inj.EquivalentEquipment.ConductingEquipment.BaseVoltage, null, null, List (), List (), null, List ())
            conducting.bitfields = Array (Integer.parseInt ("1", 2))
            val equivalent = EquivalentEquipment (conducting, null)
            equivalent.bitfields = Array (0)
            val injection = EquivalentInjection (equivalent, eq_inj.maxP, 0.0, 0.0, 0.0, 0.0, 0.0, eq_inj.r, eq_inj.r0, eq_inj.r2, false, eq_inj.regulationStatus, 0.0, eq_inj.x, eq_inj.x0, eq_inj.x2, null)
            // note: exclude r0, x0, r2, x2 since we don't really know them and they aren't used
            injection.bitfields = Array (Integer.parseInt ("0001010001000001", 2))

            // create the PositionPoint (offset slightly from the transformer)
            val pp_element = BasicElement (null, mRID + "_location_p")
            pp_element.bitfields = Array (Integer.parseInt ("1", 2))
            val position = PositionPoint (pp_element, 1, (details.x - 0.00002).toString, (details.y + 0.00002).toString, null, location.id)
            position.bitfields = Array (Integer.parseInt ("10111", 2))

            // create the terminal to join the transformer primary nodes to EquivalentInjection
            val term_element = BasicElement (null, mRID + "_terminal_1")
            term_element.bitfields = Array (Integer.parseInt ("1", 2))
            val term_id_obj = IdentifiedObject (term_element, null, null, mRID + "_terminal_1", null, null, null)
            term_id_obj.bitfields = Array (Integer.parseInt ("100", 2))
            val acdc = ACDCTerminal (term_id_obj, true, 1, null, List(), List())
            acdc.bitfields = Array (Integer.parseInt ("11", 2))
            val terminal = Terminal (acdc, details.phases, List(), List(), null, mRID, details.connectivity_node, List(), List(), List(), List(), List(), List(), List(), null, List(), details.topological_node, List())
            terminal.bitfields = Array (Integer.parseInt ("01000000000110001", 2))

            List (injection, terminal, location, position)
        }
        else
            List ()
    }

    def getShortCircuitInfo (csv: String): RDD[Element] =
    {
        // get transformers with their primary connectivity node and location
        val tsl = """
            select
                t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID transformer,
                s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station,
                t.ConductingEquipment.Equipment.PowerSystemResource.Location location
            from
                PowerTransformer t, Substation s
            where
                t.ConductingEquipment.Equipment.EquipmentContainer = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID
            %s
            %s
            """.format (
                if (table_exists ("Bay"))
                    """
                    union
                    select
                        t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID transformer,
                        b.Substation station,
                        t.ConductingEquipment.Equipment.PowerSystemResource.Location location
                    from
                        PowerTransformer t, Bay b
                    where
                        t.ConductingEquipment.Equipment.EquipmentContainer = b.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID
                    """
                else
                    "",
                if (table_exists ("VoltageLevel"))
                    """
                    union
                    select
                        t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID transformer,
                        v.Substation station,
                        t.ConductingEquipment.Equipment.PowerSystemResource.Location location
                    from
                        PowerTransformer t, VoltageLevel v
                    where
                        t.ConductingEquipment.Equipment.EquipmentContainer = v.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID
                    """
                else
                    "")
        val tslc = """
            select
                seq.transformer,
                seq.station,
                seq.location,
                t.phases phases,
                t.ConnectivityNode connectivity_node,
                t.TopologicalNode topological_node,
                end.TransformerEnd.BaseVoltage voltage
            from
                Terminal t,
                (
                    select
                        min(t.ACDCTerminal.sequenceNumber) n,
                        tsl.transformer,
                        tsl.station,
                        tsl.location
                    from Terminal t, (%s) tsl
                    where
                        t.ConductingEquipment = tsl.transformer group by tsl.transformer, tsl.station, tsl.location
                ) seq,
                PowerTransformerEnd end
            where
                t.ACDCTerminal.sequenceNumber = seq.n and t.ConductingEquipment = seq.transformer and t.ACDCTerminal.IdentifiedObject.mRID = end.TransformerEnd.Terminal
            """.format (tsl)

        val query = """
            select
                tslc.transformer,
                tslc.station,
                tslc.voltage,
                tslc.phases,
                tslc.connectivity_node,
                tslc.topological_node,
                cast (p.xPosition as double) x,
                cast (p.yPosition as double) y
            from PositionPoint p, (%s) tslc
            where
                p.Location = tslc.location
            """.format (tslc)
        val n = session.sparkContext.getExecutorMemoryStatus.size // get how many executors
        val transformerdetails = session.sql (query).rdd.map (
            row ⇒
                TransformerDetails (
                    row.getString (0),
                    row.getString (1),
                    row.getString (2),
                    row.getString (3),
                    row.getString (4),
                    row.getString (5),
                    row.getDouble (6),
                    row.getDouble (7))).coalesce (n, true).cache

        // read the csv
        val equivalents = read_csv (csv)

        // join transformers by station and add Terminal, Location and PositionPoint
        val injections: RDD[(String, EquivalentInjection)] = equivalents.keyBy (_.EquivalentEquipment.ConductingEquipment.Equipment.EquipmentContainer)
        val transformers: RDD[(String, TransformerDetails)] = transformerdetails.keyBy (_.station)
        val all = injections.join (transformers).values.flatMap (toTerminalsAndLocations)
        all.persist (storage_level)
        all
    }

    def merge (elements: RDD[Element]): Unit =
    {
        val chim = new CHIM ("")
        val classes: List[ClassInfo] = chim.classes
        val subsetters: List[String] = classes.map (info ⇒ info.name)
        val old_elements = session.sparkContext.getPersistentRDDs.filter (_._2.name == "Elements").head._2.asInstanceOf[RDD[Element]]

        // get the list of classes that need to be merged
        def supers (element: Element): List[String] =
        {
            if (null != element)
            {
                val cls = element.getClass
                val classname = cls.getName
                val name = classname.substring (classname.lastIndexOf (".") + 1)
                subsetters.find (_ == name) match { case Some (subsetter) ⇒ List (subsetter) ::: supers (element.sup) case None ⇒ List() }
            }
            else
                List ()
        }
        val uniq_to_be_merged: RDD[String] = elements.flatMap (supers).distinct.cache
        val array_to_be_merged: Array[String] = uniq_to_be_merged.collect
        val list = classes.filter (x ⇒ array_to_be_merged.contains (x.name)).toArray
        // merge each class
        def add (subsetter: CIMSubsetter[_]): Unit =
        {
            val subrdd: RDD[Element] = elements.collect (subsetter.pf).asInstanceOf[RDD[Element]]
            val existing = session.sparkContext.getPersistentRDDs.filter (_._2.name == subsetter.cls)
            val rdd = if (existing.nonEmpty)
            {
                val old_rdd = existing.head._2.asInstanceOf[RDD[Element]]
                old_rdd.name = "pre_shortcircuit_info_" + subsetter.cls
                subrdd.union (old_rdd)
            }
            else
                subrdd
            subsetter.make (session.sqlContext, rdd, storage_level)
        }
        for (info <- list)
            add (info.subsetter)

        // replace elements in Elements
        val new_elements: RDD[Element] = old_elements.union (elements)

        // swap the old Elements RDD for the new one
        old_elements.name = "pre_shortcircuit_info_Elements"
        new_elements.name = "Elements"
        new_elements.persist (storage_level)
        session.sparkContext.getCheckpointDir match
        {
            case Some (_) => new_elements.checkpoint ()
            case None =>
        }
    }
}
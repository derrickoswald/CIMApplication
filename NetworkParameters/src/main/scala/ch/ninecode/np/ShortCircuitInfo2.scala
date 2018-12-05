package ch.ninecode.np

import scala.collection.Map

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.BooleanType
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.IntegerType
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
 * @param session       the Spark session
 * @param storage_level specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level</a> used to persist and serialize the objects
 */
case class ShortCircuitInfo2 (session: SparkSession, storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER")) extends Serializable
{

    import session.sqlContext.implicits._

    val log: Logger = LoggerFactory.getLogger (getClass)

    // get a map of voltages
    val voltage_map: Map[Double, String] =
    {
        // ToDo: fix this 1000V multiplier
        val voltages = session.sql ("select IdentifiedObject.mRID, nominalVoltage * 1000.0 voltage from BaseVoltage")
        voltages.rdd.map (v ⇒ (v.getDouble (1), v.getString (0))).collectAsMap ()
    }

    def read_csv (csv1: String, csv2: String): RDD[EquivalentInjection] =
    {
        val customSchema1 = StructType (
            Array
            (
                StructField ("nis_number", StringType),
                StructField ("Name_Netzeinspeisung", StringType),
                StructField ("Name_Station", StringType),
                StructField ("Gemeinde_Station", StringType),
                StructField ("article_id", StringType),
                StructField ("Type", StringType),
                StructField ("kf_neplan_typ", StringType),
                StructField ("nominal_voltage", DoubleType),
                StructField ("max_connections", IntegerType)
            )
        )

        val df1 = session.sqlContext.read
            .format ("csv")
            .option ("header", "true")
            .option ("sep", ";")
            .option ("encoding", "UTF-8")
            .schema (customSchema1)
            .csv (csv1)

        val customSchema2 = StructType (
            Array
            (
                StructField ("ID", StringType),
                StructField ("Typ", StringType),
                StructField ("Beschr.", StringType),
                StructField ("LF-Typ", StringType),
                StructField ("U_reg_%", DoubleType),
                StructField ("U_Winkel_°", DoubleType),
                StructField ("P_betr_MW", DoubleType),
                StructField ("Q betr_MVAr", DoubleType),
                StructField ("Slack-Anteil_%", DoubleType),
                StructField ("Fernregelung", BooleanType),
                StructField ("Sk\"max_MVA", DoubleType),
                StructField ("Sk\"min_MVA", DoubleType),
                StructField ("Ik\"max_kA", DoubleType),
                StructField ("Ik\"min_kA", DoubleType),
                StructField ("Ik'max_kA", DoubleType),
                StructField ("Ik'min_kA", DoubleType),
                StructField ("Ik\" gem. IEC", DoubleType),
                StructField ("Ebetr", DoubleType),
                StructField ("Z(0)/Z(1) max", DoubleType),
                StructField ("Z(0)/Z(1) min", DoubleType),
                StructField ("R(1)/X(1) max", DoubleType),
                StructField ("R(1)/X(1) min", DoubleType),
                StructField ("Ikw max", DoubleType),
                StructField ("Ikw min", DoubleType),
                StructField ("R(0)/X(0) max", DoubleType),
                StructField ("R(0)/X(0) min", DoubleType),
                StructField ("C1_mF", DoubleType),
                StructField ("c0", DoubleType),
                StructField ("c1", DoubleType),
                StructField ("c2", DoubleType),
                StructField ("MultFac", DoubleType),
                StructField ("VariableLast", BooleanType),
                StructField ("Prodiktionstyp", IntegerType),
                StructField ("MinQ", DoubleType),
                StructField ("MinP", DoubleType),
                StructField ("MaxQ", DoubleType),
                StructField ("MaxP", DoubleType),
                StructField ("RelIdeal", BooleanType),
                StructField ("RelType", StringType),
                StructField ("RelLoadCharact", StringType),
                StructField ("Priority", IntegerType),
                StructField ("ScTimeDec", DoubleType),
                StructField ("FrequencyDependence", StringType),
                StructField ("Proz_wert", BooleanType),
                StructField ("EnergyIdentificationCodeEIC", StringType),
                StructField ("GroupType", StringType),
                StructField ("Rating", IntegerType)
            )
        )

        val df2 = session.sqlContext.read
            .format ("csv")
            .option ("header", "true")
            .option ("sep", ";")
            .option ("encoding", "UTF-8")
            .schema (customSchema2)
            .csv (csv2)

        val df = df1.join (df2, $"Name_Netzeinspeisung" === $"Typ")

        def toEquivalentInjection (voltages: Map[Double, String])(row: Row): EquivalentInjection =
        {
            val id = row.getString (0)
            val station = row.getString (1)
            val name = row.getString (2)
            val ort = name + " - " + row.getString (3)
            val v1 = row.getDouble (7)
            val sk_max = row.getDouble (19) * 1e6
            val sk_min = row.getDouble (20) * 1e6
            val ratioZ0Z1_max = row.getDouble (27)
            val ratioZ0Z1_min = row.getDouble (28) // ToDo: not used, where to put this in the CIM model
            val ratioX1R1_max = row.getDouble (29)
            val ratioX1R1_min = row.getDouble (30)
            // val wik1_max = row.getDouble (31) * Math.PI / 180.0 // these angles are redundant with the X:R ratio in the spreadsheet
            // val wik1_min = row.getDouble (32) * Math.PI / 180.0 // these angles are redundant with the X:R ratio in the spreadsheet
            val wik1_max = -((Math.PI / 2.0) - Math.atan (ratioX1R1_max))
            val wik1_min = -((Math.PI / 2.0) - Math.atan (ratioX1R1_min))
            val ratioX0R0_max = row.getDouble (33)
            val ratioX0R0_min = row.getDouble (34)
            val wik0_max = -((Math.PI / 2.0) - Math.atan (ratioX0R0_max))
            val wik0_min = -((Math.PI / 2.0) - Math.atan (ratioX0R0_min)) // ToDo: not used, where to put this in the CIM model

            val c = 1.0
            val zqt1_max = (c * v1 * v1) / sk_max
            val zqt1_min = (c * v1 * v1) / sk_min
            val zqt0_max = zqt1_max * ratioZ0Z1_max
            val zqt0_min = zqt1_min * ratioZ0Z1_min // ToDo: not used, where to put this in the CIM model
            val netz_r1 = zqt1_max * Math.cos (wik1_max)
            val netz_x1 = zqt1_max * Math.sin (wik1_max)
            val netz_r0 = zqt0_max * Math.cos (wik0_max)
            val netz_x0 = zqt0_max * Math.sin (wik0_max)

            val voltage = voltages.getOrElse (v1, "BaseVoltage_Unknown_%s".format (v1))
            val mRID = "EquivalentInjection_" + id
            val alias = id
            val description = "equivalent generation injection at %s".format (ort)
            val element = BasicElement (null, mRID)
            element.bitfields = Array (Integer.parseInt ("1", 2))
            val obj = IdentifiedObject (element, alias, description, mRID, name + " equivalent injection", null, null)
            obj.bitfields = Array (Integer.parseInt ("1111", 2))
            val psr = PowerSystemResource (obj, null, null, null, null, null, null, null, null, null, null, null)
            psr.bitfields = Array (0)
            val equipment = Equipment (psr, false, true, List (), List (), station, List (), List (), List (), List (), List (), List (), List (), List (), List ())
            equipment.bitfields = Array (Integer.parseInt ("10010", 2))
            val conducting = ConductingEquipment (equipment, voltage, null, null, List (), List (), null, List ())
            conducting.bitfields = Array (Integer.parseInt ("1", 2))
            val equivalent = EquivalentEquipment (conducting, null)
            equivalent.bitfields = Array (0)
            // decompose sk values into P & Q
            val maxP = sk_max * Math.cos (wik1_max)
            val maxQ = sk_max * Math.sin (wik1_max)
            val minP = sk_min * Math.cos (wik1_min)
            val minQ = sk_min * Math.sin (wik1_min)
            val injection = EquivalentInjection (equivalent, maxP, maxQ, minP, minQ, 0.0, 0.0, netz_r1, netz_r0, netz_r1, false, true, 0.0, netz_x1, netz_x0, netz_x1, null)
            // note: use RegulationStatus to indicate this is a real value and not a default
            // skip r2,x2 since we don't really know them
            injection.bitfields = Array (Integer.parseInt ("0011010011001111", 2))
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
            val injection = EquivalentInjection (equivalent, eq_inj.maxP, eq_inj.maxQ, eq_inj.minP, eq_inj.minQ, eq_inj.p, eq_inj.q, eq_inj.r, eq_inj.r0, eq_inj.r2, eq_inj.regulationCapability, eq_inj.regulationStatus, eq_inj.regulationTarget, eq_inj.x, eq_inj.x0, eq_inj.x2, eq_inj.ReactiveCapabilityCurve)
            injection.bitfields = eq_inj.bitfields

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
            val acdc = ACDCTerminal (term_id_obj, true, 1, null, List (), List ())
            acdc.bitfields = Array (Integer.parseInt ("11", 2))
            val terminal = Terminal (acdc, details.phases, List (), List (), null, mRID, details.connectivity_node, List (), List (), List (), List (), List (), List (), List (), null, List (), details.topological_node, List ())
            terminal.bitfields = Array (Integer.parseInt ("01000000000110001", 2))

            List (injection, terminal, location, position)
        }
        else
            List ()
    }

    def getShortCircuitInfo (csv1: String, csv2: String): RDD[Element] =
    {
        // get transformers with their primary connectivity node and location
        val tsl =
            """
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
        val tslc =
            """
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

        val query =
            """
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
        val equivalents = read_csv (csv1, csv2)

        // join transformers by station and add Terminal, Location and PositionPoint
        val injections: RDD[(String, EquivalentInjection)] = equivalents.keyBy (_.EquivalentEquipment.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName)
        val transformers: RDD[(String, TransformerDetails)] = transformerdetails.keyBy (_.transformer)
        val all = injections.join (transformers).values.flatMap (toTerminalsAndLocations)
        all.persist (storage_level)
        all
    }

    def merge (elements: RDD[Element]): Unit =
    {
        val chim = new CHIM ("")
        val classes: List[ClassInfo] = chim.classes
        val subsetters: List[String] = classes.map (info ⇒ info.name)
        val old_elements = session.sparkContext.getPersistentRDDs.filter (_._2.name == "Elements").head._2.asInstanceOf [RDD[Element]]

        // get the list of classes that need to be merged
        def supers (element: Element): List[String] =
        {
            if (null != element)
            {
                val cls = element.getClass
                val classname = cls.getName
                val name = classname.substring (classname.lastIndexOf (".") + 1)
                subsetters.find (_ == name) match
                {
                    case Some (subsetter) ⇒ List (subsetter) ::: supers (element.sup)
                    case None ⇒ List ()
                }
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
            val subrdd: RDD[Element] = elements.collect (subsetter.pf).asInstanceOf [RDD[Element]]
            val existing = session.sparkContext.getPersistentRDDs.filter (_._2.name == subsetter.cls)
            val rdd = if (existing.nonEmpty)
            {
                val old_rdd = existing.head._2.asInstanceOf [RDD[Element]]
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
        if (session.sparkContext.getCheckpointDir.isDefined) new_elements.checkpoint ()
    }
}

package ch.ninecode.np

import scala.collection.Map
import scala.reflect.runtime.universe

import org.apache.log4j.Level
import org.apache.log4j.LogManager
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
import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.CIMExport
import ch.ninecode.cim.CIMRDD
import ch.ninecode.cim.CIMSubsetter
import ch.ninecode.cim.DefaultSource
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
import ch.ninecode.util.CIMInitializer
import ch.ninecode.util.Main
import ch.ninecode.util.MainOptions
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Util


/**
 * Generate the RDD of available short circuit power and angle at each station.
 * Reads a CSV (in a particular format) to extract the ShortCircuitData information.
 *
 * @param session       the Spark session
 * @param options       options for processing
 */
case class ShortCircuitInfo2 (
    session: SparkSession,
    options: NetworkParametersOptions
)
    extends CIMRDD
{

    import session.sqlContext.implicits._

    implicit val spark: SparkSession = session
    implicit val storage: StorageLevel = options.cim_options.storage
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    // get a map of voltages
    val voltage_map: Map[Double, String] =
    {
        // ToDo: fix this 1000V multiplier
        val voltages = session.sql ("select IdentifiedObject.mRID, nominalVoltage * 1000.0 voltage from BaseVoltage")
        voltages.rdd.map (v => (v.getDouble (1), v.getString (0))).collectAsMap ()
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
            val wik1_max = -row.getDouble (31) * Math.PI / 180.0 // these angles are redundant with the X:R ratio in the spreadsheet
            val wik1_min = -row.getDouble (32) * Math.PI / 180.0 // these angles are redundant with the X:R ratio in the spreadsheet
            val ratioX0R0_max = row.getDouble (33)
            val wik0_max = -((Math.PI / 2.0) - Math.atan (ratioX0R0_max))

            val c = 1.0
            val zqt1_max = (c * v1 * v1) / sk_max
            val zqt0_max = zqt1_max * ratioZ0Z1_max
            val netz_r1 = zqt1_max * Math.cos (wik1_max)
            val netz_x1 = zqt1_max * Math.sin (wik1_max)
            val netz_r0 = zqt0_max * Math.cos (wik0_max)
            val netz_x0 = zqt0_max * Math.sin (wik0_max)

            val voltage = voltages.getOrElse (v1, "BaseVoltage_Unknown_%s".format (v1))
            val mRID = "EquivalentInjection_" + id
            val alias = id
            val description = "equivalent generation injection at %s".format (ort)
            val element = BasicElement (mRID = mRID)
            val obj = IdentifiedObject (element, aliasName = alias, description = description, mRID = mRID, name = s"$name equivalent injection")
            obj.bitfields = IdentifiedObject.fieldsToBitfields ("aliasName", "description", "mRID", "name")
            val psr = PowerSystemResource (obj)
            psr.bitfields = PowerSystemResource.fieldsToBitfields ()
            val equipment = Equipment (psr, inService = true, normallyInService = true, EquipmentContainer = station)
            equipment.bitfields = Equipment.fieldsToBitfields ("inService", "normallyInService", "EquipmentContainer")
            val conducting = ConductingEquipment (equipment, BaseVoltage = voltage)
            conducting.bitfields = ConductingEquipment.fieldsToBitfields ("BaseVoltage")
            val equivalent = EquivalentEquipment (conducting)
            equivalent.bitfields = EquivalentEquipment.fieldsToBitfields ()
            // decompose sk values into P & Q
            val maxP = sk_max * Math.cos (wik1_max)
            val maxQ = sk_max * Math.sin (wik1_max)
            val minP = sk_min * Math.cos (wik1_min)
            val minQ = sk_min * Math.sin (wik1_min)
            // note: use RegulationStatus to indicate this is a real value and not a default
            // skip r2,x2 since we don't really know them
            val injection = EquivalentInjection (
                equivalent,
                maxP = maxP,
                maxQ = maxQ,
                minP = minP,
                minQ = minQ,
                p = 0.0,
                q = 0.0,
                r = netz_r1,
                r0 = netz_r0,
                r2 = netz_r1,
                regulationCapability = false,
                regulationStatus = true,
                regulationTarget = 0.0,
                x = netz_x1,
                x0 = netz_x0,
                x2 = netz_x1)
            // note: exclude r2, x2 since we don't really know them and they aren't used
            // note: use RegulationStatus to indicate this is a real value and not a default
            injection.bitfields = EquivalentInjection.fieldsToBitfields (
                "maxP",
                "maxQ",
                "minP",
                "minQ",
                "p",
                "q",
                "r",
                "r0",
                "r2",
                "regulationCapability",
                "regulationStatus",
                "regulationTarget",
                "x",
                "x0",
                "x2")
            injection
        }

        val sc = df.map (toEquivalentInjection (voltage_map)).rdd

        sc
    }

    def table_exists (name: String): Boolean = session.catalog.tableExists (name)

    def toTerminalsAndLocations (pair: (EquivalentInjection, TransformerDetails)): List[Element] =
    {
        val (eq_inj, details) = pair

        // only keep transformers with matching primary voltage
        if (eq_inj.EquivalentEquipment.ConductingEquipment.BaseVoltage == details.voltage)
        {
            val mRID = details.transformer + "_equivalent_injection"

            // create the location object
            val loc_element = BasicElement (mRID = s"${mRID}_location")
            val loc_id_obj = IdentifiedObject (loc_element, mRID = s"${mRID}_location")
            loc_id_obj.bitfields = IdentifiedObject.fieldsToBitfields ("mRID")
            val location = Location (loc_id_obj, `type` = "geographic", CoordinateSystem = "wgs84")
            location.bitfields = Location.fieldsToBitfields ("type", "CoordinateSystem")

            // change the mRID and insert the location into the EquivalentInjection
            val old_obj = eq_inj.EquivalentEquipment.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject
            val obj = IdentifiedObject (BasicElement (mRID = mRID), aliasName = eq_inj.id, description = old_obj.description, mRID = mRID, name = old_obj.name, DiagramObjects = old_obj.DiagramObjects, Names = old_obj.Names)
            obj.bitfields = IdentifiedObject.fieldsToBitfields ("aliasName", "description", "mRID", "name", "DiagramObjects", "Names")
            val psr = PowerSystemResource (obj, Location = location.id)
            psr.bitfields = PowerSystemResource.fieldsToBitfields ("Location")
            val equipment = Equipment (psr, inService = true, normallyInService = true, EquipmentContainer = details.station)
            equipment.bitfields = Equipment.fieldsToBitfields ("inService", "normallyInService", "EquipmentContainer")
            val conducting = ConductingEquipment (equipment, BaseVoltage = eq_inj.EquivalentEquipment.ConductingEquipment.BaseVoltage)
            conducting.bitfields = ConductingEquipment.fieldsToBitfields ("BaseVoltage")
            val equivalent = EquivalentEquipment (conducting)
            equivalent.bitfields = EquivalentEquipment.fieldsToBitfields ()
            val injection = EquivalentInjection (equivalent, eq_inj.maxP, eq_inj.maxQ, eq_inj.minP, eq_inj.minQ, eq_inj.p, eq_inj.q, eq_inj.r, eq_inj.r0, eq_inj.r2, eq_inj.regulationCapability, eq_inj.regulationStatus, eq_inj.regulationTarget, eq_inj.x, eq_inj.x0, eq_inj.x2, eq_inj.ReactiveCapabilityCurve)
            injection.bitfields = eq_inj.bitfields.clone

            // create the PositionPoint (offset slightly from the transformer)
            val pp_element = BasicElement (mRID = s"${mRID}_location_p")
            val position = PositionPoint (pp_element, sequenceNumber = 1, xPosition = (details.x - 0.00002).toString, yPosition = (details.y + 0.00002).toString, Location = location.id)
            position.bitfields = PositionPoint.fieldsToBitfields ("sequenceNumber", "xPosition", "yPosition", "Location")

            // create the terminal to join the transformer primary nodes to EquivalentInjection
            val term_element = BasicElement (mRID = s"${mRID}_terminal_1")
            val term_id_obj = IdentifiedObject (term_element, mRID = s"${mRID}_terminal_1")
            term_id_obj.bitfields = IdentifiedObject.fieldsToBitfields ("mRID")
            val acdc = ACDCTerminal (term_id_obj, connected = true, sequenceNumber = 1)
            acdc.bitfields = ACDCTerminal.fieldsToBitfields ("connected", "sequenceNumber")
            val terminal = Terminal (acdc, phases = details.phases, ConductingEquipment = mRID, ConnectivityNode = details.connectivity_node, TopologicalNode = details.topological_node)
            terminal.bitfields = Terminal.fieldsToBitfields ("phases", "ConductingEquipment", "ConnectivityNode", "TopologicalNode")

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
        val nexec = session.sparkContext.getExecutorMemoryStatus.size
        val transformerdetails = session.sql (query).rdd.map (
            row =>
                TransformerDetails (
                    row.getString (0),
                    row.getString (1),
                    row.getString (2),
                    row.getString (3),
                    row.getString (4),
                    row.getString (5),
                    row.getDouble (6),
                    row.getDouble (7))).coalesce (nexec, shuffle = true).cache

        // read the csv
        val equivalents = read_csv (csv1, csv2)

        // join transformers by station and add Terminal, Location and PositionPoint
        val injections: RDD[(String, EquivalentInjection)] = equivalents.keyBy (_.EquivalentEquipment.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName)
        val transformers: RDD[(String, TransformerDetails)] = transformerdetails.keyBy (_.transformer)
        val all = injections.join (transformers).values.flatMap (toTerminalsAndLocations)
        val _ = all.persist (storage)
        all
    }

    def merge (elements: RDD[Element]): Unit =
    {
        val chim = new CHIM ("")
        val subsetters: List[String] = chim.classes.map (info => info.name)
        val old_elements = get [Element]("Elements")

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
                    case Some (subsetter) => List (subsetter) ::: supers (element.sup)
                    case None => List ()
                }
            }
            else
                List ()
        }

        val uniq_to_be_merged: RDD[String] = elements.flatMap (supers).distinct.cache
        val array_to_be_merged: Array[String] = uniq_to_be_merged.collect
        val list = chim.classes.filter (x => array_to_be_merged.contains (x.name)).toArray

        // merge each class
        def add[T <: Product] (subsetter: CIMSubsetter[T]): Unit =
        {
            implicit val classtag: scala.reflect.ClassTag[T] = scala.reflect.ClassTag[T](subsetter.runtime_class)
            implicit val tag: universe.TypeTag[T] = subsetter.tag
            val subrdd: RDD[T] = elements.flatMap (subsetter.asThisClass)
            val existing: RDD[T] = getOrElse [subsetter.basetype](subsetter.cls)
            val _ = put [T](subrdd.union (existing))
        }

        for (info <- list)
            add (info.subsetter)

        // replace elements in Elements
        val new_elements: RDD[Element] = old_elements.union (elements)
        val _ = put (new_elements, "Elements", true)
    }


    def run (): Unit =
    {
        val equivalents = getShortCircuitInfo (options.available_power_csv, options.station_transformer_csv)
        val export = new CIMExport (session)
        export.export (equivalents, options.available_power_csv.replace (".csv", ".rdf"))
        if ("" != options.export)
        {
            merge (equivalents)
            export.exportAll (options.export)
        }
    }
}

object ShortCircuitInfo2 extends CIMInitializer[NetworkParametersOptions] with Main
{
    def run (options: NetworkParametersOptions): Unit =
    {
        if (options.verbose)
        {
            LogManager.getLogger (getClass).setLevel (Level.INFO)
            LogManager.getLogger (classOf[ShortCircuitInfo2]).setLevel (org.apache.log4j.Level.INFO)
        }
        if (options.main_options.valid)
        {
            if (options.cim_options.files.nonEmpty)
            {
                if ("" != options.available_power_csv && "" != options.station_transformer_csv)
                {
                    val session: SparkSession = createSession (options)
                    readCIM (session, options)
                    time ("execution: %s seconds")
                    {
                        ShortCircuitInfo2 (session, options).run ()
                    }
                }
                else
                    log.error (s"no CSV file (${ if ("" != options.available_power_csv) "csv1" else "csv2" }) specified")
            }
            else
                log.error ("no CIM files specified")
        }
    }

    def main (args: Array[String])
    {
        val have = util.Properties.versionNumberString
        val need = scala_library_version
        if (have != need)
        {
            log.error (s"Scala version ($have) does not match the version ($need) used to build $application_name")
            sys.exit (1)
        }
        else
        {
            // get the necessary jar files to send to the cluster
            val jars = Set (
                jarForObject (new DefaultSource ()),
                jarForObject (NetworkParametersOptions ())
            ).toArray

            // compose the classes to be registered with Kryo
            val kryo = Array.concat (
                // register CIMReader classes
                CIMClasses.list,
                // register Util classes
                Util.classes)

            // initialize the default options
            val temp = NetworkParametersOptions ()
            val default = NetworkParametersOptions (
                main_options = MainOptions (s"Customer2_NetworkParameters", application_version),
                spark_options = SparkOptions (jars = jars, kryo = kryo),
                cim_options = temp.cim_options.copy (options = temp.cim_options.toMap),
                available_power_csv = "Trafos_fuer_Analytiks.csv",
                station_transformer_csv = "Netzeinspeisungen.csv"
            )

            // parse the command line arguments
            new Customer2OptionsParser (default).parse (args, default) match
            {
                case Some (options) =>
                    // execute the main program if everything checks out
                    run (options)
                    if (!options.main_options.unittest)
                        sys.exit (0)
                case None =>
                    sys.exit (1)
            }
        }
    }
}

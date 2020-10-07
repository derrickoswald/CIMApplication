package ch.ninecode.np

import scala.collection.Map
import scala.reflect.runtime.universe

import org.apache.log4j.Level
import org.apache.log4j.LogManager
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
case class ShortCircuitInfo3 (
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

    def read_csv (csv: String): RDD[EquivalentInjection] =
    {
        val customSchema = StructType (
            Array
            (
                StructField ("OBJEKT", StringType),
                StructField ("KS-LEISTUNG MS-NETZ SK", DoubleType),
                StructField ("R-X-VERHAELTNIS", DoubleType),
                StructField ("SR_TYP_IN_KVA", DoubleType),
                StructField ("UR1_NENNSPANNUNG1", DoubleType),
                StructField ("UR2_NENNSPANNUNG1", DoubleType),
                StructField ("URR1_KURZSCHLUSSVERLUSTE_IN_W", DoubleType),
                StructField ("URR1_KURZSCHLUSSVERLUSTE_IN_PR", DoubleType),
                StructField ("UKR1_KURZSCHLUSSSPANNUNG_IN_PR", DoubleType),
                StructField ("EIGENTUEMER", StringType)
            )
        )

        val df = session.sqlContext.read
            .format ("csv")
            .option ("header", "true")
            .option ("sep", ";")
            .option ("encoding", "UTF-8")
            .option ("mode", "DROPMALFORMED") // remove units in second row
            .schema (customSchema)
            .csv (csv)

        def toEquivalentInjection (voltages: Map[Double, String])(row: Row): EquivalentInjection =
        {
            val id = row.getString (0)
            val v1 = if (row.isNullAt (4)) 20000 else row.getDouble (4) * 1e3 // default 20kV
            val rx_ratio = row.getDouble (2) // 0.1 = r / x and also sqrt (r*r + x*x) == 1.0
            val x = Math.sqrt (1.0 / (rx_ratio * rx_ratio + 1.0))
            val r = rx_ratio * x
            val sk = row.getDouble (1) * 1e6

            val station = "unknown"

            val c = 1.0
            //val ratioZ0Z1 = 4
            //val ratioX0R0 = 10
            val zqt = (c * v1 * v1) / sk
            //val zqt0 = zqt * ratioZ0Z1
            val netz_r1 = zqt * r
            val netz_x1 = zqt * x
            val netz_r0 = 0.0 // zqt0 * Math.cos (Math.abs (Math.atan (ratioX0R0)))
            val netz_x0 = 0.0 // zqt0 * Math.sin (Math.abs (Math.atan (ratioX0R0)))

            def findClosestVoltage (voltage: Double): String =
            {
                val best = voltages.foldLeft ((Double.MaxValue, "BaseVoltage_Unknown_%s".format (voltage)))(
                    (best, next) =>
                    {
                        val diff = Math.abs (voltage - next._1)
                        if (diff < best._1) (diff, next._2) else best
                    }
                )
                best._2
            }

            val voltage = voltages.getOrElse (v1, findClosestVoltage (v1))
            val mRID = "EquivalentInjection_" + id
            val description = "equivalent generation injection at %s primary".format (id)
            val element = BasicElement (mRID = mRID)
            // Note: use aliasName as a primary key temporarily
            val obj = IdentifiedObject (element, aliasName = id, description = description, mRID = mRID, name = s"$id equivalent injection")
            obj.bitfields = IdentifiedObject.fieldsToBitfields ("aliasName", "description", "mRID", "name")
            val psr = PowerSystemResource (obj)
            psr.bitfields = PowerSystemResource.fieldsToBitfields ()
            val equipment = Equipment (psr, inService = true, normallyInService = true, EquipmentContainer = station)
            equipment.bitfields = Equipment.fieldsToBitfields ("inService", "normallyInService", "EquipmentContainer")
            val conducting = ConductingEquipment (equipment, BaseVoltage = voltage)
            conducting.bitfields = ConductingEquipment.fieldsToBitfields ("BaseVoltage")
            val equivalent = EquivalentEquipment (conducting)
            equivalent.bitfields = EquivalentEquipment.fieldsToBitfields ()
            // decompose sk values into P & Q, use maxP and maxQ also as minP and minQ respectively
            val maxP = sk * r
            val maxQ = sk * x
            val injection = EquivalentInjection (
                equivalent,
                maxP = maxP,
                maxQ = maxQ,
                minP = maxP,
                minQ = maxQ,
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

    def toTerminalsAndLocations (pairs: Iterable[(EquivalentInjection, TransformerDetails)]): List[Element] =
    {
        val pair :: _ = pairs.toList
        val (eq_inj, details) = pair

        // only keep transformers with matching primary voltage
        if (eq_inj.EquivalentEquipment.ConductingEquipment.BaseVoltage == details.voltage)
        {
            val mRID = pairs.map (_._2.transformer).toArray.sortWith (_ < _).mkString ("_") + "_equivalent_injection"

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

    def getShortCircuitInfo (csv: String): RDD[Element] =
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
        val equivalents = read_csv (csv)

        // join transformers by station and add Terminal, Location and PositionPoint
        val injections: RDD[(String, EquivalentInjection)] = equivalents.keyBy (_.EquivalentEquipment.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName)
        val transformers: RDD[(String, TransformerDetails)] = transformerdetails.keyBy (_.transformer)
        // group transformers by (primary) TopologicalNode
        val grouped = injections.join (transformers).values.groupBy (_._2.topological_node).values
        val all = grouped.flatMap (toTerminalsAndLocations)
        val _ = all.persist (storage)
        all
    }

    def merge (elements: RDD[Element]): Unit =
    {
        val chim = new CHIM ("")
        val subsetters: List[String] = chim.classes.map (info => info.name)
        val old_elements = getOrElse[Element]

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
        val _ = put (new_elements, true)
    }

    def run (): Unit =
    {
        val equivalents = getShortCircuitInfo (options.available_power_csv)
        val export = new CIMExport (session)
        export.export (equivalents, options.available_power_csv.replace (".csv", ".rdf"))
        if ("" != options.export)
        {
            merge (equivalents)
            export.exportAll (options.export)
        }
    }
}

object ShortCircuitInfo3 extends CIMInitializer[NetworkParametersOptions] with Main
{
    def run (options: NetworkParametersOptions): Unit =
    {
        if (options.verbose)
        {
            LogManager.getLogger (getClass).setLevel (Level.INFO)
            LogManager.getLogger (classOf[ShortCircuitInfo3]).setLevel (org.apache.log4j.Level.INFO)
        }
        if (options.main_options.valid)
        {
            if (options.cim_options.files.nonEmpty)
            {
                if ("" != options.available_power_csv)
                {
                    val session: SparkSession = createSession (options)
                    readCIM (session, options)
                    time ("execution: %s seconds")
                    {
                        ShortCircuitInfo3 (session, options).run ()
                    }
                }
                else
                    log.error ("no CSV file specified")
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
                main_options = MainOptions (s"Customer3_NetworkParameters", application_version),
                spark_options = SparkOptions (jars = jars, kryo = kryo),
                cim_options = temp.cim_options.copy (options = temp.cim_options.toMap),
                available_power_csv = "20181002_Transformatoren.csv"
            )

            // parse the command line arguments
            new Customer3OptionsParser (default).parse (args, default) match
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
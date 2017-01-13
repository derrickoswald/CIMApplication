package ch.ninecode.sp

import java.io.UnsupportedEncodingException
import java.net.URLDecoder

import scala.reflect.runtime.universe
import scala.tools.nsc.io.Jar
import scala.util.Random
import java.util.HashMap
import java.util.Map

import org.apache.spark.sql.Row
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim._
import ch.ninecode.model._


case class PositionedEnergyConsumer
(
    override val sup: ConductingEquipment,
    val customerCount: Int,
    val grounded: Boolean,
    val p: Double,
    val pfixed: Double,
    val pfixedPct: Double,
    val phaseConnection: String,
    val q: Double,
    val qfixed: Double,
    val qfixedPct: Double,
    val LoadDynamics: String,
    val LoadResponse: String,
    val PowerCutZone: String,
    val xPosition: String,
    val yPosition: String
)
extends
    Element
{
    def this () = { this (null, 0, false, 0.0, 0.0, 0.0, null, 0.0, 0.0, 0.0, null, null, null, "", "") }
    def ConductingEquipment: ConductingEquipment = sup.asInstanceOf[ConductingEquipment]
    override def copy (): Row = { return (clone ().asInstanceOf[PositionedEnergyConsumer]); }
    override def get (i: Int): Object =
    {
        if (i < productArity)
            productElement (i).asInstanceOf[AnyRef]
        else
            throw new IllegalArgumentException ("invalid property index " + i)
    }
    override def length: Int = productArity
}

case class HouseService (
    val mRID: String,
    val name: String,
    val aliasName: String,
    val xPosition: String,
    val yPosition: String,
    val PSRType: String,
    val BaseVoltage: String,
    val EquipmentContainer: String,
    val phaseConnection: String,
    val ao_name: String,
    val ao_aliasName: String,
    val ao_description: String,
    val ao_mainAddress: String,
    val ao_secondaryAddress: String)

class SpatialOperations extends Serializable
{
    var _StorageLevel = StorageLevel.MEMORY_ONLY

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


//        <cim:EnergyConsumer rdf:ID="HAS3047">
//                <cim:IdentifiedObject.name>HAS3047</cim:IdentifiedObject.name>
//                <cim:IdentifiedObject.aliasName>209192256:nis_el_house_service</cim:IdentifiedObject.aliasName>
//                <cim:PowerSystemResource.Location rdf:resource="#_location_654244_1068880481_209192258"/>
//                <cim:PowerSystemResource.PSRType rdf:resource="#PSRType_Unknown"/>
//                <cim:ConductingEquipment.BaseVoltage rdf:resource="#BaseVoltage_400"/>
//                <cim:Equipment.EquipmentContainer rdf:resource="#_line_ABG163519|HAS3047|KLE11084"/>
//                <cim:EnergyConsumer.phaseConnection rdf:resource="http://iec.ch/TC57/2010/CIM-schema-cim15#PhaseShuntConnectionKind.Y"/>
//        </cim:EnergyConsumer>
//
//        <cim:ServiceLocation rdf:ID="MST3240">
//                <cim:IdentifiedObject.name>MST3240</cim:IdentifiedObject.name>
//                <cim:IdentifiedObject.aliasName>327144463:nis_el_meter_point</cim:IdentifiedObject.aliasName>
//                <cim:Location.CoordinateSystem rdf:resource="wgs_84"/>
//                <cim:Location.type>geographic</cim:Location.type>
//        </cim:ServiceLocation>

    def position_consumers (a: Tuple2[EnergyConsumer, PositionPoint]): PositionedEnergyConsumer =
    {
        PositionedEnergyConsumer (
            a._1.ConductingEquipment,
            a._1.customerCount,
            a._1.grounded,
            a._1.p,
            a._1.pfixed,
            a._1.pfixedPct,
            a._1.phaseConnection,
            a._1.q,
            a._1.qfixed,
            a._1.qfixedPct,
            a._1.LoadDynamics,
            a._1.LoadResponse,
            a._1.PowerCutZone,
            a._2.xPosition,
            a._2.yPosition)
    }

    def shrink (a: Tuple2[PositionedEnergyConsumer, Option[ServiceLocation]]): HouseService =
    {
        return (
            a._2 match
            {
                case (Some (x: ServiceLocation)) ⇒
                {
                    HouseService (
                        x.id,
                        a._1.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name,
                        a._1.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName,
                        a._1.xPosition,
                        a._1.yPosition,
                        a._1.ConductingEquipment.Equipment.PowerSystemResource.PSRType,
                        a._1.ConductingEquipment.BaseVoltage,
                        a._1.ConductingEquipment.Equipment.EquipmentContainer,
                        a._1.phaseConnection,
                        x.WorkLocation.Location.IdentifiedObject.name,
                        x.WorkLocation.Location.IdentifiedObject.aliasName,
                        x.WorkLocation.Location.IdentifiedObject.description,
                        x.WorkLocation.Location.mainAddress,
                        x.WorkLocation.Location.secondaryAddress)
                }
                case (None) ⇒
                {
                    HouseService (
                        "",
                        a._1.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name,
                        a._1.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName,
                        a._1.xPosition,
                        a._1.yPosition,
                        a._1.ConductingEquipment.Equipment.PowerSystemResource.PSRType,
                        a._1.ConductingEquipment.BaseVoltage,
                        a._1.ConductingEquipment.Equipment.EquipmentContainer,
                        a._1.phaseConnection,
                        "",
                        "",
                        "",
                        "",
                        "")
                }
            }
        )
    }

    def nearest (sc: SparkContext, sqlContext: SQLContext, args: String): DataFrame =
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

        // get the name of the class of interest
        val clazz = arguments.getOrElse ("psr", "EnergyConsumer")

        // get longitude and latitude
        val lon = arguments.getOrElse ("lon", "7.281558").toDouble
        val lat = arguments.getOrElse ("lat", "47.124142").toDouble

        // get how many
        val n = arguments.getOrElse ("n", "5").toInt

        // I can't figure out how to do this with a generic class
        // maybe use PowerSysemResource RDD (which has the Location), and then join to Elements via mRID, and then filter elements by class name

        // get all house connections (energy consumers with a base voltage of 400 volts, eliminates the 230 volt public lighting)
        val consumers = get ("EnergyConsumer", sc).asInstanceOf[RDD[EnergyConsumer]].filter (_.ConductingEquipment.BaseVoltage == "BaseVoltage_400")

        // get the points
        val points = get ("PositionPoint", sc).asInstanceOf[RDD[PositionPoint]]

        // attach the locations to the house connections to get RDD[PositionedEnergyConsumer]
        val located_consumers = consumers.keyBy (_.ConductingEquipment.Equipment.PowerSystemResource.Location).join (points.keyBy (_.Location)).values.map (position_consumers)

        // try and join the EnergyConsumer to a ServicePoint
        val attributes = get ("UserAttribute", sc).asInstanceOf[RDD[UserAttribute]]
        val locations = get ("ServiceLocation", sc).asInstanceOf[RDD[ServiceLocation]]
        def pull (a: Tuple2[UserAttribute, ServiceLocation]): Tuple2[String, ServiceLocation] =
        {
            (a._1.value, a._2)
        }
        val location_by_has = attributes.keyBy (_.name).join (locations.keyBy (_.id)).values.map (pull)
        val all = located_consumers.keyBy (_.id).leftOuterJoin (location_by_has).values.map (shrink)

        def ordering (item: HouseService) =
        {
            val dx = lon - item.xPosition.toDouble;
            val dy = lat - item.yPosition.toDouble;
            dx * dx + dy * dy;
        }

        val closest = all.sortBy (ordering) // ToDo: takeOrdered in one step?
        val df = sqlContext.createDataFrame (closest.take (n))
        val schema = df.schema

        return (df)
    }
}

object SpatialOperations
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
        val spatial = new SpatialOperations ()
        val filename = if (args.length > 0)
            args (0)
        else
            // "hdfs://sandbox:9000/data/" + "NIS_CIM_Export_b4_Bruegg" + ".rdf"
            "hdfs://sandbox:9000/data/" + "NIS_CIM_Export_b4_Guemligen" + ".rdf"

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("ShortCircuit")
        configuration.setMaster ("spark://sandbox:7077")
        configuration.setSparkHome ("/home/derrick/spark-1.6.0-bin-hadoop2.6/")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "4g")
        // get the necessary jar files to send to the cluster
        val s1 = jarForObject (new DefaultSource ())
        val s2 = jarForObject (spatial)
        configuration.setJars (Array (s1, s2))

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[ch.ninecode.cim.Edge]))

        // make a Spark session
        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val start = System.nanoTime ()
        val files = filename.split (",")
        val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER");
        options.put ("ch.ninecode.cim.make_edges", "false");
        options.put ("ch.ninecode.cim.do_join", "false");
        val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        val count = elements.count

        val read = System.nanoTime ()

        spatial._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER
//        val results = spatial.nearest (_Context, _SqlContext, "psr=EnergyConsumer,lon=7.281558,lat=47.124142,n=5")
        val results = spatial.nearest (session.sparkContext, session.sqlContext, "psr=EnergyConsumer,lon=7.486344,lat=46.929949,n=5")
        val s = results.schema
        val stuff = results.collect ()

        println ("" + count + " elements")
        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("execute: " + (System.nanoTime () - read) / 1e9 + " seconds")

        for (i <- 0 until stuff.length)
            println (stuff(i))
        println ();
    }
}

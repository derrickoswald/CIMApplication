package ch.ninecode.sp

import scala.reflect.ClassTag
import scala.reflect.classTag

import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession

import ch.ninecode.model._

class SpatialOperations extends Serializable
{
    def get[T: ClassTag] (session: SparkSession): RDD[T] =
    {

        val classname = classTag [T].runtimeClass.getName
        val name = classname.substring (classname.lastIndexOf (".") + 1)
        val rdds: collection.Map[Int, RDD[_]] = session.sparkContext.getPersistentRDDs
        rdds.find (_._2.name == name) match
        {
            case Some ((_: Int, rdd: RDD[_])) =>
                rdd.asInstanceOf [RDD[T]]
            case Some (_) =>
                null
            case None =>
                null
        }
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

    def position_consumers (a: (EnergyConsumer, PositionPoint)): PositionedEnergyConsumer =
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

    def shrink (a: (PositionedEnergyConsumer, Option[ServiceLocation])): HouseService =
    {
        a._2 match
        {
            case Some (x: ServiceLocation) ⇒
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
            case None ⇒
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

    def nearest (session: SparkSession, args: SpatialOperationParameters): DataFrame =
    {
        // I can't figure out how to do this with a generic class
        // maybe use PowerSysemResource RDD (which has the Location), and then join to Elements via mRID, and then filter elements by class name

        // get all house connections (energy consumers with a base voltage of 400 volts, eliminates the 230 volt public lighting)
        val consumers = get [EnergyConsumer](session).filter (_.ConductingEquipment.BaseVoltage == "BaseVoltage_400")

        // get the points
        val points = get [PositionPoint](session)

        // attach the locations to the house connections to get RDD[PositionedEnergyConsumer]
        val located_consumers = consumers.keyBy (_.ConductingEquipment.Equipment.PowerSystemResource.Location).join (points.keyBy (_.Location)).values.map (position_consumers)

        // try and join the EnergyConsumer to a ServicePoint
        val attributes = get [UserAttribute](session)
        val locations = get [ServiceLocation](session)

        def pull (a: (UserAttribute, ServiceLocation)): (String, ServiceLocation) = (a._1.value, a._2)

        val location_by_has = attributes.keyBy (_.name).join (locations.keyBy (_.id)).values.map (pull)
        val all = located_consumers.keyBy (_.id).leftOuterJoin (location_by_has).values.map (shrink)

        def ordering (item: HouseService) =
        {
            val dx = args.lon - item.xPosition.toDouble
            val dy = args.lat - item.yPosition.toDouble
            dx * dx + dy * dy
        }

        session.sqlContext.createDataFrame (all.sortBy (ordering).take (args.n))
    }
}

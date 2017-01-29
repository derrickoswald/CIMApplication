package ch.ninecode.gl

import scala.collection.Map

import org.apache.spark.sql.SparkSession
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType

import ch.ninecode.model._

/**
 * transformer - PowerTransformer, the transformer element
 * station - Substation, the station where the transformer is located
 * voltages - Array[Double], the voltage for each transformer end
 * ends - Array[PowerTransformerEnd], the transformer end elements
 * terminals - Array[Terminal], the terminal elements
 * Note: element 0 of the arrays corresponds to the primary
 */
case class TData (transformer: PowerTransformer, station: Substation, voltages: Array[Double], ends: Array[PowerTransformerEnd], terminals: Array[Terminal])

class Transformers () extends Serializable
{
    def get (name: String, session: SparkSession): RDD[Element] =
    {
        val rdds = session.sparkContext.getPersistentRDDs
        for (key <- rdds.keys)
        {
            val rdd = rdds (key)
            if (rdd.name == name)
                return (rdd.asInstanceOf[RDD[Element]])
        }
        return (null)
    }

    def getTransformerData (session: SparkSession): RDD[TData] =
    {
        // get all transformers in substations
        val transformers = get ("PowerTransformer", session).asInstanceOf[RDD[PowerTransformer]]
        val substation_transformers = transformers.filter ((t: PowerTransformer) => { (t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name != "Messen_Steuern") })

        // get an RDD of substations by filtering out distribution boxes
        val stations = get ("Substation", session).asInstanceOf[RDD[Substation]].filter (_.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType == "PSRType_TransformerStation")

        // the equipment container for a transformer could be a Bay, VoltageLevel or Station... the first two of which have a reference to their station
        def station_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                case (key: String, (t: PowerTransformer, station: Substation)) =>
                {
                    (station.id, t)
                }
                case (key: String, (t: PowerTransformer, bay: Bay)) =>
                {
                    (bay.Substation, t)
                }
                case (key: String, (t: PowerTransformer, level: VoltageLevel)) =>
                {
                    (level.Substation, t)
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        // create an RDD of container-transformer pairs, e.g. { (KAB8526,TRA13730), (STA4551,TRA4425), ... }
        val elements = get ("Elements", session).asInstanceOf[RDD[Element]]
        val tpairs = substation_transformers.keyBy(_.ConductingEquipment.Equipment.EquipmentContainer).join (elements.keyBy (_.id)).map (station_fn)

        // only keep the pairs where the transformer is in a substation we have
        val transformers_stations = tpairs.join (stations.keyBy (_.id)).values

        // get the transformer ends keyed by transformer
        val ends = get ("PowerTransformerEnd", session).asInstanceOf[RDD[PowerTransformerEnd]].groupBy (_.PowerTransformer)

        // attach all PowerTransformerEnd elements
        val transformers_stations_plus_ends = transformers_stations.keyBy (_._1.id).leftOuterJoin (ends).map (
            (x) => (x._1, (x._2._1._1, x._2._1._2,
                        x._2._2 match
                        {
                            case Some(y) => // Iterable[PowerTransformerEnd]
                                y.toArray.sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
                            case None => null
                        }
                    ))
            )

        // get the TERMINALS keyed by transformer
        val terms = get ("Terminal", session).asInstanceOf[RDD[Terminal]].groupBy (_.ConductingEquipment)

        // attach all Terminal elements
        val transformers_stations_plus_ends_plus_terminals = transformers_stations_plus_ends.leftOuterJoin (terms).map (
            (x) => (x._2._1._1, x._2._1._2, x._2._1._3,
                        x._2._2 match
                        {
                            case Some(y) => // Iterable[Terminal]
                                y.toArray.sortWith (_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)
                            case None => null
                        }
                    )
            )

        def transformer_fn (voltages: Map[String, Double]) (x: Tuple4 [PowerTransformer, Substation, Array[PowerTransformerEnd], Array[Terminal]]): TData =
        {
            val transformer = x._1
            val station = x._2
            val ends = x._3
            val terminals = x._4
            val v = ends.map ((x) => voltages.getOrElse (x.TransformerEnd.BaseVoltage, 0.0))
            TData (transformer, station, v, ends, terminals)
        }

        // get a map of voltages
        val voltages = get ("BaseVoltage", session).asInstanceOf[RDD[BaseVoltage]].map ((v) => (v.id, v.nominalVoltage)).collectAsMap ()

        val transformer_data = transformers_stations_plus_ends_plus_terminals.map (transformer_fn (voltages))

        return (transformer_data)
    }
}

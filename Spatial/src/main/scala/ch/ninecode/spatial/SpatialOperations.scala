package ch.ninecode.spatial

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.graphx._
import org.apache.spark.rdd._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

import java.net.URLDecoder

import ch.ninecode._
import ch.ninecode.cim._
import ch.ninecode.model._

class SpatialOperations extends Serializable
{
    var _StorageLevel = StorageLevel.MEMORY_ONLY

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

//        // get all objects with a location attribute
//        val psrs = sc.getPersistentRDDs.values.filter ((x) => {"PowerSystemResource" == x.name}).head.asInstanceOf[RDD[PowerSystemResource]]
//        // get the rdd
//        val interesting = sc.getPersistentRDDs.values.filter ((x) => {clazz == x.name}).head.asInstanceOf[RDD[Element]]
//        // get the join
//        val dudu = psrs.keyBy (_.id).join (interesting.keyBy (_.id)).values

        // I can't figure out how to do this with a generic class
        val consumers = sc.getPersistentRDDs.values.filter ((x) => {"EnergyConsumer" == x.name}).head.asInstanceOf[RDD[EnergyConsumer]]
        val dudu = consumers.keyBy (_.sup.sup.sup.Location)

        // get the points
        val points = sc.getPersistentRDDs.values.filter ((x) => {"PositionPoint" == x.name}).head.asInstanceOf[RDD[PositionPoint]]
        val dada = points.keyBy (_.Location)

        val located_consumers = dudu.join (dada).values

        def ordering (item: Tuple2[EnergyConsumer, PositionPoint]) =
        {
            val dx = lon - item._2.xPosition.toDouble;
            val dy = lat - item._2.yPosition.toDouble;
            dx * dx + dy * dy;
        }

        return (sqlContext.createDataFrame (located_consumers.sortBy (ordering).map (_._1).take (n))) // ToDo: takeOrdered in one step?
    }
}

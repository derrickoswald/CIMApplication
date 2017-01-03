package ch.ninecode.geo

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.graphx._
import org.apache.spark.rdd._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.types._
import scala.collection.mutable._
import scala.math._

import ch.ninecode.cim._
import ch.ninecode.model._
import org.apache.spark.sql.Row


case class SpezificAcLineSegment(id: String, name: String, aliasName: String, location: String, baseVoltage: String)
case class BBox(xmin: Double, ymin: Double, xmax: Double, ymax: Double)

/**
 * Line extraction with bounding box and simplification.
 * @author Markus Jung
 */
class GeoVis extends Serializable
{
    def extract (sc: SparkContext, sqlContext: SQLContext, args: String): DataFrame =
    {

        val arguments = args.split (",").map (
            (s) ⇒
                {
                    val pair = s.split ("=")
                    if (2 == pair.length)
                        (pair(0), pair(1))
                    else
                        (pair(0), "")
                }
        ).toMap

        val xmin = arguments.getOrElse ("xmin", "7.28").toDouble
        val ymin = arguments.getOrElse ("ymin", "47.12").toDouble
        val xmax = arguments.getOrElse ("xmax", "7.29").toDouble
        val ymax = arguments.getOrElse ("ymax", "47.13").toDouble
        val reduceLines = arguments.getOrElse ("reduceLines", "true").toBoolean
        val maxLines = arguments.getOrElse ("maxLines", "2000").toInt
        val dougPeuk = arguments.getOrElse ("dougPeuk", "true").toBoolean
        val dougPeukFactor = arguments.getOrElse ("dougPeukFactor", "2.0").toDouble
        val resolution = arguments.getOrElse ("resolution", "1.0e-4").toDouble

        val bbox = BBox (xmin, ymin, xmax, ymax)

        val positionPoint = get (sc, "PositionPoint").asInstanceOf[RDD[PositionPoint]]
        val acLineSegment = get (sc, "ACLineSegment").asInstanceOf[RDD[ACLineSegment]]

        val line = acLineSegment.map((line: ACLineSegment) ⇒ { SpezificAcLineSegment (line.sup.sup.sup.sup.sup.mRID, line.sup.sup.sup.sup.sup.name, line.sup.sup.sup.sup.sup.aliasName, line.sup.sup.sup.sup.Location, line.sup.sup.BaseVoltage) })
        var filteredOrderedPositions = preparePositionPoints(positionPoint, bbox)

        if (dougPeuk) {
            filteredOrderedPositions = generalize(resolution, dougPeukFactor, filteredOrderedPositions)
        }
        val lines = line.keyBy(_.location).join(filteredOrderedPositions)

        val numbLines = lines.count()
        var result = lines.values
        if (reduceLines && (numbLines > maxLines)) {
            /*val resultList = result.sortBy(x => x._1.baseVoltage.split("_")(1)
                 .toDouble, false).take(maxLines)*/
            result = result.sample(true, maxLines.toDouble / numbLines.toDouble)
        }
        val ppDf = sqlContext.createDataFrame (result)
        return ppDf
    }

    def preparePositionPoints(pp: RDD[PositionPoint], bbox: BBox): RDD[(String, List[String])] =
    {

        val filteredPoints = pp.filter((pp: PositionPoint) ⇒
            {
                var xPos = pp.xPosition
                var yPos = pp.yPosition
                (xPos.toDouble >= bbox.xmin &&
                    yPos.toDouble >= bbox.ymin &&
                    xPos.toDouble <= bbox.xmax &&
                    yPos.toDouble <= bbox.ymax)
            })

        val orderedPoints = filteredPoints.sortBy(_.sequenceNumber)

        val groupedPoints = orderedPoints.map((pp: PositionPoint) ⇒ (pp.Location, (pp.sequenceNumber, pp.xPosition, pp.yPosition)))
            .groupByKey()

        val flattenPoints = groupedPoints.mapValues(value ⇒
            {
                var orderedPoints = List[Seq[String]]()
                val it = value.iterator
                while (it.hasNext) {
                    var point = it.next()
                    orderedPoints = orderedPoints :+ Seq(point._2, point._3)
                }
                val flatten = orderedPoints.flatten
                flatten
            })

        return flattenPoints
    }

    def get(sc: SparkContext, name: String): RDD[Element] =
    {
        val rdds = sc.getPersistentRDDs
        for (key ← rdds.keys) {
            val rdd = rdds (key)
            if (rdd.name == name)
                return (rdd.asInstanceOf[RDD[Element]])
        }
        return (null)
    }

    def generalize(resolution: Double, dougPeukFactor: Double, positions: RDD[(String, List[String])]): RDD[(String, List[String])] =
    {
        var epsilon = 5 * dougPeukFactor * resolution
        var dMaxList: List[Double] = List()
        val reducedPos = positions.mapValues(list ⇒ {
            douglasPeuker(list, epsilon)
        })
        return reducedPos
    }

    def douglasPeuker(list: List[String], epsilon: Double): (List[String]) =
    {
        var dmax = 0.0
        var index = 0
        var size = list.size
        var i = 2
        val firstPoint = List(list(0).toDouble, list(1).toDouble)
        val lastPoint = List(list(size - 2).toDouble, list(size - 1).toDouble)
        while (i < size - 1) {
            val curPoint = List(list(i).toDouble, list(i + 1).toDouble)
            val d = calcLot(firstPoint, lastPoint, curPoint)
            if (d > dmax) {
                index = i
                dmax = d
            }
            i += 2
        }
        var result: List[String] = List()
        if (dmax >= epsilon) {
            val recResult1 = douglasPeuker(list.take(index + 2), epsilon)
            val recResult2 = douglasPeuker(list.drop(index), epsilon)

            result = recResult1 ++ recResult2.drop(2)
        }
        else {
            result = List(list(0), list(1), list(size - 2), list(size - 1))
        }
        return (result)
    }

    def calcLot(firstPoint: List[Double], lastPoint: List[Double], curPoint: List[Double]): Double =
    {
        val deltaX = lastPoint(0) - firstPoint(0)
        val deltaY = lastPoint(1) - firstPoint(1)
        val zah = abs((deltaY) * curPoint(0) - (deltaX) * curPoint(1) + lastPoint(0) * firstPoint(1) - lastPoint(1) * firstPoint(0))
        val nen = sqrt(pow(deltaY, 2) + pow(deltaX, 2))
        return zah / nen
    }

    def toGeoJSON (record: Row): String =
    {
        val c = record.getStruct (0)
        val sb = new StringBuilder ()

        sb.append ("        {\n")
        sb.append ("            \"type\": \"Feature\",\n")
        sb.append ("            \"geometry\":\n")
        sb.append ("            {\n")
        sb.append ("                \"type\": \"Line\",\n")
        val d = record.getSeq[String] (1)
        sb.append ("                \"coordinates\": [")
        var first = true
        for (i <- 0 until d.length; if (0 == (1 & i)) )
        {
            if (first)
                first = false
            else
                sb.append (", ")
            sb.append ("[")
            sb.append (d(i))
            sb.append (",")
            sb.append (d(i + 1))
            sb.append ("]")
        }
        sb.append ("]\n")
        sb.append ("            },\n")
        sb.append ("            \"properties\":\n")
        sb.append ("            {\n")
        sb.append ("                \"id\": \"")
        sb.append (c.getString (0))
        sb.append ("\",\n                \"name\": \"")
        sb.append (c.getString (1))
        sb.append ("\",\n                \"aliasName\": \"")
        sb.append (c.getString (2))
        sb.append ("\",\n                \"baseVoltage\": \"")
        sb.append (c.getString (4))
        sb.append ("\n            }\n")
        sb.append ("        }")
        return (sb.toString ())
    }

    def extract_json (sc: SparkContext, sqlContext: SQLContext, args: String): String =
    {
        val ret = new StringBuilder ()

        val df = extract (sc, sqlContext, args)

        ret.append ("{\n")
        ret.append ("    \"type\": \"FeatureCollection\",\n")
        ret.append ("    \"features\": \n")
        ret.append ("    [\n")
        import sqlContext.implicits._
        val strings = df.map (toGeoJSON).rdd
        ret.append (strings.fold ("") ((a,b) => a + (if ("" == a) "" else ",\n") + b))
        ret.append ("\n    ]\n")
        ret.append ("\n}\n")
        return (ret.toString ())
    }
}

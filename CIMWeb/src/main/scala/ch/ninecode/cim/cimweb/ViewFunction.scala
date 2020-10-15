package ch.ninecode.cim.cimweb

import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.Text
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMExport
import ch.ninecode.cim.CIMRDD
import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.Element
import ch.ninecode.model.Location
import ch.ninecode.model.PositionPoint

/**
 * Extract elements from what is currently loaded in Spark.
 *
 * Derived from the GeoVis line extraction with bounding box and simplification.
 *
 * @author Markus Jung
 * @param about          md:FullModel rdf:about contents
 * @param all            if <code>true</code> return all features, otherwise filter as dictated by the other oarameters
 * @param xmin           minimum longitude
 * @param ymin           minimum latitude
 * @param xmax           maximum longitude
 * @param ymax           maximum latitude
 * @param reduceLines    if <code>true</code> restricts the number of ACLineSegment to <code>maxLines</code>
 * @param maxLines       the maximum number of ACLineSegments if <code>reduceLines</code> is <code>true</code>
 * @param dougPeuk       if <code>true</code> applies the Ramer–Douglas–Peucker algorithm to reduce the number of PositionPoint verticies in ACLineSegment geometries
 * @param dougPeukFactor smoothing factor for the Ramer–Douglas–Peucker algorithm
 * @param resolution     distance factor for the Ramer–Douglas–Peucker algorithm
 *                       (the epsilon parameter in the Ramer–Douglas–Peucker algorithm is epsilon = 5 * dougPeukFactor * resolution)
 */
case class ViewFunction (
    about: String,
    all: Boolean,
    xmin: Double,
    ymin: Double,
    xmax: Double,
    ymax: Double,
    reduceLines: Boolean = true,
    maxLines: Int = 2000,
    dougPeuk: Boolean = true,
    dougPeukFactor: Double = 2.0,
    resolution: Double = 1.0e-4
) extends CIMWebFunction with CIMRDD
{
    jars = Array (jarForObject (this))

    override def getReturnType: Return = Return.String

    override def executeString (spark: SparkSession): String =
    {
        implicit val session: SparkSession = spark
        implicit val log: Logger = LoggerFactory.getLogger (getClass)

        val epsilon = 5 * dougPeukFactor * resolution

        final case class PositionPointPlus (pp: PositionPoint, x: Double, y: Double)

        def inside (pp: PositionPointPlus): Boolean =
        {
            pp.x >= xmin && pp.y >= ymin && pp.x <= xmax && pp.y <= ymax
        }

        def preparePositionPoints: RDD[(String, List[PositionPointPlus])] =
        {
            val pp: RDD[PositionPointPlus] = get [PositionPoint].map (p => PositionPointPlus (p, p.xPosition.toDouble, p.yPosition.toDouble))
            pp.filter (inside).groupBy (_.pp.Location).mapValues (_.toList.sortBy (_.pp.sequenceNumber))
        }

        def calcLotrecht (firstPoint: PositionPointPlus, lastPoint: PositionPointPlus, curPoint: PositionPointPlus): Double =
        {
            val deltaX = lastPoint.x - firstPoint.x
            val deltaY = lastPoint.y - firstPoint.y
            val zah = Math.abs (deltaY * curPoint.x - deltaX * curPoint.y + lastPoint.x * firstPoint.y - lastPoint.y * firstPoint.x)
            val nen = Math.sqrt (deltaY * deltaY + deltaX * deltaX)
            zah / nen
        }

        // see https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm
        def douglasPeuker (list: List[PositionPointPlus]): (List[PositionPointPlus]) =
        {
            list match
            {
                case head +: middle :+ last =>
                    val (dmax, index) = middle.zipWithIndex.foldLeft ((Double.MinPositiveValue, 0))(
                        (current: (Double, Int), next: (PositionPointPlus, Int)) =>
                        {
                            val (dmax, _) = current
                            val (p, i) = next
                            val d = calcLotrecht (head, last, p)
                            if (d > dmax)
                                (d, i + 1) // the head is removed, so +1
                            else
                                current
                        }
                    )
                    // if max distance is greater than epsilon, recursively simplify
                    if (dmax >= epsilon)
                        douglasPeuker (list.take (index + 1)) ++ douglasPeuker (list.drop (index))
                    else
                        List (head, last)
                case _ =>
                    list // lists of less than three points
            }
        }

        def to_elements (arg: ((ACLineSegment, List[PositionPointPlus]), Location)): List[Element] =
        {
            arg._1._1.asInstanceOf [Element] :: arg._2.asInstanceOf [Element] :: arg._1._2.map (_.pp.asInstanceOf [Element])
        }

        val elements = if (all)
            getOrElse[Element]
        else
        {
            // get the spatially filtered points
            val filteredOrderedPositions: RDD[(String, List[PositionPointPlus])] =
                if (dougPeuk)
                    preparePositionPoints.mapValues (douglasPeuker)
                else
                    preparePositionPoints
            log.debug ("points count %d".format (filteredOrderedPositions.count))

            // get the reduced lines
            val lines: RDD[(String, ((ACLineSegment, List[PositionPointPlus]), Location))] = get [ACLineSegment].keyBy (_.Conductor.ConductingEquipment.Equipment.PowerSystemResource.Location).join (filteredOrderedPositions).join (get [Location].keyBy (_.id))
            val numbLines = lines.count
            log.debug ("lines count %d".format (numbLines))
            val result: RDD[((ACLineSegment, List[PositionPointPlus]), Location)] =
                if (reduceLines && (numbLines > maxLines))
                    lines.values.sample (true, maxLines.toDouble / numbLines.toDouble)
                else
                    lines.values

            // get the reduced element list - just lines, locations and positions
            result.flatMap (to_elements)
        }

        // write the reduced RDF
        val file: Path = new Path ("/tmp/view.rdf")
        val f: Path = new Path (hdfs.getUri.toString, file)
        val _ = hdfs.delete (f, false)
        log.info ("exporting %s".format (f.toString))
        val export = new CIMExport (spark)
        export.export (elements, f.toString)

        // read the file
        log.info ("reading %s".format (f.toString))
        try
        {
            val data = hdfs.open (f)
            // ToDo: handle files bigger than 2GB
            val size = hdfs.getFileStatus (f).getLen.toInt
            val bytes = new Array[Byte](size)
            data.readFully (0, bytes)
            Text.decode (bytes, 0, size)
        }
        catch
        {
            case e: Exception =>
                e.getMessage
        }
    }

    override def toString: String = s"""${super.toString} ("$about",all=$all,[$xmin,$ymin],[$xmax,$ymax],reduce=$reduceLines,maxLines=$maxLines,dougPeuk=$dougPeuk,dougPeukFactor=$dougPeukFactor,resolution=$resolution)"""
}

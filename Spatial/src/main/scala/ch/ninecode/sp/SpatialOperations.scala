package ch.ninecode.sp

import scala.reflect.runtime.universe._

import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CHIM
import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.Element
import ch.ninecode.model.PositionPoint
import ch.ninecode.model.PowerSystemResource

class SpatialOperations (session: SparkSession) extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)
    val psr: String = PowerSystemResource.getClass.getName.replace ("$", "")

    /**
     * Get the location reference from a PowerSystemResource.
     *
     * @param element the element to get the location from
     * @return the location (if any) or <code>""</code> if the element is not a PowerSystemResource
     */
    def location (element: Element): String =
    {
        var e = element

        while ((null != e) && (e.getClass.getName != psr))
            e = e.sup

        e match
        {
            case p: PowerSystemResource => p.Location
            case _ => ""
        }
    }

    @SuppressWarnings (Array ("org.wartremover.warts.AsInstanceOf"))
    def asElement (e: Any): Element = e.asInstanceOf[Element]

    def nearest (args: SpatialOperationParameters): DataFrame =
    {
        // get the subsetter
        val chim = new CHIM ("")
        val cls = chim.classes.find (_.subsetter.cls == args.clazz)

        cls match
        {
            case Some (clz) =>
                // do the fucking Scala type voodoo
                val subsetter = clz.subsetter
                type T = subsetter.basetype
                implicit val tag: TypeTag[T] = subsetter.tag.asInstanceOf[TypeTag[T]]

                // get the RDD of desired objects
                val rdd: RDD[T] = getOrElse[T](subsetter.cls)

                // get the points
                val points = getOrElse[PositionPoint]

                // join
                val targets: RDD[(T, PositionPoint)] = rdd.keyBy (x => location (asElement (x))).join (points.keyBy (_.Location)).values

                object nearestOrdering extends Ordering[(T, PositionPoint)]
                {
                    def dist2 (point: PositionPoint): Double =
                    {
                        val dx = args.lon - point.xPosition.toDouble
                        val dy = args.lat - point.yPosition.toDouble
                        dx * dx + dy * dy
                    }

                    /** Returns an integer whose sign communicates how x compares to y.
                     *
                     * The result sign has the following meaning:
                     *
                     *  - negative if x < y
                     *  - positive if x > y
                     *  - zero otherwise (if x == y)
                     */
                    def compare (x: (T, PositionPoint), y:(T, PositionPoint)): Int =
                    {
                        Math.signum (dist2 (x._2) - dist2 (y._2)).toInt
                    }
                }

                // sort by distance using implicit ordering
                val result = targets.takeOrdered (args.n)(nearestOrdering).map (_._1)

                // make a DataFrame
                session.sqlContext.sparkSession.createDataFrame (result)
            case None =>
                log.error (s"class ${args.clazz} not found")
                spark.emptyDataFrame
        }
    }
}

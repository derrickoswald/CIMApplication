package ch.ninecode.ts

import java.io.File
import java.sql.Timestamp
import java.util.Calendar
import java.util.TimeZone

import scala.reflect.runtime.universe.TypeTag

import com.datastax.spark.connector._
import com.datastax.spark.connector.SomeColumns
import org.apache.commons.io.FileUtils
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.tree.DecisionTree
import org.apache.spark.mllib.tree.model.DecisionTreeModel
import org.apache.spark.rdd.RDD
import org.apache.spark.sql._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions.udf
import org.apache.spark.storage.StorageLevel


case class Model (session: SparkSession, keyspace: String, storage_level: StorageLevel)
{
    import session.sqlContext.implicits._

    def makeSimpleModel ()
    {
        def tick[Type_t: TypeTag, Type_period: TypeTag]: UserDefinedFunction = udf [Int, Timestamp, Int](
            (t: Timestamp, period: Int) =>
                ((t.getTime / period) % (24 * 60 * 60 * 1000 / period)).toInt)
        def day[Type_t: TypeTag]: UserDefinedFunction = udf [Int, Timestamp](
            (t: Timestamp) =>
            {
                val c = Calendar.getInstance ()
                c.setTimeZone (TimeZone.getTimeZone ("GMT"))
                c.setTime (t)
                c.get (Calendar.DAY_OF_WEEK)
            }
        )
        def week[Type_t: TypeTag]: UserDefinedFunction = udf [Int, Timestamp](
            (t: Timestamp) =>
            {
                val c = Calendar.getInstance ()
                c.setTimeZone (TimeZone.getTimeZone ("GMT"))
                c.setTime (t)
                c.get (Calendar.WEEK_OF_YEAR)
            }
        )

        val stats = session
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value_stats", "keyspace" -> keyspace))
            .load
            .select ("mrid", "type", "average")
            .persist (storage_level)
        val raw = session
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value", "keyspace" -> keyspace))
            .load
            .filter (s"real_a > 0.0")
            .withColumn ("tick", tick [Timestamp, Int].apply (functions.col ("time"), functions.col ("period")))
            .withColumn ("day", day [Timestamp].apply (functions.col ("time")))
            .withColumn ("week", week [Timestamp].apply (functions.col ("time")))
            .selectExpr ("mrid", "type", "tick", "day", "week", "real_a as value")
            .join (stats, Seq ("mrid", "type"))
            .persist (storage_level)

        val data = raw.map (
            row =>
            {
                LabeledPoint (row.getDouble (5),
                    //               tick            day             week             average
                    Vectors.dense (row.getInt (2), row.getInt (3), row.getInt (4), row.getDouble (6)))
            }
        ).rdd

        // split the data into training and test sets (30% held out for testing)
        val splits: Array[RDD[LabeledPoint]] = data.randomSplit (Array(0.7, 0.3))
        val (trainingData, testData) = (splits(0), splits(1))

        // train a DecisionTree model for regression
        // an empty categoricalFeaturesInfo indicates all features are continuous
        val categoricalFeaturesInfo = Map[Int, Int]()
        val impurity = "variance"
        val maxDepth = 20
        val maxBins = 32

        val model = DecisionTree.trainRegressor (trainingData, categoricalFeaturesInfo, impurity, maxDepth, maxBins)

        // evaluate model on test instances and compute test error
        val labelAndPreds: RDD[(Double, Double)] = testData.map { point =>
            val prediction = model.predict (point.features)
            (point.label, prediction)
        }
        val testErr = labelAndPreds.map (r => { val diff = r._1 - r._2; diff * diff }).sum / testData.count ()
        println (s"Root Mean Squared Test Error = ${math.sqrt (testErr)}")
        //println (s"Learned classification tree model:\n ${model.toDebugString}")

        // save the model
        FileUtils.deleteQuietly (new File ("target/tmp/"))
        model.save (session.sparkContext, "target/tmp/myDecisionTreeClassificationModel")
    }

    def generateTimeSeries (mrid: String, start: Calendar, end: Calendar, period: Int, yearly_kWh: Double): Unit =
    {
        // generate the feature vector
        println (s"generating features")
        val periods = 24 * 60 * 60 * 1000 / period
        val average = yearly_kWh * 1000.0 / 365.25 / periods
        val features = Iterator.continually
        {
            val millis = start.getTimeInMillis
            val tick = ((millis / period) % (24 * 60 * 60 * 1000 / period)).toInt
            val c = Calendar.getInstance ()
            c.setTimeZone (TimeZone.getTimeZone ("GMT"))
            c.setTimeInMillis (millis)
            val day = c.get (Calendar.DAY_OF_WEEK)
            val week = c.get (Calendar.DAY_OF_WEEK)
            start.add (Calendar.MILLISECOND, period)
            (millis, Vectors.dense (tick, day, week, average))
        }.takeWhile (_ => start.getTimeInMillis <= end.getTimeInMillis)

        // generate the predictions
        println (s"generating model predictions")
        val model = DecisionTreeModel.load (session.sparkContext, "target/tmp/myDecisionTreeClassificationModel")
        val predictions = features.map (feature => (mrid, "energy", feature._1, period, model.predict (feature._2), 0.0, "Wh"))

        // save to the synthesized_value table
        println (s"saving to $keyspace.synthesized_value")
        session.sparkContext.parallelize (predictions.toSeq)
            .saveToCassandra (keyspace, "synthesized_value", SomeColumns ("synthesis", "type", "time", "period", "real_a", "imag_a", "units"))
        println (s"synthesized values saved")
    }
}

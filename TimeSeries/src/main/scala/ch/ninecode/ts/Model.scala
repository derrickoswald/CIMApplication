package ch.ninecode.ts

import java.io.File
import java.net.URI
import java.sql.Timestamp
import java.util.Calendar
import java.util.TimeZone

import scala.reflect.runtime.universe.TypeTag

import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.log4j.Level
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.regression.DecisionTreeRegressionModel
import org.apache.spark.ml.regression.DecisionTreeRegressor
import org.apache.spark.ml.tuning.ParamGridBuilder
import org.apache.spark.ml.tuning.TrainValidationSplit
import org.apache.spark.sql._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.types.LongType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.datastax.spark.connector._
import com.datastax.spark.connector.SomeColumns

case class Model (session: SparkSession, options: TimeSeriesOptions)
{
    org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (Level.toLevel (options.log_level.toString))
    val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Get the scheme for the model file.
     */
    val model_file_scheme: String =
    {
        val uri = new URI (options.model_file)
        if (null == uri.getScheme)
            ""
        else
            uri.getScheme
    }

    /**
     * Get just the URI for the model file.
     */
    val model_file_uri: String =
    {
        val uri = new URI (options.model_file)
        if (null == uri.getScheme)
            ""
        else
            uri.getScheme + "://" + (if (null == uri.getAuthority) "" else uri.getAuthority) + "/"
    }

    def eraseModelFile ()
    {
        if ((model_file_scheme == "file") || (model_file_scheme == ""))
            FileUtils.deleteQuietly (new File (options.model_file))
        else
        {
            val hdfs_configuration = new Configuration ()
            hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get (URI.create (model_file_uri), hdfs_configuration)

            val directory = new Path (options.model_file)
            hdfs.delete (directory, true)
        }
    }

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

        log.info (s"reading sample data")
        val stats = session
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value_stats", "keyspace" -> options.keyspace))
            .load
            .select ("mrid", "type", "average")
            .persist (StorageLevel.fromString (options.storage_level))
        val raw = session
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value", "keyspace" -> options.keyspace))
            .load
            .filter (s"real_a > 0.0")
            .withColumn ("tick", tick [Timestamp, Int].apply (functions.col ("time"), functions.col ("period")))
            .withColumn ("day", day [Timestamp].apply (functions.col ("time")))
            .withColumn ("week", week [Timestamp].apply (functions.col ("time")))
            .selectExpr ("mrid", "type", "tick", "day", "week", "real_a as value")
            .join (stats, Seq ("mrid", "type"))
            .persist (StorageLevel.fromString (options.storage_level))

        // split the data into training and test sets (30% held out for testing)
        val splits = raw.randomSplit (Array(0.7, 0.3))
        val (trainingData, testData) = (splits(0), splits(1))

        val assembler = new VectorAssembler ()
            .setInputCols  (Array("tick", "day", "week", "average"))
            .setOutputCol ("features")
        val train_df = assembler.transform (trainingData)
        val test_df = assembler.transform (testData)

        // train a DecisionTree model for regression
        log.info (s"training model")
        val regressor = new DecisionTreeRegressor ()
            .setFeaturesCol ("features")
            .setLabelCol ("value")
            .setImpurity ("variance")
            .setMaxDepth (16)
            .setMaxBins (32)
            .setCacheNodeIds (true)
            .setCheckpointInterval (10)
        if (-1L != options.seed)
            regressor.setSeed (options.seed)

        // straight forward training
        // val model = regressor.fit (train_df)

        // hyperparameter tuning training
        // construct a grid of parameters to search over
        val grid = new ParamGridBuilder()
            .addGrid (regressor.maxDepth, options.tree_depth)
            .addGrid (regressor.maxBins, options.bins)
            .addGrid (regressor.minInfoGain, options.info)
            .build()
        val evaluator = new RegressionEvaluator ()
            .setLabelCol ("value")
            .setPredictionCol ("prediction")
            .setMetricName ("rmse")
        val trainer = new TrainValidationSplit ()
            .setEstimator (regressor) // in this case the estimator is simply the decision tree regression
            .setEvaluator (evaluator)
            .setEstimatorParamMaps (grid)
            // 70% of the data will be used for training and the remaining 30% for validation
            .setTrainRatio (0.7)
            // evaluate up to 2 parameter settings in parallel
            // .setParallelism (2)

        // run train validation split, and choose the best set of parameters.
        val fitted = trainer.fit (train_df)
        val model = fitted.bestModel.asInstanceOf[DecisionTreeRegressionModel]

        val predictions = model.transform (test_df)
        val rmse = evaluator.evaluate (predictions)
        log.info (s"root mean squared error (RMSE) on test data = $rmse")
        log.info (s"tree_depth = ${model.getMaxDepth}")
        log.info (s"bins = ${model.getMaxBins}")
        log.info (s"info = ${model.getMinInfoGain}")
        log.info (s"seed = ${model.getSeed}")

        // save the model
        eraseModelFile ()
        model.save (options.model_file)
    }

    def generateTimeSeries (synthesis: String, start: Calendar, end: Calendar, period: Int, yearly_kWh: Double): Unit =
    {
        // generate the feature vector
        log.info (s"generating features")
        val periods = 24 * 60 * 60 * 1000 / period
        val average = yearly_kWh * 1000.0 / 365.25 / periods
        val data = Iterator.continually
        {
            val millis = start.getTimeInMillis
            val tick = ((millis / period) % (24 * 60 * 60 * 1000 / period)).toInt
            val c = Calendar.getInstance ()
            c.setTimeZone (TimeZone.getTimeZone ("GMT"))
            c.setTimeInMillis (millis)
            val day = c.get (Calendar.DAY_OF_WEEK)
            val week = c.get (Calendar.DAY_OF_WEEK)
            start.add (Calendar.MILLISECOND, period)
            Row (millis, tick, day, week, average)
        }.takeWhile (_ => start.getTimeInMillis <= end.getTimeInMillis)

        // make a dataframe
        val schema = StructType (
            List (
                StructField ("time", LongType, false),
                StructField ("tick", IntegerType, false),
                StructField ("day", IntegerType, false),
                StructField ("week", IntegerType, false),
                StructField ("average", DoubleType, false)
            )
        )

        val df = session.createDataFrame (
            session.sparkContext.parallelize (data.toSeq),
            schema
        )
        val assembler = new VectorAssembler ()
            .setInputCols  (Array("tick", "day", "week", "average"))
            .setOutputCol ("features")
        val features = assembler.transform (df)

        // generate the predictions
        log.info (s"generating model predictions")
        val model = DecisionTreeRegressionModel.load (options.model_file)
        val predictions = model
            .setPredictionCol ("real_a")
            .transform (features)

        // save to the synthesized_value table
        log.info (s"saving to ${options.keyspace}.synthesized_value")
        predictions
            .withColumn ("synthesis", lit (synthesis))
            .withColumn ("type", lit ("energy"))
            .withColumn ("period", lit (period))
            .withColumn ("imag_a", lit (0.0))
            .withColumn ("units", lit ("Wh"))
            .select ("synthesis", "type", "time", "period", "real_a", "imag_a", "units")
            .rdd.saveToCassandra (options.keyspace, "synthesized_value", SomeColumns ("synthesis", "type", "time", "period", "real_a", "imag_a", "units"))
        log.info (s"synthesized values saved")
    }
}

package ch.ninecode.ts

import java.net.URI
import java.sql.Timestamp
import java.util.Calendar
import java.util.TimeZone

import scala.reflect.runtime.universe.TypeTag

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.regression.DecisionTreeRegressionModel
import org.apache.spark.ml.regression.DecisionTreeRegressor
import org.apache.spark.ml.tuning.ParamGridBuilder
import org.apache.spark.ml.tuning.TrainValidationSplit
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.types.LongType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.datastax.spark.connector._
import com.datastax.spark.connector.SomeColumns
//import com.intel.analytics.bigdl.dlframes.DLEstimator
//import com.intel.analytics.bigdl.nn._
//import com.intel.analytics.bigdl.tensor.TensorNumericMath.TensorNumeric.NumericFloat


case class TimeSeriesModel (session: SparkSession, options: TimeSeriesOptions)
{
    org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (options.spark_options.log)
    val log: Logger = LoggerFactory.getLogger (getClass)

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

    val day_ordinals: Seq[(String, Int)] = Seq (
        ("sun", 1),
        ("mon", 2),
        ("tue", 3),
        ("wed", 4),
        ("thu", 5),
        ("fri", 6),
        ("sat", 7))
    lazy val day_names: Seq[String] = for (d <- day_ordinals) yield d._1

    val class_names: Seq[String] = TimeSeriesMeta.classes.toSeq

    def eraseModelFile (suffix: String = ""): Unit =
    {
        val hdfs_configuration = new Configuration ()
        hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
        hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
        val hdfs = FileSystem.get (URI.create (model_file_uri), hdfs_configuration)
        val directory = new Path (s"${options.model_file}$suffix")
        val _ = hdfs.delete (directory, true)
    }

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

    def one_hot[Type_day: TypeTag, Type_index: TypeTag]: UserDefinedFunction = udf [Int, Int, Int](
        (day: Int, index: Int) =>
        {
            if (day == index) 1 else 0
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

    def demultiplex[Type_m: TypeTag, Type_c: TypeTag]: UserDefinedFunction = udf [Int, Map[String, Int], String](
        (map: Map[String, Int], cls: String) =>
        {
            map.getOrElse (cls, 0)
        }
    )

    def averages: DataFrame =
        session
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value_stats", "keyspace" -> options.keyspace))
            .load
            .select ("mrid", "type", "average")
            .persist (options.storage)

    def meta: DataFrame =
        session
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value_meta", "keyspace" -> options.keyspace))
            .load
            .select ("mrid", "classes")
            .persist (options.storage)

    def gen_day_columns (frame: DataFrame, day_col: String): DataFrame =
    {
        var ret = frame
        day_ordinals.foreach (day => ret = ret.withColumn (day._1, one_hot [Int, Int].apply (functions.col (day_col), functions.lit (day._2))))
        ret
    }

    def gen_class_columns (frame: DataFrame, class_col: String): DataFrame =
    {
        var ret = frame
        class_names.foreach (cls => ret = ret.withColumn (cls, demultiplex [Map[String, Int], String].apply (functions.col (class_col), functions.lit (cls))))
        ret
    }

    def getRawData: DataFrame =
    {
        log.info (s"reading sample data")
        var data = session
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value", "keyspace" -> options.keyspace))
            .load
            .filter (s"real_a > 0.0")
            .withColumn ("tick", tick [Timestamp, Int].apply (functions.col ("time"), functions.col ("period")))
            .withColumn ("week", week [Timestamp].apply (functions.col ("time")))
            .withColumn ("day", day [Timestamp].apply (functions.col ("time")))
        data = gen_day_columns (data, "day")
            .join (averages, Seq ("mrid", "type"))
        val cols = Seq ("mrid", "real_a as value", "average", "tick", "week") ++ day_names
        data
            .selectExpr (cols: _*)
            .drop ("type")
            .persist (options.storage)
    }

    def getMetaRawData: DataFrame =
    {
        log.info ("reading meta data")
        val meta_frame = meta
        val stats_and_meta =
            averages
                .join (meta_frame, Seq ("mrid"))
        // only use measurements for which we have metadata
        val in = meta_frame.select ("mrid").rdd.collect.map (row => row.getString (0)).mkString ("mrid in ('", "','", "')")
        var data = session
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value", "keyspace" -> options.keyspace))
            .load
            .filter (s"type='energy' and $in") // push down filter
            .filter (s"real_a > 0.0")
            .join (stats_and_meta, Seq ("mrid", "type"))
            .withColumn ("tick", tick [Timestamp, Int].apply (functions.col ("time"), functions.col ("period")))
            .withColumn ("week", week [Timestamp].apply (functions.col ("time")))
            .withColumn ("day", day [Timestamp].apply (functions.col ("time")))
        data = gen_day_columns (data, "day")
        data = gen_class_columns (data, "classes")
        val cols = Seq ("mrid", "real_a as value", "average", "tick", "week") ++ day_names ++ class_names
        data
            .selectExpr (cols: _*)
            .persist (options.storage)
    }

    def getSingleMetaRawData (cls: String): DataFrame =
    {
        log.info ("reading meta data")
        val meta_frame = meta
        val stats_and_meta =
            averages
                .join (meta_frame, Seq ("mrid"))
        // only use measurements for which we have metadata
        val in = meta_frame.select ("mrid").rdd.collect.map (row => row.getString (0)).mkString ("mrid in ('", "','", "')")
        var data = session
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value", "keyspace" -> options.keyspace))
            .load
            .filter (s"type='energy' and $in") // push down filter
            .filter (s"real_a > 0.0")
            .join (stats_and_meta, Seq ("mrid", "type"))
            .filter (s"classes['$cls'] = 1")
            .withColumn ("tick", tick [Timestamp, Int].apply (functions.col ("time"), functions.col ("period")))
            .withColumn ("week", week [Timestamp].apply (functions.col ("time")))
            .withColumn ("day", day [Timestamp].apply (functions.col ("time")))
        data = gen_day_columns (data, "day")
        val cols = Seq ("mrid", "real_a as value", "average", "tick", "week") ++ day_names
        data
            .selectExpr (cols: _*)
            .persist (options.storage)
    }

    def makeDecisionTreeRegressorModel ()
    {
        // split the data into training and test sets (30% held out for testing)
        val raw = getRawData
        val splits = raw.randomSplit (Array (0.7, 0.3))
        val (trainingData, testData) = (splits (0), splits (1))

        val cols = Seq ("average", "tick", "week") ++ day_names
        val assembler = new VectorAssembler ()
            .setInputCols (cols.toArray)
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
            .setSeed (if (-1L != options.seed) options.seed else getClass.getName.hashCode.toLong)

        // straight forward training
        // val model = regressor.fit (train_df)

        // hyperparameter tuning training
        // construct a grid of parameters to search over
        val grid = new ParamGridBuilder ()
            .addGrid (regressor.maxDepth, options.tree_depth)
            .addGrid (regressor.maxBins, options.bins)
            .addGrid (regressor.minInfoGain, options.info)
            .build ()
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
        val model = fitted.bestModel.asInstanceOf [DecisionTreeRegressionModel]

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

    def makeMetaDecisionTreeRegressorModel (): Unit =
    {
        // split the data into training and test sets (30% held out for testing)
        val raw = getMetaRawData
        val splits = raw.randomSplit (Array (0.7, 0.3))
        val (trainingData, testData) = (splits (0), splits (1))
        val cols = Seq ("average", "tick", "week") ++ day_names ++ class_names

        //println (cols.mkString (","))
        val assembler = new VectorAssembler ()
            .setInputCols (cols.toArray)
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
            .setSeed (if (-1L != options.seed) options.seed else getClass.getName.hashCode.toLong)

        val evaluator = new RegressionEvaluator ()
            .setLabelCol ("value")
            .setPredictionCol ("prediction")
            .setMetricName ("rmse")

        // straight forward training
        // val model = regressor.fit (train_df)

        // hyperparameter tuning training
        // construct a grid of parameters to search over
        val grid = new ParamGridBuilder ()
            .addGrid (regressor.maxDepth, options.tree_depth)
            .addGrid (regressor.maxBins, options.bins)
            .addGrid (regressor.minInfoGain, options.info)
            .build ()

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
        val model = fitted.bestModel.asInstanceOf [DecisionTreeRegressionModel]

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

    def makeSingleMetaDecisionTreeRegressorModel (): Unit =
    {
        for (cls <- TimeSeriesMeta.classes)
        {
            // split the data into training and test sets (30% held out for testing)
            val raw = getSingleMetaRawData (cls)
            val splits = raw.randomSplit (Array (0.7, 0.3))
            val (trainingData, testData) = (splits (0), splits (1))
            val cols = Seq ("average", "tick", "week") ++ day_names

            //println (columns.mkString (","))
            val assembler = new VectorAssembler ()
                .setInputCols (cols.toArray)
                .setOutputCol ("features")
            val train_df = assembler.transform (trainingData)
            val test_df = assembler.transform (testData)

            // train a DecisionTree model for regression
            log.info (s"training model $cls")
            val regressor = new DecisionTreeRegressor ()
                .setFeaturesCol ("features")
                .setLabelCol ("value")
                .setImpurity ("variance")
                .setMaxDepth (16)
                .setMaxBins (32)
                .setCacheNodeIds (true)
                .setCheckpointInterval (10)
                .setSeed (if (-1L != options.seed) options.seed else getClass.getName.hashCode.toLong)

            val evaluator = new RegressionEvaluator ()
                .setLabelCol ("value")
                .setPredictionCol ("prediction")
                .setMetricName ("rmse")

            // straight forward training
            // val model = regressor.fit (train_df)

            // hyperparameter tuning training
            // construct a grid of parameters to search over
            val grid = new ParamGridBuilder ()
                .addGrid (regressor.maxDepth, options.tree_depth)
                .addGrid (regressor.maxBins, options.bins)
                .addGrid (regressor.minInfoGain, options.info)
                .build ()

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
            val model = fitted.bestModel.asInstanceOf [DecisionTreeRegressionModel]

            val predictions = model.transform (test_df)
            val rmse = evaluator.evaluate (predictions)
            log.info (s"root mean squared error (RMSE) on test data = $rmse")
            log.info (s"tree_depth = ${model.getMaxDepth}")
            log.info (s"bins = ${model.getMaxBins}")
            log.info (s"info = ${model.getMinInfoGain}")
            log.info (s"seed = ${model.getSeed}")

            // save the model
            eraseModelFile (cls)
            model.save (s"${options.model_file}$cls")
        }
    }

//    def makeDLModel ()
//    {
//        // split the data into training and test sets (30% held out for testing)
//        val raw = getRawData
//        val splits = raw.randomSplit (Array (0.7, 0.3))
//        val (trainingData, testData) = (splits (0), splits (1))
//
//        val cols = Seq ("average", "tick", "week") ++ day_names
//        val assembler = new VectorAssembler ()
//            .setInputCols (cols.toArray)
//            .setOutputCol ("features")
//        val train_df = assembler.transform (trainingData)
//        val test_df = assembler.transform (testData)
//
//        val model = Sequential ().add (Linear (10, 1))
//        val criterion = MSECriterion ()
//        val estimator = new DLEstimator (model, criterion, Array (10), Array (1))
//            .setLabelCol ("value")
//            .setBatchSize (4)
//            .setMaxEpoch (2)
//
//        val dlModel = estimator.fit (train_df)
//        dlModel.transform (test_df).show (false)
//    }

    def save (predictions: DataFrame, synthesis: String, period: Int): Unit =
    {
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

    def generateSimpleTimeSeries (synthesis: String, start: Calendar, end: Calendar, period: Int, yearly_kWh: Double): Unit =
    {
        // generate the feature vector
        log.info (s"generating features")
        val periods = 24 * 60 * 60 * 1000 / period
        val average = yearly_kWh * 1000.0 / 365.25 / periods

        def oneHot (day: Int, weekday: Int): Int = if (day == weekday) 1 else 0

        val data = Iterator.continually
        {
            val millis = start.getTimeInMillis
            val tick = ((millis / period) % (24 * 60 * 60 * 1000 / period)).toInt
            val c = Calendar.getInstance ()
            c.setTimeZone (TimeZone.getTimeZone ("GMT"))
            c.setTimeInMillis (millis)
            val day = c.get (Calendar.DAY_OF_WEEK)
            val week = c.get (Calendar.DAY_OF_WEEK)
            val days = for (d <- 1 to 7) yield (oneHot (day, d))
            start.add (Calendar.MILLISECOND, period)
            Row.fromSeq (Seq [Any](millis, average, tick, week) ++ days)
        }.takeWhile (_ => start.getTimeInMillis <= end.getTimeInMillis)

        // make a dataframe
        val schema = StructType (
            List (
                StructField ("time", LongType, false),
                StructField ("average", DoubleType, false),
                StructField ("tick", IntegerType, false),
                StructField ("week", IntegerType, false))
                ++ (for (d <- day_names) yield StructField (d, IntegerType, false)).toList
        )

        val df = session.createDataFrame (
            session.sparkContext.parallelize (data.toSeq),
            schema
        )
        val cols = Seq ("average", "tick", "week") ++ day_names
        val assembler = new VectorAssembler ()
            .setInputCols (cols.toArray)
            .setOutputCol ("features")
        val features = assembler.transform (df)

        // load the model
        log.info (s"loading model ${options.model_file}")
        val model = DecisionTreeRegressionModel.load (options.model_file)

        // generate the predictions
        log.info (s"generating model predictions")
        val predictions = model
            .setPredictionCol ("real_a")
            .transform (features)

        // save to the synthesized_value table
        save (predictions, synthesis, period)
    }

    def generateMetaTimeSeries (synthesis: String, start: Calendar, end: Calendar, period: Int, yearly_kWh: Double, types: Map[String, Int]): Unit =
    {
        // generate the feature vector
        log.info (s"generating features")
        val periods = 24 * 60 * 60 * 1000 / period
        val average = yearly_kWh * 1000.0 / 365.25 / periods

        def oneHot (day: Int, weekday: Int): Int = if (day == weekday) 1 else 0

        val data = Iterator.continually
        {
            val millis: Long = start.getTimeInMillis
            val tick = ((millis / period) % (24 * 60 * 60 * 1000 / period)).toInt
            val c = Calendar.getInstance ()
            c.setTimeZone (TimeZone.getTimeZone ("GMT"))
            c.setTimeInMillis (millis)
            val day = c.get (Calendar.DAY_OF_WEEK)
            val week = c.get (Calendar.DAY_OF_WEEK)
            start.add (Calendar.MILLISECOND, period)
            val days = for (d <- 1 to 7) yield oneHot (day, d)
            val classes = for (c <- class_names) yield if (types.contains (c)) types (c) else 0
            Row.fromSeq (Seq [Any](millis, average, tick, week) ++ days ++ classes)
        }.takeWhile (_ => start.getTimeInMillis <= end.getTimeInMillis)

        // make a dataframe
        val schema = StructType (
            List (
                StructField ("time", LongType, false),
                StructField ("average", DoubleType, false),
                StructField ("tick", IntegerType, false),
                StructField ("week", IntegerType, false))
                ++ (for (d <- day_names) yield StructField (d, IntegerType, false)).toList
                ++ (for (c <- class_names) yield StructField (c, IntegerType, false)).toList
        )

        val df = session.createDataFrame (
            session.sparkContext.parallelize (data.toSeq),
            schema
        )
        val cols = Seq ("average", "tick", "week") ++ day_names ++ class_names
        val assembler = new VectorAssembler ()
            .setInputCols (cols.toArray)
            .setOutputCol ("features")
        val features = assembler.transform (df)

        // load the model
        log.info (s"loading model ${options.model_file}")
        val model = DecisionTreeRegressionModel.load (options.model_file)

        // generate the predictions
        log.info (s"generating model predictions")
        val predictions = model
            .setPredictionCol ("real_a")
            .transform (features)
            .select ("time", "real_a")

        // save to the synthesized_value table
        save (predictions, synthesis, period)
    }

    def generateSingleMetaTimeSeries (synthesis: String, start: Calendar, end: Calendar, period: Int, yearly_kWh: Double, types: Map[String, Int]): Unit =
    {
        // generate the feature vector
        log.info (s"generating features")
        val periods = 24 * 60 * 60 * 1000 / period
        val average = yearly_kWh * 1000.0 / 365.25 / periods

        def oneHot (day: Int, weekday: Int): Int = if (day == weekday) 1 else 0

        val data = Iterator.continually
        {
            val millis: Long = start.getTimeInMillis
            val tick = ((millis / period) % (24 * 60 * 60 * 1000 / period)).toInt
            val c = Calendar.getInstance ()
            c.setTimeZone (TimeZone.getTimeZone ("GMT"))
            c.setTimeInMillis (millis)
            val day = c.get (Calendar.DAY_OF_WEEK)
            val week = c.get (Calendar.DAY_OF_WEEK)
            start.add (Calendar.MILLISECOND, period)
            val days = for (d <- 1 to 7) yield oneHot (day, d)
            Row.fromSeq (Seq [Any](millis, average, tick, week) ++ days)
        }.takeWhile (_ => start.getTimeInMillis <= end.getTimeInMillis)

        // make a dataframe
        val schema = StructType (
            List (
                StructField ("time", LongType, false),
                StructField ("average", DoubleType, false),
                StructField ("tick", IntegerType, false),
                StructField ("week", IntegerType, false))
                ++ (for (d <- day_names) yield StructField (d, IntegerType, false)).toList
        )

        val df = session.createDataFrame (
            session.sparkContext.parallelize (data.toSeq),
            schema
        )
        val cols = Seq ("average", "tick", "week") ++ day_names
        val assembler = new VectorAssembler ()
            .setInputCols (cols.toArray)
            .setOutputCol ("features")
        val features = assembler.transform (df)

        // sum over class types
        var sum =
            features
                .select ("time")
                .withColumn ("sum", lit (0.0))

        for (cls <- TimeSeriesMeta.classes
             if types.contains (cls))
        {
            // load the model
            val file = s"${options.model_file}$cls"
            log.info (s"loading model $file")
            val model = DecisionTreeRegressionModel.load (file)

            // generate the predictions
            log.info (s"generating model predictions")
            val predictions = model
                .setPredictionCol ("real_a")
                .transform (features)
                .select ("time", "real_a")
            sum =
                sum
                    .join (predictions.withColumn ("value", lit (types (cls)) * predictions ("real_a")), "time")
                    .withColumn ("total", sum ("sum") + predictions (("value")))
                    .select ("time", "total")
                    .withColumnRenamed ("total", "sum")

        }

        // save to the synthesized_value table
        save (sum, synthesis, period)
    }
}

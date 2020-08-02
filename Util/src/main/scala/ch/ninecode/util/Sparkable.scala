package ch.ninecode.util

/**
 * Necessary options for Spark context creation.
 */
trait Sparkable
{
    /**
     * Contains Spark specific options.
     */
    var spark_options: SparkOptions
}

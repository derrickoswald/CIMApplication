package ch.ninecode.util

import org.apache.spark.sql.SparkSession

/**
 * Common functionality to read CIM files in a main program.
 *
 * @tparam T the type of options needed for CIM reading
 */
trait CIMInitializer[T <: Mainable with Sparkable with CIMAble] extends SparkInitializer[T]
{
    def readCIM (session: SparkSession, options: T): Unit =
    {
        time(s"read: %s seconds")
        {
            val elements = session
                .read
                .format("ch.ninecode.cim")
                .options(options.cim_options.toMap)
                .load(options.cim_options.files: _*)
                .count
            log.info(s"$elements elements")
        }
    }
}

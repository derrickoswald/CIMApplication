package ch.ninecode.ts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercise the JSON serialization of options classes.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class JSONTest
{
    @Test def roundTrip ()
    {
        val options = TimeSeriesOptions ()
        val json = options.toJSON
        TimeSeriesOptions.fromJSON(json) match
        {
            case Left(errors) => fail(errors)
            case Right(o) =>
                // assertEquals("round trip", options, o) fails on mutable arrays, so check equality piece by piece
                assertEquals("main_options", options.main_options, o.main_options)
                assertEquals("spark_options.master", options.spark_options.master, o.spark_options.master)
                assertEquals("spark_options.options", options.spark_options.options, o.spark_options.options)
                assertEquals("spark_options.log", options.spark_options.log, o.spark_options.log)
                assertArrayEquals("spark_options.jars", options.spark_options.jars.asInstanceOf[Array[Object]], o.spark_options.jars.asInstanceOf[Array[Object]])
                assertArrayEquals("spark_options.kryo", options.spark_options.kryo.asInstanceOf[Array[Object]], o.spark_options.kryo.asInstanceOf[Array[Object]])
                assertEquals("spark_options.checkpoint", options.spark_options.checkpoint, o.spark_options.checkpoint)
                assertEquals("cassandra_options", options.cassandra_options, o.cassandra_options)
                assertEquals("storage", options.storage, o.storage)
                assertEquals("keyspace", options.keyspace, o.keyspace)
                assertEquals("replication", options.replication, o.replication)
                assertEquals("model_file", options.model_file, o.model_file)
                assertEquals("meta_file", options.meta_file, o.meta_file)
                assertEquals("operation", options.operation, o.operation)
                assertArrayEquals("tree_depth", options.tree_depth, o.tree_depth)
                assertArrayEquals("bins", options.bins, o.bins)
                assertArrayEquals("info", options.info, o.info, 1e-12)
                assertEquals("seed", options.seed, o.seed)
                assertEquals("synthesis", options.synthesis, o.synthesis)
                assertEquals("start", options.start, o.start)
                assertEquals("end", options.end, o.end)
                assertEquals("period", options.period, o.period)
                assertEquals("yearly_kWh", options.yearly_kWh, o.yearly_kWh, 1e-12)
                assertEquals("classes", options.classes, o.classes)

        }
    }
}

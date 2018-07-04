package ch.ninecode.sim

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

import org.apache.spark.SparkEnv
import org.apache.spark.sql.SparkSession
import org.apache.spark.util.AccumulatorV2
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Determine the executors available at runtime.
 *
 * @param session The Spark session.
 */
case class SimulationExecutors (session: SparkSession)
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Get the list of workers.
     *
     * Uses the memory status report to get the executor names.
     *
     * @return a list of executors available
     */
    def getActiveWorkerHosts: List[String] =
    {
        val driver = session.sparkContext.getConf.get ("spark.driver.host")
        val hosts = session.sparkContext.getExecutorMemoryStatus.map (_._1.split (":")(0)).toList
        hosts.diff (List (driver))
    }

    /**
     * An accumulator to collect executor information from the cluster.
     *
     * @param executors the list to fill with executors
     */
    class ExecutorAccumulator (var executors: ConcurrentHashMap[String,String]) extends AccumulatorV2[ConcurrentHashMap[String,String],ConcurrentHashMap[String,String]]
    {
        def this () = this (new ConcurrentHashMap[String,String]())

        /**
         * Returns if this accumulator is zero value or not. e.g. for a counter accumulator, 0 is zero value; for a list accumulator, Nil is zero value.
         *
         * @return <code>true</code> if the accumulator is empty, <code>false</code> otherwise
         */
        override def isZero: Boolean = executors.isEmpty

        /**
         * Creates a new copy of this accumulator.
         *
         * @return a new copy of the accumulator with the same value as this
         */
        override def copy (): AccumulatorV2[ConcurrentHashMap[String, String], ConcurrentHashMap[String, String]] = new ExecutorAccumulator (executors)

        /**
         * Resets this accumulator, which is zero value.
         */
        override def reset (): Unit = executors.clear ()

        /**
         * Takes the inputs and accumulates.
         *
         * @param v the map to add to this accumulator (same as merge)
         */
        override def add (v: ConcurrentHashMap[String, String]): Unit = executors.putAll (v)

        /**
         * Merges another same-type accumulator into this one and update its state, i.e. this should be merge-in-place.
         *
         * @param other the map to merge into to this accumulator
         */
        override def merge (other: AccumulatorV2[ConcurrentHashMap[String, String], ConcurrentHashMap[String, String]]): Unit = executors.putAll (other.value)

        /**
         * Defines the current value of this accumulator
         *
         * @return the map held by this accumulator
         */
        override def value: ConcurrentHashMap[String, String] = executors
    }

    /**
     * Get the list of executors by sending a bogus flood task out to the cluster.
     *
     * @return a map of executor name to host address
     */
    def getActiveWorkerHostSet: ConcurrentHashMap[String, String] =
    {
        val accumulator = new ExecutorAccumulator ()
        session.sparkContext.register (accumulator, "executors")

        var foundNewHosts: Boolean = true
        val hosts = getActiveWorkerHosts.size
        if (0 != hosts)
        {
            while (foundNewHosts)
            {
                val old = accumulator.value
                val dataSet = 1 to hosts * 1000
                val numTasks = hosts * 100
                val data = session.sparkContext.parallelize (dataSet, numTasks)
                data.foreach (
                    _ =>
                    {
                        val identifier = SparkEnv.get.executorId
                        log.info ("""executor id: %s""".format (identifier))
                        val host = InetAddress.getLocalHost
                        val name = host.getHostName
                        log.info ("""localhost is %s named %s""".format (host, name))
                        val split = identifier.split ("=")
                        val id = split(0)
                        val worker = if (split.length > 1) split(1) else name
                        log.info ("""worker %s id: %s""".format (worker, id))
                        val executor = "executor_" + worker + "_" + id
                        val address = host.getHostAddress
                        log.info ("""executing on %s @ %s""".format (executor, address))
                        val v = new ConcurrentHashMap[String,String]()
                        v.put (executor, address)
                        accumulator.add (v)
                    }
                )
                val newSet = accumulator.value
                foundNewHosts = newSet.size > old.size
            }
            accumulator.value
        }
        else
            new ConcurrentHashMap[String, String]()
    }
}

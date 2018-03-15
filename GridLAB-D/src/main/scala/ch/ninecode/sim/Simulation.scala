package ch.ninecode.sim

import org.apache.spark.sql.SparkSession

import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class Simulation (session: SparkSession, options: SimulationOptions)
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    def read (rdf: String, reader_options: Map[String,String])
    {
        log.info ("""reading "%s"""".format (rdf))
        val start = System.nanoTime ()
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (rdf)
        log.info (elements.count () + " elements")
        val read = System.nanoTime ()
        log.info ("read: " + (read - start) / 1e9 + " seconds")
    }

    def execute (job: SimulationJob): Unit =
    {
        log.info ("""executing simulation job "%s"""".format (job.name))
    }

    def process (batch: Seq[SimulationJob]): Unit =
    {
        val ajob = batch.head // assumes that all jobs in a batch should have the same cluster state
        read (ajob.cim, ajob.cimreaderoptions)
        val executors = Math.max (1, session.sparkContext.getExecutorMemoryStatus.keys.size - 1)
        val simulations = session.sparkContext.parallelize (batch, executors)
        simulations.foreach (execute)
    }

    def run (): Unit =
    {
        var jobs = SimulationJob.getAll (options)
        // organize by same RDF and same options
        var batches = jobs.groupBy (job ⇒ job.cim + job.optionString)
        batches.values.foreach (process)
    }
}

object Simulation
{
    /**
     * The list of classes that can be persisted in RDD.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array (
            classOf[ch.ninecode.sim.Simulation],
            classOf[ch.ninecode.sim.SimulationJob],
            classOf[ch.ninecode.sim.SimulationOptions],
            classOf[ch.ninecode.sim.SimulationPlayer],
            classOf[ch.ninecode.sim.SimulationRecorder]
        )
    }
}

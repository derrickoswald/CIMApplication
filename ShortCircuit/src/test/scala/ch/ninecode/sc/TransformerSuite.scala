package ch.ninecode.sc

import java.io.File
import java.util.HashMap
import java.util.Map

import org.scalatest.BeforeAndAfter

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.Transformers

class TransformerSuite
    extends
        SparkSuite
    with
        BeforeAndAfter
{
    val PRIVATE_FILE_DEPOT = "private_data/"
    val log: Logger = LoggerFactory.getLogger (getClass)

    val FILE_DEPOT = "data/"

    val FILENAME1 = "DemoData.rdf"

    before
    {
        // unpack the zip files
        if (!new File (FILE_DEPOT + FILENAME1).exists)
            new Unzip ().unzip (FILE_DEPOT + "DemoData.zip", FILE_DEPOT)
    }

    test ("transformer area")
    {
        session: SparkSession ⇒
            val filename = FILE_DEPOT + FILENAME1

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
            options.put ("ch.ninecode.cim.make_edges", "false")
            options.put ("ch.ninecode.cim.do_join", "false")
            options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
            options.put ("ch.ninecode.cim.do_topo_islands", "false")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
            elements.persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) /  1e9 + " seconds")

            // identify topological nodes and islands
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true, true)
            val ele = ntp.process (true).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            val _transformers = new Transformers (session, StorageLevel.MEMORY_AND_DISK_SER)
            val tdata = _transformers.getTransformerData (true)

            // get all low voltage power transformers
            // ToDo: fix this 1kV multiplier on the voltages
            val niederspannug = tdata.filter (td ⇒ td.voltage0 != 0.4 && td.voltage1 == 0.4)
            val transformers = niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet (_))

            val trafo = System.nanoTime ()
            println ("transformers: " + (trafo - topo) / 1e9 + " seconds")

            val sc_options = ShortCircuitOptions (workdir = "./results/")
            val short_circuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = short_circuit.fix (transformers, session.sparkContext.emptyRDD)

            val sc = System.nanoTime ()
            println ("fix: " + (sc - trafo) / 1e9 + " seconds")

            // output SQLite database
            Database.store (sc_options) (results)

            val db = System.nanoTime ()
            println ("database: " + (db - sc) / 1e9 + " seconds")

            val total = System.nanoTime ()
            println ("total: " + (total - start) / 1e9 + " seconds")
    }
}

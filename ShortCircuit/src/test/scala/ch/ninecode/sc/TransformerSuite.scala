package ch.ninecode.sc

import java.util.HashMap
import java.util.Map

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.CIMNetworkTopologyProcessor

class TransformerSuite
    extends
        SparkSuite
{
    val PRIVATE_FILE_DEPOT = "private_data/"

    test ("transformer area")
    {
        session: SparkSession ⇒
            val filename = PRIVATE_FILE_DEPOT + "bkw_cim_export_schopfen_all" + ".rdf"

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

            // transformer area calculations
            val debug = false
            val tsa = if (debug)
            {
                org.apache.log4j.LogManager.getLogger ("ch.ninecode.sc.TransformerServiceArea").setLevel (org.apache.log4j.Level.DEBUG)
                TransformerServiceArea (session, true)
            }
            else
                TransformerServiceArea (session)
            val trafos_islands = tsa.getTransformerServiceAreas.map (x ⇒ (x._2, x._1)) // (trafosetid, islandid)
            val scopt = ShortCircuitOptions ()
            val short_circuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, scopt)
            val simulations = short_circuit.queryNetwork (trafos_islands)
            val results = trafos_islands.collect
            println (results.map (x ⇒ """%s %s""".format (x._1, x._2)).mkString ("\n"))

            val sc = System.nanoTime ()
            println ("transformer service areas: " + (sc - topo) / 1e9 + " seconds")

            println ("total: " + (sc - start) / 1e9 + " seconds")
    }
}

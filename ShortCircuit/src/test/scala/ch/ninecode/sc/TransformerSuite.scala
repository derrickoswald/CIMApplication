package ch.ninecode.sc

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.HashMap
import java.util.Map
import java.util.TimeZone

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.gl.Complex
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.Transformers

class TransformerSuite
    extends
        SparkSuite
{
    val PRIVATE_FILE_DEPOT = "private_data/"
    val log: Logger = LoggerFactory.getLogger (getClass)

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

            val _transformers = new Transformers (session, StorageLevel.MEMORY_AND_DISK_SER)
            val tdata = _transformers.getTransformerData (true)

            // get all low voltage power transformers
            // ToDo: fix this 1kV multiplier on the voltages
            val niederspannug = tdata.filter (td ⇒ td.voltage0 != 0.4 && td.voltage1 == 0.4)
            val transformers = niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet (_))

            val scopt = ShortCircuitOptions (workdir = "./results/")
            val short_circuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, scopt)
            val areas = short_circuit.queryNetwork (trafos_islands)
            val now = javax.xml.bind.DatatypeConverter.parseDateTime ("2018-07-19T12:00:00")
            val simulations = areas.join (transformers.keyBy (_.transformer_name)).map (x ⇒ (x._1, x._2._2, x._2._1._1, x._2._1._2)) // (areaid, trafoset, [nodes], [edges])
                .map (x ⇒
                    SimulationTransformerServiceArea (
                        simulation = x._1,
                        island = x._1,
                        transformer = x._2,
                        nodes = x._3,
                        edges = x._4,
                        start_time = now,
                        directory = x._2.transformer_name)
                )
            val sc = System.nanoTime ()
            println ("transformer service areas: " + (sc - topo) / 1e9 + " seconds")

            val z = short_circuit.remedial (simulations)
            println (z.collect.mkString ("\n"))

            val sim = System.nanoTime ()
            println ("total: " + (sim - start) / 1e9 + " seconds")
    }
}

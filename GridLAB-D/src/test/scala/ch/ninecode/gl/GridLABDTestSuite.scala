package ch.ninecode.gl

import java.io.File

import scala.collection.mutable
import org.scalatest.BeforeAndAfter

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue

class GridLABDTestSuite extends SparkSuite with BeforeAndAfter
{
    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData.rdf"
    val filename: String = FILE_DEPOT + FILENAME1

    before
    {
        // unpack the zip files
        if (!new File (filename).exists)
            new Unzip ().unzip (FILE_DEPOT + "DemoData.zip", FILE_DEPOT)
    }

    after
    {
        new File (filename).delete
    }

    test ("Basic")
    {
        session: SparkSession ⇒

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new mutable.HashMap[String, String]()
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
            options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
            options.put ("ch.ninecode.cim.do_topo_islands", "false")
            val storage = StorageLevel.fromString ("MEMORY_AND_DISK_SER")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) / 1e9 + " seconds")

            // identify topological nodes
            val ntp = CIMNetworkTopologyProcessor (session)
            val ele = ntp.process (
                CIMTopologyOptions (
                    identify_islands = true,
                    force_retain_switches = ForceTrue,
                    force_retain_fuses = ForceTrue,
                    debug = true,
                    storage = storage))
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            val gen = new GLMGenerator ()
            val text = gen.make_glm ()

            val generate = System.nanoTime ()
            println ("generate: " + (generate - topo) / 1e9 + " seconds")

            assert (text ==
                """// gridlabd.glm
// GridLAB-D
//*********************************************

        module tape;

        module powerflow
        {
            solver_method NR;
            default_maximum_voltage_error 10e-6;
            NR_iteration_limit 500;
            NR_superLU_procs 16;
            nominal_frequency 50;
        };

        clock
        {
            timezone "UTC0UTC";
            starttime "2017-05-08 10:00:00 UTC";
            stoptime "2017-05-08 10:00:00 UTC";
        };

        class player
        {
            complex value;
        };
""")
            println ("total: " + (generate - start) / 1e9 + " seconds")
    }
}

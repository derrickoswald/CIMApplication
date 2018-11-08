package ch.ninecode.sc

import java.io.File
import java.sql.DriverManager
import java.util.HashMap
import java.util.Map

import org.scalatest.BeforeAndAfter

import org.apache.commons.io.FileUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced
import ch.ninecode.gl.Complex
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.Transformers

class NonradialSuite extends SparkSuite with BeforeAndAfter
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    val FILE_DEPOT = "data/"
    val PRIVATE_FILE_DEPOT = "private_data/"

    val FILENAME1 = "bkw_cim_export_schopfen_all.rdf"
    val FILENAME2 = "sak_sample_complex_parallel.rdf"
    val FILENAME3 = "three_winding_non-radial.rdf"

    var run_one = 1
    var run_two = 2

    before
    {
        // unpack the zip files
        if (!new File (FILE_DEPOT + FILENAME2).exists)
            new Unzip ().unzip (FILE_DEPOT + "sak_sample_complex_parallel.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME3).exists)
            new Unzip ().unzip (FILE_DEPOT + "three_winding_non-radial.zip", FILE_DEPOT)
    }

    test ("Basic")
    {
        session: SparkSession ⇒
            val filename = PRIVATE_FILE_DEPOT + FILENAME1

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
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) /  1e9 + " seconds")

            // identify topological nodes
            val ntp = CIMNetworkTopologyProcessor (session)
            val ele = ntp.process (
                CIMTopologyOptions (
                    identify_islands = false,
                    force_retain_switches = Unforced,
                    force_retain_fuses = ForceTrue,
                    default_switch_open_state = false,
                    debug = true,
                    storage = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
            )
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (description = "Basic", trafos = PRIVATE_FILE_DEPOT + "trafo.txt")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            val sc = System.nanoTime ()
            println ("short circuit: " + (sc - topo) / 1e9 + " seconds")

            // output SQLite database
            run_one = Database.store (sc_options) (results)

            val db = System.nanoTime ()
            println ("database: " + (db - sc) / 1e9 + " seconds")

            println ("total: " + (db - start) / 1e9 + " seconds")
    }

    def check_impedances (percent: Double = 5.0): Unit =
    {
        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")
        // create a database connection
        val connection = DriverManager.getConnection ("jdbc:sqlite:results/shortcircuit.db")

        val s = connection.prepareStatement ("select distinct node from shortcircuit where run = ? and node like 'HAS%'")
        s.setInt (1, run_two)
        val r = s.executeQuery ()
        var nodes = List[String] ()
        while (r.next)
            nodes = r.getString (1) :: nodes
        r.close ()
        s.close ()

        case class Z (run: Int, node: String, equipment: String, z: Complex)
        {
            def near (that: Z, percentage: Double = percent): Boolean =
            {
                val diff = z - that.z
                val pct = diff.modulus / z.modulus * 100.0
                if (pct >= percentage) println ("""%s vs. %s %s %s%% (%s)""".format (this, that, if (pct >= percentage) ">" else "<", percentage, pct))
                pct < percentage
            }
        }

        for (node ← nodes)
        {
            // loop through the path from leaf to transformer checking the first run against the second
            val statement = connection.prepareStatement ("select run,node,equipment,r,x,prev from shortcircuit where node = ? and run in (?, ?)")
            var prev: String = node
            while ((null != prev) && ("self" != prev))
            {
                var data = List[Z] ()
                statement.setString (1, prev)
                statement.setInt (2, run_one)
                statement.setInt (3, run_two)
                prev = null
                val resultset = statement.executeQuery ()
                while (resultset.next)
                {
                    val datum = Z (resultset.getInt (1), resultset.getString (2), resultset.getString (3), Complex (resultset.getDouble (4), resultset.getDouble (5)))
                    data = datum :: data
                    val p = resultset.getString (6)
                    if (!resultset.wasNull) prev = p
                }
                resultset.close ()
                data.tail.foreach (x ⇒ assert (x.near (data.head, percent), "impedance difference"))
            }
            statement.close ()
        }

        connection.close ()
    }

//    test ("dummy")
//    {
//        session: SparkSession ⇒
//            check_impedances ()
//    }

    test ("Transformer Area")
    {
        session: SparkSession ⇒
            val filename = PRIVATE_FILE_DEPOT + FILENAME1

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
            val ntp = CIMNetworkTopologyProcessor (session)
            val ele = ntp.process (
                CIMTopologyOptions (
                    identify_islands = false,
                    force_retain_switches = Unforced,
                    force_retain_fuses = ForceTrue,
                    default_switch_open_state = false,
                    debug = true,
                    storage = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
            ).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            val _transformers = new Transformers (session, StorageLevel.MEMORY_AND_DISK_SER)
            val tdata = _transformers.getTransformerData (true)

            // get all low voltage power transformers
            // ToDo: fix this 1kV multiplier on the voltages
            val niederspannug = tdata.filter (td ⇒ td.voltage0 > 1.0 && td.voltage1 <= 1.0)
            val transformers = niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet (_))

            val trafo = System.nanoTime ()
            println ("transformers: " + (trafo - topo) / 1e9 + " seconds")

            val sc_options = ShortCircuitOptions (workdir = "./results/")
            val short_circuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = short_circuit.fix (transformers, session.sparkContext.emptyRDD)

            val sc = System.nanoTime ()
            println ("fix: " + (sc - trafo) / 1e9 + " seconds")

            // output SQLite database
            run_two = Database.store (sc_options) (results)

            val db = System.nanoTime ()
            println ("database: " + (db - sc) / 1e9 + " seconds")

            val total = System.nanoTime ()
            println ("total: " + (total - start) / 1e9 + " seconds")

            check_impedances (percent = 2.0)
    }

    test ("Complex Parallel without non-radial errors")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME2

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) /  1e9 + " seconds")

            // identify topological nodes
            val ntp = CIMNetworkTopologyProcessor (session)
            val ele = ntp.process (
                CIMTopologyOptions (
                    identify_islands = true,
                    force_retain_switches = Unforced,
                    force_retain_fuses = ForceTrue,
                    default_switch_open_state = false,
                    debug = true,
                    storage = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
            ).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                cmax = 0.95,
                cmin = 0.95,
                trafos = FILE_DEPOT + "sak_sample.transformers",
                workdir = "./data/result/")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
            val string = results.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val csv = string.collect
            println ("results: " + csv.length)
            println (ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv (i)
                println (h)
            }

            val consumer = results.filter (_.equipment == "EnergyConsumer").first ()
            assert (null != consumer.errors)
            assert (!consumer.errors.contains (ScError (true, true, "non-radial network detected through Line2").toString))
    }


    test ("Three Winding Transformer with non-radial network")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME3

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) /  1e9 + " seconds")

            // identify topological nodes
            val ntp = CIMNetworkTopologyProcessor (session)
            val ele = ntp.process (
                CIMTopologyOptions (
                    identify_islands = true,
                    force_retain_switches = Unforced,
                    force_retain_fuses = ForceTrue,
                    default_switch_open_state = false,
                    debug = true,
                    storage = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
            ).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                default_short_circuit_power_max = 600.0e6,
                default_short_circuit_impedance_max = Complex (0.0, 20.166666666666667), // purely reactive
                default_short_circuit_power_min = 600.0e6,
                default_short_circuit_impedance_min = Complex (0.0, 20.166666666666667), // purely reactive
                base_temperature = 20.0,
                low_temperature = 20.0,
                workdir = "./results/")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            val string = results.sortBy (_.tx).map (_.csv)
            val csv = string.collect
            println ("results: " + csv.length)
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))

            assert (results.filter (_.equipment == "USR0001").count == 0, "USR0001 should not be computed")
            assert (results.filter (_.equipment == "USR0002").first ().low_sk == 0.0, "USER002 power")
            assert (results.filter (_.equipment == "USR0002").first ().errors.contains (ScError (false, true, "%s transformer windings for edge %s".format (3, "TX0001")).toString), "USER002 errors")
            assert (results.filter (_.equipment == "USR0003").first ().low_sk == 0.0, "USER003 power")
            assert (results.filter (_.equipment == "USR0003").first ().errors.contains (ScError (false, true, "%s transformer windings for edge %s".format (3, "TX0001")).toString), "USER003 errors")
    }
}

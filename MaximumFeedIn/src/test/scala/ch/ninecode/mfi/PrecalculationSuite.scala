package ch.ninecode.mfi

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

import org.scalatest._

import org.apache.spark.SparkConf
import org.apache.spark.graphx.Graph
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.Solar
import ch.ninecode.gl.TransformerIsland
import ch.ninecode.gl.Transformers

class PrecalculationSuite extends fixture.FunSuite with BeforeAndAfter
{
    type FixtureParam = SparkSession
    val FILE_DEPOT = "data/"
    val FILENAME1 = "multipleconductor"
    val FILENAME2 = "multipletransformer"
    val FILENAME3 = "meshednetwork"

    def using[T <: AutoCloseable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block (resource)
        }
        finally
        {
            resource.close ()
        }
    }

    /**
     * This utility extracts files and directories of a standard zip file to
     * a destination directory.
     *
     * @author www.codejava.net
     *
     */
    class Unzip
    {
        /**
         * Extracts a zip file specified by the file to a directory.
         *
         * The directory will be created if does not exist.
         *
         * @param file      The Zip file.
         * @param directory The directory to extract it to
         * @throws IOException If there is a problem with the zip extraction
         */
        @throws[IOException]
        def unzip (file: String, directory: String): Unit =
        {
            val dir = new File (directory)
            if (!dir.exists)
                dir.mkdir
            using (new ZipInputStream (new FileInputStream (file)))
            {
                zip =>
                    var entry = zip.getNextEntry
                    // iterates over entries in the zip file
                    while (null != entry)
                    {
                        val path = directory + entry.getName
                        if (!entry.isDirectory)
                        // if the entry is a file, extract it
                            extractFile (zip, path)
                        else
                        // if the entry is a directory, make the directory
                            new File (path).mkdir
                        zip.closeEntry ()
                        entry = zip.getNextEntry
                    }
            }
        }

        /**
         * Extracts a zip entry (file entry).
         *
         * @param zip  The Zip input stream for the file.
         * @param path The path to extract he file to.
         * @throws IOException If there is a problem with the zip extraction
         */
        @throws[IOException]
        private def extractFile (zip: ZipInputStream, path: String): Unit =
        {
            val bytesIn = new Array[Byte](4096)
            using (new BufferedOutputStream (new FileOutputStream (path)))
            {
                bos =>
                    var read = -1
                    while (
                    {
                        read = zip.read (bytesIn)
                        read != -1
                    })
                        bos.write (bytesIn, 0, read)
            }
        }
    }

    def near (number: Double, reference: Double, epsilon: Double = 1.0e-3): Boolean =
    {
        val diff = number - reference
        val ret = Math.abs (diff) < epsilon
        if (!ret)
            println (s"""$number vs. reference $reference differs by more than $epsilon ($diff)""")
        ret
    }

    before
    {
        // unpack the zip files
        if (!new File (s"$FILE_DEPOT$FILENAME1.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME1.zip", FILE_DEPOT)
        if (!new File (s"$FILE_DEPOT$FILENAME2.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME2.zip", FILE_DEPOT)
        if (!new File (s"$FILE_DEPOT$FILENAME3.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME3.zip", FILE_DEPOT)
    }

    after
    {
        new File (s"$FILE_DEPOT$FILENAME1.rdf").delete
        new File (s"$FILE_DEPOT$FILENAME2.rdf").delete
        new File (s"$FILE_DEPOT$FILENAME3.rdf").delete
    }

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("GridLABDSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")
        configuration.set ("spark.ui.showConsoleProgress", "false")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GridLAB-D classes
        configuration.registerKryoClasses (GridLABD.classes)
        // register Einspeiseleistung classes
        configuration.registerKryoClasses (Einspeiseleistung.classes)

        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("WARN")

        val end = System.nanoTime ()
        println ("setup : " + (end - start) / 1e9 + " seconds")
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop () // clean up the fixture
    }

    def readFile (session: SparkSession, filename: String): Unit =
    {
        val files = filename.split (",")
        val options = Map[String,String](
            "path" -> filename,
            "ch.ninecode.cim.do_topo" -> "true",
            "ch.ninecode.cim.do_topo_islands" -> "true"
        )
        val elements = session.read.format ("ch.ninecode.cim").options (options).load (files: _*)
        println (elements.count () + " elements")
    }

    /**
     * Test for correct calculation of heuristic limit for parallel cables.
     */
    test ("MultipleServiceCables")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = s"$FILE_DEPOT$FILENAME1.rdf"
            readFile (session, filename)

            val read = System.nanoTime ()
            println ("read : " + (read - begin) / 1e9 + " seconds")

            // set up for execution
            val gridlabd = new GridLABD (session = session, topological_nodes = true, one_phase = true, workdir = ".")
            val storage_level = StorageLevel.MEMORY_AND_DISK_SER

            // do all low voltage power transformers
            val _transformers = new Transformers (session, storage_level)
            val transformer_data = _transformers.getTransformers ()
            val transformers = transformer_data.groupBy (_.node1.TopologicalIsland).values.map (TransformerIsland.apply)
            transformers.persist (storage_level).name = "Transformers"

            // construct the initial graph from the real edges and nodes
            val (xedges, xnodes) = gridlabd.prepare ()
            val initial = Graph.apply [PreNode, PreEdge](xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)

            // get the existing photo-voltaic installations keyed by terminal
            val solar = Solar (session, topologicalnodes = true, storage_level)
            val sdata = solar.getSolarInstallations

            val power_feeding = new PowerFeeding (session)
            val results: PreCalculationResults = power_feeding.threshold_calculation (initial, sdata, transformers, EinspeiseleistungOptions ())
            val houses = results.has
            // println (houses.take (100).mkString ("\n"))
            val has = houses.filter (_.mrid == "USR0001")
            assert (!has.isEmpty)
            val nodes = has.take (10)
            assert (nodes.length == 1)
            val node = nodes(0)
            assert (node.reason == "heuristic limit")
            assert (node.details == "limitation of last cable(s)")
            // ToDo: note that the 0.67V drop (including the cable) is derived from incorrect impedances due to incorrect legacy CIM export
            // two cables GKN 3x10re/10 1/0.6 kV with ratedCurrent 67A, @ (400 + 0.67)V * √3 = 92993
            assert (near (node.max_power_feeding, 92993, 1.0))
    }

    /**
     * Test for correct calculation of heuristic limit for multiple transformers.
     */
    test ("MultipleTransformers")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = s"$FILE_DEPOT$FILENAME2.rdf"
            readFile (session, filename)

            val read = System.nanoTime ()
            println ("read : " + (read - begin) / 1e9 + " seconds")

            // set up for execution
            val gridlabd = new GridLABD (session = session, topological_nodes = true, one_phase = true, workdir = ".")
            val storage_level = StorageLevel.MEMORY_AND_DISK_SER

            // do all low voltage power transformers
            val _transformers = new Transformers (session, storage_level)
            val transformer_data = _transformers.getTransformers ()
            val transformers = transformer_data.groupBy (_.node1.TopologicalIsland).values.map (TransformerIsland.apply)
            transformers.persist (storage_level).name = "Transformers"

            // construct the initial graph from the real edges and nodes
            val (xedges, xnodes) = gridlabd.prepare ()
            val initial = Graph.apply [PreNode, PreEdge](xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)

            // get the existing photo-voltaic installations keyed by terminal
            val solar = Solar (session, topologicalnodes = true, storage_level)
            val sdata = solar.getSolarInstallations

            val power_feeding = new PowerFeeding (session)
            val results: PreCalculationResults = power_feeding.threshold_calculation (initial, sdata, transformers, EinspeiseleistungOptions ())
            val houses = results.has
            // println (houses.take (100).mkString ("\n"))
            val has = houses.filter (_.mrid == "USR0001")
            assert (!has.isEmpty)
            val nodes = has.take (10)
            assert (nodes.length == 1)
            val node = nodes(0)
            assert (node.reason == "non-radial network")
            assert (node.details == "transformer limit")
            // 2 x 100kVA = 200000
            assert (near (node.max_power_feeding, 200000, 1.0))
    }

    /**
     * Test for correct calculation of heuristic limit for meshed networks.
     */
    test ("MeshedNetwork")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = s"$FILE_DEPOT$FILENAME3.rdf"
            readFile (session, filename)

            val read = System.nanoTime ()
            println ("read : " + (read - begin) / 1e9 + " seconds")

            // set up for execution
            val gridlabd = new GridLABD (session = session, topological_nodes = true, one_phase = true, workdir = ".")
            val storage_level = StorageLevel.MEMORY_AND_DISK_SER

            // do all low voltage power transformers
            val _transformers = new Transformers (session, storage_level)
            val transformer_data = _transformers.getTransformers ()
            val transformers = transformer_data.groupBy (_.node1.TopologicalIsland).values.map (TransformerIsland.apply)
            transformers.persist (storage_level).name = "Transformers"

            // construct the initial graph from the real edges and nodes
            val (xedges, xnodes) = gridlabd.prepare ()
            val initial = Graph.apply [PreNode, PreEdge](xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)

            // get the existing photo-voltaic installations keyed by terminal
            val solar = Solar (session, topologicalnodes = true, storage_level)
            val sdata = solar.getSolarInstallations

            val power_feeding = new PowerFeeding (session)
            val results: PreCalculationResults = power_feeding.threshold_calculation (initial, sdata, transformers, EinspeiseleistungOptions ())
            val houses = results.has
            // println (houses.take (100).mkString ("\n"))
            val has = houses.filter (_.mrid == "USR0001")
            assert (!has.isEmpty)
            val nodes = has.take (10)
            assert (nodes.length == 1)
            val node = nodes(0)
            assert (node.reason == "heuristic limit")
            assert (node.details == "limitation of last cable(s)")
            // two cables from two different transformers:
            // GKN 3x10re/10 1/0.6 kV with ratedCurrent 67A, @ (400 + 1.97)V * √3 = 46648
            // GKN 3x16rm/16 1/0.6 kV with ratedCurrent 88A, @ (400 + 2.58)V * √3 = 61361
            //                                                                     108009
            assert (near (node.max_power_feeding, 108009, 1.0))
    }
}

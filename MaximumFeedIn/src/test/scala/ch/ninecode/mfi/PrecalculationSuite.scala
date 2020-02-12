package ch.ninecode.mfi

import java.io.File

import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.Solar
import ch.ninecode.gl.TransformerIsland
import ch.ninecode.gl.Transformers
import org.apache.spark.graphx.Graph
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.scalatest._

class PrecalculationSuite extends MFITestBase with BeforeAndAfter
{
    val FILENAME1 = "multipleconductor"
    val FILENAME2 = "multipletransformer"
    val FILENAME3 = "meshednetwork"
    val FILENAME4 = "multiplesupplies"
    val FILENAME5 = "rückspannung"
    val FILENAME6 = "gangedtransformer"

    before
    {
        // unpack the zip files
        if (!new File (s"$FILE_DEPOT$FILENAME1.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME1.zip", FILE_DEPOT)
        if (!new File (s"$FILE_DEPOT$FILENAME2.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME2.zip", FILE_DEPOT)
        if (!new File (s"$FILE_DEPOT$FILENAME3.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME3.zip", FILE_DEPOT)
        if (!new File (s"$FILE_DEPOT$FILENAME4.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME4.zip", FILE_DEPOT)
        if (!new File (s"$FILE_DEPOT$FILENAME5.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME5.zip", FILE_DEPOT)
        if (!new File (s"$FILE_DEPOT$FILENAME6.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME6.zip", FILE_DEPOT)
    }

    after
    {
        new File (s"$FILE_DEPOT$FILENAME1.rdf").delete
        new File (s"$FILE_DEPOT$FILENAME2.rdf").delete
        new File (s"$FILE_DEPOT$FILENAME3.rdf").delete
        new File (s"$FILE_DEPOT$FILENAME4.rdf").delete
        new File (s"$FILE_DEPOT$FILENAME5.rdf").delete
        new File (s"$FILE_DEPOT$FILENAME6.rdf").delete
    }

    /**
     * Test for correct calculation of heuristic limit for parallel cables.
     */
    test ("MultipleServiceCables")
    {
        session: SparkSession ⇒
        {
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
            val initial = Graph.apply[PreNode, PreEdge](xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)

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
            val node = nodes (0)
            assert (node.reason == "heuristic limit")
            assert (node.details == "limitation of last cable(s)")
            // ToDo: note that the 0.67V drop (including the cable) is derived from incorrect impedances due to incorrect legacy CIM export
            // two cables GKN 3x10re/10 1/0.6 kV with ratedCurrent 67A, @ (400 + 0.67)V * √3 = 92993
            near (node.max_power_feeding, 92993, 1.0)
        }
    }

    /**
     * Test for correct calculation of heuristic limit for multiple transformers.
     */
    test ("MultipleTransformers")
    {
        session: SparkSession ⇒
        {
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
            val initial = Graph.apply[PreNode, PreEdge](xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)

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
            val node = nodes (0)
            assert (node.reason == "non-radial network")
            assert (node.details == "transformer limit")
            // 2 x 100kVA = 200000
            near (node.max_power_feeding, 200000, 1.0)
        }
    }

    /**
     * Test for correct calculation of heuristic limit for meshed networks.
     */
    test ("MeshedNetwork")
    {
        session: SparkSession ⇒
        {
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
            val initial = Graph.apply[PreNode, PreEdge](xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)

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
            val node = nodes (0)
            assert (node.reason == "heuristic limit")
            assert (node.details == "limitation of last cable(s)")
            // two cables from two different transformers:
            // GKN 3x10re/10 1/0.6 kV with ratedCurrent 67A, @ (400 + 1.97)V * √3 = 46648
            // GKN 3x16rm/16 1/0.6 kV with ratedCurrent 88A, @ (400 + 2.58)V * √3 = 61361
            //                                                                     108009
            near (node.max_power_feeding, 108009, 1.0)

            val options = EinspeiseleistungOptions (
                verbose = true,
                all = true,
                workdir = "simulation",
                files = List (filename)
            )
            runMFI(session, options)

            val query = s"select trafo, house, maximum, reason, details from results where simulation = ${getMaxSimulation (options.outputfile)} and house like 'USR%'"
            val result = querySQLite (options.outputfile, query)

            assert (result.size == 1, "number of records")
            while (result.next)
            {
                checkResults (result, 58000, "current limit", "CAB0002 > 67.0 Amps")
            }
        }
    }

    /**
     * Test for correct calculation of heuristic limit for multiple supply scenarios.
     */
    test ("MultipleSupplies")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = s"$FILE_DEPOT$FILENAME4.rdf"
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
            val initial = Graph.apply[PreNode, PreEdge](xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)

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
            val node = nodes (0)
            assert (node.reason == "heuristic limit")
            assert (node.details == "limitation of last cable(s)")
            // ToDo: this is not quite right, the voltage drop will depend on both supplies, but only one supply is found by the trace
            // two cables from two different supplies:
            // GKN 3x16rm/16 1/0.6 kV with ratedCurrent 88A, @ (400 + 2.00)V * √3 = 122547
            near (node.max_power_feeding, 122547, 1.0)
    }

    /**
     * Test for correct calculation of heuristic limit for Rückspannung scenarios.
     */
    test ("Rückspannung")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = s"$FILE_DEPOT$FILENAME5.rdf"
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
            val initial = Graph.apply[PreNode, PreEdge](xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)

            // get the existing photo-voltaic installations keyed by terminal
            val solar = Solar (session, topologicalnodes = true, storage_level)
            val sdata = solar.getSolarInstallations

            val power_feeding = new PowerFeeding (session)
            val results: PreCalculationResults = power_feeding.threshold_calculation (initial, sdata, transformers, EinspeiseleistungOptions ())
            val houses = results.has
            println (houses.take (100).mkString ("\n"))
            val has = houses.filter (_.mrid == "USR0001")
            assert (!has.isEmpty)
            val nodes = has.take (10)
            assert (nodes.length == 1)
            val node = nodes (0)
            assert (node.reason == "heuristic limit")
            assert (node.details == "limitation of last cable(s)")
            // GKN 3x10re/10 1/0.6 kV with ratedCurrent 67A, @ (400V + 13.45V) * √3 = 47980
            near (node.max_power_feeding, 47980.0, 1.0)
    }

    /**
     * Test for correct calculation of heuristic limit for ganged transformer scenarios.
     */
    test ("ganged transformers")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = s"$FILE_DEPOT$FILENAME6.rdf"
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
            val initial = Graph.apply[PreNode, PreEdge](xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)

            // get the existing photo-voltaic installations keyed by terminal
            val solar = Solar (session, topologicalnodes = true, storage_level)
            val sdata = solar.getSolarInstallations

            val power_feeding = new PowerFeeding (session)
            val results: PreCalculationResults = power_feeding.threshold_calculation (initial, sdata, transformers, EinspeiseleistungOptions ())
            val houses = results.has
            println (houses.take (100).mkString ("\n"))
            val has = houses.filter (_.mrid == "USR0001")
            assert (!has.isEmpty)
            val nodes = has.take (10)
            assert (nodes.length == 1)
            val node = nodes (0)
            assert (node.reason == "transformer limit")
            assert (node.details == "assuming no EEA")
            // 2×100KVA = 200000
            near (node.max_power_feeding, 200000.0, 1.0)
    }
}

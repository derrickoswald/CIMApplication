package ch.ninecode.mfi

import java.io.File

import org.apache.spark.sql.SparkSession
import org.scalatest._

class GridLABDSuite extends MFITestBase with BeforeAndAfter
{
    val FILENAME1 = "DemoData"
    val FILENAME2 = "parallel_cable_sample"
    val FILENAME3 = "three_winding_transformer"

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

    /**
     * Test for equality of precalculation and load-flow feed in values.
     */
    test ("Basic")
    {
        session: SparkSession ⇒
        {
            val filename = s"$FILE_DEPOT$FILENAME1.rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                all = true,
                files = List (filename)
            )
            runMFI(session, options)

            val maxSimulation = getMaxSimulation (options.outputfile)
            val query =
                "select trafo, house, maximum, reason, details from results where " +
                    s"(simulation = ${maxSimulation} or simulation = ${maxSimulation} - 1)" +
                    "and house like 'USR%' order by house, simulation"
            val result = querySQLite (options.outputfile, query)

            assert (result.size == 76, "number of records")
            while (result.next)
            {
                val max_precalc = result.getDouble ("maximum")
                assert (result.next, "expected pairs of results")
                val max_loadflow = result.getDouble ("maximum")
                assert (max_precalc - max_loadflow <= 1500.0, "compare precalc with loadflow")
            }
        }
    }

    test ("Test cable_impedance_limit parameter")
    {
        session: SparkSession ⇒
        {
            val filename = s"$FILE_DEPOT$FILENAME1.rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                all = true,
                workdir = "simulation/",
                files = List (filename),
                cable_impedance_limit = 0.14
            )
            runMFI(session, options)

            val maxSimulation = getMaxSimulation (options.outputfile)
            val query =
                "select trafo, house, maximum, reason, details from results where " +
                    s"(simulation = ${maxSimulation} or simulation = ${maxSimulation} - 1) " +
                    "and house like 'USR%' and trafo like 'TX0002'"
            val result = querySQLite (options.outputfile, query)

            assert (result.size == 28, "number of records")
            while (result.next)
            {
                checkResults (result, null.asInstanceOf [Double], "no results", "invalid element (CAB0014 r=0.14600148356433446)")
            }
        }
    }

    /**
     * Test for the correct current limit on a parallel set of cables.
     */
    test ("Parallel")
    {
        session: SparkSession ⇒
        {
            val filename = s"$FILE_DEPOT$FILENAME2.rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                workdir = "simulation/",
                files = List (filename)
            )
            runMFI(session, options)

            val query = "select trafo, house, maximum, reason, details from results where id = (select max(id) from results)"
            val result = querySQLite (options.outputfile, query)

            assert (result.size == 1, "number of records")
            while (result.next)
            {
                assert (result.getString (1) == "TX0001", "transformer name")
                assert (result.getString (2) == "USR0001", "energy consumer name")
                checkResults (result, 95000, "current limit", "CAB0001 > 134.0 Amps")
            }
        }
    }

    /**
     * Test for the correct handling of special transformers.
     */
    test ("Special transformer")
    {
        session: SparkSession ⇒
        {
            val filename = s"$FILE_DEPOT$FILENAME3.rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                workdir = "simulation/",
                all     = true,
                files = List (filename)
            )
            runMFI(session, options)

            val query = s"select trafo, house, maximum, reason, details from results where simulation = ${getMaxSimulation (options.outputfile)}"
            val result = querySQLite (options.outputfile, query)

            assert (result.size == 5, "should have 5 results")
            while (result.next)
            {
                if (result.getString ("trafo") == "TX0003")
                    fail ("""transformer "TX00003" should not be present""")
                else if (result.getString ("trafo") == "TX0002")
                {
                    assert (result.getObject ("maximum") != null, "all results on TX00002 should have a value for maximum")
                    assert (result.getString ("reason") == "current limit", "load-flow should find a current limit")
                    assert (result.getString ("details").contains (" > 67.0 Amps"), "limit should be set by GKN 3x10re/10 1/0.6 kV limit of 67 Amps")
                }
            }
        }
    }


    /**
     * Test for equality of three phase and single phase feed in values.
     */
    test ("Three phase")
    {
        session: SparkSession ⇒
        {
            val filename = s"$FILE_DEPOT$FILENAME1.rdf"

            val options_one_phase = EinspeiseleistungOptions (
                verbose = true,
                all = true,
                workdir = "simulation_three_phase/",
                files = List (filename)
            )
            runMFI(session, options_one_phase)
            val one_phase = getMaxSimulation (options_one_phase.outputfile)

            val options_three_phase = EinspeiseleistungOptions (
                verbose = true,
                three = true,
                all = true,
                workdir = "simulation_three_phase/",
                files = List (filename)
            )
            runMFI(session, options_one_phase)
            val three_phase = getMaxSimulation (options_three_phase.outputfile)

            val queryOnePhase = s"select trafo, house, maximum, reason, details from results where simulation='$one_phase'"
            val resultOnePhase = querySQLite (options_one_phase.outputfile, queryOnePhase)

            while (resultOnePhase.next)
            {
                val trafo = resultOnePhase.getString (1)
                val house = resultOnePhase.getString (2)
                val maximum = resultOnePhase.getDouble (3)
                val reason = resultOnePhase.getString (4)
                val details = resultOnePhase.getString (5)

                val queryThreePhase = s"select house, trafo, maximum, reason, details from results where simulation='$three_phase' and trafo='$trafo' and house='$house'"
                val resultThreePhase = querySQLite (options_three_phase.outputfile, queryThreePhase)

                assert (resultThreePhase.next, s"record for trafo '$trafo' house '$house' not found")
                checkResults (resultThreePhase, maximum, reason, details)
            }
        }
    }
}

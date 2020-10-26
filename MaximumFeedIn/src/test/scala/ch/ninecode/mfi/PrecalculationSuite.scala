package ch.ninecode.mfi

import java.io.File

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfter

@SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.NonUnitStatements"))
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
        if (!new File(s"$FILE_DEPOT$FILENAME1.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME1.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME2.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME2.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME3.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME3.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME4.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME4.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME5.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME5.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME6.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME6.zip", FILE_DEPOT)
    }

    after
    {
        new File(s"$FILE_DEPOT$FILENAME1.rdf").delete
        new File(s"$FILE_DEPOT$FILENAME2.rdf").delete
        new File(s"$FILE_DEPOT$FILENAME3.rdf").delete
        new File(s"$FILE_DEPOT$FILENAME4.rdf").delete
        new File(s"$FILE_DEPOT$FILENAME5.rdf").delete
        new File(s"$FILE_DEPOT$FILENAME6.rdf").delete
    }

    /**
     * Test for correct calculation of heuristic limit for parallel cables.
     */
    test("MultipleServiceCables")
    {
        session: SparkSession =>
        {
            val filename = s"$FILE_DEPOT$FILENAME1.rdf"
            readFile(session, filename)
            val options = EinspeiseleistungOptions(
                cim_options = setFile(filename),
                verbose = true,
                all = true,
                workdir = "simulation"
            )

            val einspeiseleistung: Einspeiseleistung = new Einspeiseleistung(session, options)
            val results: PreCalculationResults = einspeiseleistung.preCalculation(einspeiseleistung.initializeTransformers()._1)
            val houses: RDD[MaxPowerFeedingNodeEEA] = results.has
            // println (houses.take (100).mkString ("\n"))
            val has = houses.filter(_.mrid == "USR0001")
            assert(!has.isEmpty)
            val nodes = has.take(10)
            assert(nodes.length == 1)
            val node = nodes(0)
            assert(node.reason == "non-radial network")
            assert(node.details == null)
            // ToDo: note that the 0.67V drop (including the cable) is derived from incorrect impedances due to incorrect legacy CIM export
            // two cables GKN 3x10re/10 1/0.6 kV with ratedCurrent 67A, @ (400 + 0.67)V * √3 = 92993
            near(node.max_power_feeding, 92993, 1.0)

            runMFI(session, options)
            val query = "select trafo, house, maximum, reason, details from results where house = 'USR0001' order by simulation desc limit 2"
            val result = querySQLite(options.outputfile, query)
            assert(result.size == 2, "number of records")
            result.next
            checkResults(result, 93000, "current limit", "CAB0002 > 134.0 Amps")
            result.next
            checkResults(result, 0, "no results", "non-radial network")
        }
    }

    /**
     * Test for correct calculation of heuristic limit for multiple transformers.
     */
    test("MultipleTransformers")
    {
        session: SparkSession =>
        {
            val filename = s"$FILE_DEPOT$FILENAME2.rdf"
            readFile(session, filename)
            val options = EinspeiseleistungOptions(
                cim_options = setFile(filename),
                verbose = true,
                all = true,
                workdir = "simulation"
            )

            val einspeiseleistung: Einspeiseleistung = new Einspeiseleistung(session, options)
            val results: PreCalculationResults = einspeiseleistung.preCalculation(einspeiseleistung.initializeTransformers()._1)
            val houses: RDD[MaxPowerFeedingNodeEEA] = results.has
            // println (houses.take (100).mkString ("\n"))
            val has = houses.filter(_.mrid == "USR0001")
            assert(!has.isEmpty)
            val nodes = has.take(10)
            assert(nodes.length == 1)
            val node = nodes(0)
            assert(node.reason == "non-radial network")
            assert(node.details == null)
            // 2 x 100kVA = 200000
            near(node.max_power_feeding, 200000, 1.0)

            runMFI(session, options)
            val query = "select trafo, house, maximum, reason, details from results where house = 'USR0001' order by simulation desc limit 2"
            val result = querySQLite(options.outputfile, query)
            assert(result.size == 2, "number of records")
            result.next
            checkResults(result, 130000, "current limit", "CAB0005 > 88.0 Amps")
            result.next
            checkResults(result, 0, "no results", "non-radial network")
        }
    }

    /**
     * Test for correct calculation of heuristic limit for meshed networks.
     */
    test("MeshedNetwork")
    {
        session: SparkSession =>
        {
            val filename = s"$FILE_DEPOT$FILENAME3.rdf"
            readFile(session, filename)
            val options = EinspeiseleistungOptions(
                cim_options = setFile(filename),
                verbose = true,
                all = true,
                workdir = "simulation"
            )

            val einspeiseleistung: Einspeiseleistung = new Einspeiseleistung(session, options)
            val results: PreCalculationResults = einspeiseleistung.preCalculation(einspeiseleistung.initializeTransformers()._1)
            val houses: RDD[MaxPowerFeedingNodeEEA] = results.has
            // println (houses.take (100).mkString ("\n"))
            val has = houses.filter(_.mrid == "USR0001")
            assert(!has.isEmpty)
            val nodes = has.take(10)
            assert(nodes.length == 1)
            val node = nodes(0)
            assert(node.reason == "non-radial network")
            assert(node.details == null)
            // two cables from two different transformers:
            // GKN 3x10re/10 1/0.6 kV with ratedCurrent 67A, @ (400 + 1.97)V * √3 = 46648
            // GKN 3x16rm/16 1/0.6 kV with ratedCurrent 88A, @ (400 + 2.58)V * √3 = 61361
            //                                                                     108009
            near(node.max_power_feeding, 107599, 1.0)

            runMFI(session, options)

            val query = "select trafo, house, maximum, reason, details from results where house = 'USR0001' order by simulation desc limit 2"
            val result = querySQLite(options.outputfile, query)

            assert(result.size == 2, "number of records")
            result.next
            checkResults(result, 58000, "current limit", "CAB0002 > 67.0 Amps")
            result.next
            checkResults(result, 0, "no results", "non-radial network")
        }
    }

    /**
     * Test for correct calculation of heuristic limit for multiple supply scenarios.
     */
    test("MultipleSupplies")
    {
        session: SparkSession =>
        {
            val filename = s"$FILE_DEPOT$FILENAME4.rdf"
            readFile(session, filename)
            val options = EinspeiseleistungOptions(
                cim_options = setFile(filename),
                verbose = true,
                all = true,
                workdir = "simulation"
            )

            val einspeiseleistung: Einspeiseleistung = new Einspeiseleistung(session, options)
            val results: PreCalculationResults = einspeiseleistung.preCalculation(einspeiseleistung.initializeTransformers()._1)
            val houses: RDD[MaxPowerFeedingNodeEEA] = results.has
            // println (houses.take (100).mkString ("\n"))
            val has = houses.filter(_.mrid == "USR0001")
            assert(!has.isEmpty)
            val nodes = has.take(10)
            assert(nodes.length == 1)
            val node = nodes(0)
            assert(node.reason == "non-radial network")
            assert(node.details == null)
            // ToDo: this is not quite right, the voltage drop will depend on both supplies, but only one supply is found by the trace
            // two cables from two different supplies:
            // GKN 3x16rm/16 1/0.6 kV with ratedCurrent 88A, @ (400 + 2.00)V * √3 = 122547
            near(node.max_power_feeding, 122547, 1.0)

            runMFI(session, options)

            val query = "select trafo, house, maximum, reason, details from results where house = 'USR0001' order by simulation desc limit 2"
            val result = querySQLite(options.outputfile, query)

            assert(result.size == 2, "number of records")
            result.next
            checkResults(result, 85000, "current limit", "CAB0002 > 88.0 Amps")
            result.next
            checkResults(result, 0, "no results", "non-radial network")
        }
    }

    /**
     * Test for correct calculation of heuristic limit for Rückspannung scenarios.
     */
    test("Rueckspannung")
    {
        session: SparkSession =>
        {
            val filename = s"$FILE_DEPOT$FILENAME5.rdf"
            readFile(session, filename)
            val options = EinspeiseleistungOptions(
                cim_options = setFile(filename),
                verbose = true,
                all = true,
                workdir = "simulation"
            )

            val einspeiseleistung: Einspeiseleistung = new Einspeiseleistung(session, options)
            val results: PreCalculationResults = einspeiseleistung.preCalculation(einspeiseleistung.initializeTransformers()._1)
            val houses: RDD[MaxPowerFeedingNodeEEA] = results.has
            // println (houses.take (100).mkString ("\n"))
            val has = houses.filter(_.mrid == "USR0001")
            assert(!has.isEmpty)
            val nodes = has.take(10)
            assert(nodes.length == 1)
            val node = nodes(0)
            assert(node.reason == "non-radial network")
            assert(node.details == null)
            // GKN 3x10re/10 1/0.6 kV with ratedCurrent 67A, @ (400V + 13.45V) * √3 = 47980
            near(node.max_power_feeding, 47980.0, 1.0)

            runMFI(session, options)
            val query = "select trafo, house, maximum, reason, details from results where house = 'USR0001' order by simulation desc limit 2"
            val result = querySQLite(options.outputfile, query)
            assert(result.size == 2, "number of records")
            result.next
            checkResults(result, 28000, "voltage limit", "CAB0002 > 412.0 Volts")
            result.next
            checkResults(result, 0, "no results", "non-radial network")
        }
    }

    /**
     * Test for correct calculation of heuristic limit for ganged transformer scenarios.
     */
    test("ganged transformers")
    {
        session: SparkSession =>
        {
            val filename = s"$FILE_DEPOT$FILENAME6.rdf"
            readFile(session, filename)
            val options = EinspeiseleistungOptions(
                cim_options = setFile(filename),
                verbose = true,
                all = true,
                workdir = "simulation"
            )

            val einspeiseleistung: Einspeiseleistung = new Einspeiseleistung(session, options)
            val results: PreCalculationResults = einspeiseleistung.preCalculation(einspeiseleistung.initializeTransformers()._1)
            val houses: RDD[MaxPowerFeedingNodeEEA] = results.has
            // println (houses.take (100).mkString ("\n"))
            val has = houses.filter(_.mrid == "USR0001")
            assert(!has.isEmpty)
            val nodes = has.take(10)
            assert(nodes.length == 1)
            val node = nodes(0)
            assert(node.reason == "transformer limit")
            assert(node.details == "assuming no EEA")
            // 2×100KVA = 200000
            near(node.max_power_feeding, 200000.0, 1.0)

            runMFI(session, options)
            val query = "select trafo, house, maximum, reason, details from results where house = 'USR0001' order by simulation desc limit 2"
            val result = querySQLite(options.outputfile, query)
            assert(result.size == 2, "number of records")
            result.next
            checkResults(result, 0, "no results", "no limit")
            result.next
            checkResults(result, 200000, "transformer limit", "assuming no EEA")
        }
    }
}

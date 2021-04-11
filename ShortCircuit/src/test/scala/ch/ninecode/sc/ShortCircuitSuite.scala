package ch.ninecode.sc

import java.io.File

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.util.Complex

import org.scalatest.BeforeAndAfter

class ShortCircuitSuite extends SCTestBase with BeforeAndAfter
{

    val FILENAME1 = "Beispiel_zur_Ermittlung_der_Kurzschlussleistung"
    val FILENAME2 = "Beispiel_zur_Ermittlung_der_Kurzschlussleistung_mit_EquivalentInjection"
    val FILENAME3 = "sak_sample"
    val FILENAME4 = "sak_sample_ganged"
    val FILENAME5 = "sak_sample_parallel"
    val FILENAME6 = "sak_sample_complex_parallel"
    val FILENAME7 = "sak_sample_complex2_parallel"
    val FILENAME8 = "ibw_cim_export"
    val FILENAME9 = "fuse_no_sample"
    val FILENAME10 = "fuse_nc_sample"
    val FILENAME11 = "messagetest"

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
        if (!new File(s"$FILE_DEPOT$FILENAME7.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME7.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME8.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME8.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME9.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME9.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME10.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME10.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME11.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME11.zip", FILE_DEPOT)
    }

    after
    {
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME1.rdf"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME1.transformers"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME2.rdf"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME2.transformers"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME3.rdf"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME3.transformers"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME4.rdf"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME4.transformers"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME5.rdf"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME5.transformers"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME6.rdf"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME6.transformers"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME7.rdf"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME7.transformers"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME8.rdf"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME9.rdf"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME10.rdf"))
        deleteRecursive(new File(s"$FILE_DEPOT$FILENAME11.rdf"))
    }

    test("DACHCZ")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME1.rdf"
            readCIMElements(session, filename)
            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                default_short_circuit_power_max = 600.0e6,
                default_short_circuit_impedance_max = Complex(0.0, 20.166666666666667), // purely reactive
                default_short_circuit_power_min = 600.0e6,
                default_short_circuit_impedance_min = Complex(0.0, 20.166666666666667), // purely reactive
                low_temperature = 20.0,
                trafos = readTrafoFile(s"$FILE_DEPOT$FILENAME1.transformers"))
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            val consumer = results.filter(_.equipment == "E1").first()
            assert(Math.abs(consumer.low_sk - 2.13e6) < 5e3, "expected 2.13MVA")

            val busbar = results.filter(_.equipment == "SS").first()
            assert(Math.abs(busbar.low_sk - 8.98e6) < 5e3, "expected 8.98MVA")
    }

    test("DACHCZ with EquivalentInjection")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME2.rdf"
            readCIMElements(session, filename)

            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                low_temperature = 20.0,
                trafos = readTrafoFile(s"$FILE_DEPOT$FILENAME2.transformers"))
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            val consumer = results.filter(_.equipment == "E1").first()
            assert(Math.abs(consumer.low_sk - 2.13e6) < 5e3, "expected 2.13MVA")

            val busbar = results.filter(_.equipment == "SS").first()
            assert(Math.abs(busbar.low_sk - 8.98e6) < 5e3, "expected 8.98MVA")
    }

    test("SAK Spreadsheet")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME3.rdf"

            readCIMElements(session, filename)

            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                cmax = 0.95,
                cmin = 0.95,
                trafos = readTrafoFile(s"$FILE_DEPOT$FILENAME3.transformers"))
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            val consumer = results.filter(_.equipment == "EnergyConsumer").first()
            assert(Math.abs(consumer.low_r - 0.19521016) < 0.0005, "expected r=195mΩ")
            assert(Math.abs(consumer.low_x - 0.05195) < 0.0005, "expected x=52mΩ")
            assert(Math.abs(consumer.low_ik - 604) < 0.5, "expected ik1polig=604A")
            assert(Math.abs(consumer.low_ik3pol - 1085) < 0.5, "expected ik3polig=1085A")
            // I'm not sure why SAK uses ik3pol (which is scaled by cmax) to calculate Sk
            assert(Math.abs(consumer.low_sk * sc_options.cmax - 0.752e6) < 5e3, "expected 0.752MVA")
    }

    test("SAK Spreadsheet Ganged")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME4.rdf"

            readCIMElements(session, filename)

            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                low_temperature = 20.0,
                cmax = 0.95,
                cmin = 0.95,
                trafos = readTrafoFile(s"$FILE_DEPOT$FILENAME4.transformers"))
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            val consumer = results.filter(_.equipment == "EnergyConsumer").first()
            assert(Math.abs(consumer.low_r - 193.36016e-3) < 0.0005, "expected r=193mΩ")
            assert(Math.abs(consumer.low_x - 46.45e-3) < 0.0005, "expected x=46mΩ")
            assert(Math.abs(consumer.low_ik - 601) < 0.5, "expected ik1polig=601A")
            assert(Math.abs(consumer.low_ik3pol - 1103) < 0.5, "expected ik3polig=1103A")
            // I'm not sure why SAK uses ik3pol (which is scaled by cmax) to calculate Sk
            assert(Math.abs(consumer.low_sk * sc_options.cmax - 0.764e6) < 5e3, "expected 0.764MVA")
    }

    test("SAK Spreadsheet Parallel")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME5.rdf"
            readCIMElements(session, filename)


            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                low_temperature = 20.0,
                cmax = 0.95,
                cmin = 0.95,
                trafos = readTrafoFile(s"$FILE_DEPOT$FILENAME5.transformers"))
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            val consumer = results.filter(_.equipment == "EnergyConsumer").first()
            assert(Math.abs(consumer.low_r - 162.55141e-3) < 0.0005, "expected r=163mΩ")
            assert(Math.abs(consumer.low_x - 37.1e-3) < 0.0005, "expected x=37mΩ")
            assert(Math.abs(consumer.low_ik - 746) < 0.5, "expected ik1polig=746A")
            assert(Math.abs(consumer.low_ik3pol - 1316) < 0.5, "expected ik3polig=1316A")
            // I'm not sure why SAK uses ik3pol (which is scaled by cmax) to calculate Sk
            assert(Math.abs(consumer.low_sk * sc_options.cmax - 0.912e6) < 5e3, "expected 0.912MVA")
    }

    test("Complex Parallel")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME6.rdf"
            readCIMElements(session, filename, Map[String, String](
                "path" -> filename,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo" -> "true", // turn off island processing
                "ch.ninecode.cim.force_retain_switches" -> "Unforced",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.debug" -> "true",
                "ch.ninecode.cim.do_deduplication" -> "true"
            )
            )

            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                cmax = 0.95,
                cmin = 0.95,
                trafos = readTrafoFile(s"$FILE_DEPOT$FILENAME6.transformers"))
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            val consumer = results.filter(_.equipment == "EnergyConsumer").first()
            assert(consumer.errors.contains(
                ScError(fatal = true, invalid = true, "non-radial network detected through Line2").toString)
            )
    }

    test("Complex 2 Parallel")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME7.rdf"

            readCIMElements(session, filename, Map[String, String](
                "path" -> filename,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo" -> "true", // turn off island processing
                "ch.ninecode.cim.force_retain_switches" -> "Unforced",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.debug" -> "true",
                "ch.ninecode.cim.do_deduplication" -> "true"
            )
            )

            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                cmax = 0.95,
                cmin = 0.95,
                trafos = readTrafoFile(s"$FILE_DEPOT$FILENAME7.transformers"))
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            val consumer = results.filter(_.equipment == "EnergyConsumer").first()
            assert(consumer.errors.contains(ScError(
                fatal = true,
                invalid = true,
                "non-radial network detected from Line1_node_2_topo to Line_A_node_2_topo").toString)
            )
    }

    test("IBW")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME8.rdf"
            readCIMElements(session, filename)

            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                description = "IBW",
                low_temperature = 20.0,
                cmin = 1.0,
                worstcasepf = false,
                cosphi = 1.0)
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            // output SQLite database
            val _ = Database(sc_options).store(results)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            val data1 = results.filter(_.equipment == "HAS9754").first()
            assert(Math.abs(data1.low_ik - 756.115324830728) < 0.5, "expected ik1polig=756A")
            assert(Math.abs(data1.low_ik3pol - 1460.71083079341) < 0.5, "expected ik3polig=1461A")
            assert(Math.abs(data1.low_sk - 1.01198046357367e6) < 5e3, "expected sk=1.012MVA")
            assert(Math.abs(data1.imax_3ph_med - 49.3) < 0.5, "expected maxanlaufstrom=49A")
            assert(Math.abs(data1.imax_2ph_med - 42.7) < 0.5, "expected maxanlaufstrom=43A")
            assert(Math.abs(data1.imax_1ph_med - 24.7) < 0.5, "expected maxanlaufstrom=25A")

            val data2 = results.filter(_.equipment == "HAS9753") first()
            assert(Math.abs(data2.imax_3ph_med - 288.6) < 0.5, "expected maxanlaufstrom=289A")
            // not 250A like the spreadsheet says:
            assert(Math.abs(data2.imax_2ph_med - 212.8) < 0.5, "expected maxanlaufstrom=213A")
            assert(Math.abs(data2.imax_1ph_med - 144.3) < 0.5, "expected maxanlaufstrom=145A")


    }

    test("normalOpen=true Fuse")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME9.rdf"
            readCIMElements(session, filename)

            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                low_temperature = 20.0,
                cmax = 0.95,
                cmin = 0.95)
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            val consumer = results.filter(_.equipment == "EnergyConsumer").first()
            assert(Math.abs(consumer.low_ik - 812) < 0.5, "expected ik1polig=812A")
            assert(Math.abs(consumer.low_ik3pol - 1463) < 0.5, "expected ik3polig=1463A")
            // I'm not sure why SAK uses ik3pol (which is scaled by cmax) to calculate Sk
            assert(Math.abs(consumer.low_sk * sc_options.cmax - 1.015e6) < 5e3, "expected sk=1.015MVA")
            assert(0 == results.filter(_.errors.nonEmpty).count, "expected no errors")
    }

    test("normalOpen=false open=true Fuse")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME10.rdf"
            readCIMElements(session, filename)

            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                low_temperature = 20.0,
                cmax = 0.95,
                cmin = 0.95)
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            val consumer = results.filter(_.equipment == "EnergyConsumer").first()
            assert(Math.abs(consumer.low_ik - 812) < 0.5, "expected ik1polig=812A")
            assert(Math.abs(consumer.low_ik3pol - 1463) < 0.5, "expected ik3polig=1463A")
            // I'm not sure why SAK uses ik3pol (which is scaled by cmax) to calculate Sk
            assert(Math.abs(consumer.low_sk * sc_options.cmax - 1.015e6) < 5e3, "expected sk=1.015MVA")
            assert(0 == results.filter(_.errors.nonEmpty).count, "expected no errors")
        // if the transformer impedances are removed from the sample file, this command yields the same results:
        // spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/ShortCircuit-*-jar-with-dependencies.jar --logging "INFO" --netz "0.0 + 0.0j" --trafoz "0.01375 + 0.05312j" --cmax 0.95 --cmin 0.95 "hdfs://sandbox:8020/fuse_nc_sample.rdf"
        // except there is a warning "transformer has no impedance value, using default 0.01375+0.05312j"
    }

    test("message test")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME11.rdf"
            val MESSAGELIMIT = 2
            readCIMElements(session, filename, Map[String, String](
                "path" -> filename,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo" -> "true", // turn off island processing
                "ch.ninecode.cim.force_retain_switches" -> "Unforced",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.debug" -> "true",
                "ch.ninecode.cim.do_deduplication" -> "true"
            )
            )

            // short circuit calculations
            val sc_options = ShortCircuitOptions(
                messagemax = MESSAGELIMIT)
            val shortcircuit = ShortCircuit(session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run()

            // write output to file and console
            val output = "target/result"
            val string = results.sortBy(_.tx).map(_.csv(sc_options))

            deleteRecursive(new File(output))
            string.saveAsTextFile(output)

            val csv = string.collect
            println(s"results: ${csv.length}")
            println(ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv(i)
                println(h)
            }

            assert(0 == results.filter(_.errors.size > MESSAGELIMIT)
                .count, "expected no more than %d errors".format(MESSAGELIMIT))
            assert(0 != results.filter(_.errors.exists(_.startsWith("FATAL")))
                .count, "expected a fatal message")
    }
}

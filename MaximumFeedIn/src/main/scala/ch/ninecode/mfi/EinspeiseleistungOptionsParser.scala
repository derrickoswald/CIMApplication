package ch.ninecode.mfi

import scopt.OptionParser

class EinspeiseleistungOptionsParser (APPLICATION_NAME: String, APPLICATION_VERSION: String)
    extends OptionParser[EinspeiseleistungOptions](APPLICATION_NAME)
{
    head (APPLICATION_NAME, APPLICATION_VERSION)

    val default = new EinspeiseleistungOptions
    var unittest = false
    var helpout = false
    var versionout = false

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    implicit val mapRead: scopt.Read[Map[String, String]] = scopt.Read.reads (
        s =>
        {
            var ret = Map [String, String]()
            val ss = s.split (",")
            for (p <- ss)
            {
                val kv = p.split ("=")
                ret = ret + ((kv (0), kv (1)))
            }
            ret
        }
    )

    override def terminate (exitState: Either[String, Unit]): Unit =
    {
        if ((helpout || versionout) && !unittest)
            exitState match
            {
                case Left (_) => sys.exit (1)
                case Right (_) => sys.exit (0)
            }
    }

    opt [Unit]("unittest").
        hidden ().
        action ((_, c) =>
        {
            unittest = true;
            c.copy (unittest = true)
        }).
        text (s"unit testing - don't call sys.exit() [${default.unittest}]")

    opt [String]("master").valueName ("MASTER_URL").
        action ((x, c) => c.copy (master = x)).
        text ("spark://host:port, mesos://host:port, yarn, or local[*]")

    opt [Map[String, String]]("sparkopts").valueName ("k1=v1,k2=v2").
        action ((x, c) => c.copy (spark_options = x)).
        text (s"Spark options [${default.spark_options.map (x ⇒ x._1 + "=" + x._2).mkString (",")}]")

    opt [String]("storage_level").
        action ((x, c) => c.copy (storage = x)).
        text (s"storage level for RDD serialization [${default.storage}]")

    opt [Map[String, String]]("cimopts").valueName ("k1=v1,k2=v2").
        action ((x, c) => c.copy (cim_reader_options = x)).
        text (s"CIMReader options [${default.cim_reader_options.map (x ⇒ x._1 + "=" + x._2).mkString (",")}]")

    opt [Unit]("deduplicate").
        action ((_, c) => c.copy (dedup = true)).
        text (s"de-duplicate input (striped) files [${default.dedup}]")

    opt [LogLevels.Value]("logging").
        action ((x, c) => c.copy (log_level = x)).
        text (s"log level, one of ${LogLevels.values.iterator.mkString (",")} [${default.log_level}]")

    opt [String]("checkpoint").valueName ("<dir>").
        action ((x, c) => c.copy (checkpoint_dir = x)).
        text (s"checkpoint directory on HDFS, e.g. hdfs://... [${default.checkpoint_dir}]")

    opt [Unit]("verbose").
        action ((_, c) => c.copy (verbose = true)).
        text (s"turns on the INFO messages logging [${default.verbose}]")

    opt [Unit]("three").
        action ((_, c) => c.copy (three = true)).
        text (s"use three phase computations [${default.three}]")

    opt [Unit]("precalculation").
        action ((_, c) => c.copy (precalculation = true)).
        text (s"only calculates threshold and EEA existence for all HAS, assuming no EEA [${default.precalculation}]")

    opt [String]("trafos").valueName ("<TRA file>").
        action ((x, c) => c.copy (trafos = x)).
        text (s"file of transformer names (one per line) to process [${default.trafos}]")

    opt [Unit]("export_only").
        action ((_, c) => c.copy (export_only = true)).
        text (s"generates glm files only - no solve or analyse operations [${default.export_only}]")

    opt [Unit]("all").
        action ((_, c) => c.copy (all = true)).
        text (s"process all transformers (not just those with EEA) [${default.all}]")

    opt [Unit]("erase").
        action ((_, c) => c.copy (erase = true)).
        text (s"clean up (delete) simulation files [${default.erase}]")

    opt [Int]("simulation").valueName ("N").
        action ((x, c) => c.copy (simulation = x)).
        text (s"simulation number (precalc) to use for transformer list [${default.simulation}]")

    opt [Int]("reference").valueName ("N").
        action ((x, c) => c.copy (reference = x)).
        text (s"simulation number (precalc) to use as reference for transformer list [${default.reference}]")

    opt [Int]("delta").valueName ("D").
        action ((x, c) => c.copy (delta = x)).
        text (f"delta power difference threshold for reference comparison [${default.delta}%g]")

    opt [Double]("precalcfactor").valueName ("D").
        action ((x, c) => c.copy (precalc_factor = x)).
        text (f"factor to multiply precalculation results for gridlabd [${default.precalc_factor}%g]")

    opt [Double]("cosphi").valueName ("D").
        action ((x, c) => c.copy (cosphi = x)).
        text (f"power factor for photo-voltaic installations, positive leading, negative lagging [${default.cosphi}%g]")

    opt [Double]("voltage_threshold").valueName ("D").
        action ((x, c) => c.copy (voltage_threshold = x)).
        text (f"the voltage threshold for the feeder of the house under test [${default.voltage_threshold}%g%%]")

    opt [Double]("voltage_threshold2").valueName ("D").
        action ((x, c) => c.copy (voltage_threshold2 = x)).
        text (f"the voltage threshold for neighboring feeders of the house under test [${default.voltage_threshold2}%g%%]")

    opt [Unit]("ignore_other").
        action ((_, c) => c.copy (ignore_other = true)).
        text (s"ignore cable currents on neighboring feeders [${default.ignore_other}]")

    opt [Double]("cable_impedance_limit").valueName ("D").
        action ((x, c) => c.copy (cable_impedance_limit = x)).
        text (f"cables with higher impedances for R1 will not be processed with GridLAB-D [${default.cable_impedance_limit}%gΩ]")

    opt [String]("workdir").valueName ("<dir>").
        action ((x, c) => c.copy (workdir = x)).
        text (s"shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files [${default.workdir}]")

    opt [String]("outputfile").valueName ("<file>").
        action ((x, c) => c.copy (outputfile = x)).
        text (s"name of the SQLite database results file [${default.outputfile}]")

    opt [Double]("tbase").valueName ("<value>").
        action ((x, c) ⇒ c.copy (base_temperature = x)).
        text ("base temperature for feedin (°C) [%g]".format (default.base_temperature))

    opt [Double]("tsim").valueName ("<value>").
        action ((x, c) ⇒ c.copy (sim_temperature = x)).
        text ("simulation temperature for feedin (°C) [%g]".format (default.sim_temperature))

    arg [String]("<CIM> <CIM> ...").optional ().unbounded ().
        action ((x, c) => c.copy (files = c.files :+ x)).
        text ("CIM rdf files to process")

    help ("help").
        hidden ().
        validate (Unit =>
        {
            helpout = true;
            Right (Unit)
        })

    version ("version").
        validate (Unit =>
        {
            versionout = true;
            Right (Unit)
        }).
        text ("Scala: %s, Spark: %s, %s: %s".format (
            APPLICATION_VERSION.split ("-")(0),
            APPLICATION_VERSION.split ("-")(1),
            APPLICATION_NAME,
            APPLICATION_VERSION.split ("-")(2)
        )
        )

    checkConfig (o =>
    {
        o.valid = !(helpout || versionout);
        Right (Unit)
    })

    note (
        """
Calculate maximum feed-in power without reinforcement or exceeding voltage, current or power constraints.
""")

}
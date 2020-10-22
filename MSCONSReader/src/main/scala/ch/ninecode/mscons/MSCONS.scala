package ch.ninecode.mscons

import java.io.FileOutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat

import org.slf4j.LoggerFactory
import org.slf4j.Logger

import ch.ninecode.util.Main
import ch.ninecode.util.MainOptions

/**
 * Sample main program for MSCONS parsing.
 */
object MSCONS extends Main
{
    def main (args: Array[String]): Unit =
    {
        val log: Logger = LoggerFactory.getLogger(getClass)

        val default = MSCONSOptions(main_options = MainOptions(application_name, application_version))

        new MSCONSOptionsParser(default).parse(args, default) match
        {
            case Some(options) =>
                if (options.main_options.valid)
                {
                    if (options.verbose) org.apache.log4j.LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
                    if (options.verbose) org.apache.log4j.LogManager.getLogger("ch.ninecode.mscons.MSCONSParser").setLevel(org.apache.log4j.Level.INFO)

                    val template = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
                    if (options.mscons.nonEmpty)
                        for (name <- options.mscons)
                        {
                            val readings = MSCONSParser(options).parse(name)
                            val out = if ("" == options.output_file) System.out else new PrintStream(new FileOutputStream(options.output_file, true))
                            readings.foreach(
                                x =>
                                {
                                    val d = options.delimiter
                                    val record = s"${x._1}$d${x._2}$d${template.format(x._3.getTime)}$d${x._4}$d${x._5}+${x._6}j$d${x._7}"
                                    out.println(record)
                                }
                            )
                            out.close()
                        }
                    else
                        log.error("no input MSCONS files specified")
                }
                if (!options.main_options.unittest)
                    sys.exit(0)
            case None =>
                sys.exit(1)
        }
    }
}
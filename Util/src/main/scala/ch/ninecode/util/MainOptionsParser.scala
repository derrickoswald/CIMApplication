package ch.ninecode.util

import scopt.OptionParser

/**
 * Parser for command line operation of standard programs.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class MainOptionsParser[T <: Mainable] (default: T) extends OptionParser[T](default.main_options.application)
{
    var unittest = false
    var helpout = false
    var versionout = false

    override def terminate (state: Either[String, Unit]): Unit =
    {
        if ((helpout || versionout) && !unittest)
            sys.exit(if (state.isRight) 0 else 1)
    }

    head(default.main_options.application, default.main_options.version)

    opt[Unit]("unittest")
        .hidden
        .action(
            (_, c) =>
            {
                unittest = true;
                c.main_options = c.main_options.copy(unittest = true);
                c
            }
        )
        .text(s"unit testing - don't call sys.exit() [${default.main_options.unittest}]")

    help("help")
        .hidden
        .validate(Unit =>
        {
            helpout = true;
            Right(Unit)
        })

    version("version")
        .validate(Unit =>
        {
            versionout = true;
            Right(Unit)
        })
        .text(
            {
                val version = default.main_options.version.split("-")
                if (3 == version.length)
                    s"Scala: ${version(0)}, Spark: ${version(1)}, ${default.main_options.application}: ${version(2)}"
                else
                    default.main_options.version
            }
        )

    checkConfig(o =>
    {
        o.main_options = o.main_options.copy(valid = !(helpout || versionout));
        Right(Unit)
    })
}

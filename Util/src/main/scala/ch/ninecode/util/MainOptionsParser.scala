package ch.ninecode.util

import scala.io.Source

import scopt.OptionParser

/**
 * Parser for command line operation of standard programs.
 *
 * @param default object that provides default values
 * @tparam T T class type required for parsed values
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.Throw"))
class MainOptionsParser[T <: Mainable with JSONAble[T]] (default: T) extends OptionParser[T](default.main_options.application) with Using
{
    var unittest = false
    var helpout = false
    var versionout = false

    /**
     * Return the object that has defaults.
     *
     * @return the default object
     */
    def getDefault: T = default

    override def terminate (state: Either[String, Unit]): Unit =
    {
        if ((helpout || versionout) && !unittest)
            sys.exit(if (state.isRight) 0 else 1)
    }

    head(getDefault.main_options.application, getDefault.main_options.version)

    opt[Unit]("unittest")
        .hidden
        .action(
            (_, c) =>
            {
                unittest = true
                c.main_options = c.main_options.copy(unittest = true)
                c
            }
        )
        .text(s"unit testing - don't call sys.exit() [${getDefault.main_options.unittest}]")

    help("help")
        .hidden
        .validate(Unit =>
        {
            helpout = true
            Right(Unit)
        })

    version("version")
        .validate(Unit =>
        {
            versionout = true
            Right(Unit)
        })
        .text(
            {
                val version = getDefault.main_options.version.split("-")
                if (3 == version.length)
                    s"Scala: ${version(0)}, Spark: ${version(1)}, ${getDefault.main_options.application}: ${version(2)}"
                else
                    getDefault.main_options.version
            }
        )

    opt[Unit]("default_json")
        .action((_, c) => { println(c.toJSON); c })
        .validate(Unit =>
        {
            helpout = true
            Right(Unit)
        })
        .text(s"emit the current options as JSON to provide an example file for --json")

    opt[String]("json")
        .valueName("<JSON file>")
        .action(
            (x, c) =>
            {
                using (Source.fromFile(x, "UTF-8"))(
                    source =>
                    {
                        c.fromJSON (source.mkString) match
                        {
                            case Right (options) => options
                            case Left(message) => throw new Exception(message) // sadly, scopt only understands exceptions
                        }
                    }
                )
            }
        )
        .text(s"override the options with the contents of the given file")

    checkConfig(c =>
    {
        c.main_options = c.main_options.copy(valid = !(helpout || versionout))
        Right(Unit)
    })
}

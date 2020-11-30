package ch.ninecode.util

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class OptionsSuiteOptionsParser (options: OptionsSuiteOptions)
    extends MainOptionsParser[OptionsSuiteOptions](options)
    with SparkOptionsParser[OptionsSuiteOptions]
    with CIMReaderOptionsParser[OptionsSuiteOptions]
{
    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"enable informational messages [${options.verbose}]")
}

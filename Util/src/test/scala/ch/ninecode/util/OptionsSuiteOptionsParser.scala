package ch.ninecode.util

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class OptionsSuiteOptionsParser (options: OptionsSuiteOptions) extends CIMReaderOptionsParser[OptionsSuiteOptions](options)
{
    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"enable informational messages [${options.verbose}]")
}

package ch.ninecode.ingest

import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Ingest meter readings options.
 *
 * @param verbose    If <code>true</code>, emit progress messages.
 * @param host       Cassandra connection host.
 * @param port       Cassandra connection port.
 * @param workdir    Working directory for unzipping and copying if nocopy is <code>false</code>.
 * @param ingestions File to ingest.
 */
case class IngestOptions
(
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    verbose: Boolean = false,
    host: String = "localhost",
    port: Int = 9042,
    workdir: String = s"${IngestOptions.cwd}/work/",
    ingestions: Seq[String] = Seq()
) extends Mainable with Sparkable

object IngestOptions
{
    def cwd: String =
    {
        val pwd = new java.io.File(".").getCanonicalPath
        if (pwd.endsWith("."))
            pwd.substring(0, pwd.length - 1)
        else
            pwd
    }
}

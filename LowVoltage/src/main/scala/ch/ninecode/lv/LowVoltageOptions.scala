package ch.ninecode.lv

import java.net.URI

import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.util.CIMAble
import ch.ninecode.util.CIMReaderOptions
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Low voltage GLM generation options.
 *
 * @param main_options main() program options
 * @param spark_options Spark session options
 * @param cim_options CIMReader options
 * @param verbose if <code>true</code> output informational messages
 * @param three if <code>true</code> generate three phase .glm files, else one phase.
 * @param trafos the list of transformers to process,
 *               default is an empty list which means all low voltage transformers in the input file(s) are processed
 * @param workdir the shared directory (among Spark executors) to use for staging GridLAB-D simulations,
 *                each simulation is created in a subdirectory of this directory
 */
case class LowVoltageOptions (
    var main_options: MainOptions = MainOptions (),
    var spark_options: SparkOptions = SparkOptions (),
    var cim_options: CIMReaderOptions = CIMReaderOptions (
        topology = true,
        topology_options = CIMTopologyOptions (identify_islands = true),
        dedup = true),
    verbose: Boolean = false,
    three: Boolean = false,
    trafos: String = "",
    workdir: String = ""
) extends Mainable with Sparkable with CIMAble
{
    def derive_work_dir (files: Seq[String]): String =
    {
        files.toList match
        {
            case paths :: _ =>
                val file = paths.split (",")(0).replace (" ", "%20")
                val uri = new URI (file)
                val scheme = uri.getScheme
                val auth = if (null == uri.getAuthority) "" else uri.getAuthority
                if (null == scheme)
                    "/simulation/"
                else
                    s"$scheme://$auth/simulation/"
            case _ =>
                "/simulation/"
        }
    }

    /**
     * Get user specified directory or generate a working directory matching the files.
     */
    def getWorkDir: String = if ("" != workdir) workdir else derive_work_dir (cim_options.files)
}

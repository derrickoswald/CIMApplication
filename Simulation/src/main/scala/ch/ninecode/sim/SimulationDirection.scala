package ch.ninecode.sim

import java.io.File
import java.io.PrintWriter

import scala.io.Source

import org.apache.log4j.LogManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.net.LineEdge
import ch.ninecode.util.Complex
import ch.ninecode.util.ThreePhaseComplexDataElement

/**
 * Determine the direction of current in the passive network.
 *
 * This is to handle "Rücklieferung" when a PV installation is supplying
 * more power than the EnergyConsumer is using and feeding back into the network.
 *
 * Each node is loaded with 1kVA in a GridLAB-D load-flow and
 * the current through each cable is simulated and given a sign value (±1)
 * according to whether the simulated value is negative or positive.
 *
 * @param workdir the directory to create the .glm and location of /input_data and /output_data directories
 * @param verbose when <code>true</code> set the log level for this class as INFO
 */
case class SimulationDirection (workdir: String, verbose: Boolean = false) extends SimulationGridlab(workdir, verbose)
{
    if (verbose)
        LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
    override val log: Logger = LoggerFactory.getLogger(getClass)

    def write_glm (trafo: SimulationTrafoKreis, workdir: String): Unit =
    {
        val filename = s"${trafo.directory}${trafo.transformer.transformer_name}.glm"
        log.info(s"generating $filename")
        val generator = SimulationDirectionGenerator(one_phase = true, date_format = glm_date_format, trafo)
        val text = generator.make_glm()
        val file = new File(s"$workdir$filename")
        val _ = file.getParentFile.mkdirs
        using(new PrintWriter(file, "UTF-8"))
        {
            writer =>
                writer.write(text)
        }
    }

    def read_voltage_dump_csv (
        workdir: String,
        file: String,
        time: Long,
        units: String): Array[ThreePhaseComplexDataElement] =
    {
        val name = new File(workdir + file)
        if (!name.exists)
        {
            log.error(s"voltage dump file ${name.getCanonicalPath} does not exist")
            Array()
        }
        else
        {
            val handle = Source.fromFile(name, "UTF-8")
            val text = handle.getLines().filter(line => (line != "") && !line.startsWith("#") && !line.startsWith("node_name"))

            val ret = text.map(
                line =>
                {
                    val fields = line.split(",")
                    ThreePhaseComplexDataElement(
                        fields(0),
                        time,
                        Complex.fromPolar(fields(1).toDouble, fields(2).toDouble),
                        Complex.fromPolar(fields(3).toDouble, fields(4).toDouble),
                        Complex.fromPolar(fields(5).toDouble, fields(6).toDouble),
                        units)
                }
            ).toArray
            handle.close

            ret
        }
    }

    def execute (trafo: SimulationTrafoKreis): Map[String, Int] =
    {
        log.info(trafo.island + " direction detection")

        write_glm(trafo, workdir)
        val ret = run_gridlabd(trafo)

        // read the voltage dump file and convert to direction
        val list = if (ret._1)
        {
            read_gridlab_results(trafo)
        }
        else
        {
            log.error(s"GridLAB-D failed for ${trafo.name}: ${ret._2}")
            List()
        }
        list.toMap
    }

    private def read_gridlab_results (trafo: SimulationTrafoKreis) =
    {
        val file = s"${trafo.directory}output_data/${trafo.name}_voltdump.csv"
        val records = read_voltage_dump_csv(workdir, file, trafo.start_time.getTimeInMillis, "V")
        val lookup = records.map(x => (x.element, x)).toMap
        val filtered_edges = trafo.edges.filter(
            _.rawedge match
            {
                case _: LineEdge => true;
                case _ => false
            })

        val edges = filtered_edges.map(edge =>
        {
            val v1 = lookup(edge.cn1)
            val v2 = lookup(edge.cn2)
            if (v1.value_a.modulus > v2.value_a.modulus) // ToDo: three phase ?
                (edge.id, +1)
            else
                (edge.id, -1)
        })

        edges ++ Seq((trafo.transformer.transformer_name, +1))
    }
}

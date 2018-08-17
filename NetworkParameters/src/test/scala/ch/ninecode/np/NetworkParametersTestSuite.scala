package ch.ninecode.np

import scala.collection.mutable
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.model.EquivalentInjection
import org.apache.spark.rdd.RDD

class NetworkParametersTestSuite
extends SparkSuite
{
    val FILE_DEPOT = "private_data/"
    val FILENAME1 = "NIS_CIM_Export_sias_current_20161220_Sample4.rdf"
    val CSVNAME1 = "KS_Leistungen.csv"
    val filename: String = FILE_DEPOT + FILENAME1
    val csv: String = FILE_DEPOT + CSVNAME1

    test ("Basic")
    {
        session: SparkSession ⇒

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new mutable.HashMap[String, String] ()
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
            options.put ("ch.ninecode.cim.make_edges", "false")
            options.put ("ch.ninecode.cim.do_join", "false")
            options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
            options.put ("ch.ninecode.cim.do_topo_islands", "false")
            val storage = StorageLevel.fromString ("MEMORY_AND_DISK_SER")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) /  1e9 + " seconds")

            // identify topological nodes
            val ntp = new CIMNetworkTopologyProcessor (session, storage, true, true, true)
            val ele = ntp.process (false)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            val infos = ShortCircuitInfo (session, storage)
            val equivalents = infos.getShortCircuitInfo (csv)

            val ei: RDD[EquivalentInjection] = equivalents.filter (_.id == "TRA5555_equivalent_injection").map (_.asInstanceOf[EquivalentInjection])
            assert (!ei.isEmpty, "equivalent injection for transformer TRA5555 not found")
            val TRA5555 = ei.first ()
            assert (Math.abs (TRA5555.maxP - 82.699e6) < 1.0, "TRA5555 max power")
            assert (Math.abs (Math.atan2 (TRA5555.x, TRA5555.r) * 180.0 / Math.PI - -63.34) < 1.0e-3, "TRA5555 angle")

            val equiv = System.nanoTime ()
            println ("equivalents: " + (equiv - topo) / 1e9 + " seconds")

            println ("total: " + (equiv - start) / 1e9 + " seconds")
    }

}

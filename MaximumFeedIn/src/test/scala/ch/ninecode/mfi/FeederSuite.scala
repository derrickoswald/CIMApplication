package ch.ninecode.mfi

import java.io.File

import ch.ninecode.model.Connector
import ch.ninecode.model.Element
import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfter


case class MyFeeders
(
    session: SparkSession
)
    extends Feeders(session)
{
    override def isFeeder (element: Element): Boolean =
    {
        element match
        {
            case c: Connector =>
                val equipment = c.ConductingEquipment
                (equipment.BaseVoltage == "BaseVoltage_400") &&
                    equipment.Equipment.EquipmentContainer.startsWith("STA")
            case _ => false
        }
    }
}

class FeederSuite extends MFITestBase with BeforeAndAfter
{
    val FILENAME = "DemoData"

    before
    {
        // unpack the zip files
        if (!new File(s"$FILE_DEPOT$FILENAME.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME.zip", FILE_DEPOT)
    }

    after
    {
        new File(s"$FILE_DEPOT$FILENAME.rdf").delete
    }

    /**
     * Test getFeeders.
     */
    test("getFeeders")
    {
        session: SparkSession =>

            val begin = System.nanoTime()

            val filename = s"$FILE_DEPOT$FILENAME.rdf"
            val opts = Map[String, String](
                "ch.ninecode.cim.do_topo_islands" -> "true",
                "ch.ninecode.cim.force_retain_switches" -> "ForceTrue",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue")
            val elements = session.read.format("ch.ninecode.cim").options(opts).load(filename)
            val count = elements.count
            val read = System.nanoTime()
            info(s"read $count elements in ${(read - begin) / 1e9} seconds")

            val feeders = MyFeeders(session).getFeeders()
            val nfeeders = feeders.collect.length
            info(s"found $nfeeders feeders in ${(System.nanoTime() - read) / 1e9} seconds")

            assert(nfeeders == 62, "feeder count")
            val bad = feeders.filter(_.control == null)
            assert(bad.count == 0, "bad feeder count")
    }
}

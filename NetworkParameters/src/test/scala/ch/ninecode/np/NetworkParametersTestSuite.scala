package ch.ninecode.np

import java.io.File

import scala.io.Source

import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import ch.ninecode.np.ShortCircuitInfo1.{main => main1}
import ch.ninecode.np.ShortCircuitInfo2.{main => main2}
import ch.ninecode.np.ShortCircuitInfo3.{main => main3}
import ch.ninecode.testutil.Unzip

class NetworkParametersTestSuite extends AnyFunSuite with BeforeAndAfter with Unzip
{
    val FILE_DEPOT = "data/"
    val CSV_FILE1 = "parameters1"
    val CSV_FILE2_1 = "parameters2"
    val CSV_FILE2_2 = "mapping2"
    val CSV_FILE3 = "parameters3"
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
     * Delete files and directories recursively.
     *
     * @param path The starting path.
     */
    def deleteRecursive (path: File): Unit =
    {
        if (path.isDirectory)
            for (subpath <- path.list)
                deleteRecursive(new File(path, subpath))
        val _ = path.delete
    }

    test("Help1")
    {
        main1(Array("--unittest", "--help"))
    }

    test("Basic 1")
    {
        val eq1: String =
            """	<cim:EquivalentInjection rdf:ID="TX0001_equivalent_injection">
		<cim:IdentifiedObject.aliasName>EquivalentInjection_1</cim:IdentifiedObject.aliasName>
		<cim:IdentifiedObject.description>equivalent generation injection at Andreasstaat from Middelbeers</cim:IdentifiedObject.description>
		<cim:IdentifiedObject.mRID>TX0001_equivalent_injection</cim:IdentifiedObject.mRID>
		<cim:IdentifiedObject.name>Andreasstaat equivalent injection</cim:IdentifiedObject.name>
		<cim:PowerSystemResource.Location rdf:resource="#TX0001_equivalent_injection_location"/>
		<cim:Equipment.inService>true</cim:Equipment.inService>
		<cim:Equipment.normallyInService>true</cim:Equipment.normallyInService>
		<cim:Equipment.EquipmentContainer rdf:resource="#STA0001"/>
		<cim:ConductingEquipment.BaseVoltage rdf:resource="#BaseVoltage_20000"/>
		<cim:EquivalentInjection.maxP>7.134224204852414E7</cim:EquivalentInjection.maxP>
		<cim:EquivalentInjection.maxQ>5.4426976661300905E7</cim:EquivalentInjection.maxQ>
		<cim:EquivalentInjection.minP>7.134224204852414E7</cim:EquivalentInjection.minP>
		<cim:EquivalentInjection.minQ>5.4426976661300905E7</cim:EquivalentInjection.minQ>
		<cim:EquivalentInjection.p>0.0</cim:EquivalentInjection.p>
		<cim:EquivalentInjection.q>0.0</cim:EquivalentInjection.q>
		<cim:EquivalentInjection.r>3.544070642125705</cim:EquivalentInjection.r>
		<cim:EquivalentInjection.r0>0.0</cim:EquivalentInjection.r0>
		<cim:EquivalentInjection.r2>3.544070642125705</cim:EquivalentInjection.r2>
		<cim:EquivalentInjection.regulationCapability>false</cim:EquivalentInjection.regulationCapability>
		<cim:EquivalentInjection.regulationStatus>true</cim:EquivalentInjection.regulationStatus>
		<cim:EquivalentInjection.regulationTarget>0.0</cim:EquivalentInjection.regulationTarget>
		<cim:EquivalentInjection.x>2.703770509395812</cim:EquivalentInjection.x>
		<cim:EquivalentInjection.x0>0.0</cim:EquivalentInjection.x0>
		<cim:EquivalentInjection.x2>2.703770509395812</cim:EquivalentInjection.x2>
	</cim:EquivalentInjection>"""

        val eq2 =
            """	<cim:EquivalentInjection rdf:ID="TX0002_equivalent_injection">
		<cim:IdentifiedObject.aliasName>EquivalentInjection_2</cim:IdentifiedObject.aliasName>
		<cim:IdentifiedObject.description>equivalent generation injection at Driehoek from Middelbeers</cim:IdentifiedObject.description>
		<cim:IdentifiedObject.mRID>TX0002_equivalent_injection</cim:IdentifiedObject.mRID>
		<cim:IdentifiedObject.name>Driehoek equivalent injection</cim:IdentifiedObject.name>
		<cim:PowerSystemResource.Location rdf:resource="#TX0002_equivalent_injection_location"/>
		<cim:Equipment.inService>true</cim:Equipment.inService>
		<cim:Equipment.normallyInService>true</cim:Equipment.normallyInService>
		<cim:Equipment.EquipmentContainer rdf:resource="#STA0003"/>
		<cim:ConductingEquipment.BaseVoltage rdf:resource="#BaseVoltage_20000"/>
		<cim:EquivalentInjection.maxP>6.761002757807562E7</cim:EquivalentInjection.maxP>
		<cim:EquivalentInjection.maxQ>4.951931134306954E7</cim:EquivalentInjection.maxQ>
		<cim:EquivalentInjection.minP>6.761002757807562E7</cim:EquivalentInjection.minP>
		<cim:EquivalentInjection.minQ>4.951931134306954E7</cim:EquivalentInjection.minQ>
		<cim:EquivalentInjection.p>0.0</cim:EquivalentInjection.p>
		<cim:EquivalentInjection.q>0.0</cim:EquivalentInjection.q>
		<cim:EquivalentInjection.r>3.850625154659208</cim:EquivalentInjection.r>
		<cim:EquivalentInjection.r0>0.0</cim:EquivalentInjection.r0>
		<cim:EquivalentInjection.r2>3.850625154659208</cim:EquivalentInjection.r2>
		<cim:EquivalentInjection.regulationCapability>false</cim:EquivalentInjection.regulationCapability>
		<cim:EquivalentInjection.regulationStatus>true</cim:EquivalentInjection.regulationStatus>
		<cim:EquivalentInjection.regulationTarget>0.0</cim:EquivalentInjection.regulationTarget>
		<cim:EquivalentInjection.x>2.820296230153556</cim:EquivalentInjection.x>
		<cim:EquivalentInjection.x0>0.0</cim:EquivalentInjection.x0>
		<cim:EquivalentInjection.x2>2.820296230153556</cim:EquivalentInjection.x2>
	</cim:EquivalentInjection>"""

        val export_file = s"target/$FILENAME.1.rdf"
        main1(Array("--unittest", "--verbose", "--master", "local[*]",
            "--csv", s"$FILE_DEPOT$CSV_FILE1.csv",
            "--export", export_file,
            s"$FILE_DEPOT$FILENAME.rdf"))

        val output_file = s"$FILE_DEPOT$CSV_FILE1.rdf"
        val output = new File(output_file)
        assert(output.exists, "equivalent injection output")
        val source = Source.fromFile(output, "UTF-8")
        val contents = source.mkString
        source.close
        assert(contents.contains(eq1))
        assert(contents.contains(eq2))
        deleteRecursive(output)

        val export = new File(export_file)
        assert(export.exists, "exported composite file")
    }

    test("Basic 2")
    {
        val eq1 =
            """	<cim:EquivalentInjection rdf:ID="TX0001_equivalent_injection">
		<cim:IdentifiedObject.aliasName>EquivalentInjection_TX0001</cim:IdentifiedObject.aliasName>
		<cim:IdentifiedObject.description>equivalent generation injection at Andreasstaat - Oirschot (North Brabant)</cim:IdentifiedObject.description>
		<cim:IdentifiedObject.mRID>TX0001_equivalent_injection</cim:IdentifiedObject.mRID>
		<cim:IdentifiedObject.name>Andreasstaat equivalent injection</cim:IdentifiedObject.name>
		<cim:PowerSystemResource.Location rdf:resource="#TX0001_equivalent_injection_location"/>
		<cim:Equipment.inService>true</cim:Equipment.inService>
		<cim:Equipment.normallyInService>true</cim:Equipment.normallyInService>
		<cim:Equipment.EquipmentContainer rdf:resource="#STA0001"/>
		<cim:ConductingEquipment.BaseVoltage rdf:resource="#BaseVoltage_20000"/>
		<cim:EquivalentInjection.maxP>1.959385833285108E7</cim:EquivalentInjection.maxP>
		<cim:EquivalentInjection.maxQ>5.156278509890948E8</cim:EquivalentInjection.maxQ>
		<cim:EquivalentInjection.minP>9004098.621883266</cim:EquivalentInjection.minP>
		<cim:EquivalentInjection.minQ>2.728514727979444E8</cim:EquivalentInjection.minQ>
		<cim:EquivalentInjection.p>0.0</cim:EquivalentInjection.p>
		<cim:EquivalentInjection.q>0.0</cim:EquivalentInjection.q>
		<cim:EquivalentInjection.r>0.029436119122725615</cim:EquivalentInjection.r>
		<cim:EquivalentInjection.r0>4.7466930199509815E-17</cim:EquivalentInjection.r0>
		<cim:EquivalentInjection.r2>0.029436119122725615</cim:EquivalentInjection.r2>
		<cim:EquivalentInjection.regulationCapability>false</cim:EquivalentInjection.regulationCapability>
		<cim:EquivalentInjection.regulationStatus>true</cim:EquivalentInjection.regulationStatus>
		<cim:EquivalentInjection.regulationTarget>0.0</cim:EquivalentInjection.regulationTarget>
		<cim:EquivalentInjection.x>0.7746347139431147</cim:EquivalentInjection.x>
		<cim:EquivalentInjection.x0>-0.7751937984496124</cim:EquivalentInjection.x0>
		<cim:EquivalentInjection.x2>0.7746347139431147</cim:EquivalentInjection.x2>
	</cim:EquivalentInjection>"""

        val eq2 =
            """	<cim:EquivalentInjection rdf:ID="TX0002_equivalent_injection">
		<cim:IdentifiedObject.aliasName>EquivalentInjection_TX0002</cim:IdentifiedObject.aliasName>
		<cim:IdentifiedObject.description>equivalent generation injection at Driehoek - Oirschot (North Brabant)</cim:IdentifiedObject.description>
		<cim:IdentifiedObject.mRID>TX0002_equivalent_injection</cim:IdentifiedObject.mRID>
		<cim:IdentifiedObject.name>Driehoek equivalent injection</cim:IdentifiedObject.name>
		<cim:PowerSystemResource.Location rdf:resource="#TX0002_equivalent_injection_location"/>
		<cim:Equipment.inService>true</cim:Equipment.inService>
		<cim:Equipment.normallyInService>true</cim:Equipment.normallyInService>
		<cim:Equipment.EquipmentContainer rdf:resource="#STA0003"/>
		<cim:ConductingEquipment.BaseVoltage rdf:resource="#BaseVoltage_20000"/>
		<cim:EquivalentInjection.maxP>1.959385833285108E7</cim:EquivalentInjection.maxP>
		<cim:EquivalentInjection.maxQ>5.156278509890948E8</cim:EquivalentInjection.maxQ>
		<cim:EquivalentInjection.minP>9004098.621883266</cim:EquivalentInjection.minP>
		<cim:EquivalentInjection.minQ>2.728514727979444E8</cim:EquivalentInjection.minQ>
		<cim:EquivalentInjection.p>0.0</cim:EquivalentInjection.p>
		<cim:EquivalentInjection.q>0.0</cim:EquivalentInjection.q>
		<cim:EquivalentInjection.r>0.029436119122725615</cim:EquivalentInjection.r>
		<cim:EquivalentInjection.r0>4.7466930199509815E-17</cim:EquivalentInjection.r0>
		<cim:EquivalentInjection.r2>0.029436119122725615</cim:EquivalentInjection.r2>
		<cim:EquivalentInjection.regulationCapability>false</cim:EquivalentInjection.regulationCapability>
		<cim:EquivalentInjection.regulationStatus>true</cim:EquivalentInjection.regulationStatus>
		<cim:EquivalentInjection.regulationTarget>0.0</cim:EquivalentInjection.regulationTarget>
		<cim:EquivalentInjection.x>0.7746347139431147</cim:EquivalentInjection.x>
		<cim:EquivalentInjection.x0>-0.7751937984496124</cim:EquivalentInjection.x0>
		<cim:EquivalentInjection.x2>0.7746347139431147</cim:EquivalentInjection.x2>
	</cim:EquivalentInjection>"""

        val export_file = s"target/$FILENAME.2.rdf"
        main2(Array("--unittest", "--verbose", "--master", "local[*]",
            "--csv1", s"$FILE_DEPOT$CSV_FILE2_1.csv",
            "--csv2", s"$FILE_DEPOT$CSV_FILE2_2.csv",
            "--export", export_file,
            s"$FILE_DEPOT$FILENAME.rdf"))

        val output_file = s"$FILE_DEPOT$CSV_FILE2_1.rdf"
        val output = new File(output_file)
        assert(output.exists, "equivalent injection output")
        val source = Source.fromFile(output, "UTF-8")
        val contents = source.mkString
        source.close
        assert(contents.contains(eq1))
        assert(contents.contains(eq2))
        deleteRecursive(output)

        val export = new File(export_file)
        assert(export.exists, "exported composite file")
    }

    test("Basic 3")
    {
        val eq1 =
            """	<cim:EquivalentInjection rdf:ID="TX0001_equivalent_injection">
		<cim:IdentifiedObject.aliasName>EquivalentInjection_TX0001</cim:IdentifiedObject.aliasName>
		<cim:IdentifiedObject.description>equivalent generation injection at TX0001 primary</cim:IdentifiedObject.description>
		<cim:IdentifiedObject.mRID>TX0001_equivalent_injection</cim:IdentifiedObject.mRID>
		<cim:IdentifiedObject.name>TX0001 equivalent injection</cim:IdentifiedObject.name>
		<cim:PowerSystemResource.Location rdf:resource="#TX0001_equivalent_injection_location"/>
		<cim:Equipment.inService>true</cim:Equipment.inService>
		<cim:Equipment.normallyInService>true</cim:Equipment.normallyInService>
		<cim:Equipment.EquipmentContainer rdf:resource="#STA0001"/>
		<cim:ConductingEquipment.BaseVoltage rdf:resource="#BaseVoltage_20000"/>
		<cim:EquivalentInjection.maxP>3980148.760839957</cim:EquivalentInjection.maxP>
		<cim:EquivalentInjection.maxQ>3.980148760839956E7</cim:EquivalentInjection.maxQ>
		<cim:EquivalentInjection.minP>3980148.760839957</cim:EquivalentInjection.minP>
		<cim:EquivalentInjection.minQ>3.980148760839956E7</cim:EquivalentInjection.minQ>
		<cim:EquivalentInjection.p>0.0</cim:EquivalentInjection.p>
		<cim:EquivalentInjection.q>0.0</cim:EquivalentInjection.q>
		<cim:EquivalentInjection.r>1.0454109479643698</cim:EquivalentInjection.r>
		<cim:EquivalentInjection.r0>0.0</cim:EquivalentInjection.r0>
		<cim:EquivalentInjection.r2>1.0454109479643698</cim:EquivalentInjection.r2>
		<cim:EquivalentInjection.regulationCapability>false</cim:EquivalentInjection.regulationCapability>
		<cim:EquivalentInjection.regulationStatus>true</cim:EquivalentInjection.regulationStatus>
		<cim:EquivalentInjection.regulationTarget>0.0</cim:EquivalentInjection.regulationTarget>
		<cim:EquivalentInjection.x>10.454109479643698</cim:EquivalentInjection.x>
		<cim:EquivalentInjection.x0>0.0</cim:EquivalentInjection.x0>
		<cim:EquivalentInjection.x2>10.454109479643698</cim:EquivalentInjection.x2>
	</cim:EquivalentInjection>"""

        val eq2 =
            """	<cim:EquivalentInjection rdf:ID="TX0002_equivalent_injection">
		<cim:IdentifiedObject.aliasName>EquivalentInjection_TX0002</cim:IdentifiedObject.aliasName>
		<cim:IdentifiedObject.description>equivalent generation injection at TX0002 primary</cim:IdentifiedObject.description>
		<cim:IdentifiedObject.mRID>TX0002_equivalent_injection</cim:IdentifiedObject.mRID>
		<cim:IdentifiedObject.name>TX0002 equivalent injection</cim:IdentifiedObject.name>
		<cim:PowerSystemResource.Location rdf:resource="#TX0002_equivalent_injection_location"/>
		<cim:Equipment.inService>true</cim:Equipment.inService>
		<cim:Equipment.normallyInService>true</cim:Equipment.normallyInService>
		<cim:Equipment.EquipmentContainer rdf:resource="#STA0003"/>
		<cim:ConductingEquipment.BaseVoltage rdf:resource="#BaseVoltage_20000"/>
		<cim:EquivalentInjection.maxP>3980148.760839957</cim:EquivalentInjection.maxP>
		<cim:EquivalentInjection.maxQ>3.980148760839956E7</cim:EquivalentInjection.maxQ>
		<cim:EquivalentInjection.minP>3980148.760839957</cim:EquivalentInjection.minP>
		<cim:EquivalentInjection.minQ>3.980148760839956E7</cim:EquivalentInjection.minQ>
		<cim:EquivalentInjection.p>0.0</cim:EquivalentInjection.p>
		<cim:EquivalentInjection.q>0.0</cim:EquivalentInjection.q>
		<cim:EquivalentInjection.r>1.0454109479643698</cim:EquivalentInjection.r>
		<cim:EquivalentInjection.r0>0.0</cim:EquivalentInjection.r0>
		<cim:EquivalentInjection.r2>1.0454109479643698</cim:EquivalentInjection.r2>
		<cim:EquivalentInjection.regulationCapability>false</cim:EquivalentInjection.regulationCapability>
		<cim:EquivalentInjection.regulationStatus>true</cim:EquivalentInjection.regulationStatus>
		<cim:EquivalentInjection.regulationTarget>0.0</cim:EquivalentInjection.regulationTarget>
		<cim:EquivalentInjection.x>10.454109479643698</cim:EquivalentInjection.x>
		<cim:EquivalentInjection.x0>0.0</cim:EquivalentInjection.x0>
		<cim:EquivalentInjection.x2>10.454109479643698</cim:EquivalentInjection.x2>
	</cim:EquivalentInjection>"""

        val export_file = s"target/$FILENAME.3.rdf"
        main3(Array("--unittest", "--verbose", "--master", "local[*]",
            "--csv", s"$FILE_DEPOT$CSV_FILE3.csv",
            "--export", export_file,
            s"$FILE_DEPOT$FILENAME.rdf"))

        val output_file = s"$FILE_DEPOT$CSV_FILE3.rdf"
        val output = new File(output_file)
        assert(output.exists, "equivalent injection output")
        val source = Source.fromFile(output, "UTF-8")
        val contents = source.mkString
        source.close
        assert(contents.contains(eq1))
        assert(contents.contains(eq2))
        deleteRecursive(output)

        val export = new File(export_file)
        assert(export.exists, "exported composite file")
    }
}

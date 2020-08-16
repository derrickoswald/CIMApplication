package ch.ninecode.on

import java.io.File

import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfter

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.net.Net
import ch.ninecode.on.OneOfN.main
import ch.ninecode.testutil.TestUtil
import ch.ninecode.util.Util

class OneOfNMainSuite extends TestUtil with BeforeAndAfter
{
    override val classesToRegister: Array[Class[_]] = Array.concat (
        CIMClasses.list,
        GridLABD.classes,
        Net.classes,
        Util.classes)
    val FILE_DEPOT = "data/"
    val FILENAME = "DemoData_medium_voltage_network"

    before
    {
        // unpack the zip files
        if (!new File (s"$FILE_DEPOT$FILENAME.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME.zip", FILE_DEPOT)
    }

    after
    {
        new File (s"$FILE_DEPOT$FILENAME.rdf").delete
    }

    test ("Help")
    {
        _: SparkSession =>
            main (Array ("--unittest", "--help"))
    }

    test ("Version")
    {
        _: SparkSession =>
            main (Array ("--unittest", "--version"))
    }

    test ("Basic")
    {
        session: SparkSession =>
            main (Array (
                "--unittest",
                "--verbose",
                "--log", "INFO",
                "--workdir", "target/",
                s"$FILE_DEPOT$FILENAME.rdf"
            ))
    }
}

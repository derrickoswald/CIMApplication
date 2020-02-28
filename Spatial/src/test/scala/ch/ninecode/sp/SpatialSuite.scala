package ch.ninecode.sp

import java.io.File

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.testutil.TestUtil

import org.scalatest._

class SpatialSuite extends TestUtil with BeforeAndAfter
{
    override val classesToRegister: Array[Array[Class[_]]] = Array (CIMClasses.list)
    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData"

    def readFile (context: SQLContext, filename: String): DataFrame =
    {
        val files = filename.split (",")
        val options = new java.util.HashMap[String, String]().asInstanceOf [java.util.Map[String, String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        context.read.format ("ch.ninecode.cim").options (options).load (files: _*)
    }

    before
    {
        // unpack the zip files
        if (!new File (s"$FILE_DEPOT$FILENAME1.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME1.zip", FILE_DEPOT)
    }

    after
    {
        new File (FILE_DEPOT + FILENAME1).delete
    }

    test ("Basic")
    {
        session: SparkSession ⇒

            val start = System.nanoTime ()

            val filename = s"$FILE_DEPOT$FILENAME1.rdf"
            val elements = readFile (session.sqlContext, filename)
            println (s"${elements.count} elements")
            val read = System.nanoTime ()

            val spatial = new ch.ninecode.sp.SpatialOperations (session)

            val results1 = spatial.nearest (SpatialOperationParameters (lon = 5.271720, lat = 51.47120))
            val process1 = System.nanoTime ()

            val results2 = spatial.nearest (SpatialOperationParameters (lon = 5.272310, lat = 51.47024))
            val process2 = System.nanoTime ()

            results1.show
            val array1 = results1.collect ()
            var text = array1 (0).toString ()
            assert (text.contains ("USR0019"))

            results2.show
            val array2 = results2.collect ()
            text = array2 (0).toString ()
            assert (text.contains ("USR0023"))

            println ("read : " + (read - start) / 1e9 + " seconds")
            println (s"process first location: ${(process1 - read) / 1e9} seconds")
            println (s"process second location: ${(process2 - process1) / 1e9} seconds")
    }
}

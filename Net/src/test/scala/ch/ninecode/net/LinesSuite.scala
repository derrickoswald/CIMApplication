package ch.ninecode.net

import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.testutil.TestUtil

class LinesSuite extends TestUtil
{
    override val classesToRegister: Array[Array[Class[_]]] = Array(CIMClasses.list)
    val FILENAME = "hdfs://sandbox:8020/ckw/TRA9702.rdf"
    test ("basic")
    {
        session: SparkSession =>
            readCIMElements (session, FILENAME, Map[String, String]())
            val lines = Lines (session)
            time ("execution time: %s seconds")
            {
                val doit = lines.getLines ()
                doit.count
            }
    }
}
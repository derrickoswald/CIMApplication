package ch.ninecode.np

import ch.ninecode.cim.CIMClasses
import ch.ninecode.util.TestUtil
import org.apache.spark.sql.SparkSession

class NetworkParametersTestSuite extends TestUtil
{
    override val classesToRegister: Array[Array[Class[_]]] = Array (CIMClasses.list)

    test ("Basic")
    {
        session: SparkSession ⇒
            println ("placeholder")
    }
}

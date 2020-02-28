package ch.ninecode.np

import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.testutil.TestUtil

class NetworkParametersTestSuite extends TestUtil
{
    override val classesToRegister: Array[Array[Class[_]]] = Array (CIMClasses.list)

    test ("Basic")
    {
        session: SparkSession ⇒
            println ("placeholder")
    }
}

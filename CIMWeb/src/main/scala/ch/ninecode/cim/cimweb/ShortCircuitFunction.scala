package ch.ninecode.cim.cimweb

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.sc.ShortCircuitOptions
import ch.ninecode.sc.ShortCircuit
import ch.ninecode.util.Complex

case class ShortCircuitFunction (options: ShortCircuitOptions) extends CIMWebFunction
{
    jars = Array (
        jarForObject (this),
        jarForObject (options),               // ShortCircuit.jar
        jarForObject (new GLMGenerator ()),   // GridLAB-D.jar
        jarForObject (Complex (0.0, 0.0)))    // Util.jar

    override def getReturnType: Return = Return.Dataset

    override def executeResultSet (spark: SparkSession): Dataset[Row] =
    {
        val sc = ShortCircuit (spark, StorageLevel.MEMORY_AND_DISK_SER, options)
        val results = sc.run ()
        val pseudoresults = results.map (_.toPseudo)
        spark.sqlContext.createDataFrame (pseudoresults)
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" (")
        sb.append (options.toString)
        sb.append (")")
        sb.toString
    }
}

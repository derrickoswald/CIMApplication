package ch.ninecode.cim.cimweb

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.sc.ShortCircuitOptions
import ch.ninecode.sc.ShortCircuit
import ch.ninecode.cim.connector.CIMFunction.Return

case class ShortCircuitFunction (options: ShortCircuitOptions) extends CIMWebFunction
{
    jars = Array (jarForObject (this), jarForObject (options))

    override def getReturnType: Return = Return.Dataset

    override def executeResultSet (spark: SparkSession): Dataset[Row] =
    {
        val opt = options.copy (csv_file = uri + "/" + options.csv_file)
        val sc = new ShortCircuit (spark, StorageLevel.MEMORY_AND_DISK_SER, opt)
        spark.sqlContext.createDataFrame (sc.run ())
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

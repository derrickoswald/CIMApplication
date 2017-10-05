package ch.ninecode.cim.cimweb

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.connector.CIMFunction.Return

case class QueryFunction (sql: String) extends CIMWebFunction
{
    override def getReturnType: Return = Return.Dataset

    override def executeResultSet (spark: SparkSession): Dataset[Row] =
    {
        spark.sql (sql)
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" (sql=")
        sb.append (sql)
        sb.append (")")
        sb.toString
    }
}
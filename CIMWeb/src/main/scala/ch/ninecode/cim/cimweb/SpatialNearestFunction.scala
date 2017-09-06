package ch.ninecode.cim.cimweb

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.sp.SpatialOperations
import ch.ninecode.sp.SpatialOperationParameters

class SpatialNearestFunction extends CIMWebFunction
{
    protected var _Parameters = new SpatialOperationParameters ("EnergyConsumer", 7.281558, 47.124142, 5)

    def setSpatialOperationParameters (parameters: SpatialOperationParameters): Unit =
    {
        _Parameters = parameters
        _Jars = Array[String](jarForObject (parameters))
    }

    override def getReturnType: Return = Return.Dataset

    override def getMimeType = null

    override def execute (spark: SparkSession): Dataset[Row] =
    {
        val ops = new SpatialOperations
        ops.nearest (spark, _Parameters)
    }

    override def execute (spark: SparkSession, mime_type: String) =
        throw new UnsupportedOperationException ("execute called on wrong method signature")

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" is SpatialOperations.nearest (")
        sb.append (_Parameters.toString)
        sb.append (")")
        sb.toString
    }
}

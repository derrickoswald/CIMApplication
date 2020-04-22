package ch.ninecode.cim.cimweb

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.sp.SpatialOperations
import ch.ninecode.sp.SpatialOperationParameters

case class SpatialNearestFunction (var parameters: SpatialOperationParameters) extends CIMWebFunction
{
    jars = Array (jarForObject (parameters))

    def setSpatialOperationParameters (newparameters: SpatialOperationParameters): Unit =
    {
        parameters = newparameters
        jars = Array[String](jarForObject (parameters))
    }

    override def getReturnType: Return = Return.Dataset

    override def executeResultSet (spark: SparkSession): Dataset[Row] =
    {
        val ops = new SpatialOperations (spark)
        ops.nearest (parameters)
    }

    override def toString: String = s"${super.toString} is SpatialOperations.nearest (${parameters.toString})"
}

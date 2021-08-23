package ch.ninecode.sim

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

trait SimulationCleanup
{
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def vanishRDD (rdd: RDD[_]): Unit =
    {
        val _ = rdd
            .unpersist(false)
            .setName(null)
    }

    def vanishRDDs (rddList: List[RDD[_]]): Unit =
    {
        rddList.foreach(vanishRDD)
    }

    def cleanRDDs (session: SparkSession): Unit =
    {
        session.sparkContext.getPersistentRDDs.foreach(x => vanishRDD(x._2))
    }
}

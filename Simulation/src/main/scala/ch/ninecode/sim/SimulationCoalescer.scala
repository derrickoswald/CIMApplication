package ch.ninecode.sim

import org.apache.spark.Partition
import org.apache.spark.rdd.PartitionCoalescer
import org.apache.spark.rdd.PartitionGroup
import org.apache.spark.rdd.RDD
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Coalesce partitions (of SimulationTrafoKreis) into partitions placed on executors.
 *
 * This is the second phase of the distribution of tasks across the cluster:
 *
 *  - repartition so that each trafokreis is in it's own partition
 *  - repartition to coalesce those partitions into placed partitions on executor instances
 *
 * @param executors The executors to place the partitions on: format "executor_&lt;host&gt;_&lt;executorid&gt;"
 */
case class SimulationCoalescer (executors: Array[String]) extends PartitionCoalescer with Serializable
{
    /**
     * Coalesce the partitions of the given RDD.
     *
     * @param maxPartitions the maximum number of partitions to have after coalescing
     * @param parent        the parent RDD whose partitions to coalesce
     * @return an array of PartitionGroups, where each element is itself an array of Partitions and represents a partition after coalescing is performed
     */
    override def coalesce (maxPartitions: Int, parent: RDD[_]): Array[PartitionGroup] =
    {
        org.apache.log4j.LogManager.getLogger (getClass).setLevel (org.apache.log4j.Level.INFO)
        val log: Logger = LoggerFactory.getLogger (getClass)

        log.info ("""coalescing '%s' of %d partitions to %d partitions on executors [%s]""".format (parent.name, parent.getNumPartitions, maxPartitions, executors.mkString (", ")))
        val ret: Array[PartitionGroup] = executors.map (executor => new PartitionGroup (Some (executor))).take (maxPartitions)
        val partitions: Array[Partition] = parent.partitions
        var index: Int = 0
        partitions.foreach (
            partition =>
            {
                ret (index % ret.length).partitions.append (partition)
                index = index + 1
            })
        ret.foreach (
            group =>
                log.info ("""group %s of %s partitions at %s [%s]""".format (group.prefLoc.get, group.numPartitions, if (1 <= group.partitions.size) "index" else "indicies", group.partitions.map (_.index.toString).mkString (",")))
        )
        ret
    }
}

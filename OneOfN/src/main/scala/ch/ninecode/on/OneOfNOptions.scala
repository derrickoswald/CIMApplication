package ch.ninecode.on

import scala.collection.mutable.HashMap

import org.apache.spark.storage.StorageLevel

/**
 * Options for the One-Of-N program.
 *
 * @param verbose            flag to output progress and interesting values
 * @param cim_reader_options settings for the CIMReader
 * @param three              if <code>true</code> generate three phase GridLAB-D .glm files, else single phase .glm files
 * @param base_temperature   temperature of elements in the input CIM file (°C)
 * @param temperature        temperature at which to generate the GridLAB-D .glm files (°C)
 * @param storage            storage level for RDD serialization
 * @param workdir            shared directory (HDFS or NFS share) for intermediate results
 * @param files              the list of files to process
 */
case class OneOfNOptions
(
    verbose: Boolean = false,
    cim_reader_options: Iterable[(String, String)] = new HashMap[String, String](),
    three: Boolean = false,
    base_temperature: Double = 20.0,
    temperature: Double = 60.0,
    storage: StorageLevel = StorageLevel.MEMORY_AND_DISK_SER,
    workdir: String = "",
    files: Seq[String] = Seq ()
)

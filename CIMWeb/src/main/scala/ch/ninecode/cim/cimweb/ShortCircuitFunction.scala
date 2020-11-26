package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.net.LoadFlowNode
import ch.ninecode.sc.Database
import ch.ninecode.sc.ScCassandra
import ch.ninecode.sc.ShortCircuitOptions
import ch.ninecode.sc.ShortCircuitOutputType
import ch.ninecode.util.Complex
import ch.ninecode.util.SparkOptions

case class ShortCircuitFunction (options: ShortCircuitOptions) extends CIMWebFunction
{
    jars = Array(
        jarForObject(this),
        jarForObject(options), // ShortCircuit.jar
        jarForObject(new GLMGenerator()), // GridLAB-D.jar
        jarForObject(new LoadFlowNode("", 0.0)), // Net.jar
        jarForObject(Complex(0.0, 0.0)), // Util.jar
        jarForClass(classOf[javax.json.JsonStructure]), // javaee-api <JSON implementation>.jar
        jarForObject(com.datastax.oss.driver.api.core.ConsistencyLevel.ANY), // spark-cassandra-connector.jar
        jarForObject(com.datastax.oss.driver.shaded.guava.common.collect.ImmutableListMultimap.of[String,String]()), // com/datastax/oss/driver/shaded/guava/common/collect/
        jarForObject(new com.datastax.oss.protocol.internal.util.Flags ()), // com.datastax.oss.protocol.internal.util.collection.NullAllowingImmutableMap
        jarForClass (classOf[org.reactivestreams.Publisher[_]]), // org/reactivestreams/Publisher
        jarForObject(com.typesafe.config.ConfigMemorySize.ofBytes(0))) // com/typesafe/config/ConfigMergeable

    override def getReturnType: Return = Return.JSON

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // since these are set when the Spark instance is created, they cannot affect the ShortCircuit run, but include them anyway
        val host = spark.sparkContext.getConf.get("spark.cassandra.connection.host", "localhost")
        val port = spark.sparkContext.getConf.get("spark.cassandra.connection.port", "9042")
        val opt = options.copy (
            spark_options = SparkOptions(
                master = spark.sparkContext.master,
                options = options.spark_options.options + (
                    "spark.cassandra.connection.host" -> host,
                    "spark.cassandra.connection.port" -> port)),
            host = host,
            port = port.toInt)
        val group = if ("" == opt.id) "ShortCircuit" else opt.id
        val sc = ch.ninecode.sc.ShortCircuit(spark, StorageLevel.MEMORY_AND_DISK_SER, opt)
        spark.sparkContext.setJobGroup(group, "short circuit calculation")
        val results = sc.run()
        spark.sparkContext.setJobDescription("short circuit result storage")
        val (id: String, run: Int) = opt.output match
        {
            case ShortCircuitOutputType.SQLite =>
                val db = Database(opt)
                val id = db.store(results)
                (id.toString, 0)
            case ShortCircuitOutputType.Cassandra =>
                val cassandra = ScCassandra(spark, opt.copy(id = java.util.UUID.randomUUID.toString))
                cassandra.store(results)
        }
        spark.sparkContext.setJobGroup(null, null)
        Json.createObjectBuilder
            .add ("id", id)
            .add ("run", run)
            .build
    }

    override def toString: String = s"${super.toString} (${options.toString})"
}

package ch.ninecode.util

import org.apache.log4j.Level
import org.json4s.Formats
import org.json4s.JsonAST.JString

/**
 * Options for Spark context.
 *
 * @param master     Spark master
 * @param options    Spark options
 * @param log        logging level
 * @param jars       set of jar names to pass to executors
 * @param kryo       set of classes to register with Kryo
 * @param checkpoint checkpoint directory
 */
case class SparkOptions (
    master: String = "local[*]",
    options: Map[String, String] = Map(
        "spark.graphx.pregel.checkpointInterval" -> "8",
        "spark.serializer" -> "org.apache.spark.serializer.KryoSerializer",
        "spark.kryo.registrator" -> "ch.ninecode.cim.CIMRegistrator",
        "spark.ui.showConsoleProgress" -> "false",
        "spark.sql.debug.maxToStringFields" -> "250",
        "spark.sql.catalog.casscatalog" -> "com.datastax.spark.connector.datasource.CassandraCatalog"
    ),
    log: Level = Level.OFF,
    jars: Array[String] = Array(),
    kryo: Array[Class[_]] = Array(),
    checkpoint: String = ""
)
{
    def toJSON: String = SparkOptions.toJSON(this)
}
object SparkOptions extends JSON[SparkOptions]
{

    def schemaResourceName: String = "SparkOptionsSchema.json"

    class LevelSerializer extends JSONCustomSerializer[Level](
        (format: Formats) =>
            (
                {
                    case JString(s) => Level.toLevel(s)
                },
                {
                    case x: Level => JString(x.toString)
                }
            )
    )

    class ClassSerializer extends JSONCustomSerializer[Class[_]](
        (format: Formats) =>
            (
                {
                    case JString(s) if ClassSerializer.test(s) => Class.forName (s)
                },
                {
                    case x: Class[_] => JString(x.getName)
                }
            )
    )
    {
        override def errors: String = ClassSerializer.errors
    }
    object ClassSerializer
    {
        var errors: String = ""
        def test(s: String): Boolean = try { Class.forName (s) != null } catch { case t: Throwable => errors = t.toString; false }
    }

    lazy val Custom = List(new LevelSerializer, new ClassSerializer)

    override def fromJSON (json: String, serializers: Seq[JSONCustomSerializer[_]])
        (implicit m: Manifest[SparkOptions]): Either[String, SparkOptions] =
    {
        ClassSerializer.errors = ""
        super.fromJSON(json, serializers ++ Custom)
    }

    override def toJSON (options: SparkOptions, serializers: Seq[JSONCustomSerializer[_]] = List()): String =
    {
        super.toJSON (options, serializers ++ Custom)
    }
}
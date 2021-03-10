package ch.ninecode.util

/**
 * Parser for command line operation of programs using Cassandra and Spark.
 *
 * @tparam T class type required for parsed values
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
trait CassandraOptionsParser[T <: Cassandraable with Sparkable with Mainable] extends MainOptionsParser[T]
{
    opt[String]("host")
        .valueName("Cassandra")
        .action((x, c) =>
            {
                c.cassandra_options = c.cassandra_options.copy(host = x)
                c.spark_options = c.spark_options.copy(options = c.spark_options.options + ("spark.cassandra.connection.host" -> x))
                c
            }
        )
        .text(s"Cassandra connection host (listen_address or seed in cassandra.yaml) [${getDefault.cassandra_options.host}]")

    opt[Int]("port")
        .valueName("<port_number>")
        .action((x, c) =>
            {
                c.cassandra_options = c.cassandra_options.copy(port = x)
                c.spark_options = c.spark_options.copy(options = c.spark_options.options + ("spark.cassandra.connection.port" -> x.toString))
                c
            }
        )
        .text(s"Cassandra connection port [${getDefault.cassandra_options.port}]")

    opt[String]("user")
        .valueName("<username>")
        .action((x, c) =>
        {
            c.cassandra_options = c.cassandra_options.copy(user = x)
            c.spark_options = c.spark_options.copy(options = c.spark_options.options + ("spark.cassandra.auth.username" -> x))
            c
        }
        )
        .text(s"Cassandra user to use to connect to database) [${getDefault.cassandra_options.user}]")


    opt[String]("password")
        .valueName("<password>")
        .action((x, c) =>
        {
            c.cassandra_options = c.cassandra_options.copy(password = x)
            c.spark_options = c.spark_options.copy(options = c.spark_options.options + ("spark.cassandra.auth.password" -> x))
            c
        }
        )
        .text(s"Cassandra password to use to connect to database) [${getDefault.cassandra_options.password}]")
}

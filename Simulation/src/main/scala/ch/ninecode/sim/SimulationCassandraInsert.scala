package ch.ninecode.sim

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

import scala.collection.JavaConversions._

import com.datastax.driver.core.BatchStatement
import com.datastax.driver.core.PreparedStatement
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.ResultSetFuture
import com.datastax.driver.core.Session
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.ThreePhaseComplexDataElement

case class SimulationCassandraInsert (session: Session, keyspace: String = "cimapplication", batchsize: Int = 65535)
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    val calendar: Calendar = Calendar.getInstance ()
    calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
    calendar.setTimeInMillis (0L)
    val just_date: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd")
    just_date.setCalendar (calendar)

    var statements: Map[String, PreparedStatement] = Map()

    def pack (string: String): String =
    {
        string.replace ("\n", " ").replaceAll ("[ ]+", " ")
    }

    case class Accumulator (
        name: String,
        sql: String,
        statement: PreparedStatement,
        batch: BatchStatement,
        intervals: Int,
        average: Boolean,
        var count: Int = 0,
        var value_a_re: Double = 0.0,
        var value_a_im: Double = 0.0,
        var value_b_re: Double = 0.0,
        var value_b_im: Double = 0.0,
        var value_c_re: Double = 0.0,
        var value_c_im: Double = 0.0,
        var statements: Int = 0,
        var futures: List[ResultSetFuture] = List()
        )
    {
        def reset (): Unit =
        {
            count = 0
            value_a_re = 0.0
            value_a_im = 0.0
            value_b_re = 0.0
            value_b_im = 0.0
            value_c_re = 0.0
            value_c_im = 0.0
        }

        def description: String = """%s@%s""".format (name, intervals)

        def closeIfDone (resultset: ResultSetFuture): List[ResultSetFuture] =
        {
            if (resultset.isDone)
            {
                //val r: ResultSet = resultset.getUninterruptibly
                val r: ResultSet = resultset.get
                if (!r.wasApplied)
                    log.error ("""insert was not applied""")
                val rows = r.all // hopefully this shuts down the socket
                if (!r.isFullyFetched)
                    log.error ("""insert resultset was not fully fetched""")
                List()
            }
            else
                List (resultset)
        }

        def execute (session: Session): ResultSetFuture =
        {
            val future = session.executeAsync (batch)
            batch.clear ()
            statements = 0
            future
        }

        def add (session: Session, parameters: Seq[Object]): Unit =
        {
            batch.add (statement.bind (parameters:_*))
            statements = statements + 1
            if (statements == batchsize)
                futures = futures.flatMap (closeIfDone) :+ execute (session)
        }

        def flush (session: Session): List[ResultSetFuture] =
        {
            if (0 != statements)
                futures = futures.flatMap (closeIfDone) :+ execute (session)
            futures
        }
    }

    def execute (name: String, data: Array[ThreePhaseComplexDataElement], typ: String, period: Int, simulation: String, aggregates: List[SimulationAggregate]): (Int, List[(String, List[ResultSetFuture])])=
    {
        var ret = 0
        val accumulators = aggregates.map (
            aggregate ⇒
            {
                val sql = pack (
                    """
                    | insert into %s.simulated_value
                    | (mrid, type, period, time, real_a, imag_a, real_b, imag_b, real_c, imag_c, units, simulation)
                    | values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.stripMargin.format (keyspace)) + aggregate.time_to_live
                val statement = if (statements.contains (sql))
                    statements (sql)
                else
                {
                    val statement = session.prepare (sql)
                    statements = statements + (sql → statement)
                    statement
                }
                // we can use unlogged batch because the partition key is (mrid, type, period)
                // which is the same for each accumulator if the data entries come from one recorder file
                val batch = new BatchStatement (BatchStatement.Type.UNLOGGED)
                batch.setIdempotent (true)
                Accumulator (name, sql, statement, batch, aggregate.intervals, typ != "energy")
            }
        )
        val timestamp = new Date ()
        data.foreach (
            entry ⇒
            {
                accumulators.foreach (
                    accumulator ⇒
                    {
                        accumulator.count = accumulator.count + 1
                        accumulator.value_a_re = accumulator.value_a_re + entry.value_a.re
                        accumulator.value_a_im = accumulator.value_a_im + entry.value_a.im
                        accumulator.value_b_re = accumulator.value_b_re + entry.value_b.re
                        accumulator.value_b_im = accumulator.value_b_im + entry.value_b.im
                        accumulator.value_c_re = accumulator.value_c_re + entry.value_c.re
                        accumulator.value_c_im = accumulator.value_c_im + entry.value_c.im
                        if (accumulator.count >= accumulator.intervals)
                        {
                            // Java and Cassandra timestamps are in milliseconds, but Spark is in seconds not milliseconds
                            val timepoint = entry.millis - 1000L * (period * (accumulator.intervals - 1))
                            timestamp.setTime (timepoint)
                            val n = accumulator.intervals
                            val args: List[Object] = if (accumulator.average)
                                List[Object] (
                                    entry.element,
                                    typ,
                                    new java.lang.Integer (period * accumulator.intervals * 1000),
                                    timestamp,
                                    new java.lang.Double (accumulator.value_a_re / n),
                                    new java.lang.Double (accumulator.value_a_im / n),
                                    new java.lang.Double (accumulator.value_b_re / n),
                                    new java.lang.Double (accumulator.value_b_im / n),
                                    new java.lang.Double (accumulator.value_c_re / n),
                                    new java.lang.Double (accumulator.value_c_im / n),
                                    entry.units,
                                    simulation
                                )
                            else
                                List[Object] (
                                    entry.element,
                                    typ,
                                    new java.lang.Integer (period * accumulator.intervals * 1000),
                                    timestamp,
                                    new java.lang.Double (accumulator.value_a_re),
                                    new java.lang.Double (accumulator.value_a_im),
                                    new java.lang.Double (accumulator.value_b_re),
                                    new java.lang.Double (accumulator.value_b_im),
                                    new java.lang.Double (accumulator.value_c_re),
                                    new java.lang.Double (accumulator.value_c_im),
                                    entry.units,
                                    simulation
                                )
                            accumulator.add (session, args)
                            ret = ret + 1
                            accumulator.reset ()
                        }
                    }
                )
            }
        )
        (ret, accumulators.map (accumulator ⇒ (accumulator.description, accumulator.flush (session))))
    }
}

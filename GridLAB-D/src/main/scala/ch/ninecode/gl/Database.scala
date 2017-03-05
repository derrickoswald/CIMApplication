package ch.ninecode.gl

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.util.Calendar
import org.apache.spark.rdd.RDD
import org.slf4j.LoggerFactory
import scala.collection.mutable.ArrayBuffer

object Database
{
    val log = LoggerFactory.getLogger (getClass)

    def makeSchema (connection: Connection)
    {
        val statement = connection.createStatement ()
        val resultset1 = statement.executeQuery ("select name from sqlite_master where type = 'table' and name = 'simulation'")
        val exists1 = resultset1.next ()
        resultset1.close ()
        if (!exists1)
            statement.executeUpdate ("create table simulation (id integer primary key autoincrement, description text, time text)")
        val resultset2 = statement.executeQuery ("select name from sqlite_master where type = 'table' and name = 'results'")
        val exists2 = resultset2.next ()
        resultset2.close ()
        if (!exists2)
            statement.executeUpdate ("create table results (id integer primary key autoincrement, simulation integer, trafo text, house text, maximum double, has_eea boolean, reason text, details text)")
        statement.close ()
    }

    def store (description: String, t1: Calendar) (equipment: String, results: RDD[MaxEinspeiseleistung]): Int = synchronized
    {
        // make the directory
        val file = Paths.get ("simulation/dummy")
        Files.createDirectories (file.getParent ())

        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")

        var connection: Connection = null
        try
        {
            // create a database connection
            connection = DriverManager.getConnection ("jdbc:sqlite:simulation/results.db")
            connection.setAutoCommit (false)

            // create schema
            makeSchema (connection)

            val records = results.collect ()
            if (0 != records.length)
            {
                // insert the simulation
                val now = Calendar.getInstance ()
                val insert = connection.prepareStatement ("insert into simulation (id, description, time) values (?, ?, ?)")
                insert.setNull (1, Types.INTEGER)
                insert.setString (2, description)
                insert.setTimestamp (3, new Timestamp (now.getTimeInMillis))
                insert.executeUpdate ()
                val statement = connection.createStatement ()
                val resultset = statement.executeQuery ("select last_insert_rowid() id")
                resultset.next ()
                val id = resultset.getInt ("id")
                resultset.close
                statement.close

                // insert the results
                val datainsert = connection.prepareStatement ("insert into results (id, simulation, trafo, house, maximum, reason, details) values (?, ?, ?, ?, ?, ?, ?)")
                for (i <- 0 until records.length)
                {
                    datainsert.setNull (1, Types.INTEGER)
                    datainsert.setInt (2, id)
                    datainsert.setString (3, records(i).trafo)
                    datainsert.setString (4, records(i).house)
                    records(i).max match
                    {
                        case None =>
                            datainsert.setNull (5, Types.DOUBLE)
                        case Some (kw) =>
                            datainsert.setDouble (5, kw)
                    }
                    datainsert.setString (6, records(i).reason)
                    datainsert.setString (7, records(i).details)
                    datainsert.executeUpdate ()
                }
                datainsert.close
                connection.commit

                println (equipment + " " + records.length + " records")
                return (id)
            }
            else
                return (0)
        }
        catch
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            case e: SQLException ⇒ log.error ("exception caught: " + e);
            return (-1)
        }
        finally
        {
            try
            {
                if (connection != null)
                    connection.close ()
            }
            catch
            {
                // connection close failed
                case e: SQLException ⇒ log.error ("exception caught: " + e);
            }
        }

    }

    def store_precalculation (description: String, t1: Calendar) (results: RDD[MaxPowerFeedingNodeEEA]): Int = synchronized
    {
        // make the directory
        val file = Paths.get ("simulation/dummy")
        Files.createDirectories (file.getParent ())

        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")

        var connection: Connection = null
        try
        {
            // create a database connection
            connection = DriverManager.getConnection ("jdbc:sqlite:simulation/results.db")
            connection.setAutoCommit (false)

            // create schema
            makeSchema (connection)

            // insert the simulation
            val now = Calendar.getInstance ()
            val insert = connection.prepareStatement ("insert into simulation (id, description, time) values (?, ?, ?)")
            insert.setNull (1, Types.INTEGER)
            insert.setString (2, description)
            insert.setTimestamp (3, new Timestamp (now.getTimeInMillis))
            insert.executeUpdate ()
            val statement = connection.createStatement ()
            val resultset = statement.executeQuery ("select last_insert_rowid() id")
            resultset.next ()
            val id = resultset.getInt ("id")
            resultset.close
            statement.close

            // insert the results
            val records = results.collect ()

            val datainsert = connection.prepareStatement ("insert into results (id, simulation, trafo, house, maximum, has_eea, reason, details) values (?, ?, ?, ?, ?, ?, ?, ?)")
            for (i <- 0 until records.length)
            {
                val trafo_id = records(i).source_obj.map(_.transformer.id).sortWith (_ < _).mkString ("_")
                datainsert.setNull (1, Types.INTEGER)
                datainsert.setInt (2, id)
                datainsert.setString (3, trafo_id)
                datainsert.setString (4, records(i).has_id)
                datainsert.setDouble (5, records(i).max_power_feeding)
                datainsert.setBoolean(6, records(i).has_eea)
                datainsert.setString (7, records(i).reason)
                datainsert.setString (8, records(i).details)
                datainsert.executeUpdate ()
            }
            datainsert.close
            connection.commit

            return (id)
        }
        catch
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            case e: SQLException ⇒ log.error ("exception caught: " + e);
            return (-1)
        }
        finally
        {
            try
            {
                if (connection != null)
                    connection.close ()
            }
            catch
            {
                // connection close failed
                case e: SQLException ⇒ log.error ("exception caught: " + e);
            }
        }
    }

    def fetchHouseMaximumsForTransformer (simulation: Int, transformer: String): Array[Tuple2[String,Double]] =
    {
        var ret = new ArrayBuffer[Tuple2[String,Double]] ()

        // check if the directory exists
        val file = Paths.get ("simulation/results.db")
        if (!Files.exists (file))
            log.error ("database file " + file + " does not exist")
        else
        {
            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")

            var connection: Connection = null
            try
            {
                // create a database connection
                connection = DriverManager.getConnection ("jdbc:sqlite:simulation/results.db")

                val statement = connection.prepareStatement ("select house, maximum from results where simulation = ? and trafo = ?")
                statement.setInt (1, simulation)
                statement.setString (2, transformer)
                val resultset = statement.executeQuery ()
                while (resultset.next ())
                    ret += ((resultset.getString (1), resultset.getDouble (2)))
                resultset.close ()
            }
            catch
            {
                // if the error message is "out of memory",
                // it probably means no database file is found
                case e: SQLException ⇒ log.error ("exception caught: " + e);
            }
            finally
            {
                try
                {
                    if (connection != null)
                        connection.close ()
                }
                catch
                {
                    // connection close failed
                    case e: SQLException ⇒ log.error ("exception caught: " + e);
                }
            }
        }
        ret.toArray
    }
}
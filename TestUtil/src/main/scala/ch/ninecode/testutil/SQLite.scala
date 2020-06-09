package ch.ninecode.testutil

import java.sql.DriverManager
import com.sun.rowset.CachedRowSetImpl

trait SQLite {
    def readSQLite (databasePath: String, sqlStatement: String): CachedRowSetImpl =
    {
        // load the sqlite-JDBC driver using the current class loader
        val _ = Class.forName ("org.sqlite.JDBC")
        // create a database connection
        val connection = DriverManager.getConnection (s"jdbc:sqlite:$databasePath")

        val statement = connection.createStatement ()
        val resultset = statement.executeQuery (sqlStatement)
        val crs = new CachedRowSetImpl
        crs.populate (resultset)
        resultset.close ()
        statement.close ()
        connection.close ()
        crs
    }
    def writeSQLite (databasePath: String, sqlStatement: String) =
    {
        Class.forName ("org.sqlite.JDBC")
        val connection = DriverManager.getConnection (s"jdbc:sqlite:$databasePath")
        val statement = connection.createStatement ()
        statement.close ()
        connection.close ()
    }
}
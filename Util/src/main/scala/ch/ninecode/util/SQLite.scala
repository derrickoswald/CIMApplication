package ch.ninecode.util

import java.sql.DriverManager
import com.sun.rowset.CachedRowSetImpl

trait SQLite {
    def querySQLite (databasePath: String, sqlStatement: String): CachedRowSetImpl =
    {
        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")
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
}
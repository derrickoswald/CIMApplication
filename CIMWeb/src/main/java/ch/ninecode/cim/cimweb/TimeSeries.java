package ch.ninecode.cim.cimweb;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;
import java.sql.DriverManager;

@Path("/timeseries")
@Produces({"application/json"})
public class TimeSeries
{

    protected void meta (Connection connection, StringBuilder sb) throws SQLException
    {
        sb.append (connection.getClass () + "\n");
        DatabaseMetaData meta = connection.getMetaData ();
        sb.append ("metadata:\n");
        sb.append ("DatabaseProductName: " + meta.getDatabaseProductName () + "\n");
        sb.append ("DriverName: " + meta.getDriverName () + "\n");

        PreparedStatement s = connection.prepareStatement ("select * from sqlite_master");
        ResultSet r = s.executeQuery ();
        sb.append ("tables:\n");
        while (r.next ())
        {
//            table|simulation|simulation|2|CREATE TABLE simulation (id integer primary key autoincrement, house string, power double, time datetime)
//            table|sqlite_sequence|sqlite_sequence|3|CREATE TABLE sqlite_sequence(name,seq)
//            table|results|results|4|CREATE TABLE results (id integer primary key autoincrement, simulation integer, node string, time datetime, vreal double, vimag double)
            String name = r.getString (2);
            sb.append (name + "\n");
        }
        r.close ();
    }

    @GET
    public String select ()
    {
        StringBuilder ret = new StringBuilder ();

        try
        {
            Context context = new InitialContext ();
            DataSource datasource = (DataSource)context.lookup ("java:comp/env/jdbc/TimeSeries");

            Connection connection  = null;
            try
            {
                // create a database connection
                connection = datasource.getConnection ();
                // meta (connection, ret);

                // select the results
                PreparedStatement statement = connection.prepareStatement ("select id, time, vreal, vimag from results where simulation=1 and node like 'HAS2805_%'");
                // form the JSON
                ResultSet resultset = statement.executeQuery ();
                ret.append ("[\n");
                boolean first = true;
                while (resultset.next ())
                {
                    int id = resultset.getInt (1);
                    Timestamp time = resultset.getTimestamp (2);
                    double real = resultset.getDouble (3);
                    double imag = resultset.getDouble (4);

                    if (!first)
                        ret.append (",\n");
                    first = false;
                    ret.append ("    {\n");
                    ret.append ("        \"id\": ");
                    ret.append (id);
                    ret.append ("\n        \"time\": ");
                    ret.append (Math.round (time.getNanos () / 1000.0));
                    ret.append ("\n        \"real\": ");
                    ret.append (real);
                    ret.append ("\n        \"imag\": ");
                    ret.append (imag);
                    ret.append ("\n    }");
                }
                ret.append ("\n]\n");
                resultset.close ();
                statement.close ();
            }
            catch (SQLException sqle)
            {
                ret.append (sqle.getMessage ());
            }
            finally
            {
                try
                {
                    if (connection != null)
                        connection.close ();
                }
                catch (SQLException sqle)
                {
                    // connection close failed
                }
            }
        }
        catch (NamingException ne)
        {
            ret.append (ne.getMessage ());
        }

        return (ret.toString ());
    }
}

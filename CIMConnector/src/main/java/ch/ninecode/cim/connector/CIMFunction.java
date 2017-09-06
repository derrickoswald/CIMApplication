package ch.ninecode.cim.connector;

import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.io.Serializable;

public interface CIMFunction extends Serializable
{
    /**
     * The return types understood by the CIMConnector interaction implementation.
     */
    public enum Return
    {
        /**
         * Dataset<Row> as returned by, for example, an sql query.
         */
        Dataset,

        /**
         * Text such as JSON, GeoJSON, XML, plain text, etc.
         * See getMimeType.
         */
        String,
    }

    /**
     * Provide a list of required jar files.
     * This list may also include the jar containing the implementation class of this interface.
     * @return The list of additional jars needed to execute.
     */
    String[] getJars ();

    /**
     * The type of value returned by execute.
     * This chooses the function signature from the <code>execute()</code> functions.
     * @return One of the enumerated interactions.
     */
    Return getReturnType ();

    /**
     * For a String ReturnType, specifies the mime type desired.
     * @return The mime type. One of the constants in javax.ws.rs.core.MediaType.
     */
    String getMimeType ();

    /**
     * Execute against this class returning a dataset.
     * @param spark The Spark session to use.
     * @return The resultant data set, which will be turned into a ResultSet.
     */
    Dataset<Row> execute (SparkSession spark);

    /**
     * Execute against this class returning a string.
     * The mime type is used mostly to get a different signature so Java is happy.
     * @param spark The Spark session to use.
     * @param mime_type The mime type expected from this call.
     * @return The resultant text.
     */
    String execute (SparkSession spark, String mime_type);

}

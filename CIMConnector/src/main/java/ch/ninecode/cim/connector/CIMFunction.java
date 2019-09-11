package ch.ninecode.cim.connector;

import javax.json.JsonStructure;

import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.io.Serializable;

public interface CIMFunction extends Serializable
{
    /**
     * The key in the input CIMMappedRecord holding the CIMFunction to execute.
     */
    static String FUNCTION = "function";

    /**
     * The key in the input CIMMappedRecord holding the query to execute.
     */
    static String QUERY = "query";

    /**
     * The key in the output CIMMappedRecord holding the CIMFunction result.
     *
     * For {@link #executeString(SparkSession spark) executeString} and
     * {@link #executeJSON(SparkSession spark) executeJSON} the returned CIMMappedRecord
     * holds the result (String or JSONStructure respectively) under this key.
     */
    static String RESULT = "result";

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
         * Text such as XML, plain text, etc.
         */
        String,

        /**
         * JSON or GeoJSON text.
         */
        JSON
    }

    /**
     * The type of value returned by execute.
     * This chooses the function signature from the <code>execute()</code> functions.
     * @return One of the enumerated interactions.
     */
    Return getReturnType ();

    /**
     * Provide a list of required jar files.
     * This list may also include the jar containing the implementation class of this interface.
     * @return The list of additional jars needed to execute.
     */
    String[] getJars ();

    /**
     * Execute against this class returning a dataset.
     * @param spark The Spark session to use.
     * @return The resultant data set, which will be turned into a ResultSet.
     */
    Dataset<Row> executeResultSet (SparkSession spark);

    /**
     * Execute against this class returning a string.
     * @param spark The Spark session to use.
     * @return The resultant String.
     */
    String executeString (SparkSession spark);

    /**
     * Execute against this class returning a JSON structure (JSONObject or JSONArray).
     * @param spark The Spark session to use.
     * @return The resultant JSON.
     */
    JsonStructure executeJSON (SparkSession spark);

}

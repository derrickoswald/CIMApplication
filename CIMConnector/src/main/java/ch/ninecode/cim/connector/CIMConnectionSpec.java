package ch.ninecode.cim.connector;

import java.util.HashMap;
import java.util.ArrayList;

import javax.resource.cci.ConnectionSpec;

public class CIMConnectionSpec implements ConnectionSpec
{
    protected String _UserName = "";
    protected String _Password = "";
    protected HashMap<String,String> _Properties = new HashMap<>();
    protected ArrayList<String> _Jars = new ArrayList<> ();

    /** Checks whether this instance is equal to another. Since
     *  connectionRequestInfo is defined specific to a resource
     *  adapter, the resource adapter is required to implement
     *  this method. The conditions for equality are specific
     *  to the resource adapter.
     *
     *  @return True if the two instances are equal.
     **/
    public boolean equals (Object object)
    {
        boolean ret = false;
        if (object instanceof CIMConnectionSpec)
        {
            CIMConnectionSpec that = (CIMConnectionSpec)object;
            ret = getProperties ().size () == that.getProperties ().size () &&
                getJars ().size () == that.getJars ().size ();
            if (ret)
                for (String key : getProperties ().keySet ())
                    if (!that.getProperties ().containsKey (key) || !getProperties ().get (key).equals (that.getProperties ().get (key)))
                    {
                        ret = false;
                        break;
                    }
            if (ret)
                for (String jar : getJars ())
                    if (!that.getJars ().contains (jar))
                    {
                        ret = false;
                        break;
                    }
        }

        return (ret);
    }

    /** Returns the hashCode of the ConnectionRequestInfo.
     *
     *  @return hash code of this instance
     **/
    public int hashCode ()
    {
        return (getProperties ().hashCode () + getJars ().hashCode ());
    }

    /**
     * <em>Not currently used.</em>
     * @return The name of the user establishing a connection to an EIS instance.
     */
    public String getUserName ()
    {
        return (_UserName);
    }

    public void setUserName (String user)
    {
        _UserName = user;
    }

    /**
     * <em>Not currently used.</em>
     * @return The password for the user establishing a connection.
     */
    public String getPassword ()
    {
        return (_Password);
    }

    public void setPassword (String password)
    {
        _Password = password;
    }

    public HashMap<String, String> getProperties ()
    {
        return (_Properties);
    }

    public void setProperties (HashMap<String, String> properties)
    {
        _Properties = properties;
    }

    public ArrayList<String> getJars ()
    {
        return (_Jars);
    }

    public void setJars (ArrayList<String> jars)
    {
        _Jars = jars;
    }
}

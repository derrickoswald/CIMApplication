package ch.ninecode.cim.connector;

import java.util.HashMap;
import java.util.ArrayList;

import javax.resource.spi.ConnectionRequestInfo;

public class CIMConnectionRequestInfo implements ConnectionRequestInfo
{
    protected String _Master = "";
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
        if (object instanceof CIMConnectionRequestInfo)
        {
            CIMConnectionRequestInfo that = (CIMConnectionRequestInfo)object;
            ret = getMaster () == that.getMaster () &&
                getProperties ().size () == that.getProperties ().size () &&
                getJars ().size () == that.getJars ().size ();
            if (ret)
                for (String key : getProperties ().keySet ())
                    if (!that.getProperties ().containsKey (key) || getProperties ().get (key) != that.getProperties ().get (key))
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
     *  @return hash code os this instance
     **/
    public int hashCode ()
    {
        return (getMaster ().hashCode () + getProperties ().hashCode () + getJars ().hashCode ());
    }

    public String getMaster ()
    {
        return (_Master);
    }

    public void setMaster (String master)
    {
        _Master = master;
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

    public String toString ()
    {
        StringBuilder sb = new StringBuilder ();

        sb.append ("[@");
        sb.append (getMaster ());
        sb.append (": ");
        for (String key: getProperties ().keySet ())
        {
            sb.append (key);
            sb.append ("=");
            sb.append (getProperties ().get (key));
        }
        if (0 != getJars ().size ())
        {
            sb.append (" (");
            for (String jar: getJars ())
            {
                sb.append (jar);
            }
            sb.append (")");
        }
        sb.append ("]");

        return (sb.toString ());
    }
}

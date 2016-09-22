package ch.ninecode.cim.connector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.resource.cci.MappedRecord;

public class CIMMappedRecord implements MappedRecord
{
    private static final long serialVersionUID = 1L;
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";

    protected String _Name;
    protected String _Description;
    protected HashMap<Object,Object> _Map = new HashMap<> ();

    public Object clone () throws CloneNotSupportedException
    {
        throw new CloneNotSupportedException();
    }

    public String toString ()
    {
        StringBuilder ret = new StringBuilder ();
        boolean more = false;

        ret.append (getRecordName ());
        ret.append (" (");
        ret.append (getRecordShortDescription ());
        ret.append (") ");
        ret.append ("[");
        for (Object obj: keySet ())
        {
            if (more)
                ret.append (", ");
            ret.append (obj.toString ());
            ret.append (" = ");
            ret.append (get (obj));
            more = true;
        }
        ret.append ("]");

        return (ret.toString ());
    }

    @Override
    public String getRecordName ()
    {
        return (_Name);
    }

    @Override
    public void setRecordName (String name)
    {
        _Name = name;
    }

    @Override
    public String getRecordShortDescription ()
    {
        return (_Description);
    }

    @Override
    public void setRecordShortDescription (String description)
    {
        _Description = description;
    }

    @Override
    public int size ()
    {
        return (_Map.size ());
    }

    @Override
    public boolean isEmpty ()
    {
        return (_Map.isEmpty ());
    }

    @Override
    public boolean containsKey (Object key)
    {
        return (_Map.containsKey (key));
    }

    @Override
    public boolean containsValue (Object value)
    {
        return (_Map.containsValue (value));
    }

    @Override
    public Object get (Object key)
    {
        return (_Map.get (key));
    }

    @Override
    public Object put (Object key, Object value)
    {
        return (_Map.put (key, value));
    }

    @Override
    public Object remove (Object key)
    {
        return (_Map.remove (key));
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public void putAll (Map map)
    {
        for (Object key : map.keySet ())
            _Map.put (key, map.get (key));
    }

    @Override
    public void clear ()
    {
        _Map.clear ();
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public Set keySet ()
    {
        return (_Map.keySet ());
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public Collection values ()
    {
        return (_Map.values ());
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public Set entrySet ()
    {
        return (_Map.entrySet ());
    }
}

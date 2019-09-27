package ch.ninecode.cim.connector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import javax.resource.cci.IndexedRecord;
import javax.validation.constraints.NotNull;

public class CIMIndexedRecord implements IndexedRecord
{
    private static final long serialVersionUID = 1L;
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";

    protected String _Name;
    protected String _Description;
    protected ArrayList<Object> _List = new ArrayList<> ();

    public CIMIndexedRecord ()
    {
        super ();
    }

    public Object clone () throws CloneNotSupportedException
    {
        throw new CloneNotSupportedException();
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
        return _List.size();
    }

    @Override
    public boolean isEmpty ()
    {
        return (_List.isEmpty ());
    }

    @Override
    public boolean contains (Object o)
    {
        return (_List.contains (o));
    }

    @Override
    public Iterator<Object> iterator ()
    {
        return (_List.iterator ());
    }

    @Override
    public Object[] toArray ()
    {
        return (_List.toArray ());
    }

    @Override
    public Object[] toArray (@NotNull Object[] a)
    {
        return (_List.toArray (a));
    }

    @Override
    public boolean add (Object o)
    {
        return (_List.add (o));
    }

    @Override
    public boolean remove (Object o)
    {
        return (_List.remove (o));
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public boolean containsAll (Collection c)
    {
        return (_List.containsAll (c));
    }

    @SuppressWarnings ({ "rawtypes" })
    @Override
    public boolean addAll (Collection c)
    {
        return (_List.addAll (c));
    }

    @SuppressWarnings ({ "rawtypes" })
    @Override
    public boolean addAll (int index, Collection c)
    {
        return (_List.addAll (index, c));
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public boolean removeAll (Collection c)
    {
        return (_List.removeAll (c));
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public boolean retainAll (Collection c)
    {
        return (_List.retainAll (c));
    }

    @Override
    public void clear ()
    {
        _List.clear ();
    }

    @Override
    public Object get (int index)
    {
        return (_List.get (index));
    }

    @Override
    public Object set (int index, Object element)
    {
        return (_List.set (index, element));
    }

    @Override
    public void add (int index, Object element)
    {
        _List.add (index, element);
    }

    @Override
    public Object remove (int index)
    {
        return (_List.remove (index));
    }

    @Override
    public int indexOf (Object o)
    {
        return (_List.indexOf (o));
    }

    @Override
    public int lastIndexOf (Object o)
    {
        return (_List.lastIndexOf (o));
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public ListIterator listIterator ()
    {
        return (_List.listIterator ());
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public ListIterator listIterator (int index)
    {
        return (_List.listIterator (index));
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public List subList (int from, int to)
    {
        return (_List.subList (from, to));
    }

}

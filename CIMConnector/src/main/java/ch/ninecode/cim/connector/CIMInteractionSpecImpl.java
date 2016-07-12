package ch.ninecode.cim.connector;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;

public class CIMInteractionSpecImpl implements CIMInteractionSpec, Serializable
{
    //    The standard properties are as follows:
    //
    //        FunctionName: name of an EIS function
    //        InteractionVerb: mode of interaction with an EIS instance: SYNC_SEND, SYNC_SEND_RECEIVE, SYNC_RECEIVE
    //        ExecutionTimeout: the number of milliseconds an Interaction will wait for an EIS to execute the specified function
    //
    //    The following standard properties are used to give hints to an Interaction instance about the ResultSet requirements:
    //
    //        FetchSize
    //        FetchDirection
    //        MaxFieldSize
    //        ResultSetType
    //        ResultSetConcurrency

    private static final long serialVersionUID = 1L;
    protected String _Function;
    protected transient PropertyChangeSupport _PropertyChangeSupport;

    public CIMInteractionSpecImpl ()
    {
        super ();
    }

    /**
     * Gets the functionName
     *
     * @return Returns a String
     * @see CIMInteractionSpec#getFunctionName()
     */
    public String getFunctionName ()
    {
        return (_Function);
    }

    /**
     * Sets the functionName
     *
     * @param function The functionName to set
     * @see CIMInteractionSpec#setFunctionName(String)
     */
    public void setFunctionName (String function)
    {
        String oldFunctionName = function;
        _Function = function;
        firePropertyChange ("FunctionName", oldFunctionName, function);
    }

    /**
     * The addPropertyChangeListener method was generated to support the propertyChange field.
     */
    public synchronized void addPropertyChangeListener (PropertyChangeListener listener)
    {
        getPropertyChangeSupport ().addPropertyChangeListener (listener);
    }

    /**
     * The addPropertyChangeListener method was generated to support the propertyChange field.
     */
    public synchronized void addPropertyChangeListener (String name, PropertyChangeListener listener)
    {
        getPropertyChangeSupport ().addPropertyChangeListener (name, listener);
    }

    /**
     * The firePropertyChange method was generated to support the propertyChange field.
     */
    public void firePropertyChange (PropertyChangeEvent event)
    {

        getPropertyChangeSupport ().firePropertyChange (event);
    }

    /**
     * The firePropertyChange method was generated to support the propertyChange field.
     */
    public void firePropertyChange (String name, int oldValue, int newValue)
    {
        getPropertyChangeSupport ().firePropertyChange (name, oldValue, newValue);
    }

    /**
     * The firePropertyChange method was generated to support the propertyChange field.
     */
    public void firePropertyChange (String name, Object oldValue, Object newValue)
    {
        getPropertyChangeSupport ().firePropertyChange (name, oldValue, newValue);
    }

    /**
     * The firePropertyChange method was generated to support the propertyChange field.
     */
    public void firePropertyChange (String name, boolean oldValue, boolean newValue)
    {
        getPropertyChangeSupport ().firePropertyChange (name, oldValue, newValue);
    }

    /**
     * Accessor for the propertyChange field.
     */
    protected PropertyChangeSupport getPropertyChangeSupport ()
    {

        if (_PropertyChangeSupport == null)
            _PropertyChangeSupport = new PropertyChangeSupport (this);
        return (_PropertyChangeSupport);
    }

    /**
     * The hasListeners method was generated to support the propertyChange field.
     */
    public synchronized boolean hasListeners (String name)
    {
        return (getPropertyChangeSupport ().hasListeners (name));
    }

    /**
     * The removePropertyChangeListener method was generated to support the propertyChange field.
     */
    public synchronized void removePropertyChangeListener (PropertyChangeListener listener)
    {
        getPropertyChangeSupport ().removePropertyChangeListener (listener);
    }

    /**
     * The removePropertyChangeListener method was generated to support the propertyChange field.
     */
    public synchronized void removePropertyChangeListener (String name, PropertyChangeListener listener)
    {
        getPropertyChangeSupport ().removePropertyChangeListener (name, listener);
    }
}
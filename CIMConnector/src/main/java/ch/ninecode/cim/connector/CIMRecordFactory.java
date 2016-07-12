package ch.ninecode.cim.connector;

import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.cci.IndexedRecord;
import javax.resource.cci.MappedRecord;
import javax.resource.cci.RecordFactory;

public class CIMRecordFactory implements RecordFactory
{
    private static final String MAPPED_RECORD_NOT_SUPPORTED_ERROR = "Mapped record not supported";
    private static final String INVALID_RECORD_NAME = "Invalid record name";

    public CIMRecordFactory ()
    {
        super ();
    }

    @Override
    public IndexedRecord createIndexedRecord (String name) throws ResourceException
    {
        ch.ninecode.cim.connector.CIMIndexedRecord ret = null;

        if ((name.equals (CIMIndexedRecord.INPUT)) || (name.equals (CIMIndexedRecord.OUTPUT)))
        {
            ret = new CIMIndexedRecord ();
            ret.setRecordName (name);
        }
        else
            throw new ResourceException (INVALID_RECORD_NAME);

        return (ret);
    }

    @Override
    public MappedRecord createMappedRecord (String arg0) throws ResourceException
    {
        throw new NotSupportedException (MAPPED_RECORD_NOT_SUPPORTED_ERROR);
    }
}

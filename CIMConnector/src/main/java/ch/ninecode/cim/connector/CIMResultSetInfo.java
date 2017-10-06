package ch.ninecode.cim.connector;

import java.sql.ResultSet;

import javax.resource.ResourceException;
import javax.resource.cci.ResultSetInfo;

public class CIMResultSetInfo implements ResultSetInfo
{

    @Override
    public boolean updatesAreDetected (int type) throws ResourceException
    {
        return (false);
    }

    @Override
    public boolean insertsAreDetected (int type) throws ResourceException
    {
        return (false);
    }

    @Override
    public boolean deletesAreDetected (int type) throws ResourceException
    {
        return (false);
    }

    @Override
    public boolean supportsResultSetType (int type) throws ResourceException
    {
        boolean ret;

        switch (type)
        {
            case ResultSet.TYPE_FORWARD_ONLY:
                ret = true;
                break;
            case ResultSet.TYPE_SCROLL_INSENSITIVE:
                ret = false;
                break;
            case ResultSet.TYPE_SCROLL_SENSITIVE:
                ret = false;
                break;
            default:
                ret = false;
                break;
        }
        return (ret);
    }

    @Override
    public boolean supportsResultTypeConcurrency (int type, int concurrency) throws ResourceException
    {
        boolean ret;

        switch (type)
        {
            case ResultSet.TYPE_FORWARD_ONLY:
                switch (concurrency)
                {
                    case ResultSet.CONCUR_READ_ONLY:
                        ret = true;
                        break;
                    case ResultSet.CONCUR_UPDATABLE:
                        ret = false;
                        break;
                    default:
                        ret = false;
                        break;
                }
                break;
            case ResultSet.TYPE_SCROLL_INSENSITIVE:
                ret = false;
                break;
            case ResultSet.TYPE_SCROLL_SENSITIVE:
                ret = false;
                break;
            default:
                ret = false;
                break;
        }
        return (ret);
    }

    @Override
    public boolean othersUpdatesAreVisible (int type) throws ResourceException
    {
        return (false);
    }

    @Override
    public boolean othersDeletesAreVisible (int type) throws ResourceException
    {
        return (false);
    }

    @Override
    public boolean othersInsertsAreVisible (int type) throws ResourceException
    {
        return (false);
    }

    @Override
    public boolean ownUpdatesAreVisible (int type) throws ResourceException
    {
        return (false);
    }

    @Override
    public boolean ownInsertsAreVisible (int type) throws ResourceException
    {
        return (false);
    }

    @Override
    public boolean ownDeletesAreVisible (int type) throws ResourceException
    {
        return (false);
    }
}

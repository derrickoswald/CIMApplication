package ch.ninecode.cim.connector;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.resource.cci.Record;

import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructType;

public class CIMResultSet implements Record, ResultSet
{
    private static final long serialVersionUID = 1L;
    final String INVALID = "ResultSet is invalid";
    final String BEFOREFIRST = "ResultSet is before the first row";
    final String AFTERLAST = "ResultSet is after the last row";
    protected StructType _Schema;
    protected List<Row> _Rows;
    protected int _Index;
    protected boolean _LastNull;
    protected String _Name;
    protected String _Description;

    public CIMResultSet (StructType schema, List<Row> rows)
    {
        _Schema = schema;
        _Rows = rows;
        _Index = -1;
    }

    public CIMResultSet ()
    {
        this (null, null);
    }

    public Object clone () throws CloneNotSupportedException
    {
        throw new CloneNotSupportedException();
    }

    protected Row getCurrentRow (int columnIndex) throws SQLException
    {
        if (null == _Rows) throw new SQLException (INVALID);
        if (-1 == _Index) throw new SQLException (BEFOREFIRST);
        if (_Rows.size () <= _Index) throw new SQLException (AFTERLAST);
        Row row = _Rows.get (_Index);
        _LastNull = row.isNullAt (columnIndex - 1);
        return (row);
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
    public <T> T unwrap (Class<T> iface) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isWrapperFor (Class<?> iface) throws SQLException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean next () throws SQLException
    {
        if (null == _Rows) throw new SQLException (INVALID);
        _Index++;
        return (_Index < _Rows.size ());
    }

    @Override
    public void close () throws SQLException
    {
        _Rows = null;
        _Schema = null;
        _Index = -1;
    }

    @Override
    public boolean wasNull () throws SQLException
    {
        if (null == _Rows) throw new SQLException (INVALID);
        return (_LastNull);
    }

    @Override
    public String getString (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? null : row.get (columnIndex - 1).toString ()); // getString does a simple cast, use toString to support other objects
    }

    @Override
    public boolean getBoolean (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (!_LastNull && row.getBoolean (columnIndex - 1));
    }

    @Override
    public byte getByte (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? 0 : row.getByte (columnIndex - 1));
    }

    @Override
    public short getShort (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? 0 : row.getShort (columnIndex - 1));
    }

    @Override
    public int getInt (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? 0 : row.getInt (columnIndex - 1));
    }

    @Override
    public long getLong (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? 0L : row.getLong (columnIndex - 1));
    }

    @Override
    public float getFloat (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? 0f : row.getFloat (columnIndex - 1));
    }

    @Override
    public double getDouble (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? 0 : row.getDouble (columnIndex - 1));
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal (int columnIndex, int scale) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public byte[] getBytes (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? null : row.getString (columnIndex - 1).getBytes (StandardCharsets.US_ASCII)); // ToDo: bytes?
    }

    @Override
    public Date getDate (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? null : row.getDate (columnIndex - 1));
    }

    @Override
    public Time getTime (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? null : new Time (row.getTimestamp (columnIndex - 1).getTime ()));
    }

    @Override
    public Timestamp getTimestamp (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? null : row.getTimestamp (columnIndex - 1));
    }

    @Override
    public InputStream getAsciiStream (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? null : new ByteArrayInputStream (row.getString (columnIndex - 1).getBytes (StandardCharsets.US_ASCII)));
    }

    @Override
    @Deprecated
    public InputStream getUnicodeStream (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? null : new ByteArrayInputStream (row.getString (columnIndex - 1).getBytes (StandardCharsets.UTF_8)));
    }

    @Override
    public InputStream getBinaryStream (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? null : new ByteArrayInputStream (row.getString (columnIndex - 1).getBytes (StandardCharsets.UTF_8))); // ToDo: binary?
    }

    @Override
    public String getString (String columnLabel) throws SQLException
    {
        return (getString (findColumn (columnLabel)));
    }

    @Override
    public boolean getBoolean (String columnLabel) throws SQLException
    {
        return (getBoolean (findColumn (columnLabel)));
    }

    @Override
    public byte getByte (String columnLabel) throws SQLException
    {
        return (getByte (findColumn (columnLabel)));
    }

    @Override
    public short getShort (String columnLabel) throws SQLException
    {
        return (getShort (findColumn (columnLabel)));
    }

    @Override
    public int getInt (String columnLabel) throws SQLException
    {
        return (getInt (findColumn (columnLabel)));
    }

    @Override
    public long getLong (String columnLabel) throws SQLException
    {
        return (getLong (findColumn (columnLabel)));
    }

    @Override
    public float getFloat (String columnLabel) throws SQLException
    {
        return (getFloat (findColumn (columnLabel)));
    }

    @Override
    public double getDouble (String columnLabel) throws SQLException
    {
        return (getDouble (findColumn (columnLabel)));
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal (String columnLabel, int scale) throws SQLException
    {
        return (getBigDecimal (findColumn (columnLabel)));
    }

    @Override
    public byte[] getBytes (String columnLabel) throws SQLException
    {
        return (getBytes (findColumn (columnLabel)));
    }

    @Override
    public Date getDate (String columnLabel) throws SQLException
    {
        return (getDate (findColumn (columnLabel)));
    }

    @Override
    public Time getTime (String columnLabel) throws SQLException
    {
        return (getTime (findColumn (columnLabel)));
    }

    @Override
    public Timestamp getTimestamp (String columnLabel) throws SQLException
    {
        return (getTimestamp (findColumn (columnLabel)));
    }

    @Override
    public InputStream getAsciiStream (String columnLabel) throws SQLException
    {
        return (getAsciiStream (findColumn (columnLabel)));
    }

    @Override
    @Deprecated
    public InputStream getUnicodeStream (String columnLabel) throws SQLException
    {
        return (getUnicodeStream (findColumn (columnLabel)));
    }

    @Override
    public InputStream getBinaryStream (String columnLabel) throws SQLException
    {
        return (getBinaryStream (findColumn (columnLabel)));
    }

    @Override
    public SQLWarning getWarnings () throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void clearWarnings () throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public String getCursorName () throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResultSetMetaData getMetaData () throws SQLException
    {
        return (new CIMResultSetMetaData (_Schema));
    }

    @Override
    public Object getObject (int columnIndex) throws SQLException
    {
        Row row = getCurrentRow (columnIndex);
        return (_LastNull ? null : row.get (columnIndex - 1));
    }

    @Override
    public Object getObject (String columnLabel) throws SQLException
    {
        return (getObject (findColumn (columnLabel)));
    }

    @Override
    public int findColumn (String columnLabel) throws SQLException
    {
        int index;
        try
        {
            index = _Schema.fieldIndex (columnLabel);
        }
        catch (IllegalArgumentException iae)
        {
            throw new SQLException ("column label " + columnLabel + " not found", iae);
        }
        return (index);
    }

    @Override
    public Reader getCharacterStream (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Reader getCharacterStream (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigDecimal getBigDecimal (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigDecimal getBigDecimal (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isBeforeFirst () throws SQLException
    {
        return (-1 == _Index);
    }

    @Override
    public boolean isAfterLast () throws SQLException
    {
        return (_Rows.size () <= _Index);
    }

    @Override
    public boolean isFirst () throws SQLException
    {
        return (0 == _Index);
    }

    @Override
    public boolean isLast () throws SQLException
    {
        return (_Rows.size () - 1 == _Index);
    }

    @Override
    public void beforeFirst () throws SQLException
    {
        _Index = -1;
    }

    @Override
    public void afterLast () throws SQLException
    {
        _Index = _Rows.size ();
    }

    @Override
    public boolean first () throws SQLException
    {
        boolean ret = 0 < _Rows.size ();

        if (ret)
            _Index = 0;

        return (ret);
    }

    @Override
    public boolean last () throws SQLException
    {
        boolean ret = 0 < _Rows.size ();

        if (ret)
            _Index = _Rows.size () - 1;

        return (ret);
    }

    @Override
    public int getRow () throws SQLException
    {
        if (null == _Rows) throw new SQLException (INVALID);
        if (-1 == _Index) throw new SQLException (BEFOREFIRST);

        return (_Index);
    }

    @Override
    public boolean absolute (int row) throws SQLException
    {
        boolean ret = false;

        if (0 == row)
            beforeFirst ();
        else if (row > 0)
            if (row > _Rows.size ())
                afterLast ();
            else
            {
                _Index = row - 1;
                ret = true;
            }
        else
        {
            row = _Rows.size () + row;
            if (row < 0)
                beforeFirst ();
            else
            {
                _Index = row;
                ret = true;
            }
        }

        return (ret);
    }

    @Override
    public boolean relative (int rows) throws SQLException
    {
        return (0 == rows ? (_Index >= 0 && _Index < _Rows.size ()) : absolute (_Index + rows));
    }

    @Override
    public boolean previous () throws SQLException
    {
        _Index = Math.max (-1, _Index - 1);

        return (_Index >= 0 && _Index < _Rows.size ());
    }

    @Override
    public void setFetchDirection (int direction) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public int getFetchDirection () throws SQLException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setFetchSize (int rows) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public int getFetchSize () throws SQLException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getType () throws SQLException
    {
        return (TYPE_SCROLL_INSENSITIVE);
    }

    @Override
    public int getConcurrency () throws SQLException
    {
        return (CONCUR_READ_ONLY);
    }

    @Override
    public boolean rowUpdated () throws SQLException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean rowInserted () throws SQLException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean rowDeleted () throws SQLException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void updateNull (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBoolean (int columnIndex, boolean x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateByte (int columnIndex, byte x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateShort (int columnIndex, short x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateInt (int columnIndex, int x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateLong (int columnIndex, long x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateFloat (int columnIndex, float x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateDouble (int columnIndex, double x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBigDecimal (int columnIndex, BigDecimal x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateString (int columnIndex, String x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBytes (int columnIndex, byte[] x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateDate (int columnIndex, Date x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateTime (int columnIndex, Time x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateTimestamp (int columnIndex, Timestamp x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateAsciiStream (int columnIndex, InputStream x, int length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBinaryStream (int columnIndex, InputStream x, int length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateCharacterStream (int columnIndex, Reader x, int length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateObject (int columnIndex, Object x, int scaleOrLength) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateObject (int columnIndex, Object x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNull (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBoolean (String columnLabel, boolean x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateByte (String columnLabel, byte x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateShort (String columnLabel, short x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateInt (String columnLabel, int x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateLong (String columnLabel, long x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateFloat (String columnLabel, float x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateDouble (String columnLabel, double x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBigDecimal (String columnLabel, BigDecimal x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateString (String columnLabel, String x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBytes (String columnLabel, byte[] x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateDate (String columnLabel, Date x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateTime (String columnLabel, Time x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateTimestamp (String columnLabel, Timestamp x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateAsciiStream (String columnLabel, InputStream x, int length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBinaryStream (String columnLabel, InputStream x, int length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateCharacterStream (String columnLabel, Reader reader, int length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateObject (String columnLabel, Object x, int scaleOrLength) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateObject (String columnLabel, Object x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void insertRow () throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateRow () throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteRow () throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void refreshRow () throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void cancelRowUpdates () throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void moveToInsertRow () throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void moveToCurrentRow () throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public Statement getStatement () throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object getObject (int columnIndex, Map<String, Class<?>> map) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Ref getRef (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Blob getBlob (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Clob getClob (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Array getArray (int columnIndex) throws SQLException
    {
//        if (null == _Rows) throw new SQLException (INVALID);
//        Row row = _Rows[_Index];
//        _LastNull = null == row.get (columnIndex - 1);
//        return (row.getSeq (arg0) (columnIndex - 1));

        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object getObject (String columnLabel, Map<String, Class<?>> map) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Ref getRef (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Blob getBlob (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Clob getClob (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Array getArray (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Date getDate (int columnIndex, Calendar cal) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Date getDate (String columnLabel, Calendar cal) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Time getTime (int columnIndex, Calendar cal) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Time getTime (String columnLabel, Calendar cal) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Timestamp getTimestamp (int columnIndex, Calendar cal) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Timestamp getTimestamp (String columnLabel, Calendar cal) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public URL getURL (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public URL getURL (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updateRef (int columnIndex, Ref x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateRef (String columnLabel, Ref x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBlob (int columnIndex, Blob x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBlob (String columnLabel, Blob x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateClob (int columnIndex, Clob x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateClob (String columnLabel, Clob x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateArray (int columnIndex, Array x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateArray (String columnLabel, Array x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public RowId getRowId (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RowId getRowId (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updateRowId (int columnIndex, RowId x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateRowId (String columnLabel, RowId x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public int getHoldability () throws SQLException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isClosed () throws SQLException
    {
        return (null == _Rows);
    }

    @Override
    public void updateNString (int columnIndex, String nString) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNString (String columnLabel, String nString) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNClob (int columnIndex, NClob nClob) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNClob (String columnLabel, NClob nClob) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public NClob getNClob (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NClob getNClob (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SQLXML getSQLXML (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SQLXML getSQLXML (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updateSQLXML (int columnIndex, SQLXML xmlObject) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateSQLXML (String columnLabel, SQLXML xmlObject) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public String getNString (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getNString (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Reader getNCharacterStream (int columnIndex) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Reader getNCharacterStream (String columnLabel) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updateNCharacterStream (int columnIndex, Reader x, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNCharacterStream (String columnLabel, Reader reader, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateAsciiStream (int columnIndex, InputStream x, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBinaryStream (int columnIndex, InputStream x, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateCharacterStream (int columnIndex, Reader x, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateAsciiStream (String columnLabel, InputStream x, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBinaryStream (String columnLabel, InputStream x, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateCharacterStream (String columnLabel, Reader reader, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBlob (int columnIndex, InputStream inputStream, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBlob (String columnLabel, InputStream inputStream, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateClob (int columnIndex, Reader reader, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateClob (String columnLabel, Reader reader, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNClob (int columnIndex, Reader reader, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNClob (String columnLabel, Reader reader, long length) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNCharacterStream (int columnIndex, Reader x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNCharacterStream (String columnLabel, Reader reader) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateAsciiStream (int columnIndex, InputStream x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBinaryStream (int columnIndex, InputStream x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateCharacterStream (int columnIndex, Reader x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateAsciiStream (String columnLabel, InputStream x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBinaryStream (String columnLabel, InputStream x) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateCharacterStream (String columnLabel, Reader reader) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBlob (int columnIndex, InputStream inputStream) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateBlob (String columnLabel, InputStream inputStream) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateClob (int columnIndex, Reader reader) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateClob (String columnLabel, Reader reader) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNClob (int columnIndex, Reader reader) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateNClob (String columnLabel, Reader reader) throws SQLException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public <T> T getObject (int columnIndex, Class<T> type) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> T getObject (String columnLabel, Class<T> type) throws SQLException
    {
        // TODO Auto-generated method stub
        return null;
    }


}

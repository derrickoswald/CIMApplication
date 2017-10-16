package ch.ninecode.cim.cimweb

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.json.Json
import javax.json.JsonStructure

import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.spark.sql.SparkSession

case class PutFileFunction (path: String, data: Array[Byte], unzip: Boolean = false) extends CIMWebFunction
{
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val response = Json.createObjectBuilder
        response.add ("filesystem", uri.toString)
        val file: Path = new Path (hdfs.getUri.toString, path)
        response.add ("path", path)
        response.add ("size", data.length)
        // write the file
        try
        {
            val parent = if (path.endsWith ("/")) file else file.getParent
            hdfs.mkdirs (parent, new FsPermission("ugoa-rwx"))
            hdfs.setPermission (parent, new FsPermission("ugoa-rwx"))

            if (0 != data.length && !path.endsWith ("/"))
            {
                if (unzip)
                {
                    val zip = new ZipInputStream (new ByteArrayInputStream (data))
                    val buffer = new Array[Byte](1024)
                    var more = true
                    val files = Json.createArrayBuilder
                    do
                    {
                        val entry = zip.getNextEntry
                        if (null != entry)
                        {
                            if (entry.isDirectory)
                            {
                                val path = new Path (parent, entry.getName)
                                hdfs.mkdirs (path, new FsPermission("ugoa-rwx"))
                                hdfs.setPermission (path, new FsPermission("ugoa-rwx"))
                            }
                            else
                            {
                                val baos = new ByteArrayOutputStream ()
                                var eof = false
                                do
                                {
                                    val len = zip.read (buffer, 0, buffer.length)
                                    if (-1 == len)
                                        eof = true
                                    else
                                        baos.write (buffer, 0, len)
                                }
                                while (!eof)
                                baos.close ()
                                val f = new Path (parent, entry.getName)
                                val out = hdfs.create (f)
                                out.write (baos.toByteArray)
                                out.close ()
                                files.add (f.toString)
                            }
                            zip.closeEntry ()
                        }
                        else
                            more = false
                    }
                    while (more)
                    zip.close ()
                    response.add ("files", files)
                }
                else
                {
                    val out = hdfs.create (file)
                    out.write (data)
                    out.close ()
                }
            }
        }
        catch
        {
            case e: Exception =>
                response.add ("error", e.getMessage)
        }
        response.build
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" (path=")
        sb.append (path)
        sb.append (", unzip=")
        sb.append (unzip)
        sb.append (")")
        sb.toString
    }
}

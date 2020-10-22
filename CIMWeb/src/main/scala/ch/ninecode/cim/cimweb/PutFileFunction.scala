package ch.ninecode.cim.cimweb

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

import javax.json.Json
import javax.json.JsonStructure

import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsAction
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.spark.sql.SparkSession

case class PutFileFunction (path: String, data: Array[Byte], unzip: Boolean = false) extends CIMWebFunction
{
    lazy val wideOpen = new FsPermission(FsAction.ALL, FsAction.ALL, FsAction.ALL)

    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val header = Json.createObjectBuilder
            .add("filesystem", uri.toString)
            .add("path", path)
            .add("size", data.length)
        // write the file
        val file: Path = new Path(hdfs.getUri.toString, path)
        val response = try
        {
            val parent = if (path.endsWith("/")) file else file.getParent
            if (hdfs.mkdirs(parent, wideOpen))
                hdfs.setPermission(parent, wideOpen)

            if (0 != data.length && !path.endsWith("/"))
            {
                if (unzip)
                {
                    val zip = new ZipInputStream(new ByteArrayInputStream(data))
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
                                val path = new Path(parent, entry.getName)
                                if (hdfs.mkdirs(path, wideOpen))
                                    hdfs.setPermission(path, wideOpen)
                            }
                            else
                            {
                                val baos = new ByteArrayOutputStream()
                                var eof = false
                                do
                                {
                                    val len = zip.read(buffer, 0, buffer.length)
                                    if (-1 == len)
                                        eof = true
                                    else
                                        baos.write(buffer, 0, len)
                                }
                                while (!eof)
                                baos.close()
                                val f = new Path(parent, entry.getName)
                                val out = hdfs.create(f)
                                out.write(baos.toByteArray)
                                out.close()
                                val _ = files.add(f.toString)
                            }
                            zip.closeEntry()
                        }
                        else
                            more = false
                    }
                    while (more)
                    zip.close()
                    header.add("files", files)
                }
                else
                {
                    val out = hdfs.create(file)
                    out.write(data)
                    out.close()
                    header
                }
            }
            else
                header
        }
        catch
        {
            case e: Exception =>
                header.add("error", e.getMessage)
        }
        response.build
    }

    override def toString: String = s"${super.toString} (path=$path, unzip=$unzip)"
}

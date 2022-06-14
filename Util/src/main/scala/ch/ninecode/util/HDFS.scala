package ch.ninecode.util


import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

import java.io.{ByteArrayInputStream, File, FileOutputStream}
import java.nio.file.{Files, Paths}
import java.util.zip.ZipInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.hadoop.fs.permission.FsAction
import org.apache.hadoop.fs.permission.FsPermission

trait HDFS
{
    def putFile (dst: String, src: String, unzip: Boolean = false): Seq[String] =
    {
        implicit val log: Logger = LoggerFactory.getLogger(getClass)
        var ret = Seq[String]()
        val fs = hdfs
        val file = new Path(fs.getUri.toString, dst)
        // write the file
        try
        {
            val parent = if (dst.endsWith("/")) file else file.getParent
            if (!fs.exists(parent))
            {
                val _ = fs.mkdirs(parent, wideOpen)
                if (!parent.isRoot)
                    fs.setPermission(parent, wideOpen)
            }

            if (unzip)
            {
                val in =
                    try
                        Files.newInputStream(Paths.get(src))
                    catch
                    {
                        case e: Exception =>
                            log.error(s"""ingest failed for file "$file"""", e)
                            new ByteArrayInputStream(Array[Byte]())
                    }
                val zip = new ZipInputStream(in)
                val buffer = new Array[Byte](1024)
                var more = true
                do
                {
                    val entry = zip.getNextEntry
                    if (null != entry)
                    {
                        if (entry.isDirectory)
                        {
                            val path = new Path(parent, entry.getName)
                            val _ = fs.mkdirs(path, wideOpen)
                            fs.setPermission(path, wideOpen)
                        }
                        else
                        {
                            val tmp = File.createTempFile("simulation", ".tmp")
                            val stream = new FileOutputStream(tmp)
                            var eof = false
                            do
                            {
                                val len = zip.read(buffer, 0, buffer.length)
                                if (-1 == len)
                                    eof = true
                                else
                                    stream.write(buffer, 0, len)
                            }
                            while (!eof)
                            stream.close()
                            val f = new Path(parent, entry.getName)
                            fs.copyFromLocalFile(true, true, new Path(tmp.getAbsolutePath), f)
                            ret = ret :+ f.toString
                        }
                        zip.closeEntry()
                    }
                    else
                        more = false
                }
                while (more)
                zip.close()
            }
            else
            {
                val f = new Path(parent, dst)
                fs.copyFromLocalFile(false, true, new Path(src), f)
                ret = ret :+ file.toString
            }
        }
        catch
        {
            case e: Exception =>
                log.error(s"""putFile failed for "$src" to "$dst" with unzip=$unzip""", e)
        }
        ret
    }

    // build a file system configuration, including core-site.xml
    def hdfs_configuration: Configuration =
    {
        implicit val log: Logger = LoggerFactory.getLogger(getClass)
        val configuration = new Configuration()
        if (null == configuration.getResource("core-site.xml"))
        {
            val hadoop_conf: String = System.getenv("HADOOP_CONF_DIR")
            if (null != hadoop_conf)
            {
                val site: Path = new Path(hadoop_conf, "core-site.xml")
                val f: File = new File(site.toString)
                if (f.exists && !f.isDirectory)
                    configuration.addResource(site)
            }
            else
                log.error("HADOOP_CONF_DIR environment variable not found")
        }
        configuration
    }

    def hdfs: FileSystem =
    {
        // get the configuration
        val conf = hdfs_configuration
        // get the file system
        FileSystem.get(FileSystem.getDefaultUri(conf), conf)
    }

    lazy val wideOpen = new FsPermission(FsAction.ALL, FsAction.ALL, FsAction.ALL)

    def base_name (path: String): String =
    {
        val sep = System.getProperty("file.separator")
        val index = path.lastIndexOf(sep)
        if (-1 != index)
            path.substring(index + 1)
        else
            path
    }
}
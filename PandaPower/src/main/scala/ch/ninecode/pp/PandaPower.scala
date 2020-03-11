package ch.ninecode.pp

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class PandaPower
(
    session: SparkSession,
    storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"),
    workdir: String = "hdfs://" + java.net.InetAddress.getLocalHost.getHostName + "/simulation/"
)
extends Serializable
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Get the working directory ensuring a slash terminator.
     */
    lazy val workdir_slash: String = if (workdir.endsWith ("/")) workdir else workdir + "/"

    /**
     * Get the scheme for the working directory.
     */
    lazy val workdir_scheme: String =
    {
        val uri = new URI (workdir_slash)
        if (null == uri.getScheme)
            ""
        else
            uri.getScheme
    }

    /**
     * Get the path component of the working directory.
     */
    lazy val workdir_path: String =
    {
        val uri = new URI (workdir_slash)
        if (null == uri.getPath)
            "/"
        else
            uri.getPath
    }

    /**
     * Get just the URI for the working directory.
     */
    lazy val workdir_uri: String =
    {
        val uri = new URI (workdir_slash)
        if (null == uri.getScheme)
            ""
        else
            uri.getScheme + "://" + (if (null == uri.getAuthority) "" else uri.getAuthority) + "/"
    }

    def hdfs_filesystem (workdir: String): FileSystem =
    {
        val hdfs_configuration = new Configuration ()
        hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
        hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
        FileSystem.get (URI.create (workdir_uri), hdfs_configuration)
    }

    def parsePermissions (s: String): java.util.Set[PosixFilePermission] =
    {
        // ToDo: parse file permissions val pattern = Pattern.compile ("\\G\\s*([ugoa]*)([+=-]+)([rwx]*)([,\\s]*)\\s*")
        val ret = new java.util.HashSet[PosixFilePermission]()
        ret.add (PosixFilePermission.OWNER_READ)
        ret.add (PosixFilePermission.OWNER_WRITE)
        ret.add (PosixFilePermission.OWNER_EXECUTE)
        ret.add (PosixFilePermission.GROUP_READ)
        ret.add (PosixFilePermission.GROUP_WRITE)
        ret.add (PosixFilePermission.GROUP_EXECUTE)
        ret.add (PosixFilePermission.OTHERS_READ)
        ret.add (PosixFilePermission.OTHERS_WRITE)
        ret.add (PosixFilePermission.OTHERS_EXECUTE)
        ret
    }

    def writeInputFile (directory: String, path: String, bytes: Array[Byte], permissions: String = null): Any =
    {
        if ((workdir_scheme == "file") || (workdir_scheme == ""))
        {
            // ToDo: check for IOException
            val file = Paths.get (workdir_path + directory + "/" + path)
            Files.createDirectories (file.getParent)
            if (null != bytes)
            {
                Files.write (file, bytes)
                if (null != permissions)
                    Files.setPosixFilePermissions (file, parsePermissions (permissions))
            }
        }
        else
        {
            val hdfs = hdfs_filesystem (workdir_uri)
            val file = new Path (workdir_slash + directory + "/" + path)
            // wrong: hdfs.mkdirs (file.getParent (), new FsPermission ("ugoa+rwx")) only permissions && umask
            // fail: FileSystem.mkdirs (hdfs, file.getParent (), new FsPermission ("ugoa+rwx")) if directory exists
            hdfs.mkdirs (file.getParent, new FsPermission ("ugo-rwx"))
            hdfs.setPermission (file.getParent, new FsPermission ("ugo-rwx")) // "-"  WTF?

            if (null != bytes)
            {
                val out = hdfs.create (file)
                out.write (bytes)
                out.close ()
                if (null != permissions)
                    hdfs.setPermission (file, new FsPermission (permissions))
            }
        }
    }

    def eraseInputFile (equipment: String)
    {
        if ((workdir_scheme == "file") || (workdir_scheme == ""))
            FileUtils.deleteQuietly (new File (workdir_path + equipment))
        else
        {
            val hdfs = hdfs_filesystem (workdir_uri)
            val directory = new Path (workdir_slash + equipment)
            hdfs.delete (directory, true)
        }
    }

    def cleanup (equipment: String, includes_glm: Boolean, includes_input: Boolean, includes_output: Boolean)
    {
        if (includes_glm)
            eraseInputFile (equipment)
        else
        {
            if (includes_input)
                eraseInputFile (equipment + "/input_data/")
            if (includes_output)
            {
                eraseInputFile (equipment + "/output_data/")
                writeInputFile (equipment, "/output_data/dummy", null) // mkdir
            }
        }
    }

    def check (input: String): (Boolean, List[String]) =
    {
        if (input.contains ("Errno") || input.contains ("command not found") || input.contains ("Cannot fork") || input.contains ("pthread_create"))
        {
            log.error ("pandapower failed, message is: " + input)
            (false, input.split ("\n").toList)
        }
        else
            (true, null)
    }

    def solve (files: RDD[String]): (Boolean, List[String]) =
    {
        // assumes pandapower is installed on every node, see https://www.pandapower.org/start/
        val pandapower =
            if ((workdir_scheme == "file") || (workdir_scheme == "")) // local[*]
            {
                Array[String](
                    "bash",
                    "-c",
                    "while read line; do " +
                        "export FILE=$line; " +
                        "ulimit -Sn `ulimit -Hn`; " +
                        "pushd " + workdir_path + "$FILE; " +
                        "python $FILE.py 2>&1 | awk '{print ENVIRON[\"FILE\"] \" \" $0}' > $FILE.out; " +
                        "cat $FILE.out; " +
                        "popd; " +
                        "done < /dev/stdin")
            }
            else // cluster, either hdfs://XX or wasb://YY
            {
                Array[String](
                    "bash",
                    "-c",
                    "while read line; do " +
                        "export FILE=$line; " +
                        "HDFS_DIR=${HADOOP_HDFS_HOME:-$HADOOP_HOME}; " +
                        "HADOOP_USER_NAME=$SPARK_USER; " +
                        "ulimit -Sn `ulimit -Hn`; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyToLocal " + workdir_path + "$FILE $FILE; " +
                        "pushd $FILE; " +
                        "python $FILE.py 2>&1 | awk '{print ENVIRON[\"FILE\"] \" \" $0}' > $FILE.out; " +
                        "popd; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyFromLocal -f $FILE/$FILE.out " + workdir_path + "$FILE/$FILE.out; " +
                        "cat $FILE/$FILE.out; " +
                        "rm -rf $FILE; " +
                        "done < /dev/stdin")
            }


        val out = files.pipe (pandapower)
        // take only the first error message
        out.map (check).fold ((true, List[String]()))((x, y) ⇒ (x._1 && y._1, if (!y._1) x._2 :+ y._2.head else x._2))
    }
}

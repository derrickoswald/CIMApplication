package ch.ninecode.pp

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

import scala.collection.JavaConverters.setAsJavaSetConverter

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
    workdir: String = s"hdfs://${java.net.InetAddress.getLocalHost.getHostName}/simulation/"
)
extends Serializable
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Get the working directory ensuring a slash terminator.
     */
    lazy val workdir_slash: String = if (workdir.endsWith ("/")) workdir else s"$workdir/"

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

    lazy val isLocal: Boolean = (workdir_scheme == "file") || (workdir_scheme == "")

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
            s"${uri.getScheme}://${if (null == uri.getAuthority) "" else uri.getAuthority}/"
    }

    def hdfs_filesystem (workdir: String): FileSystem =
    {
        val hdfs_configuration = new Configuration ()
        hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
        hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
        FileSystem.get (URI.create (workdir_uri), hdfs_configuration)
    }

    def parsePermissions (s: String): Set[PosixFilePermission] =
    {
        // ToDo: parse file permissions val pattern = Pattern.compile ("\\G\\s*([ugoa]*)([+=-]+)([rwx]*)([,\\s]*)\\s*")
        Set[PosixFilePermission] (
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_EXECUTE
        )
    }

    def writeInputFile (directory: String, path: String, bytes: Option[Array[Byte]], permissions: Option[String]): Unit =
    {
        if (isLocal)
        {
            // ToDo: check for IOException
            val file = Paths.get (s"$workdir_path$directory$path")
            val _ = Files.createDirectories (file.getParent)
            bytes.foreach (
                b =>
                {
                    val f = Files.write (file, b)
                    permissions.foreach (p => Files.setPosixFilePermissions (f, parsePermissions (p).asJava))
                }
            )
        }
        else
        {
            val hdfs = hdfs_filesystem (workdir_uri)
            val file = new Path (s"$workdir_slash$directory/$path")
            // wrong: hdfs.mkdirs (file.getParent (), new FsPermission ("ugoa+rwx")) only permissions && umask
            // fail: FileSystem.mkdirs (hdfs, file.getParent (), new FsPermission ("ugoa+rwx")) if directory exists
            val _ = hdfs.mkdirs (file.getParent, new FsPermission ("ugo-rwx"))
            hdfs.setPermission (file.getParent, new FsPermission ("ugo-rwx")) // "-"  WTF?
            bytes.foreach (
                b =>
                {
                    val out = hdfs.create (file)
                    out.write (b)
                    out.close ()
                    permissions.foreach (p => hdfs.setPermission (file, new FsPermission (p)))
                }
            )
        }
    }

    def eraseInputFile (equipment: String)
    {
        if (isLocal)
        {
            val _ = FileUtils.deleteQuietly (new File (s"$workdir_path$equipment"))
        }
        else
        {
            val hdfs = hdfs_filesystem (workdir_uri)
            val directory = new Path (s"$workdir_slash$equipment")
            val _ = hdfs.delete (directory, true)
        }
    }

    def cleanup (equipment: String, includes_glm: Boolean, includes_input: Boolean, includes_output: Boolean)
    {
        if (includes_glm)
            eraseInputFile (equipment)
        else
        {
            if (includes_input)
                eraseInputFile (s"$equipment/input_data/")
            if (includes_output)
            {
                eraseInputFile (s"$equipment/output_data/")
                writeInputFile (s"$equipment/output_data/", "dummy", None, None) // mkdir
            }
        }
    }

    def check (input: String): PandaPowerResult =
    {
        if (   input.contains ("FAILED")
            || input.contains ("Errno")
            || input.contains ("command not found")
            || input.contains ("Cannot fork")
            || input.contains ("pthread_create"))
        {
            log.error (s"pandapower failed, message is: $input")
            PandaPowerResult (false, input.split ("\n").toList)
        }
        else
            PandaPowerResult ()
    }

    def solve (files: RDD[String]): PandaPowerResult =
    {
        // assumes pandapower is installed on every node, see https://www.pandapower.org/start/
        val pandapower =
            if (isLocal) // local[*]
            {
                Array[String](
                    "bash",
                    "-c",
                    "while read line; do " +
                        "export FILE=$line; " +
                        "ulimit -Sn `ulimit -Hn`; " +
                        "pushd " + workdir_path + "$FILE; " +
                        "python3 $FILE.py > $FILE.out 2>&1; " +
                        "if [ $? -eq 0 ]; then RESULT=\"SUCCESS\"; else RESULT=\"FAILED\"; fi; { echo -n \"$RESULT \"; cat $FILE.out | awk '{print ENVIRON[\"FILE\"] \" \" $0}'; }; " +
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
                        s"HDFS_DIR=$${HADOOP_HDFS_HOME:-$$HADOOP_HOME}; " +
                        "HADOOP_USER_NAME=$SPARK_USER; " +
                        "ulimit -Sn `ulimit -Hn`; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyToLocal " + workdir_path + "$FILE $FILE; " +
                        "pushd $FILE; " +
                        "python3 $FILE.py > $FILE.out 2>&1; " +
                        "if [ $? -eq 0 ]; then RESULT=\"SUCCESS\"; else RESULT=\"FAILED\"; fi; { echo -n \"$RESULT \"; cat $FILE.out | awk '{print ENVIRON[\"FILE\"] \" \" $0}'; }; " +
                        "popd; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyFromLocal -f $FILE/$FILE.out " + workdir_path + "$FILE/$FILE.out; " +
                        "cat $FILE/$FILE.out; " +
                        "rm -rf $FILE; " +
                        "done < /dev/stdin")
            }

        val out: RDD[String] = files.pipe (pandapower)
        out.map (check).fold (PandaPowerResult ())((x, y) => x.combine (y))
    }
}
object PandaPower
{
    /**
     * The list of classes that can be persisted.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array (
            classOf[ch.ninecode.pp.PandaPowerResult]
        )
    }
}
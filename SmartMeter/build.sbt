lazy val root = (project in file(".")).
  settings(
    name := "SmartMeter",
    version := "SNAPSHOT",
    scalaVersion := "2.11.8"
  )

libraryDependencies += "ch.ninecode.cim" % "cimreader_2.11" % "2.3.2-3.4.0"
libraryDependencies += "org.apache.spark" % "spark-core_2.11" % "2.3.2"
libraryDependencies += "org.apache.spark" % "spark-sql_2.11" % "2.3.2"
libraryDependencies += "org.apache.spark" % "spark-hive-thriftserver_2.11" % "2.3.2"
libraryDependencies += "org.apache.spark" % "spark-graphx_2.11" % "2.3.2"
libraryDependencies += "org.glassfish" % "javax.json" % "1.1.4"

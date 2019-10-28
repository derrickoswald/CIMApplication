lazy val root = (project in file(".")).
  settings(
    name := "SmartMeter",
    version := "SNAPSHOT",
    scalaVersion := "2.11.12"
  )

libraryDependencies += "ch.ninecode.cim" % "cimreader_2.11" % "2.4.4-4.1.0"
libraryDependencies += "org.apache.spark" % "spark-core_2.11" % "2.4.4"
libraryDependencies += "org.apache.spark" % "spark-sql_2.11" % "2.4.4"
libraryDependencies += "org.apache.spark" % "spark-hive-thriftserver_2.11" % "2.4.4"
libraryDependencies += "org.apache.spark" % "spark-graphx_2.11" % "2.4.4"
libraryDependencies += "org.glassfish" % "javax.json" % "1.1.4"

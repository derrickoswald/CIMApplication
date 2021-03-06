lazy val root = (project in file(".")).
  settings(
    name := "SmartMeter",
    version := "SNAPSHOT",
    scalaVersion := "2.11.12"
  )

libraryDependencies += "ch.ninecode.cim" % "cimreader_2.11" % "2.4.5-4.1.3"
libraryDependencies += "org.apache.spark" % "spark-core_2.11" % "2.4.5"
libraryDependencies += "org.apache.spark" % "spark-sql_2.11" % "2.4.5"
libraryDependencies += "org.apache.spark" % "spark-hive-thriftserver_2.11" % "2.4.5"
libraryDependencies += "org.apache.spark" % "spark-graphx_2.11" % "2.4.5"
libraryDependencies += "org.glassfish" % "javax.json" % "1.1.4"

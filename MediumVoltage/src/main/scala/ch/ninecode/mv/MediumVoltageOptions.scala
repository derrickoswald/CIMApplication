package ch.ninecode.mv

import scala.collection.mutable.HashMap

case class MediumVoltageOptions (
    verbose: Boolean = false,
    cim_reader_options: Iterable[(String, String)] = new HashMap[String, String] (),
    three: Boolean = false,
    trafos: String = "",
    workdir: String = "",
    files: Seq[String] = Seq()
)

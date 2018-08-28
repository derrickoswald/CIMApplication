package ch.ninecode.lv

import scala.collection.immutable.HashMap

case class LowVoltageOptions (
    verbose: Boolean = true,
    cim_reader_options: Iterable[(String, String)] = new HashMap[String, String] (),
    three: Boolean = false,
    trafos: String = "",
    workdir: String = "",
    files: Seq[String] = Seq()
)

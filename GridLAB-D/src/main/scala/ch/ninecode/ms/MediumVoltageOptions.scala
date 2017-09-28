package ch.ninecode.ms

import scala.collection.mutable.HashMap

case class MediumVoltageOptions (
    verbose: Boolean = false,
    cim_reader_options: Iterable[(String, String)] = new HashMap[String, String] (),
    three: Boolean = false,
    trafos: String = "",
    short_circuit: String = "",
    workdir: String = "",
    files: Seq[String] = Seq()
)

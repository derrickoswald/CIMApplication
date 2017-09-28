package ch.ninecode.export

import scala.collection.mutable.HashMap

case class ExportOptions (
    verbose: Boolean = true,
    cim_reader_options: Iterable[(String, String)] = new HashMap[String, String] (),
    three: Boolean = false,
    trafos: String = "",
    workdir: String = "",
    files: Seq[String] = Seq()
)


package ch.ninecode.sc

import scala.io.Source

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.net.Net
import ch.ninecode.testutil.TestUtil
import ch.ninecode.util.Util

class SCTestBase extends TestUtil
{
    val FILE_DEPOT = "data/"
    override val classesToRegister: Array[Class[_]] = Array.concat(
        CIMClasses.list,
        GridLABD.classes,
        Net.classes,
        ShortCircuit.classes,
        Util.classes)
    def readTrafoFile (filename: String): Array[String]=
    {
        using (Source.fromFile(filename, "UTF-8"))(
            source =>
            {
                source.getLines().filter(_ != "").toArray
            }
        )
    }
}

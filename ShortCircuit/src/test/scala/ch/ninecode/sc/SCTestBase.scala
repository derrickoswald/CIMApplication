package ch.ninecode.sc

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.util.TestUtil

class SCTestBase extends TestUtil
{
    val FILE_DEPOT = "data/"
    override val classesToRegister = Array (CIMClasses.list, GridLABD.classes, ShortCircuit.classes)

}

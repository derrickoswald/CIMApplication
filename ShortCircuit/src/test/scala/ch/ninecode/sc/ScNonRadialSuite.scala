package ch.ninecode.sc

import org.apache.spark.sql.SparkSession

import ch.ninecode.sc.branch.Branch
import ch.ninecode.sc.branch.ComplexBranch
import ch.ninecode.sc.branch.SimpleBranch
import ch.ninecode.util.Complex

class ScNonRadialSuite extends SCTestBase
{
    test("Nodal Admittance Matrix")
    {
        session: SparkSession =>

            val trafo = "TRAFO_GND"
            val mrid = "b1_HAS"

            def branchImpedance(R: Double): Impedanzen = {
                Impedanzen(Complex(0), Complex(0), Complex(R), Complex(0))
            }

            // WIK-1908 for situation picture
            val branches = Array[Branch](
                SimpleBranch(trafo, "b2", 0.0, "R_ab", "", None, "", branchImpedance(2)),
                SimpleBranch(trafo, "b3", 0.0, "R_ac", "", None, "", branchImpedance(1)),
                SimpleBranch(trafo, "b4", 0.0, "R31", "", None, "", branchImpedance(3)),
                SimpleBranch("b2", "b3", 0.0, "R_bc", "", None, "", branchImpedance(3)),
                SimpleBranch("b2", mrid, 0.0, "R11", "", None, "", branchImpedance(1)),
                SimpleBranch("b4", mrid, 0.0, "R21", "", None, "", branchImpedance(2)),
                SimpleBranch("b4", "b3", 0.0, "R41", "", None, "", branchImpedance(4)),
            )
            val complexBranch = ComplexBranch(Array(trafo), mrid, 0.0, branches)

            val impedance = complexBranch.z(Impedanzen())
            val expectedImpedance = 1.507
            near(impedance.impedanz_high.re, expectedImpedance, epsilon = 1.0e-3, s"impedance should be $expectedImpedance instead of $impedance")
    }

    test("Nodal Admittance Matrix with 2 transformer")
    {
        session: SparkSession =>

            val trafo_hv_nodes = Array("Trafo1_HV", "Trafo2_HV")
            val mrid = "b1_HAS"

            def branchImpedance(R: Double): Impedanzen = {
                Impedanzen(Complex(0), Complex(0), Complex(R), Complex(0))
            }

            // WIK-1908 for situation picture
            val branches = Array[Branch](
                SimpleBranch(trafo_hv_nodes(0), "b5", 0.0, "R_trafo1", "", None, "", branchImpedance(4)),
                SimpleBranch(trafo_hv_nodes(1), "b3", 0.0, "R_trafo2", "", None, "", branchImpedance(5)),
                SimpleBranch("b2", mrid, 0.0, "R11", "", None, "", branchImpedance(1)),
                SimpleBranch("b2", "b3", 0.0, "R_bc", "", None, "", branchImpedance(3)),
                SimpleBranch("b2", "b5", 0.0, "R_ab", "", None, "", branchImpedance(2)),
                SimpleBranch("b3", "b4", 0.0, "R41", "", None, "", branchImpedance(4)),
                SimpleBranch("b3", "b5", 0.0, "R_ac", "", None, "", branchImpedance(1)),
                SimpleBranch("b4", mrid, 0.0, "R21", "", None, "", branchImpedance(2)),
                SimpleBranch("b4", "b5", 0.0, "R31", "", None, "", branchImpedance(3)),
            )
            val complexBranch = ComplexBranch(trafo_hv_nodes, mrid, 0.0, branches)

            val impedance = complexBranch.z(Impedanzen())
            val expectedImpedance = 3.605
            near(impedance.impedanz_high.re, expectedImpedance, epsilon = 1.0e-3, s"impedance should be $expectedImpedance instead of $impedance")
    }
}

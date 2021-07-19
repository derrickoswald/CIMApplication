package ch.ninecode.sc.branch

import breeze.linalg.Axis
import breeze.linalg.DenseMatrix
import breeze.linalg.DenseVector
import breeze.linalg.inv
import breeze.linalg.sum

import ch.ninecode.sc.Impedanzen
import ch.ninecode.sc.ScBranches
import ch.ninecode.sc.ShortCircuitOptions
import ch.ninecode.util.Complex

/**
 * A group of elements too complex to reduce to a combination of series and parallel branches.
 *
 * @param trafo_hv_nodes all trafo hv pin nodes as the 'from' node,
 * @param to             the 'to' node
 * @param current        the current through this branch in the GridLAB-D experiment
 * @param basket         the branches in no particular order
 */
case class ComplexBranch (
    trafo_hv_nodes: Array[String],
    override val to: String,
    override val current: Double,
    basket: Array[Branch]) extends Branch(trafo_hv_nodes.mkString("_"), to, current)
{
    override def toString: String =
        s"""ComplexBranch ("$from" ⇒ "$to" ${current}A ${basket.map(_.toString).mkString("[", ",", "]")})"""

    def asString: String = basket.map(_.asString).mkString("{", ",", "}")

    def asFuse: String = basket.map(_.asFuse).mkString("{", ",", "}")

    def asId: String = basket.map(_.asId).mkString("{", ",", "}")

    def seq: Seq[ComplexBranch] = Seq(this)

    def iter: Iterable[Branch] = basket

    def lastFuses: Iterable[Branch] =
    {
        justFuses match
        {
            case Some(fuses) => Seq(fuses)
            case None => Seq()
        }
    }

    def justFuses: Option[Branch] =
    {
        val fuses = basket.flatMap(_.justFuses)
        if (1 == fuses.length)
            fuses.head match
            {
                case b: SimpleBranch =>
                    Some(SimpleBranch(from, to, current, b.mRID, b.name, b.rating, b.standard, b.z))
                case p: ParallelBranch =>
                    Some(ParallelBranch(from, to, current, p.parallel))
                case s: SeriesBranch =>
                    Some(SeriesBranch(from, to, current, s.series))
                case c: ComplexBranch =>
                    Some(ComplexBranch(trafo_hv_nodes, to, current, c.basket))
            }
        else
            if (fuses.nonEmpty)
                Some(ComplexBranch(trafo_hv_nodes, to, current, fuses))
            else
                None
    }

    def reverse: Branch = ComplexBranch(trafo_hv_nodes, to, current, basket.map(_.reverse))

    def ratios: Iterable[(Double, Branch)] = basket.map(x => (x.current / current, x))

    def voltageRatio: Double = basket.foldLeft(1.0)((v, branch) => v * branch.voltageRatio)

    /**
     * @return a fake impedance value
     */
    def z (in: Impedanzen): Impedanzen =
    {
        // TODO: get all, not only get real of impedance_high
        val real: Double = getImpedanceForComplexBranches
        val branch_impedance = Impedanzen(Complex(0), Complex(0), Complex(real, 0), Complex(0))

        // TODO: convert "in" from middle voltage to low voltage, e.g see ParallelBranch
        branch_impedance + in
    }

    def contents: Iterable[Branch] = basket

    def checkFuses (ik: Double, options: ShortCircuitOptions): (Boolean, Option[Branch]) =
    {
        val new_complex: Iterable[(Boolean, Option[Branch])] = ratios.map(
            (pair: (Double, Branch)) =>
            {
                val (fraction, branch) = pair
                val current = fraction * Math.abs(ik)
                if (current.isNaN)
                    (false, None)
                else
                    branch.checkFuses(current, options)
            }
        )
        val blows = new_complex.exists(_._1)
        if (blows)
            (blows, Some(ComplexBranch(trafo_hv_nodes, to, current, new_complex.flatMap(_._2).toArray)))
        else
            (false, Some(this))
    }

    def getAdmittanceForBuses (bus1: String, bus2: String): Double =
    {
        val impedance = basket.filter(b => (b.from == bus1 || b.to == bus1) && (b.from == bus2 || b.to == bus2))
        val admittance: Double = impedance.headOption match
        {
            // TODO: calculate impedance for low and high and null impedance
            case Some(impedance) => impedance.z(Impedanzen()).impedanz_high.reciprocal.re
            case None => Impedanzen().impedanz_high.re
        }
        admittance
    }

    private def getAdmittanceForTrafo (all_buses: List[String], row: Int): Double =
    {
        val trafo_admittances = trafo_hv_nodes.map(trafo_node =>
        {
            getAdmittanceForBuses(trafo_node, all_buses(row))
        })
        val trafo_values = trafo_admittances.filter(_ != 0.0)
        if (trafo_values.isEmpty)
        {
            0.0
        } else
        {
            trafo_values(0)
        }
    }

    private def getAdmittanceDiagValues (nodalAdmittanceMatrix: DenseMatrix[Double]) =
    {
        val helperMatrix: DenseMatrix[Double] = DenseMatrix.tabulate(nodalAdmittanceMatrix.rows, nodalAdmittanceMatrix.cols)((i, j) =>
        {
            if (i == j)
            {
                1
            } else
            {
                -1
            }
        })
        // a_{i,i} = y_i + sum[(y_{i,j}, for all j != i)]
        sum(nodalAdmittanceMatrix *:* helperMatrix, Axis._1)
    }

    private def replaceMainDiagonal (nodalAdmittanceMatrix: DenseMatrix[Double], admittanceValues: DenseVector[Double]): Unit =
    {
        var index = 0
        while (index < admittanceValues.size)
        {
            nodalAdmittanceMatrix(index, index) = admittanceValues(index)
            index += 1
        }
    }

    private def getInitialNodalAdmittanceMatrix (all_buses: List[String]): DenseMatrix[Double] =
    {
        DenseMatrix.tabulate(all_buses.size, all_buses.size)((row, col) =>
        {
            if (row == col)
            {
                getAdmittanceForTrafo(all_buses, row)
            } else
            {
                -getAdmittanceForBuses(all_buses(row), all_buses(col))
            }
        })
    }

    def getImpedanceForComplexBranches: Double =
    {
        val all_buses: List[String] = new ScBranches().get_all_nodes(basket).filter(!trafo_hv_nodes.contains(_)).toList
        val hak_index = all_buses.indexOf(to)

        val nodalAdmittanceMatrix: DenseMatrix[Double] = getInitialNodalAdmittanceMatrix(all_buses)

        val admittanceValues = getAdmittanceDiagValues(nodalAdmittanceMatrix)
        replaceMainDiagonal(nodalAdmittanceMatrix, admittanceValues)

        // Y^(-1)*I=V
        val impedanceMatrix: DenseMatrix[Double] = inv(nodalAdmittanceMatrix)
        val impedance = impedanceMatrix(hak_index, hak_index)
        impedance
    }
}

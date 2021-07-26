package ch.ninecode.sc.branch

import org.ojalgo.function.BinaryFunction
import org.ojalgo.matrix.store.MatrixStore
import org.ojalgo.matrix.store.SparseStore
import org.ojalgo.matrix.task.InverterTask
import org.ojalgo.scalar.ComplexNumber
import org.ojalgo.structure.Access1D

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
        // TODO: get all, not only get impedance of impedance_high
        val impedance: ComplexNumber = getImpedanceForComplexBranches
        val branch_impedance = Impedanzen(Complex(0), Complex(0), Complex(impedance.getReal, impedance.getImaginary), Complex(0))

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

    def getAdmittanceForBuses (bus1: String, bus2: String): ComplexNumber =
    {
        val impedance = basket.filter(b => (b.from == bus1 || b.to == bus1) && (b.from == bus2 || b.to == bus2))
        val admittance = impedance.headOption match
        {
            // TODO: calculate impedance for low and high and null impedance
            case Some(impedance) =>
                val reciprocal = impedance.z(Impedanzen()).impedanz_high.reciprocal
                ComplexNumber.of(reciprocal.re, reciprocal.im)
            case None => ComplexNumber.of(Impedanzen().impedanz_high.re, Impedanzen().impedanz_high.im)
        }
        admittance
    }

    private def getAdmittanceForTrafo (all_buses: List[String], row: Int): ComplexNumber =
    {
        val trafo_admittances = trafo_hv_nodes.map(trafo_node =>
        {
            getAdmittanceForBuses(trafo_node, all_buses(row))
        })
        val trafo_values = trafo_admittances.filter(_.getReal != 0.0)
        if (trafo_values.isEmpty)
        {
            ComplexNumber.of(0.0, 0.0)
        } else
        {
            trafo_values(0)
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf","deprecation"))
    private def getAdmittanceDiagValues (nodalAdmittanceMatrix: MatrixStore[ComplexNumber]): Access1D[ComplexNumber] =
    {
        val storeFactory = MatrixStore.COMPLEX
        val testFactory = MatrixStore.COMPLEX
        val storeResultMatrix = testFactory.makeSparse(nodalAdmittanceMatrix.countRows(),nodalAdmittanceMatrix.countColumns())
        val storeHelperMatrix: SparseStore[ComplexNumber] = storeFactory.makeSparse(nodalAdmittanceMatrix.countRows(),nodalAdmittanceMatrix.countColumns())
        for (i <- 0 until nodalAdmittanceMatrix.countRows().toInt) {
            for (j <- 0 until nodalAdmittanceMatrix.countColumns().toInt) {
                val value = if (i == j) {
                    1
                } else {
                    -1
                }
                storeHelperMatrix.set(i,j,ComplexNumber.of(value,0.0))
            }
        }

        class customComplexMulty() extends BinaryFunction[ComplexNumber]  {
            override def invoke (arg1: Double, arg2: Double): Double =
            {
                arg1*arg2
            }

            override def invoke (arg1: ComplexNumber, arg2: ComplexNumber): ComplexNumber =
            {
                arg1.multiply(arg2)
            }
        }

        nodalAdmittanceMatrix.onMatching(
            new customComplexMulty(),
            storeHelperMatrix.asInstanceOf[MatrixStore[ComplexNumber]]
        ).supplyTo(storeResultMatrix)
        storeResultMatrix.sliceDiagonal()
    }

    private def getInitialNodalAdmittanceMatrix (all_buses: List[String]): MatrixStore[ComplexNumber] =
    {
        val matrix_size = all_buses.size
        val storeFactory = SparseStore.COMPLEX
        val storeInitialNodalAdmittanceMatrix = storeFactory.make(matrix_size, matrix_size)
        for (row <- 0 until matrix_size)
        {
            for (col <- 0 until matrix_size)
            {
                val admittanceValue: ComplexNumber = if (row == col)
                {
                    getAdmittanceForTrafo(all_buses, row)
                } else
                {
                    ComplexNumber.of(-1,0).multiply(getAdmittanceForBuses(all_buses(row), all_buses(col)))
                }
                storeInitialNodalAdmittanceMatrix.set(row,col,admittanceValue)
            }
        }
        storeInitialNodalAdmittanceMatrix.get()
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def getImpedanceForComplexBranches: ComplexNumber =
    {
        val all_buses: List[String] = new ScBranches().get_all_nodes(basket).filter(!trafo_hv_nodes.contains(_)).toList
        val hak_index = all_buses.indexOf(to)

        val nodalAdmittanceMatrix: SparseStore[ComplexNumber] = getInitialNodalAdmittanceMatrix(all_buses).asInstanceOf[SparseStore[ComplexNumber]]

        val admittanceValues = getAdmittanceDiagValues(nodalAdmittanceMatrix)
        nodalAdmittanceMatrix.fillDiagonal(admittanceValues)

        // Y^(-1)*I=V
        val inverter = InverterTask.COMPLEX.make(nodalAdmittanceMatrix)
        val inv = inverter.invert(nodalAdmittanceMatrix)
        val impedanceMatrix: MatrixStore[ComplexNumber] = inv.get()
//        println(f"hak_index: ${hak_index}")
        val impedance = impedanceMatrix.get(hak_index, hak_index)
//        println(impedance)
        impedance
    }
}

package ch.ninecode.sc.branch

import org.ojalgo.RecoverableCondition
import org.ojalgo.function.BinaryFunction
import org.ojalgo.function.aggregator.Aggregator
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
        var lastFuseBranches = Set[Branch]()
        val directs: Iterable[Branch] = basket.filter(b => to == b.to || to == b.from)
        for (direct <- directs)
        {
            lastFuseBranches ++= getLastFuseBranches(direct, basket)
        }
        lastFuseBranches
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

    override def justLastFuses: Iterable[Branch] =
    {
        lastFuses.toList.flatMap(_.justLastFuses)
    }

    def reverse: Branch = ComplexBranch(trafo_hv_nodes, to, current, basket.map(_.reverse))

    private def getLastFuseBranches (branch: Branch, branches: Iterable[Branch]): Set[Branch] =
    {
        if (branch.justFuses.size < 1)
        {
            val newBranches: Iterable[Branch] = branches.filter(branch.from == _.to)
            val newFuseBranches: Iterable[Branch] = newBranches.flatMap((b: Branch) => getLastFuseBranches(b, branches))
            newFuseBranches.toSet
        } else
        {
            Set(branch)
        }
    }

    def ratios: Iterable[(Double, Branch)] =
    {
        lastFuses.toList.map(x => (x.current / current, x))
    }

    def voltageRatio: Double = basket.foldLeft(1.0)((v, branch) => v * branch.voltageRatio)

    /**
     * @return a fake impedance value
     */
    def z (in: Impedanzen): Impedanzen =
    {
        val impedance: Impedanzen = getImpedanceForComplexBranches

        val prev_impedance = getTransformerBranch match
        {
            case Some(trafoBranch) => trafoBranch.convert_impedance(in)
            case _ => in
        }

        impedance + prev_impedance
    }

    def getTransformerBranch: Option[TransformerBranch] =
    {
        val trafoBranches = basket.filter
        {
            case _: TransformerBranch => true
            case _ => false
        }

        trafoBranches.headOption match
        {
            case Some(branch) =>
                branch match
                {
                    case trafo: TransformerBranch => Option(trafo)
                    case series: SeriesBranch => series.getTransformerBranch
                    case _ => None
                }
            case None => None
        }
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

    def getAdmittanceForBuses (bus1: String, bus2: String): Impedanzen =
    {
        val impedance = basket.filter(b => (b.from == bus1 || b.to == bus1) && (b.from == bus2 || b.to == bus2))
        val admittance = impedance.headOption match
        {
            case Some(impedance) =>
                val tmp = impedance.z(Impedanzen())
                List(tmp.impedanz_low, tmp.null_impedanz_low, tmp.impedanz_high, tmp.null_impedanz_high).map((compl) =>
                {
                    if (compl.re != 0.0){
                        compl.reciprocal
                    } else {
                        compl
                    }
                })
            case None            =>
                val tmp = Impedanzen()
                List(tmp.impedanz_low, tmp.null_impedanz_low, tmp.impedanz_high, tmp.null_impedanz_high)
        }
        val reciprocal_low = admittance(0)
        val reciprocal_null_low = admittance(1)
        val reciprocal_high = admittance(2)
        val reciprocal_null_high = admittance(3)
        Impedanzen(
            Complex(reciprocal_low.re,reciprocal_low.im),
            Complex(reciprocal_null_low.re, reciprocal_null_low.im),
            Complex(reciprocal_high.re, reciprocal_high.im),
            Complex(reciprocal_null_high.re, reciprocal_null_high.im)
        )
    }

    private def getAdmittanceForTrafo (all_buses: List[String], row: Int): Impedanzen =
    {
        val trafo_admittances = trafo_hv_nodes.map(trafo_node =>
        {
            getAdmittanceForBuses(trafo_node, all_buses(row))
        })

        def filterNonResults(impedanzen: Impedanzen): Boolean =
        {
            impedanzen.impedanz_low.re != 0.0 || impedanzen.null_impedanz_low.re != 0.0 ||
                impedanzen.impedanz_high.re != 0.0 || impedanzen.null_impedanz_high.re != 0.0
        }

        val trafo_values = trafo_admittances.filter(filterNonResults)
        if (trafo_values.isEmpty)
        {
            Impedanzen()
        } else
        {
            trafo_values(0)
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf","deprecation"))
    private def getAdmittanceDiagValues (nodalAdmittanceMatrix: List[MatrixStore[ComplexNumber]]): List[Access1D[ComplexNumber]] =
    {
        val storeFactory = MatrixStore.COMPLEX

        nodalAdmittanceMatrix.map((partAdmittanceMatrix) =>
        {
            val storeResultMatrix: SparseStore[ComplexNumber] = storeFactory.makeSparse(partAdmittanceMatrix.countRows(), 1)
            val storeTempMatrix: SparseStore[ComplexNumber] = storeFactory.makeSparse(partAdmittanceMatrix.countRows(), partAdmittanceMatrix.countColumns())
            val storeHelperMatrix: SparseStore[ComplexNumber] = storeFactory.makeSparse(partAdmittanceMatrix.countRows(), partAdmittanceMatrix.countColumns())
            for (i <- 0 until partAdmittanceMatrix.countRows().toInt)
            {
                for (j <- 0 until partAdmittanceMatrix.countColumns().toInt)
                {
                    val value = if (i == j)
                    {
                        1
                    } else
                    {
                        -1
                    }
                    storeHelperMatrix.set(i, j, ComplexNumber.of(value, 0.0))
                }
            }

            class customComplexMulti () extends BinaryFunction[ComplexNumber]
            {
                override def invoke (arg1: Double, arg2: Double): Double =
                {
                    arg1 * arg2
                }

                override def invoke (arg1: ComplexNumber, arg2: ComplexNumber): ComplexNumber =
                {
                    arg1.multiply(arg2)
                }
            }

            partAdmittanceMatrix.onMatching(
                new customComplexMulti(),
                storeHelperMatrix.asInstanceOf[MatrixStore[ComplexNumber]]
            ).supplyTo(storeTempMatrix)
            storeTempMatrix.reduceRows(Aggregator.SUM).supplyTo(storeResultMatrix)
            storeResultMatrix.sliceColumn(0)
        })
    }

    private def getInitialNodalAdmittanceMatrix (all_buses: List[String]): List[MatrixStore[ComplexNumber]] =
    {
        val matrix_size = all_buses.size
        val storeFactory = SparseStore.COMPLEX
        val storeInitialNodalAdmittanceMatrixLow = storeFactory.make(matrix_size, matrix_size)
        val storeInitialNodalAdmittanceMatrixNullLow = storeFactory.make(matrix_size, matrix_size)
        val storeInitialNodalAdmittanceMatrixHigh = storeFactory.make(matrix_size, matrix_size)
        val storeInitialNodalAdmittanceMatrixNullHigh = storeFactory.make(matrix_size, matrix_size)
        val MINUS_ONE = ComplexNumber.of(-1, 0)
        for (row <- 0 until matrix_size)
        {
            for (col <- 0 until matrix_size)
            {
                val admittanceValue: List[ComplexNumber] = if (row == col)
                {
                    val admittanceValues = getAdmittanceForTrafo(all_buses, row)
                    val addmittanceValues_low = admittanceValues.impedanz_low
                    val addmittanceValues_null_low = admittanceValues.null_impedanz_low
                    val addmittanceValues_high = admittanceValues.impedanz_high
                    val addmittanceValues_null_high = admittanceValues.null_impedanz_high
                    List[ComplexNumber](
                        ComplexNumber.of(addmittanceValues_low.re,addmittanceValues_low.im),
                        ComplexNumber.of(addmittanceValues_null_low.re,addmittanceValues_null_low.im),
                        ComplexNumber.of(addmittanceValues_high.re,addmittanceValues_high.im),
                        ComplexNumber.of(addmittanceValues_null_high.re,addmittanceValues_null_high.im)
                    )
                } else
                {
                    val admittanceValues = getAdmittanceForBuses(all_buses(row), all_buses(col))
                    val addmittanceValues_low = admittanceValues.impedanz_low
                    val addmittanceValues_null_low = admittanceValues.null_impedanz_low
                    val addmittanceValues_high = admittanceValues.impedanz_high
                    val addmittanceValues_null_high = admittanceValues.null_impedanz_high
                    List[ComplexNumber](
                        ComplexNumber.of(addmittanceValues_low.re,addmittanceValues_low.im).multiply(MINUS_ONE),
                        ComplexNumber.of(addmittanceValues_null_low.re,addmittanceValues_null_low.im).multiply(MINUS_ONE),
                        ComplexNumber.of(addmittanceValues_high.re,addmittanceValues_high.im).multiply(MINUS_ONE),
                        ComplexNumber.of(addmittanceValues_null_high.re,addmittanceValues_null_high.im).multiply(MINUS_ONE)
                    )
                }
                storeInitialNodalAdmittanceMatrixLow.set(row,col,admittanceValue(0))
                storeInitialNodalAdmittanceMatrixNullLow.set(row,col,admittanceValue(1))
                storeInitialNodalAdmittanceMatrixHigh.set(row,col,admittanceValue(2))
                storeInitialNodalAdmittanceMatrixNullHigh.set(row,col,admittanceValue(3))
            }
        }
        List[MatrixStore[ComplexNumber]](
            storeInitialNodalAdmittanceMatrixLow.get(),
            storeInitialNodalAdmittanceMatrixNullLow.get(),
            storeInitialNodalAdmittanceMatrixHigh.get(),
            storeInitialNodalAdmittanceMatrixNullHigh.get()
        )
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def getImpedanceForComplexBranches: Impedanzen =
    {
        val all_buses: List[String] = new ScBranches().get_all_nodes(basket).filter(!trafo_hv_nodes.contains(_)).toList
        val hak_index = all_buses.indexOf(to)
        if (hak_index == -1) {
            Impedanzen()
        } else
        {

            val nodalAdmittanceMatrix: List[SparseStore[ComplexNumber]] = getInitialNodalAdmittanceMatrix(all_buses).asInstanceOf[List[SparseStore[ComplexNumber]]]

            val admittanceValues: List[Access1D[ComplexNumber]] = getAdmittanceDiagValues(nodalAdmittanceMatrix)
            val impedanzen: List[ComplexNumber] = nodalAdmittanceMatrix.zipWithIndex.map
            {
                (partAdmittanceMatrixWithIndex) =>
                {
                    val partAdmittanceMatrix = partAdmittanceMatrixWithIndex._1
                    val index = partAdmittanceMatrixWithIndex._2
                    partAdmittanceMatrix.fillDiagonal(admittanceValues(index))


                    // Y^(-1)*I=V
                    val inverter = InverterTask.COMPLEX.make(partAdmittanceMatrix)

                    try
                    {
                        val inv = inverter.invert(partAdmittanceMatrix)
                        val impedanceMatrix: MatrixStore[ComplexNumber] = inv.get()
                        impedanceMatrix.get(hak_index, hak_index)
                    } catch {
                        case _: RecoverableCondition => {
                            ComplexNumber.ZERO
                        }
                    }
                }
            }

            val impedanzen_low: ComplexNumber = impedanzen(0)
            val null_impedanzen_low: ComplexNumber = impedanzen(1)
            val impedanzen_high: ComplexNumber = impedanzen(2)
            val null_impedanzen_high: ComplexNumber = impedanzen(3)
            Impedanzen(
                Complex(impedanzen_low.getReal, impedanzen_low.getImaginary),
                Complex(null_impedanzen_low.getReal, null_impedanzen_low.getImaginary),
                Complex(impedanzen_high.getReal, impedanzen_high.getImaginary),
                Complex(null_impedanzen_high.getReal, null_impedanzen_high.getImaginary),
            )
        }
    }
}

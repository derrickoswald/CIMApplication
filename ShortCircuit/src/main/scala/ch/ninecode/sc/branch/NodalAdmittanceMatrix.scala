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
import ch.ninecode.util.Complex

object ImpedanceType extends Enumeration {
    val IMPEDANZ_LOW = "impedanz_low"
    val NULL_IMPEDANZ_LOW = "null_impedanz_low"
    val IMPEDANZ_HIGH = "impedanz_high"
    val NULL_IMPEDANZ_HIGH = "null_impedanz_high"
}

case class NodalAdmittanceMatrix(branches: Iterable[Branch], trafo_hv_nodes: Array[String], to: String)
{
    def getImpedanceForComplexBranches: Impedanzen =
    {
        val buses: List[String] = new ScBranches().get_all_nodes(branches).filter(!trafo_hv_nodes.contains(_)).toList
        val hak_index = buses.indexOf(to)
        if (hak_index == -1) {
            Impedanzen()
        } else
        {
            val nodalAdmittanceMatrix: Map[String, SparseStore[ComplexNumber]] = getInitialNodalAdmittanceMatrix(buses)
            val admittanceValues: Map[String, Access1D[ComplexNumber]] = getAdmittanceDiagValues(nodalAdmittanceMatrix)

            val impedanzenMap: Map[String, Complex] = nodalAdmittanceMatrix.map
            {
                partAdmittanceMatrixWithIndex =>
                {
                    val impedance_type = partAdmittanceMatrixWithIndex._1
                    val partAdmittanceMatrix = partAdmittanceMatrixWithIndex._2
                    partAdmittanceMatrix.fillDiagonal(admittanceValues(impedance_type))

                    // Y^(-1)*I=V
                    val inverter = InverterTask.COMPLEX.make(partAdmittanceMatrix)

                    val impedance: ComplexNumber = try
                    {
                        val inv = inverter.invert(partAdmittanceMatrix)
                        val impedanceMatrix: MatrixStore[ComplexNumber] = inv.get()
                        impedanceMatrix.get(hak_index, hak_index)
                    } catch {
                        case _: RecoverableCondition => ComplexNumber.ZERO
                    }

                    (impedance_type, Complex(impedance.getReal, impedance.getImaginary))
                }
            }

            Impedanzen(
                impedanzenMap(ImpedanceType.IMPEDANZ_LOW),
                impedanzenMap(ImpedanceType.NULL_IMPEDANZ_LOW),
                impedanzenMap(ImpedanceType.IMPEDANZ_HIGH),
                impedanzenMap(ImpedanceType.NULL_IMPEDANZ_HIGH),
            )
        }
    }

    private def getAdmittanceForBuses (bus1: String, bus2: String): Impedanzen =
    {
        val impedance = branches.filter(b => (b.from == bus1 || b.to == bus1) && (b.from == bus2 || b.to == bus2))
        val admittance = impedance.headOption match
        {
            case Some(impedance) =>
                val tmp = impedance.z(Impedanzen())
                List(tmp.impedanz_low, tmp.null_impedanz_low, tmp.impedanz_high, tmp.null_impedanz_high).map(compl =>
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

    private def getAdmittanceForTrafo (bus: String): Impedanzen =
    {
        val trafo_admittances = trafo_hv_nodes.map(trafo_node =>
        {
            getAdmittanceForBuses(trafo_node, bus)
        })

        def filterNonResults(impedanzen: Impedanzen): Boolean =
        {
            impedanzen.impedanz_low.re != 0.0 || impedanzen.null_impedanz_low.re != 0.0 ||
                impedanzen.impedanz_high.re != 0.0 || impedanzen.null_impedanz_high.re != 0.0
        }

        val trafo_values: Iterable[Impedanzen] = trafo_admittances.filter(filterNonResults)
        trafo_values.headOption match {
            case Some(trafo_value) => trafo_value
            case None => Impedanzen()
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf","deprecation"))
    private def getAdmittanceDiagValues (
        nodalAdmittanceMatrix: Map[String, SparseStore[ComplexNumber]]): Map[String, Access1D[ComplexNumber]] =
    {
        val storeFactory = MatrixStore.COMPLEX

        nodalAdmittanceMatrix.map(partMatrix =>
        {
            val impedance_type = partMatrix._1
            val partAdmittanceMatrix = partMatrix._2

            val rowCount = partAdmittanceMatrix.countRows()
            val colCount = partAdmittanceMatrix.countColumns()

            val storeResultMatrix: SparseStore[ComplexNumber] = storeFactory.makeSparse(rowCount, 1)
            val storeTempMatrix: SparseStore[ComplexNumber] = storeFactory.makeSparse(rowCount, colCount)
            val storeHelperMatrix: SparseStore[ComplexNumber] = storeFactory.makeSparse(rowCount, colCount)

            for (i <- 0 until rowCount.toInt)
            {
                for (j <- 0 until colCount.toInt)
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

            (impedance_type, storeResultMatrix.sliceColumn(0))
        })
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    private def getInitialNodalAdmittanceMatrix (all_buses: List[String]): Map[String, SparseStore[ComplexNumber]] =
    {
        val matrix_size = all_buses.size
        val storeFactory = SparseStore.COMPLEX
        val MINUS_ONE = ComplexNumber.of(-1, 0)

        val storeInitialNodalAdmittanceMatrix = Map(
            ImpedanceType.IMPEDANZ_LOW -> storeFactory.make(matrix_size, matrix_size),
            ImpedanceType.NULL_IMPEDANZ_LOW -> storeFactory.make(matrix_size, matrix_size),
            ImpedanceType.IMPEDANZ_HIGH -> storeFactory.make(matrix_size, matrix_size),
            ImpedanceType.NULL_IMPEDANZ_HIGH -> storeFactory.make(matrix_size, matrix_size))

        def setAdmittanceInMatrix(impedance_type: String, row: Int, col: Int, complex: Complex): Unit = {
            var admittanceValue = ComplexNumber.of(complex.re,complex.im)
            if (row != col)
                admittanceValue = admittanceValue.multiply(MINUS_ONE)
            storeInitialNodalAdmittanceMatrix(impedance_type).set(row, col, admittanceValue)
        }

        for (row <- 0 until matrix_size)
        {
            for (col <- 0 until matrix_size)
            {
                val admittanceValues: Impedanzen = if (row == col)
                    getAdmittanceForTrafo(all_buses(row))
                else
                    getAdmittanceForBuses(all_buses(row), all_buses(col))

                setAdmittanceInMatrix(ImpedanceType.IMPEDANZ_LOW, row, col, admittanceValues.impedanz_low)
                setAdmittanceInMatrix(ImpedanceType.NULL_IMPEDANZ_LOW, row, col, admittanceValues.null_impedanz_low)
                setAdmittanceInMatrix(ImpedanceType.IMPEDANZ_HIGH, row, col, admittanceValues.impedanz_high)
                setAdmittanceInMatrix(ImpedanceType.NULL_IMPEDANZ_HIGH, row, col, admittanceValues.null_impedanz_high)
            }
        }

        storeInitialNodalAdmittanceMatrix.map((matrix: (String, SparseStore[ComplexNumber])) => {
            val impedance_type = matrix._1
            val sparseNodalMatrix = matrix._2.get().asInstanceOf[SparseStore[ComplexNumber]]
            (impedance_type, sparseNodalMatrix)
        })
    }
}

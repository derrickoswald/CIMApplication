package ch.ninecode.sp

import org.apache.spark.sql.Row

import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element

case class PositionedEnergyConsumer
(
    override val sup: ConductingEquipment,
    customerCount: Int,
    grounded: Boolean,
    p: Double,
    pfixed: Double,
    pfixedPct: Double,
    phaseConnection: String,
    q: Double,
    qfixed: Double,
    qfixedPct: Double,
    LoadDynamics: String,
    LoadResponse: String,
    PowerCutZone: String,
    xPosition: String,
    yPosition: String
)
    extends
        Element
{
    def this () = { this (null, 0, false, 0.0, 0.0, 0.0, null, 0.0, 0.0, 0.0, null, null, null, "", "") }
    def ConductingEquipment: ConductingEquipment = sup.asInstanceOf[ConductingEquipment]
    override def copy (): Row = { clone ().asInstanceOf[PositionedEnergyConsumer] }
    override def get (i: Int): Object =
    {
        if (i < productArity)
            productElement (i).asInstanceOf[AnyRef]
        else
            throw new IllegalArgumentException ("invalid property index " + i)
    }
    override def length: Int = productArity
}


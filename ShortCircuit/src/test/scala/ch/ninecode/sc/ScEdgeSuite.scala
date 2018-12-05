package ch.ninecode.sc

import org.scalatest.FunSuite

import ch.ninecode.cim.CHIM

class ScEdgeSuite extends FunSuite
{
    /**
     * See example 4.6.1 Motor Start-up in DACHCZ Technical Rules for the Assessment of Network Disturbances
     */
    test ("ShortCircuitTrace: tracing should not continue on voltage level below 230")
    {
        val xml =
            """
              |	<cim:PowerTransformer rdf:ID="ID123">
              |		<cim:IdentifiedObject.name>Klemme Woertz</cim:IdentifiedObject.name>
              |		<cim:IdentifiedObject.aliasName>50191779:nis_el_int_transformer</cim:IdentifiedObject.aliasName>
              |		<cim:IdentifiedObject.description>PowerTransformer</cim:IdentifiedObject.description>
              |		<cim:PowerSystemResource.Location rdf:resource="#_location_1611089920_427086543_69441723"/>
              |		<cim:PowerSystemResource.PSRType rdf:resource="#PSRType_TransformerStation"/>
              |		<cim:PowerSystemResource.AssetDatasheet rdf:resource="#nis_el_int_transformer_art_ART16069"/>
              |		<cim:Equipment.EquipmentContainer rdf:resource="#HSZ102750"/>
              |	</cim:PowerTransformer>
            """.stripMargin

        val parser = new CHIM (xml)
        val result = CHIM.parse (parser)
        val transformerMock = result._1 ("ID123")

        val scEdgeMock = ScEdge ("ID121", 400.0, "ID123", 230.0, 2, "", transformerMock, null)
        val scNodeMock = ScNode ("ID123", 230.0, "", "", null, null, null)
        assert (!scEdgeMock.shouldContinueTo (scNodeMock), "should not continue on transformer with 230V")
    }
}

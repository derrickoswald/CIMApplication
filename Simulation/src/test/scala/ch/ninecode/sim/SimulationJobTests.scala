package ch.ninecode.sim

import java.io.StringReader
import javax.json.Json
import javax.json.JsonObject

import org.apache.log4j.Appender
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.log4j.spi.LoggingEvent

import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
import scala.collection.mutable.ArrayBuffer

import org.junit.Test

class SimulationJobTests
{
    class TestAppender extends AppenderSkeleton
    {
        val log: ArrayBuffer[LoggingEvent] = ArrayBuffer[LoggingEvent]()
        override def requiresLayout = false
        override def close (): Unit = {}
        override def append (event: LoggingEvent): Unit = log += event
        def getLog: ArrayBuffer[LoggingEvent] = log
    }

    def loggingTo[T <: TestAppender, R] (appender: T)(block: T => R): R =
    {
        val root = LogManager.getRootLogger
        val old = root.getAllAppenders.asScala.toArray
        old.foreach (x => root.removeAppender(x.asInstanceOf[Appender]))
        root.addAppender (appender)

        try
        {
            block (appender)
        }
        finally
        {
            root.removeAllAppenders ()
            old.foreach (x => root.addAppender (x.asInstanceOf[Appender]))
        }
    }

    def toJsonObject (text: String): JsonObject =
    {
        Json.createReader (new StringReader (text)).readObject match
        {
            case obj: JsonObject => obj
            case _ =>
                throw new Throwable ("not a json")
        }
    }

    @Test def NotJSON (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val text = "Now is the time for all good men to come to the aid of the party."
                val options = SimulationOptions (simulation = Seq (text))
                val empty = SimulationJob.getAll (options)
                assert (empty.isEmpty)
                val log = appender.getLog
                assert (log.length == 2)
                assert (log.exists (entry => entry.getLevel == Level.ERROR && entry.getRenderedMessage.startsWith ("unparseable as JSON")))
                assert (log.exists (entry => entry.getLevel == Level.WARN && entry.getRenderedMessage.equals ("not all simulations will be processed")))
        }
    }

    @Test def EmptyJSON (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val text = "{ }"
                val options = SimulationOptions (simulation = Seq (text))
                val empty = SimulationJob.getAll (options)
                assert (empty.isEmpty)
                val log = appender.getLog
                assert (log.length == 2)
                assert (log.exists (entry => entry.getLevel == Level.ERROR && entry.getRenderedMessage.contains ("does not specify a CIM file")))
                assert (log.exists (entry => entry.getLevel == Level.WARN && entry.getRenderedMessage.equals ("some simulation JSON files have errors")))
        }
    }

    @Test def QueryTestNone (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val TITLE = "ratedCurrent"
                val text =
                    s"""
                    |{
                    |    "title": "$TITLE",
                    |    "bork": "not correct"
                    |}
                    """.stripMargin
                val extra = SimulationJob.parseExtra ("test", toJsonObject (text))
                assert (extra.isEmpty)
                val log = appender.getLog
                assert (log.length == 1)
                assert (log.exists (entry => entry.getLevel == Level.ERROR && entry.getRenderedMessage.contains ("does not specify a query")))
        }
    }

    @Test def QueryTestType (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val TITLE = "ratedCurrent"
                val text =
                    s"""
                       |{
                       |    "title": "$TITLE",
                       |    "query": 42
                       |}
                    """.stripMargin
                val extra = SimulationJob.parseExtra ("test", toJsonObject (text))
                assert (extra.isEmpty)
                val log = appender.getLog
                assert (log.length == 1)
                assert (log.exists (entry => entry.getLevel == Level.ERROR && entry.getRenderedMessage.contains ("not a JSON string or array")))
        }
    }

    @Test def QueryTestEmptyArray (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val TITLE = "ratedCurrent"
                val text =
                    s"""
                       |{
                       |    "title": "$TITLE",
                       |    "query": []
                       |}
                    """.stripMargin
                val extra = SimulationJob.parseExtra ("test", toJsonObject (text))
                assert (extra.isEmpty)
                val log = appender.getLog
                assert (log.length == 1)
                assert (log.exists (entry => entry.getLevel == Level.ERROR && entry.getRenderedMessage.contains ("has no valid queries")))
        }

        loggingTo (new TestAppender)
        {
            appender =>
                val TITLE = "ratedCurrent"
                val text =
                    s"""
                       |{
                       |    "title": "$TITLE",
                       |    "query": [42]
                       |}
                    """.stripMargin
                val extra = SimulationJob.parseExtra ("test", toJsonObject (text))
                assert (extra.isEmpty)
                val log = appender.getLog
                assert (log.length == 1)
                assert (log.exists (entry => entry.getLevel == Level.ERROR && entry.getRenderedMessage.contains ("has no valid queries")))
        }
    }

    @Test def QueryTestSingle (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val TITLE = "ratedCurrent"
                val QUERY = "select l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (w.ratedCurrent as string) value from ACLineSegment l, WireInfo w where w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet"
                val text =
                    s"""
                    |{
                    |    "title": "$TITLE",
                    |    "query": "$QUERY"
                    |}
                    """.stripMargin
                val extra = SimulationJob.parseExtra ("test", toJsonObject (text))
                assert (extra.isDefined)
                val contents = extra.orNull
                assert (contents.title == TITLE)
                assert (contents.query == QUERY)
                assert (appender.getLog.isEmpty)
        }
    }

    @Test def QueryTestMultiple (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val TITLE = "substation"
                val QUERY1 = "create if not exists Bay..."
                val QUERY2 = "select concat_ws ('_', sort_array (collect_set (e.PowerTransformer))) key, first_value (c.substation) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, (select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
                val text =
                    s"""
                    |{
                    |    "title": "$TITLE",
                    |    "query": ["$QUERY1", "$QUERY2"]
                    |}
                    """.stripMargin
                val extra = SimulationJob.parseExtra ("test", toJsonObject (text))
                assert (extra.isDefined)
                val contents = extra.orNull
                assert (contents.title == TITLE)
                assert (contents.query == QUERY2)
                assert (appender.getLog.isEmpty)
        }
    }

    @Test def ExtrasNone (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val text =
                    s"""
                       |{
                       |    "cim": "some.rdf",
                       |    "interval": {"start": "2018-01-01T00:00:00.000+0100", "end": "2018-02-01T00:00:00.000+0100"}
                       |}
                    """.stripMargin
                val options: SimulationOptions = SimulationOptions (simulation = Seq (text))
                val simulations = SimulationJob.getAll (options)
                assert (simulations.length == 1)
                val simulation = simulations.head
                assert (simulation.extras.isEmpty)
                val log = appender.getLog
                assert (log.isEmpty)
        }
    }

    @Test def ExtrasType (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val text =
                    s"""
                       |{
                       |    "cim": "some.rdf",
                       |    "interval": {"start": "2018-01-01T00:00:00.000+0100", "end": "2018-02-01T00:00:00.000+0100"},
                       |    "extras": 42
                       |}
                    """.stripMargin
                val options: SimulationOptions = SimulationOptions (simulation = Seq (text))
                val simulations = SimulationJob.getAll (options)
                assert (simulations.length == 1)
                val simulation = simulations.head
                assert (simulation.extras.isEmpty)
                val log = appender.getLog
                assert (log.length == 1)
                assert (log.exists (entry => entry.getLevel == Level.ERROR && entry.getRenderedMessage.contains ("unexpected JSON type")))
        }
    }

    @Test def ExtrasEmpty (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val text =
                    s"""
                       |{
                       |    "cim": "some.rdf",
                       |    "interval": {"start": "2018-01-01T00:00:00.000+0100", "end": "2018-02-01T00:00:00.000+0100"},
                       |    "extras": []
                       |}
                    """.stripMargin
                val options: SimulationOptions = SimulationOptions (simulation = Seq (text))
                val simulations = SimulationJob.getAll (options)
                assert (simulations.length == 1)
                val simulation = simulations.head
                assert (simulation.extras.isEmpty)
                val log = appender.getLog
                assert (log.isEmpty)
        }
    }

    @Test def ExtrasElementType (): Unit =
    {
        loggingTo (new TestAppender)
        {
            appender =>
                val text =
                    s"""
                       |{
                       |    "cim": "some.rdf",
                       |    "interval": {"start": "2018-01-01T00:00:00.000+0100", "end": "2018-02-01T00:00:00.000+0100"},
                       |    "extras": [42]
                       |}
                    """.stripMargin
                val options: SimulationOptions = SimulationOptions (simulation = Seq (text))
                val simulations = SimulationJob.getAll (options)
                assert (simulations.length == 1)
                val simulation = simulations.head
                assert (simulation.extras.isEmpty)
                val log = appender.getLog
                assert (log.length == 1)
                assert (log.exists (entry => entry.getLevel == Level.ERROR && entry.getRenderedMessage.contains ("unexpected JSON type")))
        }

        loggingTo (new TestAppender)
        {
            appender =>
                val text =
                    s"""
                       |{
                       |    "cim": "some.rdf",
                       |    "interval": {"start": "2018-01-01T00:00:00.000+0100", "end": "2018-02-01T00:00:00.000+0100"},
                       |    "extras": [
                       |        {
                       |            "title": "ratedCurrent",
                       |            "query": "select l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (w.ratedCurrent as string) value from ACLineSegment l, WireInfo w where w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet"
                       |        },
                       |        42
                       |    ]
                       |}
                    """.stripMargin
                val options: SimulationOptions = SimulationOptions (simulation = Seq (text))
                val simulations = SimulationJob.getAll (options)
                assert (simulations.length == 1)
                val simulation = simulations.head
                assert (simulation.extras.length == 1)
                val log = appender.getLog
                assert (log.length == 1)
                assert (log.exists (entry => entry.getLevel == Level.ERROR && entry.getRenderedMessage.contains ("unexpected JSON type")))
        }
    }
}

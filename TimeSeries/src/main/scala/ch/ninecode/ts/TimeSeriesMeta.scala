package ch.ninecode.ts

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.writer.WriteConf
import com.datastax.spark.connector._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.util.Schema

case class TimeSeriesMeta (session: SparkSession, options: TimeSeriesOptions)
{

    import TimeSeriesMeta._

    val log: Logger = LoggerFactory.getLogger(getClass)
    val map: Map[String, Classifier] = common.map(x => (x.keyword, x)).toMap
    val apt_map: Map[String, Classifier] = apts.map(x => (x.keyword, x)).toMap

    def classify (in: String, addr: String): Option[Array[Classifier]] =
    {
        val splits = in.split("[ ,()-/]")
        val options = splits.flatMap(x => map.get(x))
        if (options.length > 0)
        {
            options.foreach(x => x.count = x.count + 1)
            Some(options)
        }
        else
        {
            val extra = addr.split("[ ,()-/]")
            val address = extra.flatMap(x => map.get(x))
            if (address.length > 0)
            {
                address.foreach(x => x.count = x.count + 1)
                Some(address)
            }
            else
            {
                val apartments = Array.concat(splits.flatMap(x => apt_map.get(x)), extra.flatMap(x => apt_map.get(x)))
                if (apartments.length > 0)
                {
                    apartments.foreach(x => x.count = x.count + 1)
                    Some(apartments)
                }
                else
                    None
            }
        }
    }

    def mrid (col: Array[String]): String = if ("" != col(3)) col(3) else col(12)

    def meter (col: Array[String]): String = col(7)

    def extractClasses (line: String): Option[(String, Option[Array[String]])] =
    {
        val splits = line.split("[;]")
        val mRID = mrid(splits)
        if ("" != mRID)
            classify(splits(16), splits(15)) match
            {
                case Some(classifier) =>
                    Some((mRID, Some(classifier.map(_.cls))))
                case None =>
                    log.error(s"unclassified: $mRID ${splits(7)} ${splits(16)} ==== ${splits(15)}")
                    Some((mRID, None))
            }
        else
            None
    }

    def makeMap (arg: (String, Option[Array[String]])): (String, Map[String, Int]) =
    {
        val (mrid, classes) = arg
        classes match
        {
            case Some(cls) =>
                (mrid, cls.map(x => (x, 1)).groupBy(_._1).mapValues(x => x.map(_._2).sum))
            case None =>
                unknown.count = unknown.count + 1
                (mrid, Map(unknown.cls -> 1))
        }
    }

    def toClasses (classnames: Iterable[Map[String, Int]]): Map[String, Int] =
    {
        classnames.flatMap(_.toSeq).groupBy(_._1).mapValues(x => x.map(_._2).sum)
    }

    def run ()
    {
        val schema = Schema(session, "/simulation_schema.sql", true)
        if (schema.make(keyspace = options.keyspace, replication = options.replication))
        {
            val source = scala.io.Source.fromFile(options.meta_file)
            val lines = source.getLines.toArray

            val data = lines.filter(!_.contains("Messpunktbezeichnung"))
            val pass1 = data.map(extractClasses)
            val unlabeled = pass1.count(_.isEmpty)
            val pass2 = pass1.flatten
            val unmatched = pass2.count(_._2.isEmpty)
            val matched = pass2.count(_._2.isDefined)
            val classified = pass2.map(makeMap)

            val rdd = session.sparkContext.parallelize(classified.toSeq)
            val raw: RDD[(String, Map[String, Int])] = rdd.groupByKey.mapValues(toClasses)
            val columns = SomeColumns("mrid", "classes")
            val writeConf = WriteConf(consistencyLevel = ConsistencyLevel.ANY)
            raw.saveToCassandra(options.keyspace, "measured_value_meta", columns, writeConf)

            // summarize
            log.error(s"$unlabeled unlabeled, $unmatched unmatched, matched $matched of ${matched + unmatched + unlabeled} = ${100.0 * (matched.toDouble / (matched.toDouble + unmatched.toDouble + unlabeled.toDouble))}%")
            all.groupBy(_.cls).mapValues(x => (x.map(_.count).sum, x.toSet)).foreach(x => log.info(x.toString))

            source.close()
        }
    }
}

object TimeSeriesMeta
{

    case class Classifier (var count: Int, keyword: String, cls: String)
    {
        override def toString: String = s"$keyword×$count"
    }

    // common prefixes (19242/20197)
    val common: Array[Classifier] =
        Array(
            Classifier(0, "Wohnung", "Apartment"),
            Classifier(0, "Einfamilienhaus", "House"),
            Classifier(0, "Allgemein", "General"),
            Classifier(0, "Wohnhaus", "Apartment"),
            Classifier(0, "Scheune", "Outbuilding"),
            Classifier(0, "Büro", "Office"),
            Classifier(0, "Photovoltaikanlage", "PV"),
            Classifier(0, "Werkstatt", "Factory"),
            Classifier(0, "Tiefgarage", "Garage"),
            Classifier(0, "Heizung", "Heating"),
            Classifier(0, "Pumpanlage", "Equipment"),
            Classifier(0, "Einliegerwohnung", "Apartment"),
            Classifier(0, "Laden", "Shop"),
            Classifier(0, "Garage", "Garage"),
            Classifier(0, "Stall", "Shop"),
            Classifier(0, "Strassenbeleuchtung", "StreetLighting"),
            Classifier(0, "Verstärker", "Equipment"),
            Classifier(0, "Praxis", "Office"),
            Classifier(0, "Restaurant", "Restaurant"),
            Classifier(0, "Reservoir", "Equipment"),
            Classifier(0, "Lager", "Warehouse"),
            Classifier(0, "Kirche", "Church"),
            Classifier(0, "Schreinerei", "Factory"),
            Classifier(0, "Schulanlage", "School"),
            Classifier(0, "Natelstation", "Equipment"),
            Classifier(0, "Coiffeur", "Shop"),
            Classifier(0, "Geschäft", "Office"),
            Classifier(0, "Kindergarten", "School"),
            Classifier(0, "Remise", "Outbuilding"),
            Classifier(0, "Zivilschutzanlage", "Apartment"),
            Classifier(0, "Autoreparaturwerkstatt", "Factory"),
            Classifier(0, "Heim", "Apartment"),
            Classifier(0, "Werkhalle", "Factory"),
            Classifier(0, "Atelier", "Factory"),
            Classifier(0, "Ferienhaus", "House"),
            Classifier(0, "Feuerwehrdepot", "Outbuilding"),
            Classifier(0, "Mehrfamilienhaus", "Apartment"),
            Classifier(0, "Bank", "Office"),
            Classifier(0, "Schopf", "Outbuilding"),
            Classifier(0, "Konditorei", "Shop"),
            Classifier(0, "Café", "Restaurant"),
            Classifier(0, "Wegbeleuchtung", "StreetLighting"),
            Classifier(0, "Käserei", "Shop"),
            Classifier(0, "Lagerraum", "Shop"),
            Classifier(0, "Tankstelle", "Shop"),
            Classifier(0, "Fabrik", "Factory"),
            Classifier(0, "Hotel", "Apartment"),
            Classifier(0, "Pension", "Apartment"),
            Classifier(0, "Gebäude", "Outbuilding"),
            Classifier(0, "Schützenhaus", "Outbuilding"),
            Classifier(0, "Clubhaus", "Outbuilding"),
            Classifier(0, "Kiosk", "Shop"),
            Classifier(0, "Metzgerei", "Shop"),
            Classifier(0, "Physiotherapie", "Office"),
            Classifier(0, "Waschmaschine", "Equipment"),
            Classifier(0, "Wetterstation", "Equipment"),
            Classifier(0, "Werkhof", "Outbuilding"),
            Classifier(0, "Gemeindehaus", "Office"),
            Classifier(0, "Magazin", "Shop"),
            Classifier(0, "Druckerei", "Factory"),
            Classifier(0, "Wärmepumpe", "Equipment"),
            Classifier(0, "Sportplatz", "StreetLighting"),
            Classifier(0, "Tenniscenter", "StreetLighting"),
            Classifier(0, "Kapelle", "Church"),
            Classifier(0, "Salon", "Shop"),
            Classifier(0, "Bastelraum", "Shop"),
            Classifier(0, "Pfadiheim", "Outbuilding"),
            Classifier(0, "Pfadihaus", "Outbuilding"),
            Classifier(0, "Kulturraum", "Outbuilding"),
            Classifier(0, "Gartenhaus", "Outbuilding"),
            Classifier(0, "Cabinet", "Equipment"),
            Classifier(0, "Aussenkasten", "Equipment"),
            Classifier(0, "Apotheke", "Shop"),
            Classifier(0, "Keller", "Outbuilding"),
            Classifier(0, "Wasserversorgung", "Equipment"),
            Classifier(0, "Asylantenheim", "House"),
            Classifier(0, "Post", "Shop"),
            Classifier(0, "Saal", "Shop"),
            Classifier(0, "Museum", "Shop"),
            Classifier(0, "Bäckerei", "Shop"),
            Classifier(0, "Schauraum", "Shop"),
            Classifier(0, "Schlosserei", "Shop"),
            Classifier(0, "Eingang", "General"),
            Classifier(0, "Treppenhaus", "General"),
            Classifier(0, "Korridor", "General"),
            Classifier(0, "Allgemeinzähler", "General"),
            Classifier(0, "Gang", "General"),
            Classifier(0, "Sendestation", "Equipment"),
            Classifier(0, "Umsetzer", "Equipment"),
            Classifier(0, "Bankomat", "Equipment"),
            Classifier(0, "Lagerhalle", "Warehouse"),
            Classifier(0, "Abstellraum", "Warehouse"),
            Classifier(0, "Zeughaus", "Warehouse"),
            Classifier(0, "Holzschnitzellager", "Warehouse"),
            Classifier(0, "Ferienlager", "Warehouse"),
            Classifier(0, "Lokal", "Shop"),
            Classifier(0, "Kellerlokal", "Shop"),
            Classifier(0, "Werkstattbedarf", "Factory"),
            Classifier(0, "Heizzentrale", "Heating"),
            Classifier(0, "Waschhaus", "Outbuilding"),
            Classifier(0, "Kleintheater", "Shop"),
            Classifier(0, "Verteilkabine", "Equipment"),
            Classifier(0, "Abwasserpumpwerk", "Equipment"),
            Classifier(0, "Pumpwerk", "Equipment"),
            Classifier(0, "Pumpstation", "Equipment"),
            Classifier(0, "Partykeller", "Shop"),
            Classifier(0, "Ausstellungsraum", "Shop"),
            Classifier(0, "Zollwachhaus", "Outbuilding"),
            Classifier(0, "Gartenhaus", "Outbuilding"),
            Classifier(0, "Mehrzweckanlage", "Shop"),
            Classifier(0, "Fitnesscenter", "Shop"),
            Classifier(0, "Ortsmuseum", "Shop"),
            Classifier(0, "Bahnhof", "Shop"),
            Classifier(0, "Bibliothek", "Shop"),
            Classifier(0, "Gallerie", "Shop"),
            Classifier(0, "Beleuchtung", "StreetLighting"),
            Classifier(0, "Eisenbahnwagen", "StreetLighting"),
            Classifier(0, "Haltestelle", "Equipment"),
            Classifier(0, "Tenne", "Outbuilding"),
            Classifier(0, "Gasdruckreduzierung", "Equipment"),
            Classifier(0, "Bahnanlage", "Equipment"),
            Classifier(0, "Abwasserreinigungsanlage", "Equipment"),
            Classifier(0, "ARA", "Equipment"),
            Classifier(0, "Messschacht", "Equipment"),
            Classifier(0, "Autowaschanlage", "Equipment"),
            Classifier(0, "Lichtsignal", "Equipment"),
            Classifier(0, "Kabine", "Equipment"),
            Classifier(0, "Luftschutzraum", "Equipment"),
            Classifier(0, "Antenne", "Equipment"),
            Classifier(0, "Textilbetrieb", "Factory"),
            Classifier(0, "Buchbinderei", "Factory"),
            Classifier(0, "Waschanlage", "Factory"),
            Classifier(0, "Malerei", "Factory"),
            Classifier(0, "Sanitätshilfsstelle", "Outbuilding"),
            Classifier(0, "Feuerwehrlokal", "Outbuilding"),
            Classifier(0, "Spitex", "Apartment"),
            Classifier(0, "Asylantenunterkunft", "Apartment"),
            Classifier(0, "Kirchgemeindehaus", "House"),
            Classifier(0, "Pfarrei", "House"),
            Classifier(0, "Skilift", "Outbuilding"),
            Classifier(0, "WC", "Outbuilding"),
            Classifier(0, "Telefonverteiler", "Equipment"),
            Classifier(0, "Blinklichtanlage", "Equipment"),
            Classifier(0, "Lichtsignal-Anlage", "Equipment"),
            Classifier(0, "Installationsraum", "Equipment"),
            Classifier(0, "Gemeinschaftsanlage", "Equipment"),
            Classifier(0, "Liftraum", "Equipment"),
            Classifier(0, "Elektrotankstelle", "Equipment"),
            Classifier(0, "Talhütte", "Outbuilding"),
            Classifier(0, "Marktanschluss", "Outbuilding"),
            Classifier(0, "Schiessanlage", "Outbuilding"),
            Classifier(0, "Blockhütte", "Outbuilding"),
            Classifier(0, "Station", "Outbuilding"),
            Classifier(0, "Waschküche", "Outbuilding"),
            Classifier(0, "Bauanschluss", "Outbuilding"),
            Classifier(0, "Sternwarte", "Outbuilding"),
            Classifier(0, "Friedhof", "Outbuilding"),
            Classifier(0, "Hobbyraum", "Outbuilding"),
            Classifier(0, "Einstellhalle", "Garage"),
            Classifier(0, "Einstellgarage", "Garage"),
            Classifier(0, "Autogarage", "Garage"),
            Classifier(0, "Büros", "Office"),
            Classifier(0, "Studios", "Factory"),
            Classifier(0, "Baugeschäft", "Factory"),
            Classifier(0, "Brennerei", "Factory"),
            Classifier(0, "Bergstation", "Outbuilding"),
            Classifier(0, "Talstation", "Outbuilding"),
            Classifier(0, "Fischzucht", "Factory"),
            Classifier(0, "Technikraum", "Outbuilding")
        )
    val apts: Array[Classifier] = Array(
        Classifier(0, "Erdgeschoss", "Apartment"),
        Classifier(0, "Obergeschoss", "Apartment"),
        Classifier(0, "Untergeschoss", "Apartment"),
        Classifier(0, "Dachgeschoss", "Apartment"))
    val unknown: Classifier = Classifier(0, "", "unknown")

    lazy val all: Array[Classifier] = Array.concat(common, apts, Array(unknown))
    lazy val classes: Array[String] = all.map(_.cls).distinct.sortWith(_ < _)
}
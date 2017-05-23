package ch.ninecode.gl

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.model._

/**
 * Photovoltaic attachment.
 * Generating equipment attached to a node.
 * @param node ConnectivityNode or TopologicalNode MRID.
 * @param solar SolarGeneratingUnit object attached to the node.
 */
case class PV (
    node: String,
    solar: SolarGeneratingUnit)

case class Solar (session: SparkSession, topologicalnodes: Boolean, storage_level: StorageLevel)
{
    /**
     * Lookup CIM RDD by name.
     * @param name The unqualified name of the RDD (name of the class)
     * @return The RDD found or null if nothing was found.
     */
    def get (name: String): RDD[Element] =
    {
        val rdds = session.sparkContext.getPersistentRDDs
        for (key <- rdds.keys)
        {
            val rdd = rdds (key)
            if (rdd.name == name)
                return (rdd.asInstanceOf[RDD[Element]])
        }

        return (null)
    }

    /**
     * Retain only PV that are in service or have a valid application pending.
     * @param pv The PV to filter.
     * @return The valid PV.
     */
    def filterValidSolarUnits (pv: RDD[PV]): RDD[PV] =
    {
        val lifecycle = get ("LifecycleDate").asInstanceOf[RDD[LifecycleDate]]
        val asset = get ("Asset").asInstanceOf[RDD[Asset]]
        val lifecycle_per_eea = asset.keyBy (_.lifecycle).join (lifecycle.keyBy (_.id)).map (l ⇒ (l._2._1.IdentifiedObject.name, (l._2._2)))
        val pv_lifecycle = pv.keyBy (_.solar.id).leftOuterJoin (lifecycle_per_eea)

        def lifecycleValid (lifecycle: LifecycleDate): Boolean =
        {
            if (lifecycle.installationDate != null)
                true
            else if (lifecycle.receivedDate != null)
            {
                val _DateFormat = new SimpleDateFormat ("dd.MM.yyyy")
                val receivedDate = _DateFormat.parse (lifecycle.receivedDate)
                val now = new Date ()
                val diffTime = now.getTime () - receivedDate.getTime ()
                val diffDays = diffTime / (1000 * 60 * 60 * 24);
                diffDays < 400
            }
            else
                false
        }

        val valid_pv = pv_lifecycle.filter (p ⇒ {
            val lifecycle_option = p._2._2
            if (lifecycle_option.isDefined)
                lifecycleValid (lifecycle_option.get)
            else
                false
        })

        valid_pv.map (_._2._1)
    }

    // get the existing photo-voltaic installations keyed by terminal
    def getSolarInstallations (): RDD[Tuple2[String, Iterable[PV]]] =
    {
        // note there are two independent linkages happening here through the UserAttribute class:
        // - SolarGeneratingUnit to ServiceLocation
        // - ServiceLocation to EnergyConsumer

        // link to service location ids via UserAttribute
        val attributes = get ("UserAttribute").asInstanceOf[RDD[UserAttribute]]

        // user attributes link through string quantities
        val strings = get ("StringQuantity").asInstanceOf[RDD[StringQuantity]]

        // get solar to service linkage, e.g. ("EEA5280", "MST115133")
        // and service to house linkage, e.g. ("MST115133", "HAS138130")
        val pairs = attributes.keyBy (_.value).join (strings.keyBy (_.id)).values.map (x ⇒ (x._1.name, x._2.value))

        // get a simple list of house to pv id pairs
        val links = pairs.join (pairs.map (x ⇒ (x._2, x._1))).values

        // get the pv stations
        val solars = get ("SolarGeneratingUnit").asInstanceOf[RDD[SolarGeneratingUnit]]

        // get a simple list of house to pv pairs
        val house_solars = links.map (x ⇒ (x._2, x._1)).join (solars.keyBy (_.id)).values

        // get the terminals
        val terminals = get ("Terminal").asInstanceOf[RDD[Terminal]]

        // link to the connectivity/topological node through the terminal
        val t = terminals.keyBy (_.ConductingEquipment).join (house_solars).values.map (
            (x) ⇒ PV (if (topologicalnodes) x._1.TopologicalNode else x._1.ConnectivityNode, x._2))

        val filteredPV = filterValidSolarUnits (t)
        val pv = filteredPV.groupBy (_.node)

        pv.persist (storage_level)
        session.sparkContext.getCheckpointDir match
        {
            case Some (dir) ⇒ pv.checkpoint ()
            case None ⇒
        }

        pv
    }
}
package ch.ninecode.cim.cimweb

import java.io.File
import java.util.Properties

import javax.naming.Context
import javax.naming.InitialContext
import javax.naming.NamingException
import javax.resource.cci.ConnectionFactory

import org.jboss.arquillian.container.test.api.Deployment
import org.jboss.arquillian.junit.Arquillian
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.asset.FileAsset
import org.jboss.shrinkwrap.api.exporter.ZipExporter
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive
import org.jboss.shrinkwrap.api.spec.WebArchive

import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CIMWeb test suite.
 */
@RunWith (classOf[Arquillian])
object CIMWebTest
{
    val WEBEAR_SRC = "src/main"
    val WEBAPP_SRC = s"$WEBEAR_SRC/webapp"
    println (WEBAPP_SRC)

    @Deployment
    def createDeployment: Option[EnterpriseArchive] =
    {
        try
        {
            val war = ShrinkWrap.create (classOf[WebArchive], "CIMWeb.war")
                .setWebXML (new File (WEBAPP_SRC, "WEB-INF/web.xml"))
                .addPackage (getClass.getPackage) // deprecated: (java.lang.Package.getPackage ("ch.ninecode.cim.cimweb"))
                .deleteClass (classOf[CIMWebTest])
                .addAsWebResource (new File (WEBAPP_SRC, "index.html"))
                .addManifest ()
            println (war.toString (true))
            war.as (classOf[ZipExporter]).exportTo (new File ("./target/CIMWeb.war"), true)
            val ear = ShrinkWrap.create (classOf[EnterpriseArchive], "CIMWeb.ear")
                .addAsModules (war)
                .add (new FileAsset (new File ("../CIMConnector/target/CIMConnector-2.11-2.4.5-2.7.2.rar")), "CIMConnector.rar")
                .addManifest ()
                .addAsManifestResource (new File (WEBEAR_SRC, "application.xml"))
            println (ear.toString (true))
            ear.as (classOf[ZipExporter]).exportTo (new File ("./target/CIMWeb.ear"), true)
            //        final EnterpriseArchive ear = ShrinkWrap.createFromZipFile (EnterpriseArchive.class, new File ("../CIMEar/target/CIMApplication.ear"));
            //        println (ear.toString (true));
            //        ear.as (ZipExporter.class).exportTo (new File ("./target/CIMWeb.ear"), true);
            Some (ear)
        }
        catch
        {
            case e: Exception =>
                println (e.getMessage)
                None
        }
    }
}

@RunWith (classOf[Arquillian])
class CIMWebTest
{
    def print_context_r (context: InitialContext, name: String, depth: Int): Unit =
    {
        try
        {
            val values = context.list (name)
            while (values.hasMore)
            {
                val pair = values.next
                var i = 0
                while (i < depth)
                {
                    print ("    ")
                    i += 1
                }
                println (pair.getName + " : " + pair.getClassName)
                if ("org.apache.openejb.core.ivm.naming.IvmContext" eq pair.getClassName)
                    print_context_r (context, name + "/" + pair.getName, depth + 1)
            }
        }
        catch
        {
            case _: NamingException =>
        }
    }

    def print_context (context: InitialContext, name: String): Unit =
    {
        println (name)
        print_context_r (context, name, 1)
    }

    def propertiesFromMap (properties: Map[String, String]): Properties =
        properties.foldLeft (new Properties)
        {
            case (p, (k, v)) =>
                val _ = p.put (k,v)
                p
        }

    @Test
    def testResourceAdapter (): Unit =
    {
        val properties = Map[String, String] (
            Context.INITIAL_CONTEXT_FACTORY -> "org.apache.openejb.client.LocalInitialContextFactory",
            "openejb.deployments.classpath.include" -> ".*resource-injection.*"
        )
        val initialContext = new InitialContext (propertiesFromMap (properties))
        print_context (initialContext, "java:")
        print_context (initialContext, "openejb:")
        initialContext.lookup ("openejb:Resource/CIMConnector.rar") match
        {
            case connectionFactory: ConnectionFactory =>
                val connection = connectionFactory.getConnection
                assertNotNull (connection)
                connection.close ()
            case _ =>
                fail ("could not get a ConnectionFactory")
        }
    }
}

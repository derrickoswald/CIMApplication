package ch.ninecode.cim.cimweb

import org.junit.Assert.assertNotNull
import java.io.File
import java.util.Properties
import javax.inject.Inject
import javax.naming._
import javax.resource.cci.Connection
import javax.resource.cci.ConnectionFactory
import org.jboss.arquillian.container.test.api.Deployment
import org.jboss.arquillian.junit.Arquillian
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.asset.FileAsset
import org.jboss.shrinkwrap.api.exporter.ZipExporter
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive
import org.jboss.shrinkwrap.api.spec.WebArchive
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CIMWeb test suite.
 */
@RunWith (classOf [Arquillian])
object CIMWebTest
{
    val WEBEAR_SRC = "src/main"
    val WEBAPP_SRC = WEBEAR_SRC + "/webapp"
    println (WEBAPP_SRC)

    @Deployment
    def createDeployment: EnterpriseArchive =
    {
        try
        {
            val war = ShrinkWrap.create (classOf [WebArchive], "CIMWeb.war")
            war.setWebXML (new File (WEBAPP_SRC, "WEB-INF/web.xml"))
            war.addPackage (java.lang.Package.getPackage ("ch.ninecode.cim.cimweb")) // getClass().getPackage()
            war.deleteClass (classOf [CIMWebTest])
            war.addAsWebResource (new File (WEBAPP_SRC, "index.html"))
            war.addManifest ()
            println (war.toString (true))
            war.as (classOf [ZipExporter]).exportTo (new File ("./target/CIMWeb.war"), true)
            val ear = ShrinkWrap.create (classOf [EnterpriseArchive], "CIMWeb.ear")
            ear.addAsModules (war)
            ear.add (new FileAsset (new File ("../CIMConnector/target/CIMConnector-2.11-2.4.3-2.5.0.rar")), "CIMConnector.rar")
            ear.addManifest ()
            ear.addAsManifestResource (new File (WEBEAR_SRC, "application.xml"))
            println (ear.toString (true))
            ear.as (classOf [ZipExporter]).exportTo (new File ("./target/CIMWeb.ear"), true)
            //        final EnterpriseArchive ear = ShrinkWrap.createFromZipFile (EnterpriseArchive.class, new File ("../CIMEar/target/CIMApplication.ear"));
            //        println (ear.toString (true));
            //        ear.as (ZipExporter.class).exportTo (new File ("./target/CIMWeb.ear"), true);
            ear
        }
        catch
        {
            case e: Exception ⇒
                println (e.getMessage)
                null
        }
    }
}

@RunWith (classOf [Arquillian])
class CIMWebTest
{
    var context: InitialContext = null

    def print_context_r (name: String, depth: Int): Unit =
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
                    print_context_r (name + "/" + pair.getName, depth + 1)
            }
        }
        catch
        {
            case e: NamingException ⇒

        }
    }

    def print_context (name: String): Unit =
    {
        println (name)
        print_context_r (name, 1)
    }

    @Test
    def testResourceAdapter (): Unit =
    {
        val properties = new Properties
        properties.setProperty (Context.INITIAL_CONTEXT_FACTORY, "org.apache.openejb.client.LocalInitialContextFactory")
        properties.setProperty ("openejb.deployments.classpath.include", ".*resource-injection.*")
        val initialContext = new InitialContext (properties)
        context = initialContext
        print_context ("java:")
        print_context ("openejb:")
        val connectionFactory = initialContext.lookup ("openejb:Resource/CIMConnector.rar").asInstanceOf [ConnectionFactory]
        val connection = connectionFactory.getConnection
        assertNotNull (connection)
        connection.close ()
    }
}

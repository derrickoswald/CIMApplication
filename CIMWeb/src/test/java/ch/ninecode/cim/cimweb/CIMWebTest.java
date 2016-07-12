package ch.ninecode.cim.cimweb;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.Properties;

import javax.inject.Inject;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.resource.cci.Connection;
import javax.resource.cci.ConnectionFactory;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * CIMWeb test suite.
 */
@RunWith (Arquillian.class)
public class CIMWebTest
{
    private static final String WEBEAR_SRC = "src/main";
    private static final String WEBAPP_SRC = WEBEAR_SRC + "/webapp";

    @Deployment
    public static EnterpriseArchive createDeployment ()
    {
        final WebArchive war = ShrinkWrap.create (WebArchive.class, "CIMWeb.war");
        war.setWebXML (new File (WEBAPP_SRC, "WEB-INF/web.xml"));
        war.addPackage (java.lang.Package.getPackage ("ch.ninecode.cim.cimweb")); // getClass().getPackage()
        war.deleteClass (CIMWebTest.class);
        war.addAsWebResource (new File (WEBAPP_SRC, "index.html"));
        war.addManifest ();
        System.out.println (war.toString (true));
        war.as (ZipExporter.class).exportTo (new File ("./target/CIMWeb.war"), true);

        final EnterpriseArchive ear = ShrinkWrap.create (EnterpriseArchive.class, "CIMWeb.ear");
        ear.addAsModules (war);
        ear.add (new FileAsset (new File ("../CIMConnector/target/CIMConnector-1.0-SNAPSHOT.rar")), "CIMConnector.rar");
        ear.addManifest ();
        ear.addAsManifestResource (new File (WEBEAR_SRC, "application.xml"));

        System.out.println (ear.toString (true));
        ear.as (ZipExporter.class).exportTo (new File ("./target/CIMWeb.ear"), true);

        return (ear);
    }

    Context context;

    void print_context_r (String name, int depth)
    {
        try
        {
            NamingEnumeration<NameClassPair> values = context.list (name);
            while (values.hasMore ())
            {
                NameClassPair pair = values.next ();
                for (int i = 0; i < depth; i++)
                    System.out.print ("    ");
                System.out.println (pair.getName () + " : " + pair.getClassName ());
                if ("org.apache.openejb.core.ivm.naming.IvmContext" == pair.getClassName ())
                    print_context_r (name + "/" + pair.getName (), depth + 1);
            }
        }
        catch (NamingException e)
        {

        }
    }

    void print_context (String name)
    {
        System.out.println (name);
        print_context_r (name, 1);
    }

    @Inject
    SimpleRESTEJB ejbobject;

    @Test
    public void testResourceAdapter () throws Exception
    {
        final Properties properties = new Properties ();
        properties.setProperty (Context.INITIAL_CONTEXT_FACTORY, "org.apache.openejb.client.LocalInitialContextFactory");
        properties.setProperty ("openejb.deployments.classpath.include", ".*resource-injection.*");
        final InitialContext initialContext = new InitialContext (properties);
        context = initialContext;
        print_context ("java:");
        print_context ("openejb:");
        final ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup ("openejb:Resource/CIMConnector.rar");
        final Connection connection = connectionFactory.getConnection ();
        assertNotNull (connection);
        connection.close ();
    }
//    assertTrue ("interaction returned false", interaction.execute (spec, input, output));
//    assertFalse ("interaction returned empty", output.isEmpty ());
//    assertEquals ("interaction returned wrong value", "Hello World!", output.get (0).toString ());

    @Test
    public void testWebApp () throws Exception
    {
        assertNotNull (ejbobject);
        String text = ejbobject.ejb ();
        assertNotNull (text);
        System.out.println (text);
    }
}

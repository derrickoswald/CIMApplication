package ch.ninecode.util

import java.util.Properties

/**
 * Common functionality for main programs with an application.properties resource.
 */
trait Main
{
    lazy val properties: Properties =
    {
        val in = this.getClass.getResourceAsStream ("/application.properties")
        val p = new Properties ()
        p.load (in)
        in.close ()
        p
    }

    def application_group: String = properties.getProperty ("groupId")

    def application_name: String = properties.getProperty ("artifactId")

    def application_version: String = properties.getProperty ("version")

    def project_url: String = properties.getProperty ("projecturl")

    def project_branch: String = properties.getProperty ("projectbranch")

    def project_build: String = properties.getProperty ("projectbuild")

    def scala_version: String = properties.getProperty ("scala")

    def scala_library_version: String = properties.getProperty ("scalalibrary")

    def spark_version: String = properties.getProperty ("spark")
}

package ch.ninecode.np

import org.scalatest.FunSuite

import ch.ninecode.np.MainCustomer1.main

class Customer1TestSuite
extends FunSuite
{
    val PRIVATE_FILE_DEPOT = "private_data/"
    val CSV_FILE = "KS_Leistungen.csv"
    val FILENAME = "NIS_CIM_Export_sias_current_20161220_Sample4.rdf"

    test ("Help1")
    {
        main (Array ("--unittest", "--help"))
    }

    test ("Simple1")
    {
        main (Array ("--unittest", "--master", "local[*]",
            "--csv", PRIVATE_FILE_DEPOT + CSV_FILE,
            "--export", "target/" + FILENAME,
            PRIVATE_FILE_DEPOT + FILENAME))
    }
}

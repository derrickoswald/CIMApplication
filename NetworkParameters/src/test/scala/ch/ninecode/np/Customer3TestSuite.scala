package ch.ninecode.np

import ch.ninecode.np.MainCustomer3.main
import org.scalatest.FunSuite

class Customer3TestSuite
extends FunSuite
{
    val PRIVATE_FILE_DEPOT = "private_data/"
    val CSV_FILE = "20181002_Transformatoren.csv"
    val FILENAME = "CIM_Export_CKW_EMM-Spitalhof.rdf"

    test ("Help3")
    {
        main (Array ("--unittest", "--help"))
    }

    test ("Simple3")
    {
        main (Array ("--unittest", "--master", "local[*]",
            "--csv", PRIVATE_FILE_DEPOT + CSV_FILE,
            "--export", "target/" + FILENAME,
            "--checkpoint", "/tmp/checkpoint",
            PRIVATE_FILE_DEPOT + FILENAME))
    }
}

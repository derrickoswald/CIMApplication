package ch.ninecode.np

import org.scalatest.FunSuite

import ch.ninecode.np.MainCustomer2.main

class Customer2TestSuite
extends FunSuite
{
    val PRIVATE_FILE_DEPOT = "private_data/"
    val CSV_FILE_1 = "Trafos_fuer_Analytiks.csv"
    val CSV_FILE_2 = "Netzeinspeisungen.csv"
    val FILENAME1 = "NIS_CIM_Export_sias_current_ekz_hirzel_nullungsbedingungen_no_house_fuse.rdf"

    test ("Help2")
    {
        main (Array ("--unittest", "--help"))
    }

    test ("Simple2")
    {
        main (Array ("--unittest", "--master", "local[*]",
            "--csv1", PRIVATE_FILE_DEPOT + CSV_FILE_1,
            "--csv2", PRIVATE_FILE_DEPOT + CSV_FILE_2,
            PRIVATE_FILE_DEPOT + FILENAME1))
    }
}

package ch.ninecode

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.graphx._
import org.apache.spark.rdd._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

import ch.ninecode._
import ch.ninecode.cim._
import ch.ninecode.model._

class GridLABD extends Serializable
{
    def get (name: String, context: SparkContext): RDD[Element] =
    {
        val rdds = context.getPersistentRDDs
        for (key <- rdds.keys)
        {
            val rdd = rdds (key)
            if (rdd.name == name)
                return (rdd.asInstanceOf[RDD[Element]])
        }
        return (null)
    }

    def preparation (sc: SparkContext, sqlContext: SQLContext, args: String): String  =
    {
        val arguments = args.split (",").map (
            (s) =>
                {
                    val pair = s.split ("=")
                    if (2 == pair.length)
                        (pair(0), pair(1))
                    else
                        (pair(0), "")
                }
        ).toMap
        val result =
            "        module tape;\n" +
            "        module powerflow\n" +
            "        {\n" +
            "            solver_method NR;\n" +
            "            default_maximum_voltage_error 10e-6;\n" +
            "            NR_iteration_limit 5000;\n" +
            "            NR_superLU_procs 16;\n" +
            "            nominal_frequency 50;\n" +
            "        };\n" +
            "\n" +
            "        clock\n" +
            "        {\n" +
            "            timezone GMT0+1;\n" +
            "            starttime '2013-12-10 12:00:00';\n" +
            "            stoptime '2013-12-10 15:00:00';\n" +
            "        };\n" +
            "\n" +
            "        class player\n" +
            "        {\n" +
            "            complex value;\n" +
            "        };\n" +
            "\n" +
            "        object voltdump\n" +
            "        {\n" +
            "            filename voltdump.csv;\n" +
            "            mode polar;\n" +
            "            runtime '2014-08-19 13:00:00';\n" +
            "        };\n" +
            "\n" +
            "        object line_configuration\n" +
            "        {\n" +
            "            name \"line_3x25Cu/25\";\n" +
            "            z11 0.727+0.08j Ohm/km;\n" +
            "            z12 0.0+0.0j Ohm/km;\n" +
            "            z13 0.0+0.0j Ohm/km;\n" +
            "            z21 0.0+0.0j Ohm/km;\n" +
            "            z22 0.727+0.08j Ohm/km;\n" +
            "            z23 0.0+0.0j Ohm/km;\n" +
            "            z31 0.0+0.0j Ohm/km;\n" +
            "            z32 0.0+0.0j Ohm/km;\n" +
            "            z33 0.727+0.08j Ohm/km;\n" +
            "        };\n" +
            "\n" +
            "        object line_configuration\n" +
            "        {\n" +
            "            name \"line_3x95Cu/95\";\n" +
            "            z11 0.193+0.07j Ohm/km;\n" +
            "            z12 0.0+0.0j Ohm/km;\n" +
            "            z13 0.0+0.0j Ohm/km;\n" +
            "            z21 0.0+0.0j Ohm/km;\n" +
            "            z22 0.193+0.07j Ohm/km;\n" +
            "            z23 0.0+0.0j Ohm/km;\n" +
            "            z31 0.0+0.0j Ohm/km;\n" +
            "            z32 0.0+0.0j Ohm/km;\n" +
            "            z33 0.193+0.07j Ohm/km;\n" +
            "        };\n" +
            "\n" +
            "        object transformer_configuration\n" +
            "        {\n" +
            "            name transformer;\n" +
            "            connect_type WYE_WYE;\n" +
            "            install_type PADMOUNT;\n" +
            "            power_rating 500;\n" +
            "            primary_voltage 4800;\n" +
            "            secondary_voltage 400;\n" +
            "            resistance 0.011;\n" +
            "            reactance 0.02;\n" +
            "        };\n" +
            "\n" +
            "        object node\n" +
            "        {\n" +
            "            name \"HAS42130\";\n" +
            "            phases \"ABCN\";\n" +
            "            bustype PQ;\n" +
            "            nominal_voltage 400V;\n" +
            "        }\n" +
            "\n" +
            "        object meter\n" +
            "        {\n" +
            "            name \"HAS42130_0\";\n" +
            "            phases \"ABCN\";\n" +
            "            bustype PQ;\n" +
            "            nominal_voltage 400V;\n" +
            "        };\n" +
            "\n" +
            "        object underground_line\n" +
            "        {\n" +
            "            name \"HAS42130_0_stub\";\n" +
            "            phases \"ABCN\";\n" +
            "            from \"HAS42130\";\n" +
            "            to \"HAS42130_0\";\n" +
            "            length 25m;\n" +
            "            configuration \"line_3x25Cu/25\";\n" +
            "        };";
        return (result)
    }

    def stuff (sc: SparkContext, sqlContext: SQLContext, args: String): String =
    {
        return ("OK")
    }
}

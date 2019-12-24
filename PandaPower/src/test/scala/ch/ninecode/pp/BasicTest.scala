package ch.ninecode.pp

import java.io.File
import java.io.PrintWriter

class BasicTest extends SparkSuite
{
    test ("try it")
    {
        session =>
            val workdir = "simulation/"
            val trafo = "BASIC"
            new File (s"$workdir$trafo/output_data").mkdirs
            val triple = "\"\"\""
            val contents = s"""
import os
import numpy as np
import pandas as pd
import tempfile
import pandapower as pp
from pandapower.control import ConstControl
from pandapower.timeseries import DFData
from pandapower.timeseries import OutputWriter
from pandapower.timeseries.run_time_series import run_timeseries

def timeseries_example(output_dir):
    # 1. create test net
    net = simple_test_net()

    # 2. create (random) data source
    n_timesteps = 10
    profiles, ds = create_data_source(n_timesteps)
    # 3. create controllers (to control P values of the load and the sgen)
    net = create_controllers(net, ds)

    # time steps to be calculated. Could also be a list with non-consecutive time steps
    time_steps = range(0, n_timesteps)

    # 4. the output writer with the desired results to be stored to files.
    ow = create_output_writer(net, time_steps, output_dir)

    # 5. the main time series function
    run_timeseries(net, time_steps)

def simple_test_net():
    $triple
    simple net that looks like:

    ext_grid b0---b1 trafo(110/20) b2----b3 load
                                    |
                                    |
                                    b4 sgen
    $triple
    net = pp.create_empty_network()
    pp.set_user_pf_options(net, init_vm_pu = "flat", init_va_degree = "dc", calculate_voltage_angles=True)

    b0 = pp.create_bus(net, 110)
    b1 = pp.create_bus(net, 110)
    b2 = pp.create_bus(net, 20)
    b3 = pp.create_bus(net, 20)
    b4 = pp.create_bus(net, 20)

    pp.create_ext_grid(net, b0)
    pp.create_line(net, b0, b1, 10, "149-AL1/24-ST1A 110.0")
    pp.create_transformer(net, b1, b2, "25 MVA 110/20 kV", name='tr1')
    pp.create_line(net, b2, b3, 10, "184-AL1/30-ST1A 20.0")
    pp.create_line(net, b2, b4, 10, "184-AL1/30-ST1A 20.0")

    pp.create_load(net, b2, p_mw=20., q_mvar=10., name='load1')
    pp.create_sgen(net, b4, p_mw=20., q_mvar=0.15, name='sgen1')

    return net

def create_data_source(n_timesteps=10):
    profiles = pd.DataFrame()
    profiles['load1_p'] = np.random.random(n_timesteps) * 20.
    profiles['sgen1_p'] = np.random.random(n_timesteps) * 20.

    ds = DFData(profiles)

    return profiles, ds

def create_controllers(net, ds):
    ConstControl(net, element='load', variable='p_mw', element_index=[0],
                 data_source=ds, profile_name=["load1_p"])
    ConstControl(net, element='sgen', variable='p_mw', element_index=[0],
                 data_source=ds, profile_name=["sgen1_p"])
    return net

def create_output_writer(net, time_steps, output_dir):
    ow = OutputWriter(net, time_steps, output_path=output_dir, output_file_type=".csv", log_variables=list())

    # create a mask to get the indices of all the hv buses in the grid
    mask_hv_buses = (net.bus.vn_kv > 70.0) & (net.bus.vn_kv < 380.0)
    hv_busses_index = net.bus.loc[mask_hv_buses].index
    # create a mask to get the indices of all the mv buses in the grid
    mask_mv_buses = (net.bus.vn_kv > 1.0) & (net.bus.vn_kv < 70.0)
    mv_busses_index = net.bus.loc[mask_mv_buses].index
    # now define the output writer, so that it gets the indices and specify the evaluation functions
    # since we want the maximum voltage of all mv buses, we provide the indices of the mv buses and the minimum
    # function np.min. The variable "eval_name" is free to chose and contains the name of the column in
    # which the results are saved.
    ow.log_variable('res_bus', 'p_mw', index=hv_busses_index, eval_function=np.sum, eval_name="hv_bus_sum_p")
    ow.log_variable('res_bus', 'vm_pu', index=mv_busses_index, eval_function=np.max, eval_name="mv_bus_max")
    return ow

output_dir = os.getcwd() + "/output_data"
print("Results can be found in: {}".format(output_dir))
if not os.path.exists(output_dir):
    os.mkdir(output_dir)
timeseries_example(output_dir)
        """

            val pw = new PrintWriter (new File (s"$workdir${trafo}/${trafo}.py"))
            pw.write (contents)
            pw.close ()


            val files = session.sparkContext.parallelize (Seq(trafo))
            val pp = PandaPower (session = session, workdir = workdir)
            val (success: Boolean, errors: List[String]) = pp.solve (files)
            assert (success, "PandaPower failed")
            assert (errors.isEmpty, "no errors")
    }
}

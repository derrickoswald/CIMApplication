package ch.ninecode.pp

import ch.ninecode.net.LineDetails
import ch.ninecode.net.LineEdge
import ch.ninecode.net.LoadFlowNode
import ch.ninecode.net.SwitchEdge
import ch.ninecode.net.TransformerEdge
import ch.ninecode.util.Complex

class PandaPowerGenerator extends Serializable
{
    /**
     * The name of the generated file.
     *
     * @return The unique name for the generated file and directory structure.
     */
    def name: String = "pandapower"

    /**
     * The directory to place the simulation files in.
     * Defaults to the same as the name of the generated file.
     *
     * @return The (relative) directory to use for files (script, input, output).
     */
    def directory: String = name

    /**
     * The header comment.
     *
     * @return A single line of text for the comment header.
     */
    def header: String = "PandaPower"

    /**
     * The nodes to be included in the PandaPower script.
     *
     * @return The ConnectivityNode or TopologicalNode elements to include in the export.
     */
    def nodes: Iterable[LoadFlowNode] = List ()

    /**
     * The cable edges to include in the PandaPower script.
     *
     * @return The edges to be included in the export.
     */
    def lines: Iterable[LineEdge] = List ()

    /**
     * The switch edges to include in the PandaPower script.
     *
     * @return The edges to be included in the export.
     */
    def switches: Iterable[SwitchEdge] = List ()

    /**
     * The transformer edges to include in the PandaPower script.
     *
     * @return The edges to be included in the export.
     */
    def transformers: Iterable[TransformerEdge] = List ()

    /**
     * The ID of the SWING or slack bus nodes and their voltages.
     *
     * The swing bus is used to provide for system losses by emitting or absorbing active and/or reactive power
     * to and from the system, acting as an infinite source or sink.
     *
     * @return The id and voltage of the node or nodes that are a swing bus (or slack bus).
     */
    def swing_nodes: Iterable[LoadFlowNode] = List ()

    /**
     * Additional text to add to the PandaPower script.
     *
     * This is sort of an escape hatch to provide the means to inject arbitrary lines of text into the
     * exported PandaPower script.
     *
     * @return Text to add to the .glm file.
     */
    def extra: Iterable[String] = List ()

    /**
     * Bus index lookup.
     */
    def busIndex (name: String): String = s"""busIndex("$name")"""

    /**
     * Generate the script header.
     *
     * This includes the meta-data comments and imports.
     *
     * @return The text for the script header.
     */
    def prefix: Array[String] =
    {
        val preamble =
            s"""# $header
               |
               |import os
               |import numpy as np
               |import pandas as pd
               |import tempfile
               |import pandapower as pp
               |import pandapower.shortcircuit as sc
               |from pandapower.control import ConstControl
               |from pandapower.timeseries import DFData
               |from pandapower.timeseries import OutputWriter
               |from pandapower.timeseries.run_time_series import run_timeseries
               |""".stripMargin

        val net =
            """net = pp.create_empty_network()
              |pp.set_user_pf_options(net, init_vm_pu = "flat", init_va_degree = "dc", calculate_voltage_angles=True)
              |""".stripMargin

        val index =
            """def busIndex(name):
              |   return net.bus[(net.bus.name==name)].index.values[0]
              |""".stripMargin

        Array (preamble, net, index)
    }

    def getTransformerConfigurations: Array[String] =
    {
        // get unique configurations
        val configurations = transformers.map (_.configurationName).toSet
        val templates = configurations.flatMap (configuration => transformers.find (_.configurationName == configuration))

        def getShortCircuitVoltage (edge: TransformerEdge): Complex =
        {
            val ratio = edge.transformer.v0 / edge.transformer.v1
            val z = edge.transformer.total_impedance._1 * ratio * ratio
            val i = edge.transformer.power_rating / edge.transformer.v0
            z * i
        }

        def getIronLosses: Double = 0.5 // kW

        def getOpenLoopLosses: Double = 0.0

        def getPhaseShift: Double = 0.0

        // see https://pandapower.readthedocs.io/en/latest/elements/trafo.html
        val details = for (edge <- templates)
            yield
                s"""    "${edge.configurationName}":
                   |    {
                   |        "sn_mva":       ${edge.transformer.power_rating / 1000000.0},
                   |        "vn_hv_kv":     ${edge.transformer.v0 / 1000.0},
                   |        "vn_lv_kv":     ${edge.transformer.v1 / 1000.0},
                   |        "vk_percent":   ${getShortCircuitVoltage (edge).modulus / edge.transformer.v0 * 100.0},
                   |        "vkr_percent":  ${getShortCircuitVoltage (edge).re / edge.transformer.v0 * 100.0},
                   |        "pfe_kw":       $getIronLosses,
                   |        "i0_percent":   $getOpenLoopLosses,
                   |        "shift_degree": $getPhaseShift
                   |    }""".stripMargin

        val types =
            s"""transformertypes = {
               |${details.mkString (",\n")}
               |}""".stripMargin

        Array (
            types,
            s"""pp.std_types.create_std_types(net, data=transformertypes, element="trafo", overwrite=False)\n"""
        )
    }

    def getLineConfigurations: Array[String] =
    {
        // get unique configurations
        val configurations = lines.map (_.configurationName).toSet
        // ToDo: find the "best" template - the one with PerLengthImpedance and WireInfo
        val templates = configurations.flatMap (configuration => lines.find (_.configurationName == configuration))

        // line.data.perLengthCapacitance // ToDo: capacitance
        def getCapacitance (line: LineEdge): Double = 0.0

        // line.data.perLengthCurrentRating // ToDo: currentRating
        def getCurrentRating (line: LineEdge): Double = 100.0

        // WTF?
        def getType (line: LineEdge): String = "cs"

        def getCrossSectionalArea (line: LineEdge): Double = 16.0

        // see https://pandapower.readthedocs.io/en/latest/elements/line.html
        val details = for
            (
            line: LineEdge <- templates;
            z = line.data.perLengthImpedance // ToDo: temperature
        )
            yield
                s"""    "${line.configurationName}":
                   |    {
                   |        "r_ohm_per_km": ${z.z1.re * 1000.0},
                   |        "x_ohm_per_km": ${z.z1.im * 1000.0},
                   |        "c_nf_per_km":  ${getCapacitance (line) * 1e-9 * 1000.0},
                   |        "max_i_ka":     ${getCurrentRating (line) / 1000.0},
                   |        "type":         "${getType (line)}",
                   |        "q_mm2":        ${getCrossSectionalArea (line)},
                   |        "alpha":        ${LineDetails.ALPHA}
                   |    }""".stripMargin
        val types =
            s"""linetypes = {
               |${details.mkString (",\n")}
               |}""".stripMargin

        Array (
            types,
            s"""pp.std_types.create_std_types(net, data=linetypes, element="line", overwrite=False)\n"""
        )
    }

    def emitNode (node: LoadFlowNode): String =
    // pandapower.create_bus(net, vn_kv, name=None, index=None, geodata=None, type='b', zone=None, in_service=True,
    // max_vm_pu=nan, min_vm_pu=nan, coords=None, **kwargs)
        s"""pp.create_bus(net, ${node.nominal_voltage / 1000.0}, "${node.id}")"""

    /**
     * Get the r/x (re/im) ratio of the complex impedance value.
     *
     * @param z the impedance to compute
     * @return the r/x ratio
     */
    def rx (z: Complex): Double = if (0.0 == z.im) Double.MaxValue else z.re / z.im

    def emitGrid (node: LoadFlowNode): String =
    {
        //  pandapower.create_ext_grid(net, bus, vm_pu=1.0, va_degree=0.0, name=None,
        //  in_service=True, s_sc_max_mva=nan, s_sc_min_mva=nan, rx_max=nan, rx_min=nan, max_p_mw=nan, min_p_mw=nan,
        //  max_q_mvar=nan, min_q_mvar=nan, index=None, **kwargs)
        val sc = node match
        {
            case grid: PandaPowerExternalGridNode =>
                // s_sc_max_mva - maximal short circuit apparent power to calculate internal impedance of ext_grid
                // s_sc_min_mva - minimal short circuit apparent power to calculate internal impedance of ext_grid
                // rx_max - maximal R/X-ratio to calculate internal impedance of ext_grid
                // rx_min - minimal R/X-ratio to calculate internal impedance of ext_grid
                s""", s_sc_max_mva=${grid.pmax}, s_sc_min_mva=${grid.pmin}, rx_max=${rx (grid.zmax)}, rx_min=${rx (grid.zmin)}"""
            case _: LoadFlowNode => ""
        }
        s"""pp.create_ext_grid(net, bus=${busIndex (node.id)}$sc)"""
    }

    def emitTransformer (transformer: TransformerEdge): String =
    {
        val config = s"""${transformer.configurationName}"""
        val name = s"""${transformer.name}"""
        val nodes = transformer.transformer.transformers.head.nodes
            .map (_.id)
            .toList
            .map (node => busIndex (node))
            .mkString (", ")
        // pandapower.create_transformer(net, hv_bus, lv_bus, std_type, name=None, tap_pos=nan, in_service=True,
        //    index=None, max_loading_percent=nan, parallel=1, df=1.0)
        // pandapower.create_transformer3w(net, hv_bus, mv_bus, lv_bus, std_type, name=None, tap_pos=nan, in_service=True,
        //    index=None, max_loading_percent=nan, tap_at_star_point=False)
        s"""pp.create_transformer(net, $nodes, "$config", "$name")"""
    }

    def emitLine (line: LineEdge): String =
    // pandapower.create_line(net, from_bus, to_bus, length_km, std_type, name=None,
    //    index=None, geodata=None, df=1.0, parallel=1, in_service=True, max_loading_percent=nan, alpha=None, temperature_degree_celsius=None)
    // ToDo: can we use alpha and temperature_degree_celsius to perform the temperature compensation?
    // ToDo: should we use parallel>1 given that it is limited to cables of the same type
        s"""pp.create_line(net, ${busIndex (line.cn1)}, ${busIndex (line.cn2)}, ${line.length / 1000.0}, "${line.configurationName}", "${line.id}")"""

    def emitSwitch (switch: SwitchEdge): String =
    // pandapower.create_switch(net, bus, element, et, closed=True, type=None, name=None, index=None, z_ohm=0)
        s"""pp.create.switch(net, ${busIndex (switch.cn1)}, ${busIndex (switch.cn2)}, "b", ${switch.closed}, name="${switch.id}")"""

    /**
     * Combine all the pieces into a complete network.
     *
     * Combines the header, transformer and line configurations, swing bus, nodes, edges and any extra text
     * into the complete PandaPower script.
     *
     * @return The PandaPower script contents ready for saving.
     */
    def makeScript: String =
    {
        // create the output script
        val allstrings = Array.concat (
            prefix, // emit the prefix text
            getTransformerConfigurations, // emit the transformer configurations
            getLineConfigurations, // emit line configurations
            nodes.map (emitNode).toArray, // emit the node strings
            swing_nodes.map (emitGrid).toArray, // emit the swing node
            transformers.map (emitTransformer).toArray, // emit the transformers
            lines.map (emitLine).toArray, // emit the cables
            switches.map (emitSwitch).toArray, // emit the switches
            extra.toArray // emit any extra text
        )

        allstrings.mkString ("\n")
    }
}

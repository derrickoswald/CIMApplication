GridLAB-D
======

Distributed load-flow calculations using [GridLAB-D](http://www.gridlabd.org/).

#Overview

This program reads in a [CIM file](http://cimug.ucaiug.org/default.aspx) describing an electrical network
and evaluates the maximum feed-in power at each house - to determine the upper size limit when a customer
requests permission to install a photo-voltaic (PV) system that would supply power to the network.

It's function is basically to run simulations for each house (CIM class EnergyConsumer) that determine at
what power level the injected current would raise the line voltage above 3% of nominal or would
cause the maximum rated current of any cable to be exceeded or would cause the power rating of the transformer
to be exceeded.

The output is a simple SQLite database providing, for each house id (CIM rdf:ID), the maximum power in Watts.
This database can be utilized to augment a graphical display on a map or schematic or to provide reports
and other downstream analysis.

The program runs on [Spark](https://spark.apache.org), usually on a cluster such as
[Amazon Web Services (AWS)](https://aws.amazon.com), [Azure](https://docs.microsoft.com/en-us/azure) or
an in-house system.

#Input

The starting point for analysis is a CIM export file.
The files that have been tested are exported from [nisStrom](http://www.nis.ch).
Normally the entire network is exported, but the program can work on files that are just a subset of the network.
The input file includes all electrically salient features and their connections, but can include location
and asset information which are not used by this program.

A CIM file is in RDF XML format. It includes electrical modeling parameters, such as switch status,
impedance, rated maximums, nominal power, etc.
For example, a typical cable element is shown below.

'''
        <cim:ACLineSegment rdf:ID="KLE243310">
                <cim:IdentifiedObject.name>TT-CLT 4x50</cim:IdentifiedObject.name>
                <cim:IdentifiedObject.aliasName>204282032:nis_el_cable</cim:IdentifiedObject.aliasName>
                <cim:PowerSystemResource.Location rdf:resource="#_location_685171_1142578125_204282047"/>
                <cim:PowerSystemResource.PSRType rdf:resource="#PSRType_Underground"/>
                <cim:PowerSystemResource.AssetDatasheet rdf:resource="#ART244"/>
                <cim:Equipment.EquipmentContainer rdf:resource="#_line_73674326"/>
                <cim:ConductingEquipment.BaseVoltage rdf:resource="#BaseVoltage_400"/>
                <cim:ConductingEquipment.SvStatus rdf:resource="#in_use"/>
                <cim:Conductor.length>47.48449322</cim:Conductor.length>
                <cim:ACLineSegment.b0ch>0</cim:ACLineSegment.b0ch>
                <cim:ACLineSegment.bch>257.6105976</cim:ACLineSegment.bch>
                <cim:ACLineSegment.g0ch>0</cim:ACLineSegment.g0ch>
                <cim:ACLineSegment.gch>0</cim:ACLineSegment.gch>
                <cim:ACLineSegment.r0>2.076</cim:ACLineSegment.r0>
                <cim:ACLineSegment.r>0.519</cim:ACLineSegment.r>
                <cim:ACLineSegment.shortCircuitEndTemperature>160</cim:ACLineSegment.shortCircuitEndTemperature>
                <cim:ACLineSegment.x0>0.336</cim:ACLineSegment.x0>
                <cim:ACLineSegment.x>0.084</cim:ACLineSegment.x>
        </cim:ACLineSegment>
'''

This 47.5m underground cable, of 4 conductors of 50 sq. mm. each, has a positive sequence impedance of 0.519+0.084jΩ
per kilometer and is in service at 400 volts. Not shown is that topologically, it is connected to a house and
an intermediate junction.

#Processing

The CIM file is read into Spark using a custom reader, [CIMScala](https://github.com/derrickoswald/CIMScala).
The reader produces a set of [Resilient Distributed Dataset (RDD)](https://spark.apache.org/docs/latest/programming-guide.html#resilient-distributed-datasets-rdds), one for each class of CIM element including their relations.
It also executes a topological analysis to identify a reduced subset of nodes
(sets of electrically identical ConnectivityNode elements - 
connected with zero Ohm conductors such as bus bars or closed switches)
and topological islands isolated by transformers and open switches.
Usually, each power transformer supplies one topological island (ganged/parallel transformers are not handled yet).

##Preprocessing

A graph traversal using [GraphX](https://spark.apache.org/docs/latest/graphx-programming-guide.html) yields the impedance between the transformer and each house, from which a best-case upper bound on maximum injected power can be computed.

##Load Flow

The program writes the island's equivalent circuit as
a [.glm](http://gridlab-d.sourceforge.net/wiki/index.php/Creating_GLM_Files) file
for GridLAB-D and also generates simulated power injection
[player](http://gridlab-d.sourceforge.net/wiki/index.php/Player) files for each house.
It then executes GridLAB-D to run load flow calculations.
The results of the load flow (voltages at each house and current through each conductor and the transformer)
are analyzed to find the maximum power at which voltages are still within tolerance and currents don't
exceed rated maximums.

##Database Output

After each simulation, final results are written to a SQLite database that is local to the spark master node.
This is the only step that runs only on the master node.

#SWING Bus

The SWING bus (also known as a [slack bus](https://en.wikipedia.org/wiki/Slack_bus) or reference bus) is
a node in the system which supplies or absorbs active and reactive power to and from the system that is
external to the network under study, to maintain the phase angle at zero with a unity per unit voltage.

For our system there were three choices for the location of the bus
 
![SWING Bus Options](https://rawgit.com/derrickoswald/CIMApplication/master/GridLAB-D/img/swing%20bus%20choices.svg "SWING Bus Options")

1. transformer low voltage node (usually 400v)
2. transformer high voltage node (usually 16kV)
3. foreign power attachment node (usually 132kV)

The transformer low voltage node would eliminate the transformer from the evaluation, which was deemed inappropriate.
The foreign power attachment point requires an accurate model of the middle voltage system, which is not available
at all stations. Hence, the SWING busses are set as the high voltage pins of the distribution transformers.


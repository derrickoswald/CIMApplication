MSCONSReader
======

Access to MSCONS (Metered Services Consumption Report).

# End Goal

When complete, this program will read in MSCONS files to create time series of measurements data.
These time series can then be transformed into:

- CIM classes (MeterReading, IntervalReading, IntervalBlock) for use in CIM based applications,
and eventually as as RDD of Common Information Model (CIM) classes similar to the [CIMReader](https://github.com/derrickoswald/CIMReader)
- player files for GridLAB-D calculations

The program will also be able to write MSCONS files as output.

# References

The web site of the United Nations Economic Commission for Europe (UNECE) - the group responsible for EDIFACT [United Nations Centre for Trade Facilitation and Electronic Business (UN/CEFACT)](http://www.unece.org/cefact/edifact/welcome.html).

A description of [EDIFACT message application level syntax rules](http://www.unece.org/tradewelcome/un-centre-for-trade-facilitation-and-e-business-uncefact/outputs/standards/unedifact/tradeedifactrules/part-4-edifact-rules-for-electronic-data-interchange-for-administration-commerce-and-transport/part-4-unedifact-rules-chapter-22-syntax-rules/part-4-unedifact-rules-chapter-22-syntax-rules-annex-b.html).

A description of [MSCONS (Metered Services Consumption Report) messages](http://www.unece.org/fileadmin/DAM/trade/untdid/d17a/trmd/mscons_c.htm)

A document from [Bundesverbandes der Energie- und Wasserwirtschaft e.V. ("BDEW")](https://bdew.de) (auf Deutsch) of [MSCONS messages that are used/implemented by BDEW](http://www.edi-energy.de/files2/MSCONS_MIG_2_2e_Lesefassung_2015_09_15_2015_09_11.pdf)

More specific machine readable documents:

- EDIFACT [Service segment specifications](http://www.gefeg.com/jswg/v4/data/v4.html)
- EDIFACT [Service code list UNSL](http://www.gefeg.com/jswg/v4x/data/v4x.html)
- EDIFACT [Directories](http://www.unece.org/tradewelcome/un-centre-for-trade-facilitation-and-e-business-uncefact/outputs/standards/unedifact/directories/download.html)

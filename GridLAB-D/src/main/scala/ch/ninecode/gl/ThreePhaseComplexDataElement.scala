package ch.ninecode.gl

/**
 * Recorder time series element.
 * @param element Node or branch name (ConnectivityNode/TopologicalNode name or MRID of cable).
 * @param millis Number of milliseconds since the epoc.
 * @param value_a Phase A value.
 * @param value_b Phase B value.
 * @param value_c Phase C value.
 * @param units <code>Volts</code> for a node, <code>Amps</code> for an edge.
 */
case class ThreePhaseComplexDataElement(
    element: String,
    millis:  Long,
    value_a: Complex,
    value_b: Complex,
    value_c: Complex,
    units:   String)

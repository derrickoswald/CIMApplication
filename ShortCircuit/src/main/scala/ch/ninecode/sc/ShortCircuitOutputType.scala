package ch.ninecode.sc

/**
 * Supported output types for short circuit calculation results.
 */
object ShortCircuitOutputType extends Enumeration
{
    type Formats = Value
    val SQLite, Cassandra = Value
}

package ch.ninecode.mfi

/**
 * Supported output types for maximum feed-in calculation results.
 */
object MaximumFeedInOutputType extends Enumeration
{
    type Formats = Value
    val SQLite, Cassandra = Value
}

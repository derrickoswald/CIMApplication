package ch.ninecode.sim

case class SimulationAggregate
(
    /**
     * The number of periods to accumulate for the aggregation.
     *
     * For example, if the period is 15 minutes (900000 mSec) and the
     * aggregation is over one day, then intervals = 96 (60 * 60 * 24 / 15).
     *
     * The interval 1 (one) is special in that it sets the time-to-live for the baseline period data.
     * At a minimum there should be one aggregation with an interval of 1 (one).
     */
    intervals: Int,

    /**
     * The Cassandra time-to-live value.
     *
     * If non-zero the records saved for this aggregation will be assigned
     * this time-o-live (https://docs.datastax.com/en/dse/6.7/cql/cql/cql_using/useExpire.html).
     */
    time_to_live: Int
)

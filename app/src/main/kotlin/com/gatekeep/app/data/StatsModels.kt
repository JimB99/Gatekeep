package com.gatekeep.app.data

sealed class StatsTimeRange {
    data class SingleDay(val dayEpochMs: Long) : StatsTimeRange()
    data class Week(val year: Int, val weekOfYear: Int) : StatsTimeRange()
    data class Month(val year: Int, val month: Int) : StatsTimeRange()
    data class Year(val year: Int) : StatsTimeRange()
}

enum class StatsRangeKind {
    day,
    week,
    month,
    year,
}

data class ChartBucket(
    val label: String,
    val usageMs: Long,
    val startMs: Long,
    val endMs: Long,
    val subLabel: String? = null,
)

data class TopAppUsage(
    val packageName: String,
    val label: String,
    val usageMs: Long,
)

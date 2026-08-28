package com.gatekeep.domain

/**
 * Allocates foreground session durations into time buckets.
 * Pure logic — no Android dependencies.
 */
object UsageBucketAggregator {

    data class ForegroundSession(
        val startMs: Long,
        val endMs: Long,
    )

    fun clipDuration(
        sessionStartMs: Long,
        sessionEndMs: Long,
        bucketStartMs: Long,
        bucketEndMs: Long,
    ): Long {
        val start = maxOf(sessionStartMs, bucketStartMs)
        val end = minOf(sessionEndMs, bucketEndMs)
        return (end - start).coerceAtLeast(0L)
    }

    fun allocateSessionsToBuckets(
        sessions: List<ForegroundSession>,
        bucketStarts: LongArray,
        bucketEnds: LongArray,
    ): LongArray {
        require(bucketStarts.size == bucketEnds.size)
        val totals = LongArray(bucketStarts.size)
        for (session in sessions) {
            if (session.endMs <= session.startMs) continue
            for (i in bucketStarts.indices) {
                totals[i] += clipDuration(session.startMs, session.endMs, bucketStarts[i], bucketEnds[i])
            }
        }
        return totals
    }

    fun computeScaleMs(bucketUsageMs: LongArray): Long {
        if (bucketUsageMs.isEmpty()) return 1L
        val nonZero = bucketUsageMs.filter { it > 0 }
        val avg = if (nonZero.isNotEmpty()) nonZero.average().toLong() else 0L
        return avg.coerceAtLeast(60_000L)
    }

    private const val TICK_STEP_MS = 15 * 60_000L

    fun snapToTickStep(ms: Long): Long {
        if (ms <= 0) return TICK_STEP_MS
        return ((ms + TICK_STEP_MS - 1) / TICK_STEP_MS) * TICK_STEP_MS
    }

    /** Y-axis tick values (ms), highest first; omits 0 when there is usage. */
    fun computeChartAxisTicks(scaleMs: Long, maxUsageMs: Long): List<Long> {
        val top = snapToTickStep(scaleMs.coerceAtLeast(TICK_STEP_MS))
        val mid = snapToTickStep(top * 2 / 3)
        val low = snapToTickStep(top / 3)
        val ticks = listOf(top, mid, low).distinct().sortedDescending()
        return if (maxUsageMs > 0) ticks.filter { it > 0 } else ticks
    }
}

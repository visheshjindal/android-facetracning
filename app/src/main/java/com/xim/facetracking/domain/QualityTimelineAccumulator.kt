package com.xim.facetracking.domain

/** Session-scoped history with a non-mutating snapshot. startMs uses the recording clock. */
class QualityTimelineAccumulator(private val startMs: Long, private val maximumSampleAgeMs: Long = 300) {
    private data class Sample(val time: Long, val issues: List<QualityIssue>)
    private val samples = mutableListOf<Sample>()
    fun add(timestampMs: Long, issues: List<QualityIssue>) {
        if (timestampMs < startMs || samples.lastOrNull()?.let { timestampMs <= it.time } == true) return
        samples += Sample(timestampMs, issues.toList())
    }

    fun snapshot(durationMs: Long): QualityTimeline {
        val endMs = startMs + durationMs
        var covered = 0L
        var cursor = startMs
        val intervals = mutableListOf<IssueInterval>()
        fun append(type: QualityIssueType, severity: IssueSeverity, start: Long, end: Long) {
            if (end <= start) return
            val index = intervals.indexOfLast { it.type == type && it.endMs == start - startMs }
            if (index >= 0) intervals[index] = intervals[index].copy(endMs = end - startMs)
            else intervals += IssueInterval(type, start - startMs, end - startMs, severity)
        }
        samples.forEachIndexed { index, sample ->
            if (sample.time >= endMs) return@forEachIndexed
            if (sample.time > cursor) append(QualityIssueType.TRACKING_UNRELIABLE, IssueSeverity.HARD, cursor, sample.time)
            val until = minOf(samples.getOrNull(index + 1)?.time ?: endMs, sample.time + maximumSampleAgeMs, endMs)
            covered += until - sample.time
            sample.issues.forEach { append(it.type, it.severity, sample.time, until) }
            cursor = until
        }
        if (cursor < endMs) append(QualityIssueType.TRACKING_UNRELIABLE, IssueSeverity.HARD, cursor, endMs)
        return QualityTimeline(intervals.sortedBy { it.startMs }, durationMs, covered)
    }
}

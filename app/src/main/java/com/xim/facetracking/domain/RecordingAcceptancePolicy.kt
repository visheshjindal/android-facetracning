package com.xim.facetracking.domain

class RecordingAcceptancePolicy(private val spec: CaptureSpec = CaptureSpec.DefaultV0_1) {
    fun evaluate(artifact: VideoArtifact, timeline: QualityTimeline): CaptureReport {
        val hard = union(timeline.intervals.filter { it.severity == IssueSeverity.HARD })
        val soft = union(timeline.intervals.filter { it.severity == IssueSeverity.SOFT })
        val hardInvalid = hard.sumOf { it.second - it.first } > spec.maxHardFailureMs || hard.any { it.second - it.first > spec.maxHardFailureIntervalMs }
        val softInvalid = soft.sumOf { it.second - it.first } > spec.maxSoftFailureMs || soft.any { it.second - it.first > spec.maxSoftFailureIntervalMs }
        val accepted = artifact.valid && artifact.durationMs in spec.acceptedDurationMinMs..spec.acceptedDurationMaxMs &&
            timeline.coverage >= spec.minimumAnalysisCoverage && !hardInvalid && !softInvalid
        val failure = timeline.intervals.sortedWith(compareBy<IssueInterval> { it.type.priority() }.thenByDescending { it.durationMs }).firstOrNull()?.type
        val gaps = union(timeline.intervals.filter { it.type == QualityIssueType.TRACKING_UNRELIABLE }).sumOf { it.second - it.first }
        return CaptureReport(spec.version, artifact.durationMs, timeline, accepted, if (accepted) null else failure, gaps)
    }

    private fun union(intervals: List<IssueInterval>): List<Pair<Long, Long>> {
        val result = mutableListOf<Pair<Long, Long>>()
        intervals.sortedBy { it.startMs }.forEach {
            val previous = result.lastOrNull()
            if (previous != null && it.startMs <= previous.second) result[result.lastIndex] = previous.first to maxOf(previous.second, it.endMs)
            else result += it.startMs to it.endMs
        }
        return result
    }
}

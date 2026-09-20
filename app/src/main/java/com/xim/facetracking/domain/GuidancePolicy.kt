package com.xim.facetracking.domain

data class GuidanceMemory(
    val candidate: QualityIssueType? = null,
    val candidateSince: Long = 0,
    val active: QualityIssue? = null,
    val activeSince: Long = 0,
    val acceptableSince: Long? = null,
    val lastSample: Long? = null
)
data class GuidanceTransition(val memory: GuidanceMemory, val state: GuidanceState)

/** Call with a fresh GuidanceMemory for each capture attempt. */
class GuidancePolicy(private val spec: CaptureSpec = CaptureSpec.DefaultV0_1) {
    fun update(memory: GuidanceMemory, observation: FrameObservation, issues: List<QualityIssue>): GuidanceTransition {
        val now = observation.timestampMs
        val issue = issues.minByOrNull { it.type.priority() }
        val gap = memory.lastSample?.let { now - it > 300L } ?: false
        val candidateSince = if (memory.candidate != issue?.type || gap) now else memory.candidateSince
        var active = memory.active
        var activeSince = memory.activeSince
        if (issue != null && now - candidateSince >= spec.issueOnsetMs &&
            (active == null || now - activeSince >= spec.minimumPromptMs || issue.type.priority() < active.type.priority())) {
            if (active?.type != issue.type) activeSince = now
            active = issue
        } else if (issue == null && now - candidateSince >= spec.issueRecoveryMs && now - activeSince >= spec.minimumPromptMs) active = null
        val since = if (issues.isNotEmpty() || !observation.trackingReliable || observation.primaryFace == null) null
            else if (gap) now else memory.acceptableSince ?: now
        val next = GuidanceMemory(issue?.type, candidateSince, active, activeSince, since, now)
        return GuidanceTransition(next, GuidanceState(active, since?.let { now - it >= spec.readinessMs } == true,
            observation.trackingReliable && observation.primaryFace != null))
    }
}

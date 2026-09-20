package com.xim.facetracking.domain

data class FaceSnapshot(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val regionsVisible: Boolean = true
)

data class LightingMetrics(
    val medianLuma: Float,
    val highlightFraction: Float,
    val cheekImbalance: Float,
    val temporalChange: Float = 0f
)

data class FrameObservation(
    val timestampMs: Long,
    val faceCount: Int,
    val primaryFace: FaceSnapshot?,
    val trackingReliable: Boolean,
    val lighting: LightingMetrics? = null,
    val sharpness: Float? = null,
    val regions: FacialRegions? = null
)

enum class QualityIssueType {
    METRICS_UNAVAILABLE,
    FACE_MISSING,
    MULTIPLE_FACES,
    TRACKING_UNRELIABLE,
    REGION_UNAVAILABLE,
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_UP,
    MOVE_DOWN,
    MOVE_CLOSER,
    MOVE_FARTHER,
    LOOK_STRAIGHT,
    HOLD_STEADY,
    LIGHT_TOO_DIM,
    LIGHT_TOO_BRIGHT,
    LIGHT_UNEVEN,
    LIGHT_CHANGING,
    BLURRY
}

enum class IssueSeverity { HARD, SOFT }

data class QualityIssue(
    val type: QualityIssueType,
    val severity: IssueSeverity,
    val timestampMs: Long
)

data class GuidanceState(
    val primaryIssue: QualityIssue? = null,
    val ready: Boolean = false,
    val trackingVisible: Boolean = false
)

data class IssueInterval(
    val type: QualityIssueType,
    val startMs: Long,
    val endMs: Long,
    val severity: IssueSeverity
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

data class QualityTimeline(
    val intervals: List<IssueInterval> = emptyList(),
    val analyzedMs: Long = 0L,
    val coveredMs: Long = 0L
) {
    val coverage: Float
        get() = if (analyzedMs <= 0L) 0f else coveredMs.toFloat() / analyzedMs.toFloat()
}

data class VideoArtifact(
    val id: ArtifactId,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String = "video/mp4",
    val sessionId: String,
    val valid: Boolean = true
)

data class CaptureReport(
    val specificationVersion: String,
    val durationMs: Long,
    val timeline: QualityTimeline,
    val accepted: Boolean,
    val primaryFailure: QualityIssueType? = null,
    val trackingGapMs: Long = 0L
)

package com.xim.facetracking.domain

import kotlin.math.abs

/** Stateless checks. Prompt selection happens later; every measurable failure is retained. */
class QualityEvaluator(private val spec: CaptureSpec = CaptureSpec.DefaultV0_1) {
    fun evaluate(observation: FrameObservation): List<QualityIssue> = buildList {
        fun issue(type: QualityIssueType, severity: IssueSeverity = IssueSeverity.SOFT) {
            add(QualityIssue(type, severity, observation.timestampMs))
        }
        val face = observation.primaryFace
        if (observation.faceCount > 1) issue(QualityIssueType.MULTIPLE_FACES, IssueSeverity.HARD)
        if (!observation.trackingReliable) issue(QualityIssueType.TRACKING_UNRELIABLE, IssueSeverity.HARD)
        if (observation.faceCount == 0 || face == null) {
            issue(QualityIssueType.FACE_MISSING, IssueSeverity.HARD)
            return@buildList
        }
        if (!face.regionsVisible || face.centerX - face.width / 2 < 0.03f ||
            face.centerX + face.width / 2 > 0.97f || face.centerY - face.height / 2 < 0.03f ||
            face.centerY + face.height / 2 > 0.97f) issue(QualityIssueType.REGION_UNAVAILABLE, IssueSeverity.HARD)
        when {
            face.centerX < spec.targetCenterX - spec.targetTolerance -> issue(QualityIssueType.MOVE_RIGHT)
            face.centerX > spec.targetCenterX + spec.targetTolerance -> issue(QualityIssueType.MOVE_LEFT)
        }
        when {
            face.centerY < spec.targetCenterY - spec.targetTolerance -> issue(QualityIssueType.MOVE_DOWN)
            face.centerY > spec.targetCenterY + spec.targetTolerance -> issue(QualityIssueType.MOVE_UP)
        }
        when {
            face.width < spec.minFaceWidth -> issue(QualityIssueType.MOVE_CLOSER)
            face.width > spec.maxFaceWidth -> issue(QualityIssueType.MOVE_FARTHER)
        }
        if (abs(face.yaw) > spec.maxYaw || abs(face.pitch) > spec.maxPitch || abs(face.roll) > spec.maxRoll) issue(QualityIssueType.LOOK_STRAIGHT)
        val light = observation.lighting
        if (light == null || observation.sharpness == null || observation.regions == null) {
            issue(QualityIssueType.METRICS_UNAVAILABLE, IssueSeverity.HARD)
        }
        if (light != null) {
            if (light.medianLuma < spec.minRegionalLuma) issue(QualityIssueType.LIGHT_TOO_DIM)
            if (light.medianLuma > spec.maxRegionalLuma || light.highlightFraction > spec.maxHighlightFraction) issue(QualityIssueType.LIGHT_TOO_BRIGHT)
            if (light.cheekImbalance > spec.maxCheekImbalance) issue(QualityIssueType.LIGHT_UNEVEN)
            if (light.temporalChange > 0.20f) issue(QualityIssueType.LIGHT_CHANGING)
        }
        if (observation.sharpness?.let { it < spec.minSharpness } == true) issue(QualityIssueType.BLURRY)
    }
}

fun QualityIssueType.priority(): Int = when (this) {
    QualityIssueType.FACE_MISSING, QualityIssueType.TRACKING_UNRELIABLE, QualityIssueType.MULTIPLE_FACES -> 0
    QualityIssueType.REGION_UNAVAILABLE, QualityIssueType.METRICS_UNAVAILABLE -> 1
    QualityIssueType.MOVE_LEFT, QualityIssueType.MOVE_RIGHT, QualityIssueType.MOVE_UP, QualityIssueType.MOVE_DOWN,
    QualityIssueType.MOVE_CLOSER, QualityIssueType.MOVE_FARTHER, QualityIssueType.LOOK_STRAIGHT -> 2
    QualityIssueType.LIGHT_TOO_DIM, QualityIssueType.LIGHT_TOO_BRIGHT, QualityIssueType.LIGHT_UNEVEN, QualityIssueType.LIGHT_CHANGING -> 3
    QualityIssueType.BLURRY, QualityIssueType.HOLD_STEADY -> 4
}

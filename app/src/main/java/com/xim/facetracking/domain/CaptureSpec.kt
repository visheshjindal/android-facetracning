package com.xim.facetracking.domain

/**
 * All capture thresholds live in this versioned object.  The values are intentionally
 * provisional and can be replaced by the research team's version without changing
 * the capture state machine.
 */
data class CaptureSpec(
    val version: String,
    val durationMs: Long,
    val readinessMs: Long,
    val issueOnsetMs: Long,
    val issueRecoveryMs: Long,
    val minimumPromptMs: Long,
    val targetCenterX: Float,
    val targetCenterY: Float,
    val targetTolerance: Float,
    val minFaceWidth: Float,
    val maxFaceWidth: Float,
    val maxYaw: Float,
    val maxPitch: Float,
    val maxRoll: Float,
    val maxMovementDiagonal: Float,
    val maxMovementAngle: Float,
    val minRegionalLuma: Float,
    val maxRegionalLuma: Float,
    val maxHighlightFraction: Float,
    val maxCheekImbalance: Float,
    val minSharpness: Float,
    val acceptedDurationMinMs: Long,
    val acceptedDurationMaxMs: Long,
    val minimumAnalysisCoverage: Float,
    val maxHardFailureMs: Long,
    val maxHardFailureIntervalMs: Long,
    val maxSoftFailureMs: Long,
    val maxSoftFailureIntervalMs: Long
) {
    companion object {
        val DefaultV0_1 = CaptureSpec(
            version = "capture-v0.1",
            durationMs = 40_000L,
            readinessMs = 2_000L,
            issueOnsetMs = 500L,
            issueRecoveryMs = 750L,
            minimumPromptMs = 1_000L,
            targetCenterX = 0.50f,
            targetCenterY = 0.45f,
            targetTolerance = 0.08f,
            minFaceWidth = 0.30f,
            maxFaceWidth = 0.48f,
            maxYaw = 10f,
            maxPitch = 10f,
            maxRoll = 8f,
            maxMovementDiagonal = 0.025f,
            maxMovementAngle = 5f,
            minRegionalLuma = 55f,
            maxRegionalLuma = 210f,
            maxHighlightFraction = 0.05f,
            maxCheekImbalance = 0.25f,
            minSharpness = 60f,
            acceptedDurationMinMs = 39_500L,
            acceptedDurationMaxMs = 41_000L,
            minimumAnalysisCoverage = 0.90f,
            maxHardFailureMs = 2_000L,
            maxHardFailureIntervalMs = 1_000L,
            maxSoftFailureMs = 4_000L,
            maxSoftFailureIntervalMs = 2_000L
        )
    }
}

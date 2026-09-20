package com.xim.facetracking.domain

import kotlin.math.abs
import kotlin.math.hypot

/** History belongs to the caller's tracking session; this evaluator has no hidden state. */
class MovementEvaluator(private val spec: CaptureSpec = CaptureSpec.DefaultV0_1) {
    fun evaluate(current: FrameObservation, history: List<FrameObservation>): QualityIssue? {
        val face = current.primaryFace ?: return null
        val previous = history.firstOrNull {
            current.timestampMs - it.timestampMs in 1..500 && it.trackingReliable && it.primaryFace != null
        }?.primaryFace ?: return null
        val distance = hypot(face.centerX - previous.centerX, face.centerY - previous.centerY)
        val rotation = maxOf(abs(face.yaw - previous.yaw), abs(face.pitch - previous.pitch), abs(face.roll - previous.roll))
        return if (distance > spec.maxMovementDiagonal || rotation > spec.maxMovementAngle)
            QualityIssue(QualityIssueType.HOLD_STEADY, IssueSeverity.SOFT, current.timestampMs) else null
    }
}

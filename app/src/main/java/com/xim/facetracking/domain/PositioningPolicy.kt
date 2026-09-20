package com.xim.facetracking.domain

import kotlin.math.abs
import kotlin.math.min

data class PositioningFace(val x: Float, val y: Float, val width: Float, val height: Float, val yaw: Float, val pitch: Float, val roll: Float)
data class PositioningTarget(val width: Float, val height: Float)
enum class PositioningHint { PLACE_FACE, ONE_PERSON, CENTER_FACE, CLOSER, FARTHER, LOOK_STRAIGHT, HOLD_STILL, FOLLOWING, TRACKING_LOST }
data class PositioningState(
    val following: Boolean = false,
    val hint: PositioningHint = PositioningHint.PLACE_FACE,
    val stableSince: Long? = null,
    val lastSample: Long? = null,
    val anchor: PositioningFace? = null
)

/** Geometry shared by the cutout and alignment checks, with a fixed physical oval ratio. */
fun positioningTarget(width: Float, height: Float): PositioningTarget {
    if (width <= 0 || height <= 0) return PositioningTarget(0f, 0f)
    val ovalWidth = min(width * 0.72f, height * 0.50f / 1.35f)
    return PositioningTarget(ovalWidth / width, ovalWidth * 1.35f / height)
}

class PositioningPolicy {
    fun update(state: PositioningState, now: Long, face: PositioningFace?, count: Int, target: PositioningTarget): PositioningState {
        if (state.lastSample != null && now <= state.lastSample) return state
        val visible = face != null && count > 0
        if (state.following) return state.copy(lastSample = now, hint = when {
            !visible -> PositioningHint.TRACKING_LOST
            else -> PositioningHint.FOLLOWING
        })
        val hint = when {
            !visible || target.width <= 0f -> PositioningHint.PLACE_FACE
            abs(face.x - 0.5f) > target.width * 0.10f || abs(face.y - 0.5f) > target.height * 0.10f -> PositioningHint.CENTER_FACE
            face.width > target.width * 0.95f || face.height > target.height * 0.95f -> PositioningHint.FARTHER
            face.width < target.width * 0.65f || face.height < target.height * 0.65f -> PositioningHint.CLOSER
            abs(face.yaw) > 10f || abs(face.pitch) > 10f || abs(face.roll) > 8f -> PositioningHint.LOOK_STRAIGHT
            else -> PositioningHint.HOLD_STILL
        }
        val anchor = state.anchor
        val moved = face != null && anchor != null && (abs(face.x - anchor.x) > 0.025f || abs(face.y - anchor.y) > 0.025f || abs(face.width - anchor.width) > 0.025f || abs(face.yaw - anchor.yaw) > 5f || abs(face.pitch - anchor.pitch) > 5f || abs(face.roll - anchor.roll) > 5f)
        val interrupted = state.lastSample == null || now - state.lastSample > 300L || moved
        val since = if (hint != PositioningHint.HOLD_STILL) null else if (interrupted) now else state.stableSince ?: now
        val following = since != null && now - since >= 2_000L
        return PositioningState(following, if (following) PositioningHint.FOLLOWING else hint, since, now, if (since == null) null else if (since == now) face else anchor)
    }
}

package com.xim.facetracking.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Face pose in view-normalized coordinates.
 * [x], [y]: face-box center as a fraction of the preview view (0..1).
 * [width], [height]: face-box size as a fraction of the preview view.
 * [yaw], [pitch], [roll]: degrees, camera-relative (ML Kit headEulerAngleY / X / Z).
 */
data class PositioningFace(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val yaw: Float,
    val pitch: Float,
    val roll: Float
)

data class PositioningTarget(val width: Float, val height: Float)

enum class PositioningHint {
    PLACE_FACE, CENTER_FACE, CLOSER, FARTHER, LOOK_STRAIGHT, HOLD_STILL, FOLLOWING, TRACKING_LOST
}

data class PositioningState(
    val following: Boolean = false,
    val hint: PositioningHint = PositioningHint.PLACE_FACE,
    val stableSince: Long? = null,
    val lastSample: Long? = null,
    /** Face at the start of the current hold. Every later sample is compared against it, so slow drift is caught. */
    val anchor: PositioningFace? = null,
    /** Time-smoothed face used for all checks. Null after a sample gap or when there is no single usable face. */
    val smoothed: PositioningFace? = null,
    /** Hysteresis latch for the pose check: once true, the looser exit limits apply. */
    val poseOk: Boolean = false
)

data class PoseLimits(val yaw: Float, val pitch: Float, val roll: Float)

/** Starting points, not measurements: tune poseEnter/poseExit from logged raw angles. */
data class PositioningConfig(
    val holdMs: Long = 1_000L,
    val maxGapMs: Long = 300L,
    val smoothingTauMs: Float = 150f,
    val centerTolerance: Float = 0.10f,
    val minScale: Float = 0.65f,
    val maxScale: Float = 0.95f,
    val poseEnter: PoseLimits = PoseLimits(yaw = 10f, pitch = 15f, roll = 8f),
    val poseExit: PoseLimits = PoseLimits(yaw = 13f, pitch = 20f, roll = 11f),
    val maxPositionDelta: Float = 0.025f,
    val maxSizeDelta: Float = 0.025f,
    val maxAngleDelta: Float = 5f
) {
    init {
        require(holdMs > 0L) { "holdMs must be positive" }
        require(smoothingTauMs > 0f) { "smoothingTauMs must be positive" }
    }
}

/** Geometry shared by the cutout and alignment checks, with a fixed physical oval ratio. */
fun positioningTarget(width: Float, height: Float): PositioningTarget {
    if (width <= 0 || height <= 0) return PositioningTarget(0f, 0f)
    val ovalWidth = min(width * 0.72f, height * 0.50f / 1.35f)
    return PositioningTarget(ovalWidth / width, ovalWidth * 1.35f / height)
}

/**
 * Pure state machine: no clock, no Android types. [update]'s `now` must be monotonic milliseconds.
 * Not thread-safe by itself; call from one thread and pass the returned state back in.
 */
class PositioningPolicy(private val config: PositioningConfig = PositioningConfig()) {

    fun update(
        state: PositioningState,
        now: Long,
        face: PositioningFace?,
        target: PositioningTarget
    ): PositioningState {
        val last = state.lastSample
        if (last != null && now <= last) return state

        val usable = face?.takeIf { it.hasFiniteValues() }

        if (state.following) {
            return state.copy(
                lastSample = now,
                hint = if (usable != null) PositioningHint.FOLLOWING else PositioningHint.TRACKING_LOST
            )
        }

        val gap = last?.let { now - it }
        val continuous = gap != null && gap <= config.maxGapMs

        // Smooth usable primary face samples; a gap or a lost face restarts the average.
        val f = usable?.let { smooth(state.smoothed.takeIf { continuous }, it, gap ?: 0L) }

        val poseOk = f != null && withinPose(f, if (state.poseOk) config.poseExit else config.poseEnter)

        val hint = when {
            f == null || target.width <= 0f || target.height <= 0f -> PositioningHint.PLACE_FACE
            abs(f.x - 0.5f) > target.width * config.centerTolerance ||
                    abs(f.y - 0.5f) > target.height * config.centerTolerance -> PositioningHint.CENTER_FACE
            f.width > target.width * config.maxScale ||
                    f.height > target.height * config.maxScale -> PositioningHint.FARTHER
            f.width < target.width * config.minScale ||
                    f.height < target.height * config.minScale -> PositioningHint.CLOSER
            !poseOk -> PositioningHint.LOOK_STRAIGHT
            else -> PositioningHint.HOLD_STILL
        }

        val anchor = state.anchor
        val moved = f != null && anchor != null && hasMoved(f, anchor)
        val interrupted = !continuous || moved

        val since = when {
            hint != PositioningHint.HOLD_STILL -> null
            interrupted -> now
            else -> state.stableSince ?: now
        }
        val following = since != null && now - since >= config.holdMs

        return PositioningState(
            following = following,
            hint = if (following) PositioningHint.FOLLOWING else hint,
            stableSince = since,
            lastSample = now,
            anchor = when (since) {
                null -> null
                now -> f
                else -> anchor
            },
            smoothed = f,
            poseOk = poseOk
        )
    }

    private fun withinPose(f: PositioningFace, limits: PoseLimits): Boolean =
        abs(f.yaw) <= limits.yaw && abs(f.pitch) <= limits.pitch && abs(f.roll) <= limits.roll

    private fun hasMoved(f: PositioningFace, anchor: PositioningFace): Boolean =
        abs(f.x - anchor.x) > config.maxPositionDelta ||
                abs(f.y - anchor.y) > config.maxPositionDelta ||
                abs(f.width - anchor.width) > config.maxSizeDelta ||
                abs(f.height - anchor.height) > config.maxSizeDelta ||
                abs(f.yaw - anchor.yaw) > config.maxAngleDelta ||
                abs(f.pitch - anchor.pitch) > config.maxAngleDelta ||
                abs(f.roll - anchor.roll) > config.maxAngleDelta

    /** Time-constant EMA: the same smoothing at 15 or 30 fps, unlike a fixed alpha. */
    private fun smooth(prev: PositioningFace?, cur: PositioningFace, dtMs: Long): PositioningFace {
        if (prev == null) return cur
        val a = 1f - exp(-dtMs / config.smoothingTauMs)
        fun mix(p: Float, c: Float) = p + a * (c - p)
        return PositioningFace(
            x = mix(prev.x, cur.x),
            y = mix(prev.y, cur.y),
            width = mix(prev.width, cur.width),
            height = mix(prev.height, cur.height),
            yaw = mix(prev.yaw, cur.yaw),
            pitch = mix(prev.pitch, cur.pitch),
            roll = mix(prev.roll, cur.roll)
        )
    }
}

/** Checked before smoothing: one NaN would otherwise poison the EMA until the next gap. */
private fun PositioningFace.hasFiniteValues(): Boolean =
    x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() &&
            yaw.isFinite() && pitch.isFinite() && roll.isFinite()

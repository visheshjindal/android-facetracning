package com.xim.facetracking.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** SDK-free affine transform from analysis-image coordinates to preview-view coordinates. */
data class AffineTransform2D(
    val scaleX: Float,
    val skewX: Float,
    val translateX: Float,
    val skewY: Float,
    val scaleY: Float,
    val translateY: Float
) {
    fun mapX(x: Float, y: Float): Float = scaleX * x + skewX * y + translateX
    fun mapY(x: Float, y: Float): Float = skewY * x + scaleY * y + translateY
}

/** A small copy of the Y plane. Values are row-major unsigned luminance bytes. */
data class SparseLumaFrame(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val columns: Int,
    val rows: Int,
    val values: ByteArray
) {
    init {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(columns > 0 && rows > 0)
        require(values.size == columns * rows)
    }
}

data class FaceLightingMetrics(
    val medianLuma: Float,
    val trimmedMeanLuma: Float,
    val leftTrimmedMeanLuma: Float,
    val rightTrimmedMeanLuma: Float,
    val shadowFraction: Float,
    val highlightFraction: Float,
    val sampleCount: Int,
    val leftSampleCount: Int,
    val rightSampleCount: Int
)

enum class LightingAssessment { UNKNOWN, EVEN, UNEVEN, TOO_DARK, TOO_BRIGHT }
enum class ShadowSide { USER_LEFT, USER_RIGHT }

data class LightingState(
    val assessment: LightingAssessment = LightingAssessment.UNKNOWN,
    val shadowSide: ShadowSide? = null,
    val candidateAssessment: LightingAssessment? = null,
    val candidateShadowSide: ShadowSide? = null,
    val candidateSinceMs: Long? = null,
    val evenSinceMs: Long? = null,
    val lastSampleMs: Long? = null
) {
    fun isCompact(nowMs: Long): Boolean =
        assessment == LightingAssessment.EVEN && evenSinceMs?.let { nowMs - it >= 1_500L } == true
}

data class LightingConfig(
    val enterWarningMs: Long = 400L,
    val acceptEvenMs: Long = 700L,
    val maxGapMs: Long = 300L,
    val darkMedianEnter: Float = 55f,
    val darkMedianExit: Float = 65f,
    val shadowFractionEnter: Float = 0.25f,
    val shadowFractionExit: Float = 0.20f,
    val brightMedianEnter: Float = 205f,
    val brightMedianExit: Float = 195f,
    val highlightFractionEnter: Float = 0.20f,
    val highlightFractionExit: Float = 0.15f,
    val unevenDifferenceEnter: Float = 28f,
    val unevenDifferenceExit: Float = 20f,
    val unevenRatioEnter: Float = 0.75f,
    val unevenRatioExit: Float = 0.82f,
    val minimumSamples: Int = 96,
    val minimumSideSamples: Int = 32
)

/** Pure image metric. The caller supplies a copied luma grid and an SDK-free transform. */
class FaceLightingMeasurer(private val config: LightingConfig = LightingConfig()) {
    fun measure(
        frame: SparseLumaFrame,
        imageToView: AffineTransform2D,
        viewportWidth: Int,
        viewportHeight: Int,
        face: PositioningFace
    ): FaceLightingMetrics? {
        if (viewportWidth <= 0 || viewportHeight <= 0 || !face.hasUsableGeometry()) return null

        val all = IntArray(LUMA_LEVELS)
        val left = IntArray(LUMA_LEVELS)
        val right = IntArray(LUMA_LEVELS)
        var allCount = 0
        var leftCount = 0
        var rightCount = 0
        var shadowCount = 0
        var highlightCount = 0

        for (row in 0 until frame.rows) {
            val imageY = (row + 0.5f) * frame.sourceHeight / frame.rows
            for (column in 0 until frame.columns) {
                val imageX = (column + 0.5f) * frame.sourceWidth / frame.columns
                val viewX = imageToView.mapX(imageX, imageY) / viewportWidth
                val viewY = imageToView.mapY(imageX, imageY) / viewportHeight
                val u = (viewX - (face.x - face.width / 2f)) / face.width
                val v = (viewY - (face.y - face.height / 2f)) / face.height
                val ellipseX = (u - 0.5f) / INNER_RADIUS_X
                val ellipseY = (v - 0.5f) / INNER_RADIUS_Y
                if (ellipseX * ellipseX + ellipseY * ellipseY > 1f) continue

                val value = frame.values[row * frame.columns + column].toInt() and 0xff
                all[value]++
                allCount++
                if (u < 0.5f) {
                    left[value]++
                    leftCount++
                } else {
                    right[value]++
                    rightCount++
                }
                if (value <= SHADOW_LUMA) shadowCount++
                if (value >= HIGHLIGHT_LUMA) highlightCount++
            }
        }

        if (allCount < config.minimumSamples || leftCount < config.minimumSideSamples ||
            rightCount < config.minimumSideSamples) return null

        return FaceLightingMetrics(
            medianLuma = median(all, allCount),
            trimmedMeanLuma = trimmedMean(all, allCount),
            leftTrimmedMeanLuma = trimmedMean(left, leftCount),
            rightTrimmedMeanLuma = trimmedMean(right, rightCount),
            shadowFraction = shadowCount.toFloat() / allCount,
            highlightFraction = highlightCount.toFloat() / allCount,
            sampleCount = allCount,
            leftSampleCount = leftCount,
            rightSampleCount = rightCount
        )
    }

    private fun median(histogram: IntArray, count: Int): Float {
        val lowerRank = (count - 1) / 2
        val upperRank = count / 2
        var cumulative = 0
        var lower = -1
        var upper = 0
        for (value in histogram.indices) {
            cumulative += histogram[value]
            if (cumulative > lowerRank && lower < 0) lower = value
            if (cumulative > upperRank) {
                upper = value
                break
            }
        }
        return (lower + upper) / 2f
    }

    private fun trimmedMean(histogram: IntArray, count: Int): Float {
        val trim = count / 10
        val startRank = trim
        val endRank = count - trim
        var total = 0L
        var included = 0
        var cumulative = 0
        for (value in histogram.indices) {
            val bucketStart = cumulative
            val bucketEnd = cumulative + histogram[value]
            val overlap = min(bucketEnd, endRank) - max(bucketStart, startRank)
            if (overlap > 0) {
                total += value.toLong() * overlap
                included += overlap
            }
            cumulative = bucketEnd
            if (cumulative >= endRank) break
        }
        return total.toFloat() / included
    }

    private fun PositioningFace.hasUsableGeometry(): Boolean =
        x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() &&
            width > 0f && height > 0f

    private companion object {
        const val INNER_RADIUS_X = 0.40f
        const val INNER_RADIUS_Y = 0.38f
        const val SHADOW_LUMA = 24
        const val HIGHLIGHT_LUMA = 235
        const val LUMA_LEVELS = 256
    }
}

/** Timestamp-driven warning persistence and threshold hysteresis. */
class LightingPolicy(private val config: LightingConfig = LightingConfig()) {
    fun update(state: LightingState, nowMs: Long, metrics: FaceLightingMetrics?): LightingState {
        val last = state.lastSampleMs
        if (last != null && nowMs <= last) return state
        if (metrics == null || !metrics.isUsable() ||
            (last != null && nowMs - last > config.maxGapMs)) {
            return LightingState(lastSampleMs = nowMs)
        }

        val (instant, side) = classify(metrics, state.assessment)
        val sameAsActive = instant == state.assessment &&
            (instant != LightingAssessment.UNEVEN || side == state.shadowSide)
        if (sameAsActive) {
            return state.copy(
                candidateAssessment = null,
                candidateShadowSide = null,
                candidateSinceMs = null,
                lastSampleMs = nowMs
            )
        }

        val sameCandidate = instant == state.candidateAssessment &&
            (instant != LightingAssessment.UNEVEN || side == state.candidateShadowSide)
        val candidateSince = if (sameCandidate) state.candidateSinceMs ?: nowMs else nowMs
        val requiredMs = if (instant == LightingAssessment.EVEN) config.acceptEvenMs else config.enterWarningMs
        if (nowMs - candidateSince < requiredMs) {
            return state.copy(
                candidateAssessment = instant,
                candidateShadowSide = side,
                candidateSinceMs = candidateSince,
                lastSampleMs = nowMs
            )
        }

        return LightingState(
            assessment = instant,
            shadowSide = side,
            evenSinceMs = if (instant == LightingAssessment.EVEN) candidateSince else null,
            lastSampleMs = nowMs
        )
    }

    private fun classify(
        metrics: FaceLightingMetrics,
        active: LightingAssessment
    ): Pair<LightingAssessment, ShadowSide?> {
        val dark = if (active == LightingAssessment.TOO_DARK) {
            metrics.medianLuma < config.darkMedianExit || metrics.shadowFraction >= config.shadowFractionExit
        } else {
            metrics.medianLuma < config.darkMedianEnter || metrics.shadowFraction >= config.shadowFractionEnter
        }
        if (dark) return LightingAssessment.TOO_DARK to null

        val bright = if (active == LightingAssessment.TOO_BRIGHT) {
            metrics.medianLuma > config.brightMedianExit || metrics.highlightFraction >= config.highlightFractionExit
        } else {
            metrics.medianLuma > config.brightMedianEnter || metrics.highlightFraction >= config.highlightFractionEnter
        }
        if (bright) return LightingAssessment.TOO_BRIGHT to null

        val darker = min(metrics.leftTrimmedMeanLuma, metrics.rightTrimmedMeanLuma)
        val brighter = max(metrics.leftTrimmedMeanLuma, metrics.rightTrimmedMeanLuma)
        val difference = abs(metrics.leftTrimmedMeanLuma - metrics.rightTrimmedMeanLuma)
        val ratio = if (brighter <= 0f) 1f else darker / brighter
        val uneven = if (active == LightingAssessment.UNEVEN) {
            difference > config.unevenDifferenceExit && ratio < config.unevenRatioExit
        } else {
            difference >= config.unevenDifferenceEnter && ratio <= config.unevenRatioEnter
        }
        if (uneven) {
            // Metrics are already in the mirrored preview coordinate system shown to the user.
            val shadowSide = if (metrics.leftTrimmedMeanLuma < metrics.rightTrimmedMeanLuma) {
                ShadowSide.USER_LEFT
            } else {
                ShadowSide.USER_RIGHT
            }
            return LightingAssessment.UNEVEN to shadowSide
        }
        return LightingAssessment.EVEN to null
    }

    private fun FaceLightingMetrics.isUsable(): Boolean =
        sampleCount >= config.minimumSamples &&
            leftSampleCount >= config.minimumSideSamples &&
            rightSampleCount >= config.minimumSideSamples &&
            medianLuma.isFinite() && trimmedMeanLuma.isFinite() &&
            leftTrimmedMeanLuma.isFinite() && rightTrimmedMeanLuma.isFinite() &&
            shadowFraction.isFinite() && highlightFraction.isFinite()
}

enum class CaptureGuidance {
    PLACE_FACE, CENTER_FACE, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN,
    CLOSER, FARTHER, LOOK_STRAIGHT, HOLD_STILL, FOLLOWING, TRACKING_LOST,
    LIGHT_USER_LEFT, LIGHT_USER_RIGHT, MORE_LIGHT, REDUCE_LIGHT
}

/** Chooses one primary action while preserving independent positioning and lighting state. */
class GuidancePolicy {
    fun resolve(positioning: PositioningState, lighting: LightingState): CaptureGuidance {
        val positionGuidance = positioning.hint.toCaptureGuidance()
        if (positioning.hint != PositioningHint.HOLD_STILL &&
            positioning.hint != PositioningHint.FOLLOWING) return positionGuidance

        return when (lighting.assessment) {
            LightingAssessment.UNEVEN -> when (lighting.shadowSide) {
                ShadowSide.USER_LEFT -> CaptureGuidance.LIGHT_USER_LEFT
                ShadowSide.USER_RIGHT -> CaptureGuidance.LIGHT_USER_RIGHT
                null -> positionGuidance
            }
            LightingAssessment.TOO_DARK -> CaptureGuidance.MORE_LIGHT
            LightingAssessment.TOO_BRIGHT -> CaptureGuidance.REDUCE_LIGHT
            LightingAssessment.UNKNOWN, LightingAssessment.EVEN -> positionGuidance
        }
    }
}

private fun PositioningHint.toCaptureGuidance(): CaptureGuidance = when (this) {
    PositioningHint.PLACE_FACE -> CaptureGuidance.PLACE_FACE
    PositioningHint.CENTER_FACE -> CaptureGuidance.CENTER_FACE
    PositioningHint.MOVE_LEFT -> CaptureGuidance.MOVE_LEFT
    PositioningHint.MOVE_RIGHT -> CaptureGuidance.MOVE_RIGHT
    PositioningHint.MOVE_UP -> CaptureGuidance.MOVE_UP
    PositioningHint.MOVE_DOWN -> CaptureGuidance.MOVE_DOWN
    PositioningHint.CLOSER -> CaptureGuidance.CLOSER
    PositioningHint.FARTHER -> CaptureGuidance.FARTHER
    PositioningHint.LOOK_STRAIGHT -> CaptureGuidance.LOOK_STRAIGHT
    PositioningHint.HOLD_STILL -> CaptureGuidance.HOLD_STILL
    PositioningHint.FOLLOWING -> CaptureGuidance.FOLLOWING
    PositioningHint.TRACKING_LOST -> CaptureGuidance.TRACKING_LOST
}

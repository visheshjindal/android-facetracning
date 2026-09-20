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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SparseLumaFrame) return false

        if (sourceWidth != other.sourceWidth) return false
        if (sourceHeight != other.sourceHeight) return false
        if (columns != other.columns) return false
        if (rows != other.rows) return false
        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sourceWidth
        result = 31 * result + sourceHeight
        result = 31 * result + columns
        result = 31 * result + rows
        result = 31 * result + values.contentHashCode()
        return result
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
    val rightSampleCount: Int,
    val topTrimmedMeanLuma: Float,
    val bottomTrimmedMeanLuma: Float,
    val topSampleCount: Int,
    val bottomSampleCount: Int
)

/** ACCEPTABLE means no supported warning persisted; it is not a quality certification. */
enum class LightingAssessment { UNKNOWN, ACCEPTABLE, UNEVEN, TOO_DARK, TOO_BRIGHT, HIGH_CONTRAST }
enum class ShadowSide { USER_LEFT, USER_RIGHT }

data class LightingState(
    val assessment: LightingAssessment = LightingAssessment.UNKNOWN,
    val shadowSide: ShadowSide? = null,
    val candidateAssessment: LightingAssessment? = null,
    val candidateShadowSide: ShadowSide? = null,
    val candidateSinceMs: Long? = null,
    val acceptableSinceMs: Long? = null,
    val lastSampleMs: Long? = null
) {
    fun isCompact(nowMs: Long): Boolean =
        assessment == LightingAssessment.ACCEPTABLE && acceptableSinceMs?.let { nowMs - it >= 1_500L } == true
}

/** Provisional heuristic thresholds; see CaptureSpec and docs/lighting-validation.md. */
data class LightingConfig(
    val enterWarningMs: Long = 400L,
    val acceptOkayMs: Long = 700L,
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
    val shadowLuma: Int = 24,
    val highlightLuma: Int = 235,
    val minimumSamples: Int = 96,
    val minimumSideSamples: Int = 32
) {
    init {
        require(enterWarningMs > 0 && acceptOkayMs > 0 && maxGapMs > 0)
        require(darkMedianEnter in 0f..<darkMedianExit)
        require(darkMedianExit < brightMedianExit && brightMedianExit < brightMedianEnter)
        require(brightMedianEnter <= 255f)
        require(shadowFractionExit in 0f..1f && shadowFractionEnter in 0f..1f &&
            shadowFractionExit < shadowFractionEnter)
        require(highlightFractionExit in 0f..1f && highlightFractionEnter in 0f..1f &&
            highlightFractionExit < highlightFractionEnter)
        require(
            unevenDifferenceExit in 0f..<unevenDifferenceEnter &&
            unevenDifferenceEnter <= 255f)
        require(unevenRatioEnter in 0f..1f && unevenRatioExit in 0f..1f &&
            unevenRatioEnter < unevenRatioExit)
        require(shadowLuma in 0..255 && highlightLuma in 0..255 && shadowLuma < highlightLuma)
        require(minimumSamples > 0 && minimumSideSamples > 0)
    }
}

/** Pure image metric. The caller supplies a copied luma grid and an SDK-free transform. */
class FaceLightingMeasurer(private val config: LightingConfig = CaptureSpec.lighting) {
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
        val top = IntArray(LUMA_LEVELS)
        val bottom = IntArray(LUMA_LEVELS)
        var allCount = 0
        var leftCount = 0
        var rightCount = 0
        var topCount = 0
        var bottomCount = 0
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
                if (!u.isFinite() || !v.isFinite()) return null
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
                if (v < 0.5f) {
                    top[value]++
                    topCount++
                } else {
                    bottom[value]++
                    bottomCount++
                }
                if (value <= config.shadowLuma) shadowCount++
                if (value >= config.highlightLuma) highlightCount++
            }
        }

        if (allCount < config.minimumSamples || leftCount < config.minimumSideSamples ||
            rightCount < config.minimumSideSamples || topCount < config.minimumSideSamples ||
            bottomCount < config.minimumSideSamples) return null

        return FaceLightingMetrics(
            medianLuma = median(all, allCount),
            trimmedMeanLuma = trimmedMean(all, allCount),
            leftTrimmedMeanLuma = trimmedMean(left, leftCount),
            rightTrimmedMeanLuma = trimmedMean(right, rightCount),
            shadowFraction = shadowCount.toFloat() / allCount,
            highlightFraction = highlightCount.toFloat() / allCount,
            sampleCount = allCount,
            leftSampleCount = leftCount,
            rightSampleCount = rightCount,
            topTrimmedMeanLuma = trimmedMean(top, topCount),
            bottomTrimmedMeanLuma = trimmedMean(bottom, bottomCount),
            topSampleCount = topCount,
            bottomSampleCount = bottomCount
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
        const val LUMA_LEVELS = 256
    }
}

/** Timestamp-driven warning persistence and threshold hysteresis. */
class LightingPolicy(private val config: LightingConfig = CaptureSpec.lighting) {
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
        val requiredMs = if (instant == LightingAssessment.ACCEPTABLE) config.acceptOkayMs else config.enterWarningMs
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
            acceptableSinceMs = if (instant == LightingAssessment.ACCEPTABLE) candidateSince else null,
            lastSampleMs = nowMs
        )
    }

    private fun classify(
        metrics: FaceLightingMetrics,
        active: LightingAssessment
    ): Pair<LightingAssessment, ShadowSide?> {
        val dark = if (active == LightingAssessment.TOO_DARK || active == LightingAssessment.HIGH_CONTRAST) {
            metrics.medianLuma < config.darkMedianExit || metrics.shadowFraction >= config.shadowFractionExit
        } else {
            metrics.medianLuma < config.darkMedianEnter || metrics.shadowFraction >= config.shadowFractionEnter
        }

        val bright = if (active == LightingAssessment.TOO_BRIGHT || active == LightingAssessment.HIGH_CONTRAST) {
            metrics.medianLuma > config.brightMedianExit || metrics.highlightFraction >= config.highlightFractionExit
        } else {
            metrics.medianLuma > config.brightMedianEnter || metrics.highlightFraction >= config.highlightFractionEnter
        }
        // Mixed exposure needs softer light, not an unconditional request for more light.
        if (dark && bright) return LightingAssessment.HIGH_CONTRAST to null
        if (dark) return LightingAssessment.TOO_DARK to null
        if (bright) return LightingAssessment.TOO_BRIGHT to null

        val horizontal = isUneven(metrics.leftTrimmedMeanLuma, metrics.rightTrimmedMeanLuma, active)
        val vertical = isUneven(metrics.topTrimmedMeanLuma, metrics.bottomTrimmedMeanLuma, active)
        // A vertical or multi-axis imbalance has no unambiguous left/right correction.
        if (vertical) return LightingAssessment.UNEVEN to null
        if (horizontal) {
            // Metrics are already in the mirrored preview coordinate system shown to the user.
            val shadowSide = if (metrics.leftTrimmedMeanLuma < metrics.rightTrimmedMeanLuma) {
                ShadowSide.USER_LEFT
            } else {
                ShadowSide.USER_RIGHT
            }
            return LightingAssessment.UNEVEN to shadowSide
        }
        return LightingAssessment.ACCEPTABLE to null
    }

    private fun isUneven(first: Float, second: Float, active: LightingAssessment): Boolean {
        val brighter = max(first, second)
        val difference = abs(first - second)
        val ratio = if (brighter <= 0f) 1f else min(first, second) / brighter
        return if (active == LightingAssessment.UNEVEN) {
            difference > config.unevenDifferenceExit && ratio < config.unevenRatioExit
        } else {
            difference >= config.unevenDifferenceEnter && ratio <= config.unevenRatioEnter
        }
    }

    private fun FaceLightingMetrics.isUsable(): Boolean =
        sampleCount >= config.minimumSamples &&
            leftSampleCount >= config.minimumSideSamples &&
            rightSampleCount >= config.minimumSideSamples &&
            topSampleCount >= config.minimumSideSamples &&
            bottomSampleCount >= config.minimumSideSamples &&
            leftSampleCount.toLong() + rightSampleCount == sampleCount.toLong() &&
            topSampleCount.toLong() + bottomSampleCount == sampleCount.toLong() &&
            medianLuma in 0f..255f && trimmedMeanLuma in 0f..255f &&
            leftTrimmedMeanLuma in 0f..255f && rightTrimmedMeanLuma in 0f..255f &&
            topTrimmedMeanLuma in 0f..255f && bottomTrimmedMeanLuma in 0f..255f &&
            shadowFraction in 0f..1f && highlightFraction in 0f..1f &&
            shadowFraction + highlightFraction <= 1f

}

enum class CaptureGuidance {
    PLACE_FACE, CENTER_FACE, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN,
    CLOSER, FARTHER, LOOK_STRAIGHT, HOLD_STILL, FOLLOWING, TRACKING_LOST,
    LIGHT_USER_LEFT, LIGHT_USER_RIGHT, MORE_LIGHT, REDUCE_LIGHT, SOFTEN_LIGHT
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
                null -> CaptureGuidance.SOFTEN_LIGHT
            }
            LightingAssessment.HIGH_CONTRAST -> CaptureGuidance.SOFTEN_LIGHT
            LightingAssessment.TOO_DARK -> CaptureGuidance.MORE_LIGHT
            LightingAssessment.TOO_BRIGHT -> CaptureGuidance.REDUCE_LIGHT
            LightingAssessment.UNKNOWN, LightingAssessment.ACCEPTABLE -> positionGuidance
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

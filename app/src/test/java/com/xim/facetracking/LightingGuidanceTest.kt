package com.xim.facetracking

import com.xim.facetracking.domain.*
import org.junit.Assert.*
import org.junit.Test

class LightingGuidanceTest {
    private val face = PositioningFace(.5f, .5f, .8f, .8f, 0f, 0f, 0f)
    private val identity = AffineTransform2D(1f, 0f, 0f, 0f, 1f, 0f)
    private val measurer = FaceLightingMeasurer()

    @Test fun measuresEvenDarkBrightAndUnevenSyntheticFaces() {
        val even = requireNotNull(measurer.measure(frame { _, _ -> 120 }, identity, 64, 64, face))
        val dark = requireNotNull(measurer.measure(frame { _, _ -> 40 }, identity, 64, 64, face))
        val bright = requireNotNull(measurer.measure(frame { _, _ -> 220 }, identity, 64, 64, face))
        val uneven = requireNotNull(measurer.measure(
            frame { x, _ -> if (x < 32) 80 else 160 }, identity, 64, 64, face
        ))

        assertEquals(120f, even.medianLuma, 0.01f)
        assertEquals(40f, dark.medianLuma, 0.01f)
        assertEquals(220f, bright.medianLuma, 0.01f)
        assertTrue(uneven.rightTrimmedMeanLuma - uneven.leftTrimmedMeanLuma >= 70f)
        assertTrue(even.sampleCount >= 96)
        assertTrue(even.leftSampleCount >= 32)
        assertTrue(even.rightSampleCount >= 32)
    }

    @Test fun mirrorTransformKeepsGuidanceOnTheVisibleShadowSide() {
        val mirror = AffineTransform2D(-1f, 0f, 64f, 0f, 1f, 0f)
        val metrics = requireNotNull(measurer.measure(
            frame { x, _ -> if (x < 32) 80 else 160 }, mirror, 64, 64, face
        ))
        val state = settle(metrics)
        assertEquals(LightingAssessment.UNEVEN, state.assessment)
        assertEquals(ShadowSide.USER_RIGHT, state.shadowSide)
    }

    @Test fun rotatedTransformKeepsLightingSidesInPreviewCoordinates() {
        val rotateClockwise = AffineTransform2D(0f, -1f, 64f, 1f, 0f, 0f)
        val metrics = requireNotNull(measurer.measure(
            frame { _, y -> if (y < 32) 80 else 160 }, rotateClockwise, 64, 64, face
        ))
        assertTrue(metrics.rightTrimmedMeanLuma < metrics.leftTrimmedMeanLuma)
        assertEquals(ShadowSide.USER_RIGHT, settle(metrics).shadowSide)
    }

    @Test fun insufficientFaceSamplesAreUnknown() {
        val tinyFace = face.copy(width = .1f, height = .1f)
        assertNull(measurer.measure(frame { _, _ -> 120 }, identity, 64, 64, tinyFace))
    }

    @Test fun policyPersistsWarningsAndUsesExitHysteresis() {
        val policy = LightingPolicy()
        val dark = metrics(median = 40f, left = 40f, right = 40f)
        val even = metrics(median = 120f, left = 120f, right = 120f)
        var state = LightingState()
        for (time in 0L..300L step 100) state = policy.update(state, time, dark)
        assertEquals(LightingAssessment.UNKNOWN, state.assessment)
        state = policy.update(state, 400, dark)
        assertEquals(LightingAssessment.TOO_DARK, state.assessment)

        for (time in 500L..1_100L step 100) state = policy.update(state, time, even)
        assertEquals(LightingAssessment.TOO_DARK, state.assessment)
        state = policy.update(state, 1_200, even)
        assertEquals(LightingAssessment.EVEN, state.assessment)
        assertFalse(state.isCompact(1_499))
        assertTrue(state.isCompact(2_000))
    }

    @Test fun gapAndMissingMeasurementResetToUnknown() {
        val even = metrics(median = 120f, left = 120f, right = 120f)
        var state = settle(even)
        assertEquals(LightingAssessment.EVEN, state.assessment)
        state = LightingPolicy().update(state, 1_500, even)
        assertEquals(LightingAssessment.UNKNOWN, state.assessment)
        state = LightingPolicy().update(state, 1_600, null)
        assertEquals(LightingAssessment.UNKNOWN, state.assessment)
    }

    @Test fun alternatingMeasurementsDoNotCreateAWarning() {
        val policy = LightingPolicy()
        val dark = metrics(median = 40f, left = 40f, right = 40f)
        val even = metrics(median = 120f, left = 120f, right = 120f)
        var state = LightingState()
        for (time in 0L..900L step 100) {
            state = policy.update(state, time, if ((time / 100) % 2L == 0L) dark else even)
        }
        assertEquals(LightingAssessment.UNKNOWN, state.assessment)
    }

    @Test fun poorLightingIsAdvisoryAndPositioningGuidanceWins() {
        val reducer = CaptureReducer(PositioningPolicy())
        var state = reducer.reduce(CaptureState(), CaptureEvent.Permission(true)).state
        state = reducer.reduce(state, CaptureEvent.Viewport(1080f, 2100f)).state
        state = reducer.reduce(state, CaptureEvent.Resumed).state
        val target = state.target
        val centered = PositioningFace(.5f, .5f, target.width * .8f, target.height * .8f, 0f, 0f, 0f)
        val dark = metrics(median = 40f, left = 40f, right = 40f)
        for (time in 0L..1_000L step 100) {
            state = reducer.reduce(state, CaptureEvent.Observation(
                TrackingObservation(state.sessionId, time, centered, dark)
            )).state
        }
        assertTrue(state.positioning.following)
        assertEquals(LightingAssessment.TOO_DARK, state.lighting.assessment)
        assertEquals(CaptureGuidance.MORE_LIGHT, state.guidance)

        val offCenter = centered.copy(x = .2f)
        state = reducer.reduce(state, CaptureEvent.Observation(
            TrackingObservation(state.sessionId, 1_100, offCenter, dark)
        )).state
        assertEquals(CaptureGuidance.MOVE_RIGHT, state.guidance)

        state = reducer.reduce(state, CaptureEvent.Observation(
            TrackingObservation(state.sessionId, 1_200, null, null)
        )).state
        assertEquals(LightingAssessment.UNKNOWN, state.lighting.assessment)
        assertEquals(CaptureGuidance.TRACKING_LOST, state.guidance)
    }

    private fun settle(metrics: FaceLightingMetrics): LightingState {
        val policy = LightingPolicy()
        var state = LightingState()
        for (time in 0L..700L step 100) state = policy.update(state, time, metrics)
        return state
    }

    private fun frame(value: (x: Int, y: Int) -> Int): SparseLumaFrame {
        val bytes = ByteArray(64 * 64)
        for (y in 0 until 64) for (x in 0 until 64) {
            bytes[y * 64 + x] = value(x, y).toByte()
        }
        return SparseLumaFrame(64, 64, 64, 64, bytes)
    }

    private fun metrics(median: Float, left: Float, right: Float) = FaceLightingMetrics(
        medianLuma = median,
        trimmedMeanLuma = median,
        leftTrimmedMeanLuma = left,
        rightTrimmedMeanLuma = right,
        shadowFraction = if (median <= 24f) 1f else 0f,
        highlightFraction = if (median >= 235f) 1f else 0f,
        sampleCount = 200,
        leftSampleCount = 100,
        rightSampleCount = 100
    )
}

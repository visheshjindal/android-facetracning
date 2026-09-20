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
        assertEquals(LightingAssessment.ACCEPTABLE, state.assessment)
        assertFalse(state.isCompact(1_499))
        assertTrue(state.isCompact(2_000))
    }

    @Test fun gapAndMissingMeasurementResetToUnknown() {
        val even = metrics(median = 120f, left = 120f, right = 120f)
        var state = settle(even)
        assertEquals(LightingAssessment.ACCEPTABLE, state.assessment)
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

    @Test fun verticalImbalanceUsesSoftLightGuidanceWithoutInventingASide() {
        for (topDark in listOf(true, false)) {
            val measured = requireNotNull(measurer.measure(
                frame { _, y -> if ((y < 32) == topDark) 80 else 160 }, identity, 64, 64, face
            ))
            assertEquals(measured.leftTrimmedMeanLuma, measured.rightTrimmedMeanLuma, .01f)
            assertEquals(80f, minOf(measured.topTrimmedMeanLuma, measured.bottomTrimmedMeanLuma), .01f)
            val state = settle(measured)
            assertEquals(LightingAssessment.UNEVEN, state.assessment)
            assertNull(state.shadowSide)
            assertEquals(CaptureGuidance.SOFTEN_LIGHT, GuidancePolicy().resolve(
                PositioningState(hint = PositioningHint.FOLLOWING), state
            ))
        }
    }

    @Test fun mixedShadowsAndHighlightsRequestSoftLightInsteadOfMoreLight() {
        val measured = requireNotNull(measurer.measure(
            frame { x, _ -> if (x < 32) 10 else 245 }, identity, 64, 64, face
        ))
        assertEquals(127.5f, measured.medianLuma, .01f)
        val state = settle(measured)
        assertEquals(LightingAssessment.HIGH_CONTRAST, state.assessment)
        assertEquals(CaptureGuidance.SOFTEN_LIGHT, GuidancePolicy().resolve(
            PositioningState(hint = PositioningHint.HOLD_STILL), state
        ))
        assertEquals(CaptureGuidance.MOVE_RIGHT, GuidancePolicy().resolve(
            PositioningState(hint = PositioningHint.MOVE_RIGHT), state
        ))
    }

    @Test fun medianEntryAndExitBoundariesUseDifferentThresholds() {
        val neutral = metrics(120f, 120f, 120f)
        assertEquals(LightingAssessment.ACCEPTABLE, settle(neutral.copy(medianLuma = 55f)).assessment)
        assertEquals(LightingAssessment.TOO_DARK, settle(neutral.copy(medianLuma = 54.9f)).assessment)
        assertEquals(LightingAssessment.ACCEPTABLE, settle(neutral.copy(medianLuma = 205f)).assessment)
        assertEquals(LightingAssessment.TOO_BRIGHT, settle(neutral.copy(medianLuma = 205.1f)).assessment)

        val dark = settle(neutral.copy(medianLuma = 40f))
        assertEquals(LightingAssessment.TOO_DARK, advance(dark, neutral.copy(medianLuma = 64.9f)).assessment)
        assertEquals(LightingAssessment.ACCEPTABLE, advance(dark, neutral.copy(medianLuma = 65f)).assessment)
        val bright = settle(neutral.copy(medianLuma = 220f))
        assertEquals(LightingAssessment.TOO_BRIGHT, advance(bright, neutral.copy(medianLuma = 195.1f)).assessment)
        assertEquals(LightingAssessment.ACCEPTABLE, advance(bright, neutral.copy(medianLuma = 195f)).assessment)
    }

    @Test fun extremeFractionsTriggerWarningsWithAnOrdinaryMedianAndLatchUntilExit() {
        val neutral = metrics(120f, 120f, 120f)
        assertEquals(LightingAssessment.ACCEPTABLE, settle(neutral.copy(shadowFraction = .249f)).assessment)
        val dark = settle(neutral.copy(shadowFraction = .25f))
        assertEquals(LightingAssessment.TOO_DARK, dark.assessment)
        assertEquals(LightingAssessment.TOO_DARK, advance(dark, neutral.copy(shadowFraction = .20f)).assessment)
        assertEquals(LightingAssessment.ACCEPTABLE, advance(dark, neutral.copy(shadowFraction = .199f)).assessment)
        assertEquals(LightingAssessment.ACCEPTABLE, settle(neutral.copy(highlightFraction = .199f)).assessment)
        val bright = settle(neutral.copy(highlightFraction = .20f))
        assertEquals(LightingAssessment.TOO_BRIGHT, bright.assessment)
        assertEquals(LightingAssessment.TOO_BRIGHT, advance(bright, neutral.copy(highlightFraction = .15f)).assessment)
        assertEquals(LightingAssessment.ACCEPTABLE, advance(bright, neutral.copy(highlightFraction = .149f)).assessment)
    }

    @Test fun contrastRetainsBothExitBandsAndRecoversToTheRemainingProblem() {
        val neutral = metrics(120f, 120f, 120f)
        val mixed = settle(neutral.copy(shadowFraction = .25f, highlightFraction = .20f))
        assertEquals(LightingAssessment.HIGH_CONTRAST, mixed.assessment)
        assertEquals(LightingAssessment.HIGH_CONTRAST,
            advance(mixed, neutral.copy(shadowFraction = .20f, highlightFraction = .15f)).assessment)
        assertEquals(LightingAssessment.TOO_DARK,
            advance(mixed, neutral.copy(shadowFraction = .25f, highlightFraction = .149f)).assessment)
        assertEquals(LightingAssessment.TOO_BRIGHT,
            advance(mixed, neutral.copy(shadowFraction = .199f, highlightFraction = .20f)).assessment)
        assertEquals(LightingAssessment.ACCEPTABLE, advance(mixed, neutral).assessment)
    }

    @Test fun unevennessRequiresBothDifferenceAndRatioAndUsesExitBandsOnBothAxes() {
        for (vertical in listOf(false, true)) {
            fun sample(low: Float, high: Float): FaceLightingMetrics =
                if (vertical) metrics(120f, 120f, 120f).copy(topTrimmedMeanLuma = low, bottomTrimmedMeanLuma = high)
                else metrics(120f, low, high)
            assertEquals(LightingAssessment.ACCEPTABLE, settle(sample(60f, 80f)).assessment) // ratio only
            assertEquals(LightingAssessment.ACCEPTABLE, settle(sample(160f, 200f)).assessment) // difference only
            val uneven = settle(sample(84f, 112f)) // exact entry difference and ratio
            assertEquals(LightingAssessment.UNEVEN, uneven.assessment)
            assertEquals(LightingAssessment.UNEVEN, advance(uneven, sample(80f, 101f)).assessment)
            assertEquals(LightingAssessment.ACCEPTABLE, advance(uneven, sample(80f, 100f)).assessment) // exit difference
            assertEquals(LightingAssessment.ACCEPTABLE, advance(uneven, sample(123f, 150f)).assessment) // exit ratio
        }
    }

    @Test fun switchingShadowSideMustPersistAndAnInterruptedSwitchRestartsTheTimer() {
        val policy = LightingPolicy()
        val left = metrics(120f, 80f, 160f)
        val right = metrics(120f, 160f, 80f)
        var state = settle(left)
        for (time in 800L..1_100L step 100) state = policy.update(state, time, right)
        assertEquals(ShadowSide.USER_LEFT, state.shadowSide)
        state = policy.update(state, 1_200, left)
        assertNull(state.candidateAssessment)
        for (time in 1_300L..1_600L step 100) state = policy.update(state, time, right)
        assertEquals(ShadowSide.USER_LEFT, state.shadowSide)
        state = policy.update(state, 1_700, right)
        assertEquals(ShadowSide.USER_RIGHT, state.shadowSide)
    }

    @Test fun obsoleteTimestampsCannotClearAWarningOrAdvanceACandidate() {
        val policy = LightingPolicy()
        val state = settle(metrics(40f, 40f, 40f))
        assertEquals(state, policy.update(state, 700, null))
        assertEquals(state, policy.update(state, 600, metrics(120f, 120f, 120f)))
        val candidate = policy.update(state, 800, metrics(120f, 120f, 120f))
        assertEquals(candidate, policy.update(candidate, 799, null))
    }

    @Test fun malformedOrMissingRegionalMeasurementsAreUnknown() {
        val neutral = metrics(120f, 120f, 120f)
        for (invalid in listOf(
            neutral.copy(topSampleCount = 0), neutral.copy(bottomSampleCount = 31),
            neutral.copy(topTrimmedMeanLuma = Float.NaN), neutral.copy(medianLuma = 256f),
            neutral.copy(highlightFraction = -1f), neutral.copy(shadowFraction = .8f, highlightFraction = .8f),
            neutral.copy(leftSampleCount = 101)
        )) assertEquals(LightingAssessment.UNKNOWN, LightingPolicy().update(settle(neutral), 800, invalid).assessment)
        assertNull(measurer.measure(frame { _, _ -> 120 }, identity.copy(scaleX = Float.NaN), 64, 64, face))
    }

    @Test fun gapBoundaryPreservesContinuityButLargerGapRestartsPersistence() {
        val neutral = metrics(120f, 120f, 120f)
        val state = settle(neutral)
        assertEquals(LightingAssessment.ACCEPTABLE, LightingPolicy().update(state, 1_000, neutral).assessment)
        val reset = LightingPolicy().update(state, 1_001, neutral)
        assertEquals(LightingAssessment.UNKNOWN, reset.assessment)
        assertNull(reset.acceptableSinceMs)
        assertNull(reset.candidateSinceMs)
        assertEquals(LightingAssessment.ACCEPTABLE, advance(reset, neutral).assessment)
    }

    private fun advance(initial: LightingState, metrics: FaceLightingMetrics): LightingState {
        val policy = LightingPolicy()
        var state = initial
        val start = requireNotNull(initial.lastSampleMs)
        for (time in start + 100..start + 800 step 100) state = policy.update(state, time, metrics)
        return state
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
        rightSampleCount = 100,
        topTrimmedMeanLuma = median,
        bottomTrimmedMeanLuma = median,
        topSampleCount = 100,
        bottomSampleCount = 100
    )
}

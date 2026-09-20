package com.xim.facetracking

import com.xim.facetracking.domain.*
import org.junit.Assert.*
import org.junit.Test

class CaptureRulesTest {
    private val spec = CaptureSpec.DefaultV0_1
    private val face = FaceSnapshot(0.5f, 0.45f, 0.38f, 0.5f)
    private fun observation(t: Long, f: FaceSnapshot = face) = FrameObservation(
        t, 1, f, true, LightingMetrics(120f, 0f, 0f), 100f,
        FacialRegions(NormalizedRegion(.2f,.4f,.4f,.6f), NormalizedRegion(.6f,.4f,.8f,.6f), NormalizedRegion(.4f,.2f,.6f,.3f))
    )

    @Test fun directionsAreUserRelative() {
        val issues = QualityEvaluator(spec).evaluate(observation(0, face.copy(centerX = .8f)))
        assertTrue(issues.any { it.type == QualityIssueType.MOVE_LEFT })
    }

    @Test fun unknownMetricsCannotPassQualityChecks() {
        val issues = QualityEvaluator(spec).evaluate(observation(0).copy(lighting = null))
        assertTrue(issues.any { it.type == QualityIssueType.METRICS_UNAVAILABLE })
    }

    @Test fun multipleFailuresAreRetainedAndPriorityIsSeparate() {
        val issues = QualityEvaluator(spec).evaluate(observation(0, face.copy(centerX = .8f)).copy(faceCount = 2))
        assertTrue(issues.any { it.type == QualityIssueType.MOVE_LEFT })
        assertEquals(QualityIssueType.MULTIPLE_FACES, issues.minBy { it.type.priority() }.type)
    }

    @Test fun movementHistoryIsExplicitAndResettable() {
        val evaluator = MovementEvaluator(spec)
        val current = observation(400, face.copy(centerX = .56f))
        assertEquals(QualityIssueType.HOLD_STEADY, evaluator.evaluate(current, listOf(observation(0)))?.type)
        assertNull(evaluator.evaluate(current, emptyList()))
    }

    @Test fun readinessRequiresUninterruptedStableWindow() {
        val policy = GuidancePolicy(spec)
        var memory = GuidanceMemory()
        var ready = false
        for (t in 0L..2_000L step 100) {
            val result = policy.update(memory, observation(t), emptyList())
            memory = result.memory
            ready = result.state.ready
        }
        assertTrue(ready)
        assertFalse(policy.update(memory, observation(3_000), emptyList()).state.ready)
    }

    @Test fun imageMetricsDetectCheekImbalanceAndHighlights() {
        val pixels = FloatArray(400) { i -> when { i % 20 < 7 -> 40f; i % 20 > 12 -> 120f; else -> 250f } }
        val result = ImageQualityMetrics.measure(LumaImage(20,20,pixels), FacialRegions(
            NormalizedRegion(0f,0f,.3f,1f), NormalizedRegion(.65f,0f,1f,1f), NormalizedRegion(.4f,0f,.6f,1f)))
        assertTrue(result.lighting.cheekImbalance > .25f)
        assertTrue(result.lighting.highlightFraction > 0)
    }
}

package com.xim.facetracking

import com.xim.facetracking.domain.*
import org.junit.Assert.*
import org.junit.Test

class PositioningPolicyTest {
    private val policy = PositioningPolicy()
    private val target = positioningTarget(1080f, 2100f)
    private val face = PositioningFace(0.5f, 0.5f, target.width * 0.8f, target.height * 0.8f, 0f, 0f, 0f)

    @Test fun ovalKeepsItsPhysicalAspectRatio() {
        for (height in listOf(1200f, 2100f, 2600f)) {
            val t = positioningTarget(1080f, height)
            assertEquals(1.35f, t.height * height / (t.width * 1080f), 0.001f)
        }
    }

    @Test fun requiresTwoSecondsAndThenStaysInFollowingMode() {
        var state = PositioningState()
        for (time in 0L..1900L step 100) state = policy.update(state, time, face, target)
        assertFalse(state.following)
        state = policy.update(state, 2000, face, target)
        assertTrue(state.following)
        state = policy.update(state, 2100, null, target)
        assertTrue(state.following)
        assertEquals(PositioningHint.TRACKING_LOST, state.hint)
    }

    @Test fun detectionAloneDoesNotDismissMask() {
        for (badFace in listOf(face.copy(x = 0.2f), face.copy(width = target.width * 1.1f), face.copy(yaw = 20f))) {
            var state = PositioningState()
            for (time in 0L..3000L step 100) state = policy.update(state, time, badFace, target)
            assertFalse(state.following)
        }
    }

    @Test fun gapsResetStabilityButOtherFacesDoNot() {
        var state = PositioningState()
        for (time in 0L..1500L step 100) state = policy.update(state, time, face, target)
        state = policy.update(state, 2100, face, target)
        assertEquals(2100L, state.stableSince)
        state = policy.update(state, 2200, face, target)
        assertEquals(2100L, state.stableSince)
        assertFalse(state.following)
        for (time in 2300L..4100L step 100) state = policy.update(state, time, face, target)
        assertTrue(state.following)
        assertEquals(PositioningHint.FOLLOWING, state.hint)
        state = policy.update(state, 4200, null, target)
        assertEquals(PositioningHint.TRACKING_LOST, state.hint)
    }
}

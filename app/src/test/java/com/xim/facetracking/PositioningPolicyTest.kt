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

    @Test fun requiresOneSecondAndThenStaysInFollowingMode() {
        var state = PositioningState()
        for (time in 0L..900L step 100) state = policy.update(state, time, face, target)
        assertFalse(state.following)
        state = policy.update(state, 1000, face, target)
        assertTrue(state.following)
        state = policy.update(state, 1100, null, target)
        assertTrue(state.following)
        assertEquals(PositioningHint.TRACKING_LOST, state.hint)
    }

    @Test fun followingModeProvidesCorrectiveGuidanceWithoutReturningToAlignment() {
        val following = followingState()
        val cases = listOf(
            face.copy(x = 0.2f, y = 0.8f, width = target.width * 1.1f, yaw = 20f) to PositioningHint.MOVE_RIGHT,
            face.copy(x = 0.8f) to PositioningHint.MOVE_LEFT,
            face.copy(y = 0.2f) to PositioningHint.MOVE_DOWN,
            face.copy(y = 0.8f) to PositioningHint.MOVE_UP,
            face.copy(width = target.width * 1.1f, yaw = 20f) to PositioningHint.FARTHER,
            face.copy(width = target.width * 0.5f, yaw = 20f) to PositioningHint.CLOSER,
            face.copy(yaw = 20f) to PositioningHint.LOOK_STRAIGHT,
            face to PositioningHint.FOLLOWING
        )

        for ((observedFace, expectedHint) in cases) {
            val updated = policy.update(following, 1400, observedFace, target)
            assertTrue(updated.following)
            assertEquals(expectedHint, updated.hint)
        }
    }

    @Test fun followingModeRecoversFromLossAndCorrectiveGuidance() {
        var state = followingState()
        state = policy.update(state, 1100, null, target)
        assertTrue(state.following)
        assertEquals(PositioningHint.TRACKING_LOST, state.hint)
        assertNull(state.smoothed)

        state = policy.update(state, 1200, face.copy(x = 0.2f), target)
        assertTrue(state.following)
        assertEquals(PositioningHint.MOVE_RIGHT, state.hint)

        state = policy.update(state, 1600, face, target)
        assertTrue(state.following)
        assertEquals(PositioningHint.FOLLOWING, state.hint)
    }

    @Test fun detectionAloneDoesNotDismissMask() {
        for (badFace in listOf(face.copy(x = 0.2f), face.copy(width = target.width * 1.1f), face.copy(yaw = 20f))) {
            var state = PositioningState()
            for (time in 0L..3000L step 100) state = policy.update(state, time, badFace, target)
            assertFalse(state.following)
        }
    }

    @Test fun initialAlignmentKeepsGenericCenterGuidance() {
        val state = policy.update(PositioningState(), 0, face.copy(x = 0.2f), target)
        assertFalse(state.following)
        assertEquals(PositioningHint.CENTER_FACE, state.hint)
    }

    @Test fun gapsResetStabilityButOtherFacesDoNot() {
        var state = PositioningState()
        for (time in 0L..500L step 100) state = policy.update(state, time, face, target)
        state = policy.update(state, 1100, face, target)
        assertEquals(1100L, state.stableSince)
        state = policy.update(state, 1200, face, target)
        assertEquals(1100L, state.stableSince)
        assertFalse(state.following)
        for (time in 1300L..2100L step 100) state = policy.update(state, time, face, target)
        assertTrue(state.following)
        assertEquals(PositioningHint.FOLLOWING, state.hint)
        state = policy.update(state, 2200, null, target)
        assertEquals(PositioningHint.TRACKING_LOST, state.hint)
    }

    private fun followingState(): PositioningState {
        var state = PositioningState()
        for (time in 0L..1000L step 100) state = policy.update(state, time, face, target)
        assertTrue(state.following)
        return state
    }
}

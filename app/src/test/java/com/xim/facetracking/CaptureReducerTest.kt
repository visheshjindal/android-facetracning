package com.xim.facetracking

import com.xim.facetracking.domain.*
import org.junit.Assert.*
import org.junit.Test

class CaptureReducerTest {
    private val reducer = CaptureReducer(PositioningPolicy())
    private fun active(): CaptureState {
        var state = reducer.reduce(CaptureState(), CaptureEvent.Permission(true)).state
        state = reducer.reduce(state, CaptureEvent.Viewport(1080f, 2100f)).state
        return reducer.reduce(state, CaptureEvent.Resumed).state
    }

    @Test fun cameraStartsOnlyAfterPermissionViewportAndResume() {
        val resumed = reducer.reduce(CaptureState(), CaptureEvent.Resumed)
        assertTrue(resumed.commands.isEmpty())
        val sized = reducer.reduce(resumed.state, CaptureEvent.Viewport(1080f, 2100f))
        assertTrue(sized.commands.isEmpty())
        val granted = reducer.reduce(sized.state, CaptureEvent.Permission(true))
        assertEquals(listOf(CaptureCommand.StartTracking(1)), granted.commands)
        assertTrue(reducer.reduce(granted.state, CaptureEvent.Resumed).commands.isEmpty())
    }

    @Test fun stopClearsOverlayAndObsoleteEventsAreIgnored() {
        val initial = active()
        val stopped = reducer.reduce(initial, CaptureEvent.Stopped)
        assertEquals(listOf(CaptureCommand.StopTracking), stopped.commands)
        assertFalse(stopped.state.monitoring)
        val resumed = reducer.reduce(stopped.state, CaptureEvent.Resumed).state
        assertTrue(resumed.sessionId > initial.sessionId)
        val old = CaptureEvent.Observation(TrackingObservation(initial.sessionId, 10, PositioningFace(.5f,.5f,.5f,.5f,0f,0f,0f)))
        assertEquals(resumed, reducer.reduce(resumed, old).state)
        assertEquals(resumed, reducer.reduce(resumed, CaptureEvent.Failed(CameraProblem(initial.sessionId, CameraFailure.UNAVAILABLE))).state)
    }

    @Test fun failureStopsCameraAndRetryGetsNewSession() {
        val initial = active()
        val failed = reducer.reduce(initial, CaptureEvent.Failed(CameraProblem(initial.sessionId, CameraFailure.DETECTOR_UNAVAILABLE)))
        assertEquals(listOf(CaptureCommand.StopTracking), failed.commands)
        assertEquals(CameraFailure.DETECTOR_UNAVAILABLE, failed.state.failure)
        val retry = reducer.reduce(failed.state, CaptureEvent.Retry)
        assertNull(retry.state.failure)
        assertEquals(listOf(CaptureCommand.StartTracking(2)), retry.commands)
    }

    @Test fun primaryFaceIsVisibleAndMissingDetectionClearsOverlay() {
        val initial = active()
        val face = PositioningFace(.5f, .5f, .3f, .3f, 0f, 0f, 0f)
        val visible = reducer.reduce(initial, CaptureEvent.Observation(
            TrackingObservation(initial.sessionId, 100, face)
        )).state
        assertEquals(face, visible.face)
        val missing = reducer.reduce(visible, CaptureEvent.Observation(
            TrackingObservation(initial.sessionId, 200, null)
        )).state
        assertNull(missing.face)
        assertEquals(PositioningHint.PLACE_FACE, missing.positioning.hint)
    }

    @Test fun revokingPermissionStopsMonitoring() {
        val result = reducer.reduce(active(), CaptureEvent.Permission(false))
        assertFalse(result.state.monitoring)
        assertNull(result.state.face)
        assertEquals(listOf(CaptureCommand.StopTracking), result.commands)
    }
}

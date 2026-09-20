package com.xim.facetracking

import com.xim.facetracking.domain.*
import com.xim.facetracking.presentation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeTracking : FaceTrackingPort {
        val samples = Channel<TrackingObservation>(Channel.CONFLATED)
        val failures = Channel<CameraProblem>(Channel.UNLIMITED)
        override val observations = samples.receiveAsFlow()
        override val problems = failures.receiveAsFlow()
        val sessions = mutableListOf<Long>()
        var stops = 0
        override fun start(sessionId: Long) { sessions += sessionId }
        override fun stop() { stops++ }
    }

    @Test fun intentsExecuteCommandsOnceAndOldCallbacksCannotUpdateNewSession() = runTest(dispatcher) {
        val port = FakeTracking()
        val vm = CaptureViewModel(port, CaptureReducer(PositioningPolicy()))
        vm.onAction(CaptureIntent.PermissionResult(true))
        vm.onAction(CaptureIntent.ViewportChanged(1080f, 2100f))
        vm.onAction(CaptureIntent.Resumed)
        vm.onAction(CaptureIntent.Resumed)
        runCurrent()
        assertEquals(listOf(1L), port.sessions)
        val target = positioningTarget(1080f, 2100f)
        val face = PositioningFace(.5f, .5f, target.width * .8f, target.height * .8f, 0f,0f,0f)
        for (time in 0L..2000L step 100) {
            port.samples.send(TrackingObservation(1, time, face))
            runCurrent()
        }
        assertFalse(vm.state.value.showPositioningMask)
        assertEquals(face, vm.state.value.trackedFace)
        vm.onAction(CaptureIntent.Stopped)
        vm.onAction(CaptureIntent.Resumed)
        runCurrent()
        assertEquals(listOf(1L, 2L), port.sessions)
        port.samples.send(TrackingObservation(1, 2200, face))
        runCurrent()
        assertTrue(vm.state.value.showPositioningMask)
        assertNull(vm.state.value.trackedFace)
        vm.onAction(CaptureIntent.Stopped)
        runCurrent()
        port.samples.close()
        port.failures.close()
    }

    @Test fun trackingLossOffersRecoveryWithoutOvalAndRestartRestoresAlignment() = runTest(dispatcher) {
        val port = FakeTracking()
        val vm = CaptureViewModel(port, CaptureReducer(PositioningPolicy()))
        vm.onAction(CaptureIntent.PermissionResult(true))
        vm.onAction(CaptureIntent.ViewportChanged(1080f, 2100f))
        vm.onAction(CaptureIntent.Resumed)
        runCurrent()
        val target = positioningTarget(1080f, 2100f)
        val face = PositioningFace(.5f, .5f, target.width * .8f, target.height * .8f, 0f, 0f, 0f)
        for (time in 0L..2000L step 100) {
            port.samples.send(TrackingObservation(1, time, face))
            runCurrent()
        }
        port.samples.send(TrackingObservation(1, 2100, null))
        runCurrent()
        assertFalse(vm.state.value.showPositioningMask)
        assertNull(vm.state.value.trackedFace)
        assertEquals(PositioningHint.TRACKING_LOST, vm.state.value.hint)
        val recoveredPrimary = face.copy(x = .55f)
        port.samples.send(TrackingObservation(1, 2200, recoveredPrimary))
        runCurrent()
        assertEquals(PositioningHint.FOLLOWING, vm.state.value.hint)
        assertEquals(recoveredPrimary, vm.state.value.trackedFace)
        assertFalse(vm.state.value.showPositioningMask)
        val offCenterFace = face.copy(x = .2f)
        port.samples.send(TrackingObservation(1, 2600, offCenterFace))
        runCurrent()
        assertEquals(PositioningHint.MOVE_RIGHT, vm.state.value.hint)
        assertEquals(offCenterFace, vm.state.value.trackedFace)
        assertFalse(vm.state.value.showPositioningMask)
        assertEquals(listOf(1L), port.sessions)
        port.samples.send(TrackingObservation(1, 2700, null))
        runCurrent()
        vm.onAction(CaptureIntent.Retry)
        runCurrent()
        assertEquals(listOf(1L, 2L), port.sessions)
        assertTrue(vm.state.value.showPositioningMask)
        assertEquals(PositioningHint.PLACE_FACE, vm.state.value.hint)
        assertNull(vm.state.value.trackedFace)
        port.samples.send(TrackingObservation(1, 2800, face))
        runCurrent()
        assertNull(vm.state.value.trackedFace)
        vm.onAction(CaptureIntent.Stopped)
        runCurrent()
        port.samples.close()
        port.failures.close()
    }

    @Test fun errorsAreStateAndRetryStartsAnotherAttempt() = runTest(dispatcher) {
        val port = FakeTracking()
        val vm = CaptureViewModel(port, CaptureReducer(PositioningPolicy()))
        vm.onAction(CaptureIntent.PermissionResult(true))
        vm.onAction(CaptureIntent.ViewportChanged(1080f, 2100f))
        vm.onAction(CaptureIntent.Resumed)
        runCurrent()
        port.failures.send(CameraProblem(1, CameraFailure.UNAVAILABLE))
        runCurrent()
        assertEquals(CameraFailure.UNAVAILABLE, vm.state.value.failure)
        vm.onAction(CaptureIntent.Retry)
        runCurrent()
        assertNull(vm.state.value.failure)
        assertEquals(listOf(1L, 2L), port.sessions)
        vm.onAction(CaptureIntent.Stopped)
        runCurrent()
        port.samples.close()
        port.failures.close()
    }
}

package com.xim.facetracking.domain

import kotlinx.coroutines.flow.Flow

/** Coordinates are normalized to the mirrored, cropped preview viewport. */
data class TrackingObservation(
    val sessionId: Long,
    val timestampMs: Long,
    /** The primary detection for this frame; additional faces receive no overlay. */
    val face: PositioningFace?
)

enum class CameraFailure { UNAVAILABLE, PERMISSION_DENIED, DETECTOR_UNAVAILABLE }
data class CameraProblem(val sessionId: Long, val failure: CameraFailure)

interface FaceTrackingPort {
    /** High-frequency observations may be conflated; timestamps preserve gaps. */
    val observations: Flow<TrackingObservation>
    /** Failure/control events are never conflated. */
    val problems: Flow<CameraProblem>
    fun start(sessionId: Long)
    fun stop()
}

interface CaptureClock {
    fun monotonicMs(): Long
}

data class CaptureState(
    val permissionGranted: Boolean = false,
    val foreground: Boolean = false,
    val target: PositioningTarget = PositioningTarget(0f, 0f),
    val sessionId: Long = 0,
    val monitoring: Boolean = false,
    val positioning: PositioningState = PositioningState(),
    val face: PositioningFace? = null,
    val failure: CameraFailure? = null
)

sealed interface CaptureEvent {
    data class Permission(val granted: Boolean) : CaptureEvent
    data class Viewport(val width: Float, val height: Float) : CaptureEvent
    data class Observation(val value: TrackingObservation) : CaptureEvent
    data class Failed(val value: CameraProblem) : CaptureEvent
    data object Resumed : CaptureEvent
    data object Stopped : CaptureEvent
    data object Retry : CaptureEvent
}

sealed interface CaptureCommand {
    data class StartTracking(val sessionId: Long) : CaptureCommand
    data object StopTracking : CaptureCommand
}

data class CaptureTransition(val state: CaptureState, val commands: List<CaptureCommand> = emptyList())

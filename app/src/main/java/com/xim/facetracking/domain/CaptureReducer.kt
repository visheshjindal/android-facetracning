package com.xim.facetracking.domain

/** Deterministic MVI transition. The caller executes commands and returns results as events. */
class CaptureReducer(private val positioningPolicy: PositioningPolicy) {
    fun reduce(state: CaptureState, event: CaptureEvent): CaptureTransition {
        if (event is CaptureEvent.Observation) {
            val value = event.value
            if (!state.monitoring || value.sessionId != state.sessionId ||
                (state.positioning.lastSample?.let { value.timestampMs <= it } == true)) return CaptureTransition(state)
            return CaptureTransition(state.copy(
                positioning = positioningPolicy.update(state.positioning, value.timestampMs, value.face, state.target),
                face = value.face
            ))
        }
        if (event is CaptureEvent.Failed && (!state.monitoring || event.value.sessionId != state.sessionId)) return CaptureTransition(state)

        var next = when (event) {
            is CaptureEvent.Permission -> state.copy(permissionGranted = event.granted)
            is CaptureEvent.Viewport -> state.copy(target = positioningTarget(event.width, event.height))
            is CaptureEvent.Failed -> state.copy(failure = event.value.failure)
            CaptureEvent.Resumed -> state.copy(foreground = true)
            CaptureEvent.Stopped -> state.copy(foreground = false, failure = null)
            CaptureEvent.Retry -> state.copy(failure = null)
            else -> state
        }
        val commands = mutableListOf<CaptureCommand>()
        val eligible = next.permissionGranted && next.foreground && next.target.width > 0f && next.failure == null
        val restart = next.target != state.target || event == CaptureEvent.Retry
        if (state.monitoring && (!eligible || restart)) {
            commands += CaptureCommand.StopTracking
            next = next.copy(monitoring = false, positioning = PositioningState(), face = null)
        }
        if (eligible && !next.monitoring) {
            next = next.copy(sessionId = state.sessionId + 1, monitoring = true, positioning = PositioningState(), face = null)
            commands += CaptureCommand.StartTracking(next.sessionId)
        }
        return CaptureTransition(next, commands)
    }
}

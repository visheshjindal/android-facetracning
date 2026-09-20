package com.xim.facetracking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xim.facetracking.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CaptureIntent {
    data class PermissionResult(val granted: Boolean) : CaptureIntent
    data class ViewportChanged(val width: Float, val height: Float) : CaptureIntent
    data object Resumed : CaptureIntent
    data object Stopped : CaptureIntent
    data object Retry : CaptureIntent
}

data class CaptureUiState(
    val permissionGranted: Boolean = false,
    val showPositioningMask: Boolean = true,
    val hint: PositioningHint = PositioningHint.PLACE_FACE,
    val trackedFace: PositioningFace? = null,
    val failure: CameraFailure? = null
)

class CaptureViewModel(
    private val tracking: FaceTrackingPort,
    private val reducer: CaptureReducer
) : ViewModel() {
    private var domainState = CaptureState()
    private val mutableState = MutableStateFlow(CaptureUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch { tracking.observations.collect { handle(CaptureEvent.Observation(it)) } }
        viewModelScope.launch { tracking.problems.collect { handle(CaptureEvent.Failed(it)) } }
    }

    fun onAction(intent: CaptureIntent) {
        viewModelScope.launch {
            handle(when (intent) {
                is CaptureIntent.PermissionResult -> CaptureEvent.Permission(intent.granted)
                is CaptureIntent.ViewportChanged -> CaptureEvent.Viewport(intent.width, intent.height)
                CaptureIntent.Resumed -> CaptureEvent.Resumed
                CaptureIntent.Stopped -> CaptureEvent.Stopped
                CaptureIntent.Retry -> CaptureEvent.Retry
            })
        }
    }

    // All inputs and port results are reduced on viewModelScope's main dispatcher.
    private fun handle(event: CaptureEvent) {
        val transition = reducer.reduce(domainState, event)
        domainState = transition.state
        mutableState.value = CaptureUiState(
            permissionGranted = domainState.permissionGranted,
            showPositioningMask = !domainState.positioning.following,
            hint = domainState.positioning.hint,
            trackedFace = domainState.face.takeIf { domainState.positioning.following },
            failure = domainState.failure
        )
        transition.commands.forEach { command ->
            when (command) {
                is CaptureCommand.StartTracking -> tracking.start(command.sessionId)
                CaptureCommand.StopTracking -> tracking.stop()
            }
        }
    }

    override fun onCleared() { tracking.stop() }
}

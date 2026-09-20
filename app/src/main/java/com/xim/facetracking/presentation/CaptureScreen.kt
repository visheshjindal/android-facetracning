package com.xim.facetracking.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xim.facetracking.R
import com.xim.facetracking.domain.CameraFailure
import com.xim.facetracking.domain.PositioningHint

@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onAction: (CaptureIntent) -> Unit,
    requestPermission: () -> Unit,
    openSettings: () -> Unit,
    exit: () -> Unit,
    preview: @Composable (Modifier) -> Unit
) {
    Scaffold { padding ->
        if (!state.permissionGranted) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.camera_permission_body))
                Button(onClick = requestPermission) { Text(stringResource(R.string.camera_permission_grant)) }
                OutlinedButton(onClick = openSettings) { Text(stringResource(R.string.camera_settings)) }
                TextButton(onClick = exit) { Text(stringResource(R.string.exit_capture)) }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding).onSizeChanged {
                onAction(CaptureIntent.ViewportChanged(it.width.toFloat(), it.height.toFloat()))
            }) {
                preview(Modifier.fillMaxSize())
                TrackingOverlay(state, Modifier.fillMaxSize())
                PositioningMask(state.showPositioningMask, Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(if (state.showPositioningMask) Color.White else Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(state.failure?.messageResource() ?: state.hint.messageResource()),
                        color = if (state.showPositioningMask) Color.Black else Color.White,
                        style = MaterialTheme.typography.titleLarge)
                    if (state.failure == null && state.hint == PositioningHint.TRACKING_LOST) {
                        Button(onClick = { onAction(CaptureIntent.Retry) }) {
                            Text(stringResource(R.string.restart_tracking))
                        }
                    }
                    if (state.failure != null) {
                        Button(onClick = { onAction(CaptureIntent.Retry) }) { Text(stringResource(R.string.retry_camera)) }
                        TextButton(onClick = exit) { Text(stringResource(R.string.exit_capture)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingOverlay(state: CaptureUiState, modifier: Modifier) {
    Canvas(modifier) {
        state.trackedFace?.let { face ->
            drawOval(Color(0xFF00FF87),
                Offset((face.x - face.width / 2) * size.width, (face.y - face.height / 2) * size.height),
                Size(face.width * size.width, face.height * size.height), style = Stroke(5.dp.toPx()))
        }
    }
}

private fun PositioningHint.messageResource(): Int = when (this) {
    PositioningHint.PLACE_FACE -> R.string.position_place
    PositioningHint.CENTER_FACE -> R.string.position_center
    PositioningHint.CLOSER -> R.string.position_closer
    PositioningHint.FARTHER -> R.string.position_farther
    PositioningHint.LOOK_STRAIGHT -> R.string.position_straight
    PositioningHint.HOLD_STILL -> R.string.position_hold
    PositioningHint.FOLLOWING -> R.string.position_following
    PositioningHint.TRACKING_LOST -> R.string.position_tracking_lost
}

private fun CameraFailure.messageResource(): Int = when (this) {
    CameraFailure.UNAVAILABLE -> R.string.camera_unavailable
    CameraFailure.PERMISSION_DENIED -> R.string.camera_permission_title
    CameraFailure.DETECTOR_UNAVAILABLE -> R.string.detector_unavailable
}

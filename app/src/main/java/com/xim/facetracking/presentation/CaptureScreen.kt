package com.xim.facetracking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xim.facetracking.R
import com.xim.facetracking.domain.CameraFailure
import com.xim.facetracking.domain.CaptureGuidance
import com.xim.facetracking.domain.LightingAssessment
import com.xim.facetracking.domain.PositioningFace
import com.xim.facetracking.domain.ShadowSide

@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onAction: (CaptureIntent) -> Unit,
    requestPermission: () -> Unit,
    openSettings: () -> Unit,
    exit: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        if (!state.permissionGranted) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                FaceReturnGuide(state.showReturnGuide, Modifier.fillMaxSize())
                TrackingOverlay(state.trackedFace, Modifier.fillMaxSize())
                PositioningMask(state.showPositioningMask, Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(if (state.showPositioningMask) Color.White else Color.Black.copy(alpha = 0.7f))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(state.failure?.messageResource() ?: state.hint.messageResource()),
                            color = if (state.showPositioningMask) Color.Black else Color.White,
                            style = MaterialTheme.typography.titleLarge)
                        if (state.failure == null && state.hint == CaptureGuidance.TRACKING_LOST) {
                            Button(onClick = { onAction(CaptureIntent.Retry) }) {
                                Text(stringResource(R.string.restart_tracking))
                            }
                        }
                        if (state.failure != null) {
                            Button(onClick = { onAction(CaptureIntent.Retry) }) { Text(stringResource(R.string.retry_camera)) }
                            TextButton(onClick = exit) { Text(stringResource(R.string.exit_capture)) }
                        }
                    }
                    if (state.failure == null) {
                        LightingIndicator(
                            state = state.lightingIndicator,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp, end = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LightingIndicator(state: LightingIndicatorUiState, modifier: Modifier = Modifier) {
    if (!state.visible) return
    val label = stringResource(state.messageResource())
    val containerColor = when (state.assessment) {
        LightingAssessment.UNKNOWN -> Color.Black.copy(alpha = 0.68f)
        LightingAssessment.ACCEPTABLE -> Color(0xFF128A52)
        LightingAssessment.UNEVEN,
        LightingAssessment.TOO_DARK,
        LightingAssessment.HIGH_CONTRAST,
        LightingAssessment.TOO_BRIGHT -> Color(0xFFFFB020)
    }
    val contentColor = if (state.assessment == LightingAssessment.UNKNOWN ||
        state.assessment == LightingAssessment.ACCEPTABLE) Color.White else Color.Black

    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LightingStatusIcon(state.assessment, 22.dp)
            AnimatedVisibility(
                visible = state.expanded,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
            ) {
                Text(
                    label,
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun LightingStatusIcon(
    assessment: LightingAssessment,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val color = LocalContentColor.current
    Spacer(
        modifier = modifier
            .size(iconSize)
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.22f
                val strokeWidth = size.minDimension * 0.09f
                val circleStroke = Stroke(strokeWidth)
                val rayStroke = strokeWidth
                val checkStroke = Stroke(strokeWidth * 0.8f)
                val exclamStroke = strokeWidth * 0.75f
                val dotRadius = strokeWidth * 0.42f

                val rayOffsets = (0 until 8).map { index ->
                    val angle = Math.toRadians(index * 45.0)
                    val cosA = kotlin.math.cos(angle).toFloat()
                    val sinA = kotlin.math.sin(angle).toFloat()
                    val inner = radius * 1.55f
                    val outer = radius * 2.05f
                    Offset(center.x + cosA * inner, center.y + sinA * inner) to
                        Offset(center.x + cosA * outer, center.y + sinA * outer)
                }

                val checkPath = Path().apply {
                    moveTo(center.x - radius * 0.55f, center.y)
                    lineTo(center.x - radius * 0.10f, center.y + radius * 0.42f)
                    lineTo(center.x + radius * 0.65f, center.y - radius * 0.45f)
                }

                onDrawBehind {
                    drawCircle(color, radius, center, style = circleStroke)
                    for ((start, end) in rayOffsets) {
                        drawLine(
                            color,
                            start,
                            end,
                            strokeWidth = rayStroke,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                    when (assessment) {
                        LightingAssessment.ACCEPTABLE -> {
                            drawPath(checkPath, color, style = checkStroke)
                        }
                        LightingAssessment.UNEVEN,
                        LightingAssessment.TOO_DARK,
                        LightingAssessment.HIGH_CONTRAST,
                        LightingAssessment.TOO_BRIGHT -> {
                            drawLine(
                                color,
                                Offset(center.x, center.y - radius * 0.5f),
                                Offset(center.x, center.y + radius * 0.15f),
                                strokeWidth = exclamStroke
                            )
                            drawCircle(color, dotRadius, Offset(center.x, center.y + radius * 0.55f))
                        }
                        LightingAssessment.UNKNOWN -> Unit
                    }
                }
            }
    )
}

@Composable
private fun TrackingOverlay(
    trackedFace: PositioningFace?,
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val stroke = Stroke(5.dp.toPx())
            val trackingColor = Color(0xFF00FF87)
            onDrawBehind {
                trackedFace?.let { face ->
                    drawOval(
                        color = trackingColor,
                        topLeft = Offset((face.x - face.width / 2f) * size.width, (face.y - face.height / 2f) * size.height),
                        size = Size(face.width * size.width, face.height * size.height),
                        style = stroke
                    )
                }
            }
        }
    )
}

private fun CaptureGuidance.messageResource(): Int = when (this) {
    CaptureGuidance.PLACE_FACE -> R.string.position_place
    CaptureGuidance.CENTER_FACE -> R.string.position_center
    CaptureGuidance.MOVE_LEFT -> R.string.position_move_left
    CaptureGuidance.MOVE_RIGHT -> R.string.position_move_right
    CaptureGuidance.MOVE_UP -> R.string.position_move_up
    CaptureGuidance.MOVE_DOWN -> R.string.position_move_down
    CaptureGuidance.CLOSER -> R.string.position_closer
    CaptureGuidance.FARTHER -> R.string.position_farther
    CaptureGuidance.LOOK_STRAIGHT -> R.string.position_straight
    CaptureGuidance.HOLD_STILL -> R.string.position_hold
    CaptureGuidance.FOLLOWING -> R.string.position_following
    CaptureGuidance.TRACKING_LOST -> R.string.position_tracking_lost
    CaptureGuidance.LIGHT_USER_LEFT -> R.string.lighting_add_left
    CaptureGuidance.LIGHT_USER_RIGHT -> R.string.lighting_add_right
    CaptureGuidance.MORE_LIGHT -> R.string.lighting_more
    CaptureGuidance.SOFTEN_LIGHT -> R.string.lighting_soften
    CaptureGuidance.REDUCE_LIGHT -> R.string.lighting_reduce
}

private fun LightingIndicatorUiState.messageResource(): Int = when (assessment) {
    LightingAssessment.UNKNOWN -> R.string.lighting_checking
    LightingAssessment.ACCEPTABLE -> R.string.lighting_okay
    LightingAssessment.UNEVEN -> when (shadowSide) {
        ShadowSide.USER_LEFT -> R.string.lighting_add_left
        ShadowSide.USER_RIGHT -> R.string.lighting_add_right
        null -> R.string.lighting_soften
    }
    LightingAssessment.HIGH_CONTRAST -> R.string.lighting_soften
    LightingAssessment.TOO_DARK -> R.string.lighting_more
    LightingAssessment.TOO_BRIGHT -> R.string.lighting_reduce
}

private fun CameraFailure.messageResource(): Int = when (this) {
    CameraFailure.UNAVAILABLE -> R.string.camera_unavailable
    CameraFailure.PERMISSION_DENIED -> R.string.camera_permission_title
    CameraFailure.DETECTOR_UNAVAILABLE -> R.string.detector_unavailable
}

@Preview(showBackground = true)
@Composable
private fun CaptureScreenPermissionPreview() {
    CaptureScreen(
        state = CaptureUiState(permissionGranted = false),
        onAction = {},
        requestPermission = {},
        openSettings = {},
        exit = {},
        preview = { Box(it) }
    )
}

@Preview(showBackground = true)
@Composable
private fun CaptureScreenPositioningPreview() {
    CaptureScreen(
        state = CaptureUiState(
            permissionGranted = true,
            showPositioningMask = true,
            hint = CaptureGuidance.PLACE_FACE
        ),
        onAction = {},
        requestPermission = {},
        openSettings = {},
        exit = {},
        preview = { Box(it) }
    )
}

@Preview(showBackground = true)
@Composable
private fun CaptureScreenTrackingPreview() {
    CaptureScreen(
        state = CaptureUiState(
            permissionGranted = true,
            showPositioningMask = false,
            showReturnGuide = true,
            hint = CaptureGuidance.FOLLOWING,
            trackedFace = PositioningFace(0.5f, 0.5f, 0.35f, 0.45f, 0f, 0f, 0f),
            lightingIndicator = LightingIndicatorUiState(
                visible = true,
                assessment = LightingAssessment.ACCEPTABLE
            )
        ),
        onAction = {},
        requestPermission = {},
        openSettings = {},
        exit = {},
        preview = { Box(it) }
    )
}

@Preview(showBackground = true)
@Composable
private fun CaptureScreenLightingWarningPreview() {
    CaptureScreen(
        state = CaptureUiState(
            permissionGranted = true,
            showPositioningMask = false,
            showReturnGuide = true,
            hint = CaptureGuidance.MORE_LIGHT,
            lightingIndicator = LightingIndicatorUiState(
                visible = true,
                assessment = LightingAssessment.TOO_DARK,
                expanded = true
            )
        ),
        onAction = {},
        requestPermission = {},
        openSettings = {},
        exit = {},
        preview = { Box(it) }
    )
}

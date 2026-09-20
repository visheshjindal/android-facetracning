package com.xim.facetracking.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xim.facetracking.R
import com.xim.facetracking.domain.positioningTarget

@Composable
fun PositioningMask(visible: Boolean, modifier: Modifier = Modifier) {
    val opacity by animateFloatAsState(if (visible) 1f else 0f, tween(450), label = "positioning mask")
    Canvas(modifier) {
        if (opacity > 0f) {
            val target = positioningTarget(size.width, size.height)
            val width = target.width * size.width
            val height = target.height * size.height
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addOval(Rect(center.x - width / 2, center.y - height / 2, center.x + width / 2, center.y + height / 2))
            }
            drawPath(path, Color.White.copy(alpha = opacity))
        }
    }
}

/**
 * Fixed ideal position retained after acquisition. This is deliberately separate from the
 * moving tracking overlay: the guide shows where to return, while the live oval shows where the
 * detector currently sees the face.
 */
@Composable
fun FaceReturnGuide(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return

    val description = stringResource(R.string.face_return_guide_description)
    val density = LocalDensity.current
    Canvas(modifier.semantics { contentDescription = description }) {
        val target = positioningTarget(size.width, size.height)
        val width = target.width * size.width
        val height = target.height * size.height
        val left = center.x - width / 2f
        val top = center.y - height / 2f
        val guideColor = Color.White.copy(alpha = 0.82f)
        val outlineWidth = with(density) { 3.dp.toPx() }
        val detailWidth = with(density) { 2.dp.toPx() }

        drawOval(
            color = guideColor,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = Stroke(outlineWidth)
        )

        val eyeWidth = width * 0.13f
        val eyeHeight = height * 0.035f
        val eyeY = center.y - height * 0.11f
        val eyeOffset = width * 0.18f
        listOf(center.x - eyeOffset, center.x + eyeOffset).forEach { eyeX ->
            drawOval(
                color = guideColor,
                topLeft = Offset(eyeX - eyeWidth / 2f, eyeY - eyeHeight / 2f),
                size = Size(eyeWidth, eyeHeight),
                style = Stroke(detailWidth)
            )
            drawCircle(
                color = guideColor,
                radius = detailWidth,
                center = Offset(eyeX, eyeY)
            )
        }
    }
}

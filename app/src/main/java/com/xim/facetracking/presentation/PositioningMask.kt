package com.xim.facetracking.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xim.facetracking.R
import com.xim.facetracking.domain.positioningTarget

@Composable
fun PositioningMask(visible: Boolean, modifier: Modifier = Modifier) {
    val opacity by animateFloatAsState(if (visible) 1f else 0f, tween(450), label = "positioning mask")
    Spacer(
        modifier = modifier.drawWithCache {
            val target = positioningTarget(size.width, size.height)
            val width = target.width * size.width
            val height = target.height * size.height
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addOval(
                    Rect(
                        centerX - width / 2f,
                        centerY - height / 2f,
                        centerX + width / 2f,
                        centerY + height / 2f
                    )
                )
            }
            onDrawBehind {
                if (opacity > 0f) {
                    drawPath(path, Color.White.copy(alpha = opacity))
                }
            }
        }
    )
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
    Spacer(
        modifier = modifier
            .semantics { contentDescription = description }
            .drawWithCache {
                val target = positioningTarget(size.width, size.height)
                val width = target.width * size.width
                val height = target.height * size.height
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val left = centerX - width / 2f
                val top = centerY - height / 2f
                val guideColor = Color.White.copy(alpha = 0.82f)
                val outlineStroke = Stroke(3.dp.toPx())
                val detailStroke = Stroke(2.dp.toPx())
                val detailRadius = 2.dp.toPx()

                val eyeWidth = width * 0.13f
                val eyeHeight = height * 0.035f
                val eyeY = centerY - height * 0.11f
                val eyeOffset = width * 0.18f
                val leftEyeX = centerX - eyeOffset
                val rightEyeX = centerX + eyeOffset

                onDrawBehind {
                    drawOval(
                        color = guideColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = outlineStroke
                    )

                    drawOval(
                        color = guideColor,
                        topLeft = Offset(leftEyeX - eyeWidth / 2f, eyeY - eyeHeight / 2f),
                        size = Size(eyeWidth, eyeHeight),
                        style = detailStroke
                    )
                    drawCircle(
                        color = guideColor,
                        radius = detailRadius,
                        center = Offset(leftEyeX, eyeY)
                    )

                    drawOval(
                        color = guideColor,
                        topLeft = Offset(rightEyeX - eyeWidth / 2f, eyeY - eyeHeight / 2f),
                        size = Size(eyeWidth, eyeHeight),
                        style = detailStroke
                    )
                    drawCircle(
                        color = guideColor,
                        radius = detailRadius,
                        center = Offset(rightEyeX, eyeY)
                    )
                }
            }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PositioningMaskPreview() {
    Box(Modifier.fillMaxSize()) {
        PositioningMask(visible = true, modifier = Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FaceReturnGuidePreview() {
    Box(Modifier.fillMaxSize()) {
        FaceReturnGuide(visible = true, modifier = Modifier.fillMaxSize())
    }
}

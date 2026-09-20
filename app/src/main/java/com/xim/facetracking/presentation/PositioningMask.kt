package com.xim.facetracking.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
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

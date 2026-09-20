package com.xim.facetracking.infrastructure.analysis

import com.xim.facetracking.domain.MeshPoint

/** Converts ML Kit's upright image-edge coordinates to the unrotated Y-plane coordinates. */
object FrameCoordinates {
    fun toBuffer(point: MeshPoint, width: Int, height: Int, rotation: Int): MeshPoint = when (rotation) {
        0 -> point
        90 -> MeshPoint(point.y, height - point.x)
        180 -> MeshPoint(width - point.x, height - point.y)
        270 -> MeshPoint(width - point.y, point.x)
        else -> error("Unsupported frame rotation")
    }
}

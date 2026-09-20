package com.xim.facetracking.infrastructure.analysis

import android.graphics.Matrix
import com.xim.facetracking.domain.MeshPoint

/** Maps raw buffer geometry through sensor coordinates into the mirrored/cropped preview. */
internal class PreviewCoordinates private constructor(
    private val bufferToPreview: Matrix,
    private val width: Int,
    private val height: Int
) {
    fun map(point: MeshPoint): MeshPoint {
        val xy = floatArrayOf(point.x, point.y)
        bufferToPreview.mapPoints(xy)
        return MeshPoint(xy[0] / width, xy[1] / height)
    }

    companion object {
        fun create(sensorToBuffer: Matrix, sensorToPreview: Matrix, width: Int, height: Int): PreviewCoordinates? {
            if (width <= 0 || height <= 0) return null
            val transform = Matrix()
            if (!sensorToBuffer.invert(transform)) return null
            transform.postConcat(sensorToPreview)
            return PreviewCoordinates(transform, width, height)
        }
    }
}

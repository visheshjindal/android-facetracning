package com.xim.facetracking.infrastructure.analysis

import androidx.camera.core.ImageProxy
import com.xim.facetracking.domain.SparseLumaFrame
import java.nio.ByteBuffer

/** Copies a fixed-size grid from a Y plane without allocating a bitmap or converting to RGB. */
internal class SparseLumaSampler(
    private val columns: Int = 64,
    private val rows: Int = 64
) {
    private val recycled = ArrayDeque<ByteArray>(MAX_RECYCLED_GRIDS)

    fun sample(image: ImageProxy): SparseLumaFrame? {
        val plane = image.planes.firstOrNull() ?: return null
        return sample(
            width = image.width,
            height = image.height,
            rowStride = plane.rowStride,
            pixelStride = plane.pixelStride,
            buffer = plane.buffer
        )
    }

    internal fun sample(
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        buffer: ByteBuffer
    ): SparseLumaFrame? {
        if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) return null
        val readable = buffer.duplicate()
        val values = acquireValues()
        for (row in 0 until rows) {
            val sourceY = ((row + 0.5f) * height / rows).toInt().coerceIn(0, height - 1)
            for (column in 0 until columns) {
                val sourceX = ((column + 0.5f) * width / columns).toInt().coerceIn(0, width - 1)
                val index = sourceY * rowStride + sourceX * pixelStride
                if (index !in 0 until readable.limit()) {
                    recycleValues(values)
                    return null
                }
                values[row * columns + column] = readable.get(index)
            }
        }
        return SparseLumaFrame(width, height, columns, rows, values)
    }

    fun recycle(frame: SparseLumaFrame) {
        if (frame.columns == columns && frame.rows == rows) recycleValues(frame.values)
    }

    @Synchronized
    private fun acquireValues(): ByteArray =
        if (recycled.isEmpty()) ByteArray(columns * rows) else recycled.removeFirst()

    @Synchronized
    private fun recycleValues(values: ByteArray) {
        if (values.size == columns * rows && recycled.size < MAX_RECYCLED_GRIDS) recycled.addLast(values)
    }

    private companion object {
        const val MAX_RECYCLED_GRIDS = 4
    }
}

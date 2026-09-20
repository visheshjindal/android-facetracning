package com.xim.facetracking.infrastructure.analysis

import com.xim.facetracking.domain.LumaImage
import java.nio.ByteBuffer

/** Copies Y only, respecting plane padding, pixel stride and buffer offset. Never persists pixels. */
internal object LumaPlaneReader {
    fun read(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): LumaImage {
        val bytes = buffer.duplicate()
        val base = bytes.position()
        val values = FloatArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            values[y * width + x] = (bytes.get(base + y * rowStride + x * pixelStride).toInt() and 255).toFloat()
        }
        return LumaImage(width, height, values)
    }
}

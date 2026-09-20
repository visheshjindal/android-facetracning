package com.xim.facetracking

import com.xim.facetracking.infrastructure.analysis.SparseLumaSampler
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class SparseLumaSamplerTest {
    @Test fun readsSparseValuesFromPaddedPlane() {
        val buffer = ByteBuffer.allocate(40)
        for (y in 0 until 4) for (x in 0 until 4) buffer.put(y * 10 + x * 2, (y * 10 + x).toByte())

        val sampled = SparseLumaSampler(columns = 2, rows = 2).sample(
            width = 4,
            height = 4,
            rowStride = 10,
            pixelStride = 2,
            buffer = buffer
        )

        assertNotNull(sampled)
        assertArrayEquals(byteArrayOf(11, 13, 31, 33), sampled!!.values)
    }

    @Test fun defaultGridNeverExceeds4096Samples() {
        val buffer = ByteBuffer.allocate(80 * 64)
        val sampled = SparseLumaSampler().sample(64, 64, 80, 1, buffer)
        assertEquals(4_096, sampled?.values?.size)
    }

    @Test fun invalidPlaneIsUnavailable() {
        val sampled = SparseLumaSampler(columns = 2, rows = 2).sample(
            width = 4,
            height = 4,
            rowStride = 2,
            pixelStride = 1,
            buffer = ByteBuffer.allocate(4)
        )
        assertNull(sampled)
    }
}

package com.xim.facetracking

import android.graphics.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xim.facetracking.infrastructure.analysis.bufferToViewTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraTransformTest {
    @Test fun composesBufferToSensorBeforeSensorToView() {
        val sensorToBuffer = Matrix().apply { setScale(2f, 2f) }
        val sensorToView = Matrix().apply { setTranslate(10f, 20f) }

        val transform = bufferToViewTransform(sensorToView, sensorToBuffer)

        assertNotNull(transform)
        assertEquals(11.5f, transform!!.mapX(3f, 4f), 0.001f)
        assertEquals(22f, transform.mapY(3f, 4f), 0.001f)
    }
}

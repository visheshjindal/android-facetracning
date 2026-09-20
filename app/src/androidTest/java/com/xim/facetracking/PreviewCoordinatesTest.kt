package com.xim.facetracking

import android.graphics.Matrix
import com.xim.facetracking.domain.MeshPoint
import com.xim.facetracking.infrastructure.analysis.FrameCoordinates
import com.xim.facetracking.infrastructure.analysis.PreviewCoordinates
import org.junit.Assert.*
import org.junit.Test

class PreviewCoordinatesTest {
    @Test fun sensorCropScaleAndMirroringMapTheSamePixelAfterEveryRotation() {
        // Sensor (20,30) -> buffer (20,40), preview mirrored/cropped to (140,20).
        val sensorToBuffer = Matrix().apply { setValues(floatArrayOf(2f,0f,-20f,0f,2f,-20f,0f,0f,1f)) }
        val sensorToPreview = Matrix().apply { setValues(floatArrayOf(-2f,0f,180f,0f,2f,-40f,0f,0f,1f)) }
        val transform = PreviewCoordinates.create(sensorToBuffer,sensorToPreview,200,100)!!
        val rotated = mapOf(0 to MeshPoint(20f,40f),90 to MeshPoint(40f,20f),
            180 to MeshPoint(80f,40f),270 to MeshPoint(40f,80f))
        rotated.forEach { (rotation,point) ->
            val result = transform.map(FrameCoordinates.toBuffer(point,100,80,rotation))
            assertEquals(.7f,result.x,.0001f)
            assertEquals(.2f,result.y,.0001f)
        }
        assertTrue(transform.map(MeshPoint(10f,40f)).x > transform.map(MeshPoint(30f,40f)).x)
    }

    @Test fun singularTransformsAndMissingViewportAreUnavailable() {
        val singular = Matrix().apply { setScale(0f,0f) }
        assertNull(PreviewCoordinates.create(singular,Matrix(),100,100))
        assertNull(PreviewCoordinates.create(Matrix(),Matrix(),0,100))
    }
}

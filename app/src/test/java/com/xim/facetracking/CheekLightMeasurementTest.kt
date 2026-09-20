package com.xim.facetracking

import com.xim.facetracking.domain.*
import com.xim.facetracking.infrastructure.analysis.FrameCoordinates
import com.xim.facetracking.infrastructure.analysis.FrameLease
import com.xim.facetracking.infrastructure.analysis.LumaPlaneReader
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class CheekLightMeasurementTest {
    private fun square(l: Float, t: Float, r: Float, b: Float) = listOf(
        MeshPoint(l,t), MeshPoint(r,t), MeshPoint(r,b), MeshPoint(l,b))

    @Test fun cheeksAreIndependentAndReportClippingAndImbalance() {
        val image = LumaImage(8, 4, FloatArray(32) { if (it % 8 < 4) 20f else 250f })
        val result = CheekLightMeasurement.measure(image, square(0f,0f,4f,4f), square(4f,0f,8f,4f))
        assertEquals(CheekLight(20f,1f,0f,16), result.left)
        assertEquals(CheekLight(250f,0f,1f,16), result.right)
        assertEquals(.92f, result.imbalance!!, .001f)
    }

    @Test fun polygonExcludesPixelsOutsideItAndComputesEvenMedian() {
        val image = LumaImage(2,2,floatArrayOf(10f,20f,240f,250f))
        val metrics = CheekLightMeasurement.region(image, square(0f,0f,2f,1f))!!
        assertEquals(15f, metrics.medianLuma, 0f)
        assertEquals(2, metrics.pixelCount)
        val triangle = CheekLightMeasurement.region(image, listOf(MeshPoint(0f,0f), MeshPoint(2f,0f), MeshPoint(0f,2f)))!!
        assertEquals(1, triangle.pixelCount)
        assertEquals(10f, triangle.medianLuma, 0f)
    }

    @Test fun invalidOrEmptyRegionsAreUnknownWithoutClamping() {
        val image = LumaImage(8,8,FloatArray(64) { 100f })
        for (p in listOf(emptyList(), square(-1f,0f,3f,3f), square(0f,0f,9f,3f),
            square(0f,1f,4f,1f), square(0f,0f,.1f,.1f), square(Float.NaN,0f,3f,3f))) {
            assertNull(CheekLightMeasurement.region(image,p))
        }
        val partial = CheekLightMeasurement.measure(image, emptyList(), square(0f,0f,4f,4f))
        assertNull(partial.left); assertNotNull(partial.right); assertNull(partial.imbalance)
    }

    @Test fun meshMatchesPrimaryOnlyWithSufficientOverlap() {
        val primary = FaceBounds(0f,0f,10f,10f)
        val other = FaceBounds(20f,0f,30f,10f)
        assertEquals(1, MeshAssociation.select(primary,listOf(other,primary)))
        assertNull(MeshAssociation.select(primary,listOf(other,FaceBounds(8f,0f,18f,10f))))
        assertNull(MeshAssociation.select(primary,emptyList()))
        assertEquals(0, MeshAssociation.select(primary,listOf(FaceBounds(0f,0f,5f,10f))))
    }

    @Test fun rotationsMapToOriginalLumaPlane() {
        val expected = MeshPoint(20f,30f)
        assertEquals(expected, FrameCoordinates.toBuffer(MeshPoint(20f,30f),100,80,0))
        assertEquals(expected, FrameCoordinates.toBuffer(MeshPoint(50f,20f),100,80,90))
        assertEquals(expected, FrameCoordinates.toBuffer(MeshPoint(80f,50f),100,80,180))
        assertEquals(expected, FrameCoordinates.toBuffer(MeshPoint(30f,80f),100,80,270))
    }

    @Test fun fixedRegionsUseDistinctAnatomicalSidesAndInsetBoundaries() {
        val points = List(468) { MeshPoint(it.toFloat(), it.toFloat()) }
        val right = MidFaceRegions.cheek(points,false)
        val left = MidFaceRegions.cheek(points,true)
        assertTrue(right.maxOf { it.x } < left.minOf { it.x })
        assertTrue(right.minOf { it.x } > MidFaceRegions.rightCheekIndices.min())
        assertTrue(left.maxOf { it.x } < MidFaceRegions.leftCheekIndices.max())
        assertTrue(MidFaceRegions.cheek(emptyList(), true).isEmpty())
        assertTrue(MidFaceRegions.edges(listOf(MeshTriangle(10,152,33))).isEmpty())
        assertEquals(3, MidFaceRegions.edges(listOf(MeshTriangle(1,2,4),MeshTriangle(4,2,1))).size)
    }

    @Test fun lumaPlaneRespectsPaddingPixelStrideAndOffset() {
        val bytes = ByteBuffer.wrap(byteArrayOf(99,10,0,20,0,99,99,30,0,250.toByte()))
        bytes.position(1)
        val image = LumaPlaneReader.read(bytes,2,2,6,2)
        assertArrayEquals(floatArrayOf(10f,20f,30f,250f),image.values,0f)
        assertEquals(1,bytes.position())
    }

    @Test fun completionAndCancellationReleaseFrameExactlyOnce() {
        var count = 0
        val lease = FrameLease { count++ }
        lease.close(); lease.close()
        assertEquals(1,count)
    }
}

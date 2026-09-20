package com.xim.facetracking.infrastructure.analysis

import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.xim.facetracking.domain.CaptureClock
import com.xim.facetracking.domain.PositioningFace
import com.xim.facetracking.domain.TrackingObservation
import java.util.concurrent.Executor
import java.util.concurrent.ConcurrentHashMap

/** Session-scoped detector adapter. ML Kit owns frame closure after analyze(). */
class MlKitFaceAnalyzer(
    private val sessionId: Long,
    private val clock: CaptureClock,
    callbackExecutor: Executor,
    private val viewport: () -> Pair<Int, Int>,
    onObservation: (TrackingObservation) -> Unit,
    onFailure: () -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val detailed = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL).build())
    private val multiple = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE).build())
    @Volatile private var closed = false
    private val frameTimes = ConcurrentHashMap<Long, Long>()
    private val delegate = MlKitAnalyzer(
        listOf(detailed, multiple), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, callbackExecutor
    ) { result ->
        if (!closed) {
            val sampleTimeMs = frameTimes.remove(result.timestamp) ?: return@MlKitAnalyzer
            val faces = result.getValue(detailed)
            val allFaces = result.getValue(multiple)
            if (faces == null || allFaces == null) {
                onFailure()
            } else {
                val (width, height) = viewport()
                val primary = faces.firstOrNull()
                val fresh = clock.monotonicMs() - sampleTimeMs <= 300L
                val face = primary?.takeIf { fresh && width > 0 && height > 0 }?.let {
                    val box = it.boundingBox
                    PositioningFace(
                        box.exactCenterX() / width, box.exactCenterY() / height,
                        box.width().toFloat() / width, box.height().toFloat() / height,
                        it.headEulerAngleY, it.headEulerAngleX, it.headEulerAngleZ
                    )
                }
                onObservation(TrackingObservation(sessionId, sampleTimeMs, allFaces.size, face))
            }
        }
    }

    override fun analyze(image: ImageProxy) {
        if (closed) { image.close(); return }
        frameTimes[image.imageInfo.timestamp] = clock.monotonicMs()
        delegate.analyze(image)
    }

    override fun getDefaultTargetResolution(): Size = delegate.defaultTargetResolution
    override fun getTargetCoordinateSystem(): Int = delegate.targetCoordinateSystem
    override fun updateTransform(matrix: Matrix?) = delegate.updateTransform(matrix)

    override fun close() {
        closed = true
        frameTimes.clear()
        detailed.close()
        multiple.close()
    }
}

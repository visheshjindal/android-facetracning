package com.xim.facetracking.infrastructure.analysis

import android.graphics.Matrix
import android.os.Trace
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.xim.facetracking.domain.CaptureClock
import com.xim.facetracking.domain.AffineTransform2D
import com.xim.facetracking.domain.FaceLightingMeasurer
import com.xim.facetracking.domain.PositioningFace
import com.xim.facetracking.domain.SparseLumaFrame
import com.xim.facetracking.domain.TrackingObservation
import java.util.concurrent.Executor

private const val TAG = "MlKitFaceAnalyzer"

/** Session-scoped detector adapter. ML Kit owns frame closure after analyze(). */
class MlKitFaceAnalyzer(
    private val sessionId: Long,
    private val clock: CaptureClock,
    callbackExecutor: Executor,
    private val viewport: () -> Pair<Int, Int>,
    onObservation: (TrackingObservation) -> Unit,
    onFailure: () -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .build())
    @Volatile private var closed = false
    @Volatile private var sensorToView: Matrix? = null
    private val sampler = SparseLumaSampler()
    private val lightingMeasurer = FaceLightingMeasurer()
    private val pendingFrames = PendingFrames(MAX_PENDING_FRAMES)
    private val delegate = MlKitAnalyzer(
        listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, callbackExecutor
    ) { result ->
        if (!closed) {
            val pending = pendingFrames.remove(result.timestamp) ?: return@MlKitAnalyzer
            try {
                val sampleTimeMs = pending.sampleTimeMs
                val faces = result.getValue(detector)
                if (faces == null) {
                    onFailure()
                } else {
                    val viewportWidth = pending.viewportWidth
                    val viewportHeight = pending.viewportHeight
                    val fresh = clock.monotonicMs() - sampleTimeMs <= 300L
                    // Select one primary detection per frame; ID changes cannot block recovery.
                    val detectedFace = faces.firstOrNull()?.takeIf {
                        fresh && viewportWidth > 0 && viewportHeight > 0
                    }
                    val face = detectedFace?.let {
                        val box = it.boundingBox
                        PositioningFace(
                            box.exactCenterX() / viewportWidth, box.exactCenterY() / viewportHeight,
                            box.width().toFloat() / viewportWidth, box.height().toFloat() / viewportHeight,
                            it.headEulerAngleY, it.headEulerAngleX, it.headEulerAngleZ
                        )
                    }
                    val metrics = if (face != null && pending.luma != null && pending.transform != null) {
                        lightingMeasurer.measure(
                            pending.luma,
                            pending.transform,
                            viewportWidth,
                            viewportHeight,
                            face
                        )
                    } else null
                    onObservation(TrackingObservation(sessionId, sampleTimeMs, face, metrics))
                }
            } finally {
                pending.luma?.let(sampler::recycle)
            }
        }
    }

    override fun analyze(image: ImageProxy) {
        if (closed) { image.close(); return }
        val sampleTimeMs = clock.monotonicMs()
        Trace.beginSection("face-light-luma-sample")
        val luma = try {
            sampler.sample(image)
        } finally {
            Trace.endSection()
        }
        val (viewportWidth, viewportHeight) = viewport()
        val bufferToView = sensorToView?.let {
            bufferToViewTransform(it, image.imageInfo.sensorToBufferTransformMatrix)
        }
        pendingFrames.put(
            image.imageInfo.timestamp,
            PendingFrame(sampleTimeMs, luma, bufferToView, viewportWidth, viewportHeight)
        )?.luma?.let(sampler::recycle)
        delegate.analyze(image)
    }

    override fun getDefaultTargetResolution(): Size = delegate.defaultTargetResolution
    override fun getTargetCoordinateSystem(): Int = delegate.targetCoordinateSystem
    override fun updateTransform(matrix: Matrix?) {
        Log.d(TAG, "updateTransform matrix=${matrix != null}")
        // CameraX supplies sensor-to-view, while the copied luma grid uses buffer coordinates.
        sensorToView = matrix?.let(::Matrix)
        delegate.updateTransform(matrix)
    }

    override fun close() {
        closed = true
        pendingFrames.clear().forEach { it.luma?.let(sampler::recycle) }
        detector.close()
    }

    private data class PendingFrame(
        val sampleTimeMs: Long,
        val luma: SparseLumaFrame?,
        val transform: AffineTransform2D?,
        val viewportWidth: Int,
        val viewportHeight: Int
    )

    private class PendingFrames(private val capacity: Int) {
        private val frames = LinkedHashMap<Long, PendingFrame>()
        @Synchronized fun put(timestamp: Long, frame: PendingFrame): PendingFrame? {
            frames[timestamp] = frame
            return if (frames.size > capacity) frames.remove(frames.keys.first()) else null
        }
        @Synchronized fun remove(timestamp: Long): PendingFrame? = frames.remove(timestamp)
        @Synchronized fun clear(): List<PendingFrame> = frames.values.toList().also { frames.clear() }
    }

    private companion object {
        const val MAX_PENDING_FRAMES = 4
    }
}

/** Compose CameraX's sensor-to-view transform with this frame's buffer-to-sensor transform. */
internal fun bufferToViewTransform(
    sensorToView: Matrix,
    sensorToBuffer: Matrix
): AffineTransform2D? {
    val bufferToSensor = Matrix()
    if (!sensorToBuffer.invert(bufferToSensor)) return null
    val bufferToView = Matrix().apply {
        // Applying this matrix maps buffer -> sensor first, then sensor -> preview view.
        setConcat(sensorToView, bufferToSensor)
    }
    val values = FloatArray(9)
    bufferToView.getValues(values)
    if (values[Matrix.MPERSP_0] != 0f || values[Matrix.MPERSP_1] != 0f ||
        values[Matrix.MPERSP_2] != 1f) return null
    return AffineTransform2D(
        scaleX = values[Matrix.MSCALE_X],
        skewX = values[Matrix.MSKEW_X],
        translateX = values[Matrix.MTRANS_X],
        skewY = values[Matrix.MSKEW_Y],
        scaleY = values[Matrix.MSCALE_Y],
        translateY = values[Matrix.MTRANS_Y]
    )
}

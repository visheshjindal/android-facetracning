package com.xim.facetracking.infrastructure.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.xim.facetracking.domain.CameraFailure
import com.xim.facetracking.domain.CameraProblem
import com.xim.facetracking.domain.CaptureClock
import com.xim.facetracking.domain.FaceTrackingPort
import com.xim.facetracking.domain.TrackingObservation
import com.xim.facetracking.infrastructure.analysis.MlKitFaceAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

/** Main-thread camera ownership. The preview bridge attaches a view; the port controls monitoring. */
class CameraXTrackingSession(context: Context, private val clock: CaptureClock) : FaceTrackingPort {
    private val appContext = context.applicationContext
    private val samples = Channel<TrackingObservation>(Channel.CONFLATED)
    private val failures = Channel<CameraProblem>(Channel.UNLIMITED)
    override val observations = samples.receiveAsFlow()
    override val problems = failures.receiveAsFlow()
    private var preview: PreviewView? = null
    private var owner: LifecycleOwner? = null
    private var controller: LifecycleCameraController? = null
    private var analyzer: MlKitFaceAnalyzer? = null
    private var executor: java.util.concurrent.ExecutorService? = null
    private var requestedSession: Long? = null
    private var generation = 0L
    private var lastArrivalMs = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchdog: Job? = null
    fun attach(view: PreviewView, lifecycleOwner: LifecycleOwner) {
        if (preview === view) return
        releaseCamera()
        preview = view
        owner = lifecycleOwner
        bindIfPossible()
    }

    fun detach(view: PreviewView) {
        if (preview !== view) return
        releaseCamera()
        preview = null
        owner = null
    }

    override fun start(sessionId: Long) {
        releaseCamera()
        requestedSession = sessionId
        bindIfPossible()
    }

    override fun stop() {
        requestedSession = null
        releaseCamera()
    }

    private fun bindIfPossible() {
        val view = preview ?: return
        val lifecycle = owner ?: return
        val id = requestedSession ?: return
        if (controller != null) return
        val token = ++generation
        try {
            val camera = LifecycleCameraController(appContext).apply {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                isPinchToZoomEnabled = false
            }
            controller = camera
            val worker = Executors.newSingleThreadExecutor()
            executor = worker
            val main = ContextCompat.getMainExecutor(appContext)
            val detector = MlKitFaceAnalyzer(
                id,
                clock,
                main,
                { view.width to view.height },
                { observation ->
                    if (token == generation && requestedSession == id) {
                        lastArrivalMs = clock.monotonicMs()
                        samples.trySend(observation)
                    }
                },
                { fail(id, token, CameraFailure.DETECTOR_UNAVAILABLE) }
            )
            analyzer = detector
            camera.setImageAnalysisAnalyzer(worker, detector)
            view.controller = camera
            camera.bindToLifecycle(lifecycle)
            camera.initializationFuture.addListener({
                if (token == generation) {
                    try {
                        camera.initializationFuture.get()
                        if (!camera.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) fail(
                            id,
                            token,
                            CameraFailure.UNAVAILABLE
                        )
                    } catch (_: Exception) {
                        fail(id, token, CameraFailure.UNAVAILABLE)
                    }
                }
            }, main)
            lastArrivalMs = clock.monotonicMs()

            watchdog = scope.launch {
                while (isActive) {
                    delay(WATCHDOG_INTERVAL_MS.milliseconds)
                    Log.w(TAG, "analysis stalled; check updateTransform delivery and detector state")
                    val now = clock.monotonicMs()
                    if (now - lastArrivalMs > STALL_MS) {
                        fail(id, token, CameraFailure.DETECTOR_UNAVAILABLE)
                    }
                    return@launch
                }
            }

        } catch (_: SecurityException) {
            fail(id, token, CameraFailure.PERMISSION_DENIED)
        } catch (_: Exception) {
            fail(id, token, CameraFailure.UNAVAILABLE)
        }
    }

    private fun fail(id: Long, token: Long, reason: CameraFailure) {
        if (generation != token) return
        failures.trySend(CameraProblem(id, reason))
        releaseCamera()
    }

    private fun releaseCamera() {
        generation++ // Invalidates callbacks before resource teardown.
        watchdog?.cancel()
        watchdog = null
        controller?.clearImageAnalysisAnalyzer()
        controller?.unbind()
        preview?.controller = null
        controller = null
        analyzer?.close()
        analyzer = null
        executor?.shutdown()
        executor = null
    }

    private companion object {
        const val TAG = "CameraXTrackingSession"
        const val WATCHDOG_INTERVAL_MS = 250L
        const val STALL_MS = 5_000L
    }
}

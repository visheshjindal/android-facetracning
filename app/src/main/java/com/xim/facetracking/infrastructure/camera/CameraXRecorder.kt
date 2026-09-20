package com.xim.facetracking.infrastructure.camera

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import com.xim.facetracking.domain.*
import com.xim.facetracking.infrastructure.storage.TemporaryCaptureStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Adapter for a future video-enabled camera session. The positioning session never constructs it. */
class CameraXRecorder(
    context: Context,
    private val controller: LifecycleCameraController,
    private val store: TemporaryCaptureStore,
    private val spec: CaptureSpec,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher
) : RecordingPort {
    private val main = ContextCompat.getMainExecutor(context.applicationContext)
    private val output = Channel<RecordingEvent>(Channel.UNLIMITED)
    override val events = output.receiveAsFlow()
    private data class Attempt(val sessionId: String, val id: ArtifactId, var cancelled: Boolean = false, var recording: Recording? = null)
    private var active: Attempt? = null

    override fun start(sessionId: String) {
        if (active != null || !controller.isVideoCaptureEnabled) {
            output.trySend(RecordingEvent.Failed(sessionId, RecordingFailure.UNAVAILABLE))
            return
        }
        val (id, file) = store.allocate()
        val attempt = Attempt(sessionId, id)
        active = attempt
        try {
            val options = FileOutputOptions.Builder(file).apply { setDurationLimitMillis(spec.durationMs) }.build()
            attempt.recording = controller.startRecording(options, AudioConfig.AUDIO_DISABLED, main) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> output.trySend(RecordingEvent.Started(sessionId))
                    is VideoRecordEvent.Status -> output.trySend(RecordingEvent.Progress(sessionId, event.recordingStats.recordedDurationNanos / 1_000_000))
                    is VideoRecordEvent.Finalize -> {
                        if (active === attempt) active = null
                        scope.launch {
                            val error = when {
                                attempt.cancelled -> RecordingFailure.INTERRUPTED
                                event.error == VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> RecordingFailure.INSUFFICIENT_STORAGE
                                event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> RecordingFailure.INTERRUPTED
                                event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> RecordingFailure.ENCODING_FAILED
                                else -> null
                            }
                            val artifact = if (error == null) withContext(io) { readArtifact(attempt) } else null
                            if (artifact == null) {
                                store.delete(id)
                                output.send(RecordingEvent.Failed(sessionId, error ?: RecordingFailure.ENCODING_FAILED))
                            } else output.send(RecordingEvent.Finalized(sessionId, artifact))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            active = null
            scope.launch {
                store.delete(id)
                output.send(RecordingEvent.Failed(sessionId, RecordingFailure.UNAVAILABLE))
            }
        }
    }

    override fun cancel() {
        active?.let { it.cancelled = true; it.recording?.stop() }
    }

    private fun readArtifact(attempt: Attempt): VideoArtifact? {
        val metadata = MediaMetadataRetriever()
        return try {
            metadata.setDataSource(store.resolve(attempt.id).absolutePath)
            val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            VideoArtifact(attempt.id, duration, width, height, sessionId = attempt.sessionId,
                valid = duration in spec.acceptedDurationMinMs..spec.acceptedDurationMaxMs)
        } catch (_: Exception) { null } finally { metadata.release() }
    }
}

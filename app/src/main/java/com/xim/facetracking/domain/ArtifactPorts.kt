package com.xim.facetracking.domain

import kotlinx.coroutines.flow.Flow

@JvmInline
value class ArtifactId(val value: String)

interface ArtifactStore {
    suspend fun exists(id: ArtifactId): Boolean
    suspend fun delete(id: ArtifactId)
    suspend fun cleanupExpired()
}

enum class RecordingFailure { UNAVAILABLE, INSUFFICIENT_STORAGE, INTERRUPTED, ENCODING_FAILED }
sealed interface RecordingEvent {
    val sessionId: String
    data class Started(override val sessionId: String) : RecordingEvent
    data class Progress(override val sessionId: String, val elapsedMs: Long) : RecordingEvent
    data class Finalized(override val sessionId: String, val artifact: VideoArtifact) : RecordingEvent
    data class Failed(override val sessionId: String, val failure: RecordingFailure) : RecordingEvent
}

/** Reserved for the later recording milestone; no recording command is exposed by today's UI. */
interface RecordingPort {
    val events: Flow<RecordingEvent>
    fun start(sessionId: String)
    fun cancel()
}

data class SubmissionRequest(val artifact: VideoArtifact, val report: CaptureReport, val idempotencyKey: String)
sealed interface SubmissionEvent {
    data class Progress(val percent: Int) : SubmissionEvent
    data class Acknowledged(val simulated: Boolean) : SubmissionEvent
    data object RetryableFailure : SubmissionEvent
    data object Rejected : SubmissionEvent
}

interface SubmissionPort {
    fun submit(request: SubmissionRequest): Flow<SubmissionEvent>
}

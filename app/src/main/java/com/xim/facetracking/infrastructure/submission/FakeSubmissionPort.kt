package com.xim.facetracking.infrastructure.submission

import com.xim.facetracking.domain.SubmissionEvent
import com.xim.facetracking.domain.SubmissionPort
import com.xim.facetracking.domain.SubmissionRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeSubmissionPort(private val mode: Mode = Mode.SUCCESS) : SubmissionPort {
    enum class Mode { SUCCESS, NETWORK_FAILURE, REJECTION, DELAYED }
    override fun submit(request: SubmissionRequest): Flow<SubmissionEvent> = flow {
        require(request.idempotencyKey.isNotBlank())
        if (!request.artifact.valid || !request.report.accepted) {
            emit(SubmissionEvent.Rejected)
            return@flow
        }
        for (progress in listOf(15, 65, 100)) {
            if (mode == Mode.DELAYED) delay(500)
            emit(SubmissionEvent.Progress(progress))
        }
        emit(when (mode) {
            Mode.NETWORK_FAILURE -> SubmissionEvent.RetryableFailure
            Mode.REJECTION -> SubmissionEvent.Rejected
            else -> SubmissionEvent.Acknowledged(simulated = true)
        })
    }
}

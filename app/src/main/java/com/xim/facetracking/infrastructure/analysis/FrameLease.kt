package com.xim.facetracking.infrastructure.analysis

import java.util.concurrent.atomic.AtomicBoolean

/** Ensures completion, failure and cancellation paths can release the same frame safely. */
internal class FrameLease(private val release: () -> Unit) : AutoCloseable {
    private val released = AtomicBoolean(false)
    override fun close() { if (released.compareAndSet(false, true)) release() }
}

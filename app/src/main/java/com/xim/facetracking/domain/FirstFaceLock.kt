package com.xim.facetracking.domain

/** Session-owned track selection. Called serially with fresh, usable observations only. */
class FirstFaceLock {
    private var selectedId: Int? = null
    private var selectedGeneration: Long? = null
    private var lastTimestampMs: Long? = null

    /**
     * Returns the selected detection's index, never a replacement track.
     * Detector IDs are local to a detector lifetime, so a recreated detector cannot
     * reuse an old ID to take over the selection. Start a new session to select again.
     * For simultaneous initial detections, detector order breaks the tie.
     */
    fun select(generation: Long, timestampMs: Long, ids: List<Int?>): Int? {
        if (lastTimestampMs?.let { timestampMs <= it } == true) return null
        lastTimestampMs = timestampMs
        if (selectedId == null) {
            // An unidentified first detection must not let a later face take the lock.
            val first = ids.firstOrNull() ?: return null
            selectedId = first
            selectedGeneration = generation
        }
        if (generation != selectedGeneration) return null
        return ids.indexOfFirst { it == selectedId }.takeIf { it >= 0 }
    }
}

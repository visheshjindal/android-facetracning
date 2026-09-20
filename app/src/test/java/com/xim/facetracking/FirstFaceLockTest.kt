package com.xim.facetracking

import com.xim.facetracking.domain.FirstFaceLock
import org.junit.Assert.*
import org.junit.Test

class FirstFaceLockTest {
    @Test fun firstFaceRemainsSelectedWhenOtherFacesAppearOrReorder() {
        val lock = FirstFaceLock()
        assertNull(lock.select(1, 0, emptyList()))
        assertEquals(0, lock.select(1, 100, listOf(7, 8)))
        assertEquals(1, lock.select(1, 200, listOf(8, 7, 9)))
        assertEquals(2, lock.select(1, 300, listOf(9, 8, 7)))
    }

    @Test fun missingFaceNeverTransfersLockAndSameTrackCanReturn() {
        val lock = FirstFaceLock()
        lock.select(1, 0, listOf(7))
        assertNull(lock.select(1, 100, listOf(8)))
        assertNull(lock.select(1, 200, emptyList()))
        assertNull(lock.select(1, 300, listOf(null)))
        assertEquals(1, lock.select(1, 400, listOf(8, 7)))
    }

    @Test fun recreatedDetectorCannotReuseIdentityButNewSessionCanSelect() {
        val lock = FirstFaceLock()
        lock.select(1, 0, listOf(7))
        assertNull(lock.select(2, 100, listOf(7)))
        assertEquals(0, FirstFaceLock().select(2, 100, listOf(8)))
    }

    @Test fun unidentifiedFirstFaceDoesNotSelectAnotherDetection() {
        val lock = FirstFaceLock()
        assertNull(lock.select(1, 0, listOf(null, 8)))
        assertEquals(0, lock.select(1, 100, listOf(7, 8)))
    }

    @Test fun nonIncreasingTimestampsCannotAcquireOrUpdateSelection() {
        val lock = FirstFaceLock()
        assertNull(lock.select(1, 100, emptyList()))
        assertNull(lock.select(1, 90, listOf(8)))
        assertNull(lock.select(1, 100, listOf(8)))
        assertEquals(0, lock.select(1, 200, listOf(7)))
        assertNull(lock.select(1, 200, listOf(7)))
        assertEquals(1, lock.select(1, 300, listOf(8, 7)))
    }
}

package com.xim.facetracking.infrastructure

import android.os.SystemClock
import com.xim.facetracking.domain.CaptureClock

class AndroidCaptureClock : CaptureClock {
    override fun monotonicMs(): Long = SystemClock.elapsedRealtime()
    override fun wallTimeMs(): Long = System.currentTimeMillis()
}

package com.xim.facetracking.domain

/** Versioned provisional guidance profile; no empirical calibration is claimed. */
object CaptureSpec {
    const val LIGHTING_PROFILE_VERSION = "provisional-v2"
    val lighting = LightingConfig()
}

package com.xim.facetracking

import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xim.facetracking.domain.CaptureGuidance
import com.xim.facetracking.domain.LightingAssessment
import com.xim.facetracking.domain.PositioningFace
import com.xim.facetracking.presentation.CaptureScreen
import com.xim.facetracking.presentation.CaptureUiState
import com.xim.facetracking.presentation.LightingIndicatorUiState
import com.xim.facetracking.presentation.theme.FacetrackingTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class CaptureScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun keepTestActivityVisible() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
    }

    private fun captureScreenshot(fileName: String) {
        compose.waitForIdle()
        Thread.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/$fileName"
        )
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }

        // Also save bitmap directly to cache directory as backup
        val bitmap: Bitmap? = instrumentation.uiAutomation.takeScreenshot()
        if (bitmap != null) {
            val cacheFile = File(instrumentation.targetContext.cacheDir, fileName)
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    @Test
    fun captureAlignmentScreenshot() {
        compose.setContent {
            FacetrackingTheme {
                CaptureScreen(
                    state = CaptureUiState(
                        permissionGranted = true,
                        showPositioningMask = true,
                        hint = CaptureGuidance.PLACE_FACE,
                        lightingIndicator = LightingIndicatorUiState(
                            visible = true,
                            assessment = LightingAssessment.ACCEPTABLE,
                            expanded = false
                        )
                    ),
                    onAction = {},
                    requestPermission = {},
                    openSettings = {},
                    exit = {},
                    preview = { Box(it.background(Color(0xFF141416))) }
                )
            }
        }
        captureScreenshot("alignment.png")
    }

    @Test
    fun captureLiveTrackingScreenshot() {
        compose.setContent {
            FacetrackingTheme {
                CaptureScreen(
                    state = CaptureUiState(
                        permissionGranted = true,
                        showPositioningMask = false,
                        showReturnGuide = true,
                        hint = CaptureGuidance.FOLLOWING,
                        trackedFace = PositioningFace(
                            x = 0.5f,
                            y = 0.46f,
                            width = 0.36f,
                            height = 0.46f,
                            yaw = 0f,
                            pitch = 0f,
                            roll = 0f
                        ),
                        lightingIndicator = LightingIndicatorUiState(
                            visible = true,
                            assessment = LightingAssessment.ACCEPTABLE,
                            expanded = false
                        )
                    ),
                    onAction = {},
                    requestPermission = {},
                    openSettings = {},
                    exit = {},
                    preview = { Box(it.background(Color(0xFF141416))) }
                )
            }
        }
        captureScreenshot("live-tracking.png")
    }

    @Test
    fun captureLightingGuidanceScreenshot() {
        compose.setContent {
            FacetrackingTheme {
                CaptureScreen(
                    state = CaptureUiState(
                        permissionGranted = true,
                        showPositioningMask = false,
                        showReturnGuide = true,
                        hint = CaptureGuidance.SOFTEN_LIGHT,
                        trackedFace = PositioningFace(
                            x = 0.5f,
                            y = 0.46f,
                            width = 0.36f,
                            height = 0.46f,
                            yaw = 0f,
                            pitch = 0f,
                            roll = 0f
                        ),
                        lightingIndicator = LightingIndicatorUiState(
                            visible = true,
                            assessment = LightingAssessment.HIGH_CONTRAST,
                            expanded = true
                        )
                    ),
                    onAction = {},
                    requestPermission = {},
                    openSettings = {},
                    exit = {},
                    preview = { Box(it.background(Color(0xFF141416))) }
                )
            }
        }
        captureScreenshot("lighting-guidance.png")
    }

    @Test
    fun captureTrackingLostScreenshot() {
        compose.setContent {
            FacetrackingTheme {
                CaptureScreen(
                    state = CaptureUiState(
                        permissionGranted = true,
                        showPositioningMask = false,
                        showReturnGuide = true,
                        hint = CaptureGuidance.TRACKING_LOST,
                        lightingIndicator = LightingIndicatorUiState(visible = false)
                    ),
                    onAction = {},
                    requestPermission = {},
                    openSettings = {},
                    exit = {},
                    preview = { Box(it.background(Color(0xFF141416))) }
                )
            }
        }
        captureScreenshot("tracking-lost.png")
    }
}

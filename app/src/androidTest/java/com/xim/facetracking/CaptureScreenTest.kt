package com.xim.facetracking

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xim.facetracking.domain.CaptureGuidance
import com.xim.facetracking.domain.LightingAssessment
import com.xim.facetracking.domain.ShadowSide
import com.xim.facetracking.domain.PositioningFace
import com.xim.facetracking.presentation.CaptureIntent
import com.xim.facetracking.presentation.CaptureScreen
import com.xim.facetracking.presentation.CaptureUiState
import com.xim.facetracking.presentation.LightingIndicatorUiState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CaptureScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun keepTestActivityVisible() {
        // A sleeping/locked device stops the host and removes its Compose semantics roots.
        // Scope these flags to the test activity; do not change device settings or the app.
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

    @Test fun permissionButtonEmitsRequestWithoutCameraDependency() {
        var requests = 0
        compose.setContent {
            MaterialTheme {
                CaptureScreen(CaptureUiState(), {}, { requests++ }, {}, {}, { Box(it) })
            }
        }
        compose.onNodeWithText("Grant camera access").performClick()
        assertEquals(1, requests)
    }

    @Test fun trackingLossKeepsReturnGuideAndOffersRestart() {
        val actions = mutableListOf<CaptureIntent>()
        compose.setContent {
            MaterialTheme {
                CaptureScreen(CaptureUiState(permissionGranted = true, showPositioningMask = false,
                    showReturnGuide = true, hint = CaptureGuidance.TRACKING_LOST),
                    actions::add, {}, {}, {}, { Box(it) })
            }
        }
        compose.onNodeWithContentDescription("Face return guide").assertIsDisplayed()
        compose.onNodeWithText("Tracking lost — return your face to the outline").assertIsDisplayed()
        compose.onNodeWithText("Restart tracking").performClick()
        assertEquals(listOf(CaptureIntent.Retry), actions.filterIsInstance<CaptureIntent.Retry>())
    }

    @Test fun rendersPositioningAndTrackingFromImmutableState() {
        val state = mutableStateOf(CaptureUiState(permissionGranted = true))
        compose.setContent {
            MaterialTheme { CaptureScreen(state.value, {}, {}, {}, {}, { Box(it) }) }
        }
        compose.onNodeWithText("Place your face inside the oval").assertIsDisplayed()
        compose.runOnIdle {
            state.value = state.value.copy(
                showPositioningMask = false,
                showReturnGuide = true,
                hint = CaptureGuidance.FOLLOWING
            )
        }
        compose.onNodeWithContentDescription("Face return guide").assertIsDisplayed()
        compose.onNodeWithText("Face positioned — following your face").assertIsDisplayed()
    }

    @Test fun rendersCorrectiveGuidanceWhileTrackingMaskRemainsHidden() {
        val trackedFace = PositioningFace(.3f, .5f, .3f, .4f, 0f, 0f, 0f)
        val state = CaptureUiState(
            permissionGranted = true,
            showPositioningMask = false,
            showReturnGuide = true,
            hint = CaptureGuidance.MOVE_RIGHT,
            trackedFace = trackedFace
        )

        compose.setContent {
            MaterialTheme { CaptureScreen(state, {}, {}, {}, {}, { Box(it) }) }
        }

        compose.onNodeWithText("Move right").assertIsDisplayed()
        compose.onNodeWithText("Restart tracking").assertDoesNotExist()
    }

    @Test fun lightingIndicatorRendersAdaptiveStates() {
        val state = mutableStateOf(CaptureUiState(
            permissionGranted = true,
            lightingIndicator = LightingIndicatorUiState(
                visible = true,
                assessment = LightingAssessment.UNKNOWN,
                expanded = false
            )
        ))
        compose.setContent {
            MaterialTheme { CaptureScreen(state.value, {}, {}, {}, {}, { Box(it) }) }
        }
        compose.onNodeWithContentDescription("Checking lighting").assertIsDisplayed()
        compose.onNodeWithText("Checking lighting").assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(
                hint = CaptureGuidance.MORE_LIGHT,
                lightingIndicator = LightingIndicatorUiState(
                    visible = true,
                    assessment = LightingAssessment.TOO_DARK,
                    expanded = true
                )
            )
        }
        compose.onAllNodesWithText("Face a light source").assertCountEquals(2)

        compose.runOnIdle {
            state.value = state.value.copy(
                hint = CaptureGuidance.FOLLOWING,
                lightingIndicator = LightingIndicatorUiState(
                    visible = true,
                    assessment = LightingAssessment.ACCEPTABLE,
                    expanded = false
                )
            )
        }
        compose.onNodeWithContentDescription("Lighting looks okay").assertIsDisplayed()
    }

    @Test fun verticalAndMixedLightingShareAnAccessibleSoftLightIndicator() {
        val state = mutableStateOf(CaptureUiState(
            permissionGranted = true,
            hint = CaptureGuidance.SOFTEN_LIGHT,
            lightingIndicator = LightingIndicatorUiState(
                visible = true, assessment = LightingAssessment.UNEVEN, expanded = true
            )
        ))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme { CaptureScreen(state.value, {}, {}, {}, {}, { Box(it) }) }
            }
        }
        compose.onNodeWithContentDescription("Face a soft, even light source").assertIsDisplayed()
        compose.onAllNodesWithText("Face a soft, even light source").assertCountEquals(2)
        compose.runOnIdle {
            state.value = state.value.copy(lightingIndicator = state.value.lightingIndicator.copy(
                assessment = LightingAssessment.HIGH_CONTRAST
            ))
        }
        compose.onNodeWithContentDescription("Face a soft, even light source").assertIsDisplayed()
        compose.onAllNodesWithText("Face a soft, even light source").assertCountEquals(2)
    }

    @Test fun directionalLightingGuidanceFitsAtLargeFontScale() {
        val state = CaptureUiState(
            permissionGranted = true,
            hint = CaptureGuidance.LIGHT_USER_LEFT,
            lightingIndicator = LightingIndicatorUiState(
                visible = true,
                assessment = LightingAssessment.UNEVEN,
                shadowSide = ShadowSide.USER_LEFT,
                expanded = true
            )
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme { CaptureScreen(state, {}, {}, {}, {}, { Box(it) }) }
            }
        }
        compose.onAllNodesWithText("Add light to the left side of your face").assertCountEquals(2)
    }
}

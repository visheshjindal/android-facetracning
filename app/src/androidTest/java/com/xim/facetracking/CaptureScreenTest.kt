package com.xim.facetracking

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xim.facetracking.domain.PositioningHint
import com.xim.facetracking.presentation.CaptureScreen
import com.xim.facetracking.presentation.CaptureUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CaptureScreenTest {
    @get:Rule val compose = createComposeRule()

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

    @Test fun rendersPositioningAndTrackingFromImmutableState() {
        val state = mutableStateOf(CaptureUiState(permissionGranted = true))
        compose.setContent {
            MaterialTheme { CaptureScreen(state.value, {}, {}, {}, {}, { Box(it) }) }
        }
        compose.onNodeWithText("Place your face inside the oval").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(showPositioningMask = false, hint = PositioningHint.FOLLOWING) }
        compose.onNodeWithText("Face positioned — following your face").assertIsDisplayed()
    }
}

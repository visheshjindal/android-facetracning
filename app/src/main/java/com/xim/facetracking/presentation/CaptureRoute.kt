package com.xim.facetracking.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CaptureRoute(
    viewModel: CaptureViewModel,
    previewHost: PreviewHost,
    modifier: Modifier = Modifier,
    onExit: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val effectiveExit: () -> Unit = onExit ?: {
        if (context is Activity) {
            context.finish()
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onAction(CaptureIntent.PermissionResult(it))
        }

    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(
            CaptureIntent.PermissionResult(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        )
        viewModel.onAction(CaptureIntent.Resumed)
        onPauseOrDispose {
            viewModel.onAction(CaptureIntent.Stopped)
        }
    }

    CaptureScreen(
        state = state,
        onAction = viewModel::onAction,
        requestPermission = { permission.launch(Manifest.permission.CAMERA) },
        openSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri()
                )
            )
        },
        exit = effectiveExit,
        preview = { previewModifier -> CameraPreviewHost(previewHost, previewModifier) },
        modifier = modifier
    )
}

@Composable
private fun CameraPreviewHost(
    host: PreviewHost,
    modifier: Modifier = Modifier
) {
    val owner = LocalLifecycleOwner.current
    AndroidView(
        factory = { context -> host.createView(context, owner) },
        modifier = modifier,
        onRelease = { view -> host.release(view) }
    )
}

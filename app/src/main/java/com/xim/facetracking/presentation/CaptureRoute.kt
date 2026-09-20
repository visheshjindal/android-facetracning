package com.xim.facetracking.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CaptureRoute(viewModel: CaptureViewModel, previewHost: PreviewHost) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onAction(CaptureIntent.PermissionResult(it))
        }
    DisposableEffect(owner, viewModel) {
        fun resume() {
            viewModel.onAction(
                CaptureIntent.PermissionResult(
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                )
            )
            viewModel.onAction(CaptureIntent.Resumed)
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_STOP -> viewModel.onAction(CaptureIntent.Stopped)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewModel.onAction(CaptureIntent.Stopped)
        }
    }
    CaptureScreen(
        state,
        viewModel::onAction,
        requestPermission = { permission.launch(Manifest.permission.CAMERA) },
        openSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri()
                )
            )
        },
        exit = { (context as? Activity)?.finish() },
        preview = { modifier -> CameraPreviewHost(previewHost, modifier) }
    )
}

@Composable
private fun CameraPreviewHost(host: PreviewHost, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(host, context, owner) { host.createView(context, owner) }
    DisposableEffect(host, view) { onDispose { host.release(view) } }
    AndroidView(factory = { view }, modifier = modifier)
}

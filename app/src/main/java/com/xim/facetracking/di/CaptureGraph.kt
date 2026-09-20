package com.xim.facetracking.di

import android.content.Context
import android.view.View
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xim.facetracking.domain.CaptureReducer
import com.xim.facetracking.domain.PositioningPolicy
import com.xim.facetracking.infrastructure.AndroidCaptureClock
import com.xim.facetracking.infrastructure.camera.CameraXTrackingSession
import com.xim.facetracking.presentation.CaptureViewModel
import com.xim.facetracking.presentation.PreviewHost

/** Retained composition owner contains no activity/view references while detached. */
class CaptureGraph(context: Context) : ViewModel() {
    private val tracking = CameraXTrackingSession(context.applicationContext, AndroidCaptureClock())
    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CaptureViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return CaptureViewModel(tracking, CaptureReducer(PositioningPolicy())) as T
        }
    }
    val previewHost: PreviewHost = object : PreviewHost {
        override fun createView(context: Context, owner: LifecycleOwner): View = PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            tracking.attach(this, owner)
        }
        override fun release(view: View) { tracking.detach(view as PreviewView) }
    }

    override fun onCleared() { tracking.stop() }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CaptureGraph::class.java)
            @Suppress("UNCHECKED_CAST")
            return CaptureGraph(context.applicationContext) as T
        }
    }
}

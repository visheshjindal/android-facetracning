package com.xim.facetracking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.xim.facetracking.di.CaptureGraph
import com.xim.facetracking.presentation.CaptureRoute
import com.xim.facetracking.presentation.CaptureViewModel
import com.xim.facetracking.presentation.theme.FacetrackingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = ViewModelProvider(this, CaptureGraph.Factory(applicationContext))[CaptureGraph::class.java]
        val viewModel = ViewModelProvider(this, graph.viewModelFactory)[CaptureViewModel::class.java]
        setContent {
            FacetrackingTheme {
                CaptureRoute(viewModel, graph.previewHost)
            }
        }
    }
}

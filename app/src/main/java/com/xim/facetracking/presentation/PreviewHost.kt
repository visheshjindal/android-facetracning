package com.xim.facetracking.presentation

import android.content.Context
import android.view.View
import androidx.lifecycle.LifecycleOwner

/** Platform attachment only. Camera ownership and analysis stay behind this bridge. */
interface PreviewHost {
    fun createView(context: Context, owner: LifecycleOwner): View
    fun release(view: View)
}

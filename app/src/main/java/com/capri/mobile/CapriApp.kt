package com.capri.mobile

import android.app.Application
import android.content.ComponentCallbacks2
import com.capri.mobile.core.CapriCore
import com.capri.mobile.ui.Sprites

/** Small app shell: warm the brain once, drop sprite bitmaps when Android is short on memory. */
class CapriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CapriCore.get(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) Sprites.trim()
    }
}

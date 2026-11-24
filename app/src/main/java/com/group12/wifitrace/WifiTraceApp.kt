package com.group12.wifitrace

import android.app.Application

class WifiTraceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(this)
    }
}

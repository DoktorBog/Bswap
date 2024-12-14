package com.bswap.app

import android.app.Application

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeAppContext(this)
    }
}
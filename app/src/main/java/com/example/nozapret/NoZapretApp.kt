package com.example.nozapret

import android.app.Application
import com.example.nozapret.core.AppLogger

class NoZapretApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("CRASH", "Uncaught exception in thread ${thread.name}", throwable)
            // Default handler will show the crash dialog
        }
    }
}

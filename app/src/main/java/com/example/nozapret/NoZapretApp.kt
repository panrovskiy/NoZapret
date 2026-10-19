package com.example.nozapret

import android.app.Application
import com.example.nozapret.core.AppLogger

class NoZapretApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.fatal("CRASH", "Fatal uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

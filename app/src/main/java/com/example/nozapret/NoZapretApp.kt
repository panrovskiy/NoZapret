package com.example.nozapret

import android.app.Application
import com.example.nozapret.core.AppLogger
import com.example.nozapret.core.ByeDpiProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NoZapretApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        
        // Asynchronously pre-warm native library in background for faster app launch
        CoroutineScope(Dispatchers.IO).launch {
            ByeDpiProxy.loadLibrary()
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.fatal("CRASH", "Fatal uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

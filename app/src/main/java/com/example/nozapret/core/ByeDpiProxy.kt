package com.example.nozapret.core

import android.util.Log

class ByeDpiProxy {
    
    companion object {
        private var isLoaded = false
        fun loadLibrary() {
            if (!isLoaded) {
                try {
                    System.loadLibrary("byedpi")
                    isLoaded = true
                } catch (e: UnsatisfiedLinkError) {
                    Log.e("ByeDpiProxy", "Native library byedpi not found: ${e.message}")
                }
            }
        }
    }

    init {
        loadLibrary()
    }

    private object Lock

    fun start(args: Array<String>): Int {
        Log.d("ByeDpiProxy", "Starting proxy with args: ${args.joinToString(" ")}")
        return if (isLoaded) {
            synchronized(Lock) {
                try {
                    jniStartProxy(args)
                } catch (e: Exception) {
                    Log.e("ByeDpiProxy", "Exception in jniStartProxy", e)
                    -1
                }
            }
        } else {
            -1
        }
    }

    fun stop() {
        Log.d("ByeDpiProxy", "Stopping proxy")
        if (isLoaded) {
            jniStopProxy()
        }
    }
    
    fun forceClose(): Int {
        Log.d("ByeDpiProxy", "Force closing proxy")
        return if (isLoaded) {
            jniForceClose()
        } else {
            -1
        }
    }

    private external fun jniStartProxy(args: Array<String>): Int
    private external fun jniStopProxy()
    private external fun jniForceClose(): Int
}

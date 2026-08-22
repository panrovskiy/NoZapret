package com.example.nozapret.core

import android.util.Log

class HevSocks5Tunnel {
    companion object {
        private var isLoaded = false
        fun loadLibrary() {
            if (!isLoaded) {
                try {
                    // We consolidated everything into the 'byedpi' shared library in CMakeLists.txt
                    System.loadLibrary("byedpi")
                    isLoaded = true
                } catch (e: UnsatisfiedLinkError) {
                    Log.e("HevSocks5Tunnel", "Native library byedpi (with TProxy) not found: ${e.message}")
                }
            }
        }
    }

    init {
        loadLibrary()
    }


    fun start(configPath: String, fd: Int): Int {
        return if (isLoaded) {
            TProxyStartService(configPath, fd)
        } else {
            -1
        }
    }

    fun stop() {
        if (isLoaded) {
            TProxyStopService()
        }
    }

    private external fun TProxyStartService(configPath: String, fd: Int): Int
    private external fun TProxyStopService()
    private external fun TProxyGetStats(): LongArray
}

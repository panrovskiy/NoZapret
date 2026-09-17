package com.example.nozapret.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.nozapret.services.DpiVpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Singleton controller for VPN operations.
 * Centralizes starting/stopping and status querying.
 */
object VpnController {
    private val TAG = "VpnController"
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mutex = Mutex()

    val stateFlow: StateFlow<DpiVpnService.VpnState> = DpiVpnService.vpnStateFlow

    fun startVpn(context: Context, strategy: String, args: String, global: Boolean, bypassedSites: List<String>) {
        controllerScope.launch {
            mutex.withLock {
                Log.d(TAG, "Starting VPN: strategy=$strategy")
                val intent = Intent(context, DpiVpnService::class.java).apply {
                    action = DpiVpnService.ACTION_START
                    putExtra("strategy", strategy)
                    putExtra("args", args)
                    putExtra("global", global)
                    putStringArrayListExtra("bypassedSites", ArrayList(bypassedSites))
                }
                context.startForegroundService(intent)
            }
        }
    }

    fun stopVpn(context: Context) {
        controllerScope.launch {
            mutex.withLock {
                Log.d(TAG, "Stopping VPN")
                val intent = Intent(context, DpiVpnService::class.java).apply {
                    action = DpiVpnService.ACTION_STOP
                }
                context.startService(intent)
            }
        }
    }

    fun pauseVpn(context: Context) {
        val intent = Intent(context, DpiVpnService::class.java).apply {
            action = DpiVpnService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeVpn(context: Context) {
        val intent = Intent(context, DpiVpnService::class.java).apply {
            action = DpiVpnService.ACTION_RESUME
        }
        context.startService(intent)
    }

    /**
     * Safely runs a task that requires the VPN to be stopped (like strategy testing).
     * @return true if successful
     */
    suspend fun runWithVpnStopped(context: Context, block: suspend () -> Unit): Boolean {
        return mutex.withLock {
            val wasRunning = DpiVpnService.isRunning
            if (wasRunning) {
                stopVpn(context)
                // Wait for stop
                var wait = 0
                while (DpiVpnService.isRunning && wait < 50) {
                    delay(100.milliseconds)
                    wait++
                }
                if (DpiVpnService.isRunning) {
                    Log.e(TAG, "Failed to stop VPN for task")
                    return@withLock false
                }
            }

            try {
                block()
            } finally {
                if (wasRunning) {
                    // We don't automatically restart here because we don't know the config,
                    // but usually the caller should handle it.
                }
            }
            true
        }
    }
}

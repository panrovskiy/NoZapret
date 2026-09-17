package com.example.nozapret.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.nozapret.services.DpiVpnService
import com.example.nozapret.services.TestingService
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
    private const val TAG = "VpnController"
    private val controllerJob = SupervisorJob()
    private val controllerScope = CoroutineScope(Dispatchers.Main + controllerJob)
    private val mutex = Mutex()

    val stateFlow: StateFlow<DpiVpnService.VpnState> = DpiVpnService.vpnStateFlow

    fun stopTests(context: Context) {
        Log.d(TAG, "[CLEANUP] Stopping all tests via VpnController")
        val intent = Intent(context, TestingService::class.java).apply {
            action = TestingService.ACTION_STOP_TEST
        }
        context.startService(intent)
    }

    suspend fun stopTestsAndWait(context: Context) = withContext(Dispatchers.Main) {
        if (!TestingService.isRunning) return@withContext
        
        Log.d(TAG, "[CLEANUP] Waiting for tests to stop...")
        stopTests(context)
        
        var wait = 0
        while (TestingService.isRunning && wait < 50) {
            delay(100.milliseconds)
            wait++
        }
        
        // Final port check
        val proxy = ByeDpiProxy()
        var nativeWait = 0
        while (proxy.isRunning() && nativeWait < 30) {
            delay(100.milliseconds)
            nativeWait++
        }
        Log.d(TAG, "[CLEANUP] Tests stopped, proxy released=${!proxy.isRunning()}")
    }

    fun startVpn(context: Context, strategy: String, args: String, global: Boolean, bypassedSites: List<String>) {
        controllerScope.launch {
            mutex.withLock {
                Log.d(TAG, "[VPN] Start requested: strategy=$strategy")
                
                // CRITICAL: Stop tests first if any are running
                stopTestsAndWait(context)
                
                val intent = Intent(context, DpiVpnService::class.java).apply {
                    action = DpiVpnService.ACTION_START
                    putExtra("strategy", strategy)
                    putExtra("args", args)
                    putExtra("global", global)
                    putStringArrayListExtra("bypassedSites", ArrayList(bypassedSites))
                }
                try {
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "[VPN] Failed to start VPN service", e)
                }
            }
        }
    }

    fun stopVpn(context: Context) {
        controllerScope.launch {
            mutex.withLock {
                Log.d(TAG, "[VPN] Stopping VPN")
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

    suspend fun runWithVpnStopped(context: Context, block: suspend () -> Unit): Boolean = withContext(Dispatchers.Main) {
        val wasRunning = DpiVpnService.isRunning || DpiVpnService.isConnecting || DpiVpnService.isDisconnecting
        if (wasRunning) {
            Log.d(TAG, "[VPN] VPN is active, stopping for task...")
            stopVpn(context)
            var wait = 0
            while ((DpiVpnService.isRunning || DpiVpnService.isConnecting || DpiVpnService.isDisconnecting) && wait < 200) {
                delay(100.milliseconds)
                wait++
            }
            
            // Extra safety: check native proxy state
            val proxy = ByeDpiProxy()
            var nativeWait = 0
            while (proxy.isRunning() && nativeWait < 50) {
                delay(100.milliseconds)
                nativeWait++
            }

            if (DpiVpnService.isRunning || DpiVpnService.isConnecting || proxy.isRunning()) {
                Log.e(TAG, "[VPN] Failed to stop VPN or native proxy for task")
                return@withContext false
            }
        }

        return@withContext try {
            block()
            true
        } catch (e: Exception) {
            Log.e(TAG, "[TASK] Task failed", e)
            false
        } finally {
            Log.d(TAG, "[TASK] Task finished")
        }
    }
}

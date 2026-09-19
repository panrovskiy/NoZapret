package com.example.nozapret.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.nozapret.MainActivity
import com.example.nozapret.MainViewModel
import com.example.nozapret.R
import com.example.nozapret.core.StrategyTester
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TestingService : Service() {
    private val TAG = "TestingService"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private lateinit var strategyTester: StrategyTester
    private var currentTestJob: Job? = null

    companion object {
        const val ACTION_START_TEST = "ACTION_START_TEST"
        const val ACTION_START_TEST_BATCH = "ACTION_START_TEST_BATCH"
        const val ACTION_STOP_TEST = "ACTION_STOP_TEST"
        const val EXTRA_STRATEGY = "strategy"
        const val EXTRA_STRATEGIES = "strategies"
        const val EXTRA_CUSTOM_ARGS = "custom_args"
        const val EXTRA_SITES = "sites"

        data class TestStatus(
            val strategyName: String = "",
            val success: Int = 0,
            val tested: Int = 0,
            val total: Int = 0,
            val isRunning: Boolean = false,
            val lastSite: String = "",
            val lastResult: MainViewModel.TlsTestResult? = null
        )

        private val _statusFlow = MutableStateFlow(TestStatus())
        val statusFlow = _statusFlow.asStateFlow()
        
        @Volatile
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        strategyTester = StrategyTester(application)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TEST -> {
                val strategy = intent.getStringExtra(EXTRA_STRATEGY) ?: return START_NOT_STICKY
                val customArgs = intent.getStringExtra(EXTRA_CUSTOM_ARGS) ?: ""
                val sites = intent.getStringArrayListExtra(EXTRA_SITES) ?: arrayListOf()
                
                handleStartTest(strategy, customArgs, sites)
            }
            ACTION_START_TEST_BATCH -> {
                val strategies = intent.getStringArrayListExtra(EXTRA_STRATEGIES) ?: return START_NOT_STICKY
                val customArgs = intent.getStringExtra(EXTRA_CUSTOM_ARGS) ?: ""
                val sites = intent.getStringArrayListExtra(EXTRA_SITES) ?: arrayListOf()
                
                handleStartBatch(strategies, customArgs, sites)
            }
            ACTION_STOP_TEST -> {
                handleStopTest()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStartBatch(strategies: List<String>, customArgs: String, sites: List<String>) {
        if (isRunning && _statusFlow.value.isRunning) {
            Log.d(TAG, "[TEST] Already testing, skipping batch start")
            return
        }

        currentTestJob?.cancel()
        isRunning = true
        
        val notification = createNotification(getString(R.string.testing_starting_hint))
        startForegroundServiceCompat(notification)

        currentTestJob = serviceScope.launch {
            try {
                strategies.forEach { strategy ->
                    if (!isActive) return@forEach
                    _statusFlow.value = TestStatus(strategyName = strategy, total = sites.size, isRunning = true)
                    updateNotification(strategy, 0, 0, sites.size)
                    
                    strategyTester.testStrategy(
                        strategy, sites, customArgs,
                        onResult = { site, result, s, t, total ->
                            _statusFlow.value = _statusFlow.value.copy(
                                lastSite = site, 
                                lastResult = result,
                                success = s, 
                                tested = t, 
                                total = total
                            )
                            updateNotification(strategy, s, t, total)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during batch test", e)
            } finally {
                withContext(NonCancellable) {
                    isRunning = false
                    _statusFlow.value = _statusFlow.value.copy(isRunning = false)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
    }

    private fun handleStartTest(strategy: String, customArgs: String, sites: List<String>) {
        if (isRunning && _statusFlow.value.strategyName == strategy && _statusFlow.value.isRunning) return

        currentTestJob?.cancel()
        isRunning = true
        _statusFlow.value = TestStatus(strategyName = strategy, total = sites.size, isRunning = true)
        
        val notification = createNotification(getString(R.string.testing_starting_hint))
        startForegroundServiceCompat(notification)

        currentTestJob = serviceScope.launch {
            try {
                strategyTester.testStrategy(
                    strategy, sites, customArgs,
                    onResult = { site, result, s, t, total ->
                        _statusFlow.value = _statusFlow.value.copy(
                            lastSite = site, 
                            lastResult = result,
                            success = s, 
                            tested = t, 
                            total = total
                        )
                        updateNotification(strategy, s, t, total)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during test task", e)
            } finally {
                withContext(NonCancellable) {
                    val finalStatus = _statusFlow.value
                    if (finalStatus.strategyName == strategy) {
                        showFinishedNotification(strategy, finalStatus.success, finalStatus.total)
                        isRunning = false
                        _statusFlow.value = finalStatus.copy(isRunning = false)
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun startForegroundServiceCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(2, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun handleStopTest() {
        Log.d(TAG, "[CLEANUP] Stop test requested")
        currentTestJob?.cancel()
        isRunning = false
        _statusFlow.value = _statusFlow.value.copy(isRunning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "testing_channel", "Strategy Testing", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(content: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, TestingService::class.java).apply { action = ACTION_STOP_TEST }
        val stopPendingIntent = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "testing_channel")
            .setContentTitle("Testing Strategy")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.btn_stop), stopPendingIntent)
            .build()
    }

    private var lastNotificationUpdate = 0L

    private fun updateNotification(strategy: String, success: Int, tested: Int, total: Int) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdate < 1000 && tested < total) return // Throttle to 1s
        lastNotificationUpdate = now

        val content = getString(R.string.notification_testing_text, tested, total, success)
        val notification = NotificationCompat.Builder(this, "testing_channel")
            .setContentTitle(getString(R.string.notification_testing_title, strategy))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setOngoing(true)
            .setProgress(total, tested, false)
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(2, notification)
    }

    private fun showFinishedNotification(strategy: String, success: Int, total: Int) {
        val content = getString(R.string.notification_testing_complete_text, success, total)
        val notification = NotificationCompat.Builder(this, "testing_channel")
            .setContentTitle(getString(R.string.notification_testing_complete_title, strategy))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setAutoCancel(true)
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(3, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "[CLEANUP] TestingService onDestroy")
        currentTestJob?.cancel()
        serviceJob.cancel()
        isRunning = false
        _statusFlow.value = _statusFlow.value.copy(isRunning = false)
        super.onDestroy()
    }
}

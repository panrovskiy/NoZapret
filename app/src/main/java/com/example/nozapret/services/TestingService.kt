package com.example.nozapret.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.nozapret.MainActivity
import com.example.nozapret.MainViewModel
import com.example.nozapret.R
import com.example.nozapret.core.Config
import com.example.nozapret.core.StrategyTester
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TestingService : Service() {
    private val TAG = "TestingService"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private lateinit var strategyTester: StrategyTester
    private var testJob: Job? = null

    companion object {
        const val ACTION_START_TEST = "ACTION_START_TEST"
        const val ACTION_STOP_TEST = "ACTION_STOP_TEST"
        const val EXTRA_STRATEGY = "strategy"
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
            ACTION_STOP_TEST -> {
                handleStopTest()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStartTest(strategy: String, customArgs: String, sites: List<String>) {
        if (isRunning) {
            if (_statusFlow.value.strategyName == strategy) return
            handleStopTest()
        }

        isRunning = true
        _statusFlow.value = TestStatus(strategyName = strategy, total = sites.size, isRunning = true)
        
        startForeground(2, createNotification(getString(R.string.testing_starting_hint)))

        testJob = serviceScope.launch {
            Log.d(TAG, "Starting test service for $strategy")
            val success = strategyTester.testStrategy(
                strategy, sites, customArgs,
                onResult = { site, result ->
                    _statusFlow.value = _statusFlow.value.copy(lastSite = site, lastResult = result)
                },
                onProgress = { s, t, total ->
                    _statusFlow.value = _statusFlow.value.copy(success = s, tested = t, total = total)
                    updateNotification(strategy, s, t, total)
                }
            )
            
            Log.d(TAG, "Test service for $strategy finished: $success")
            
            val finalStatus = _statusFlow.value
            showFinishedNotification(strategy, finalStatus.success, finalStatus.total)
            
            isRunning = false
            _statusFlow.value = _statusFlow.value.copy(isRunning = false)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun handleStopTest() {
        testJob?.cancel()
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

    private fun updateNotification(strategy: String, success: Int, tested: Int, total: Int) {
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
        testJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }
}

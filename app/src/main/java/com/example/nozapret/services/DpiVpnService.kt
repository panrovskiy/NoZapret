package com.example.nozapret.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.nozapret.MainActivity
import com.example.nozapret.R
import com.example.nozapret.core.ByeDpiProxy
import com.example.nozapret.core.Config
import com.example.nozapret.core.HevSocks5Tunnel
import com.example.nozapret.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.milliseconds

@Suppress("VpnService")
class DpiVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val proxy = ByeDpiProxy()
    private val tunnel = HevSocks5Tunnel()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null


    companion object {
        const val ACTION_STOP = "com.example.nozapret.STOP"
        const val ACTION_VPN_STATE_CHANGED = "com.example.nozapret.VPN_STATE_CHANGED"
        const val ACTION_QUERY_STATUS = "com.example.nozapret.QUERY_STATUS"
        const val EXTRA_IS_RUNNING = "is_running"
        const val EXTRA_START_TIME = "start_time"
        var isRunning = false
            private set
        var startTime = 0L
            private set
        const val CHANNEL_ID = "vpn_channel"
        
        // Mutex to prevent concurrent start/stop and protect native resources
        private val vpnLock = kotlinx.coroutines.sync.Mutex()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_QUERY_STATUS) {
                sendStateBroadcast(isRunning)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(ACTION_QUERY_STATUS)
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val powerManager = getSystemService(PowerManager::class.java)!!
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NoZapret::VpnWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ALWAYS call startForeground first to satisfy the system requirement
        // for startForegroundService() and prevent crashes during rapid toggling.
        // We do this even for ACTION_STOP if the service isn't "isRunning" yet,
        // because a previous startForegroundService() call might still be pending.
        if ((intent?.action != ACTION_STOP) || !isRunning) {
            createNotificationChannel()
            val notification = createNotification()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(1, notification)
                }
            } catch (e: Exception) {
                Log.e("DpiVpnService", "Failed to start foreground", e)
            }
        }

        if (intent?.action == ACTION_STOP) {
            stopVpn("Action Stop")
            return START_NOT_STICKY
        }

        serviceScope.launch {
            vpnLock.withLock {
                if (vpnInterface != null) {
                    Log.d("DpiVpnService", "VPN already running")
                    sendStateBroadcast(running = true)
                    return@withLock
                }

                jniSetVpnService(this@DpiVpnService)

                startVpn()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val dataStoreManager = DataStoreManager(this)
        var activeStrategy = "Auto (Recommended)"
        try {
            activeStrategy = kotlinx.coroutines.runBlocking {
                dataStoreManager.getSetting(DataStoreManager.SELECTED_STRATEGY, "Auto (Recommended)").first()
            }
        } catch (_: Exception) {}

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(this, DpiVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.home_strategy, activeStrategy))
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.btn_stop_vpn), stopPendingIntent)
            .build()
    }

    private suspend fun startVpn() {
        try {
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes fallback
            // Ensure no existing proxy is running before starting a new one
            proxy.forceClose()
            delay(50.milliseconds)

            val dataStoreManager = DataStoreManager(this)
            
            // Fetch settings from DataStore
            val strategy = dataStoreManager.getSetting(DataStoreManager.SELECTED_STRATEGY, "Auto (Recommended)").first()
            val dns = dataStoreManager.getSetting(DataStoreManager.DNS_SERVER, "1.1.1.1").first()
            val host = dataStoreManager.getSetting(DataStoreManager.PROXY_HOST, Config.DEFAULT_PROXY_HOST).first()
            val portStr = dataStoreManager.getSetting(DataStoreManager.PROXY_PORT, Config.DEFAULT_PROXY_PORT).first()
            val port = portStr.toIntOrNull() ?: 1080
            val exclude = dataStoreManager.getSetting(DataStoreManager.EXCLUDE_SELF, defaultValue = true).first()
            val global = dataStoreManager.getSetting(DataStoreManager.GLOBAL_MODE, false).first()
            val apps = dataStoreManager.getSetting(DataStoreManager.ALLOWED_APPS, emptySet()).first()
            val presets = dataStoreManager.getSetting(DataStoreManager.SELECTED_PRESETS, setOf("YouTube", "Telegram")).first()
            val customHosts = dataStoreManager.getSetting(DataStoreManager.CUSTOM_HOST_LIST, "").first()
            val args = dataStoreManager.getSetting(DataStoreManager.CUSTOM_ARGS, "").first()
            val enableIpv6 = dataStoreManager.getSetting(DataStoreManager.ENABLE_IPV6, false).first()
            val runMode = dataStoreManager.getSetting(DataStoreManager.RUN_MODE, "VPN").first()
            
            val lists = Config.BYPASS_LISTS.toMap()
            val strategyArgs = Config.getStrategyArgs(strategy, args)
            
            val presetsMap = Config.PRESETS.toMap()
            val presetSites = presets.asSequence().filter { it != "Custom" }.flatMap { presetsMap[it] ?: emptyList() }.toList()
            val customSites = if (presets.contains("Custom") && customHosts.isNotBlank()) {
                customHosts.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
            } else {
                emptyList()
            }
            val bypassedSites = (presetSites + customSites).distinct()

            val establishedInterface = if (runMode == "VPN") {
                val builder = Builder()
                    .setSession("NoZapret")
                    .addAddress("10.0.0.1", 24)
                    .addDnsServer(dns)
                    .addRoute("0.0.0.0", 0)
                    .setMtu(1400)

                if (enableIpv6 || dns.contains(":")) {
                    builder.addAddress("fd00::1", 128)
                    builder.addRoute("::", 0)
                }

                if (apps.isNotEmpty()) {
                    apps.forEach { pkg ->
                        try {
                            builder.addDisallowedApplication(pkg)
                        } catch (e: Exception) {
                            Log.e("DpiVpnService", "Failed to add disallowed application: $pkg", e)
                        }
                    }
                    if (exclude && !apps.contains(packageName)) {
                        builder.addDisallowedApplication(packageName)
                    }
                } else if (exclude) {
                    builder.addDisallowedApplication(packageName)
                }
                builder.establish()
            } else {
                null
            }

            if ((runMode == "VPN") && (establishedInterface == null)) {
                Log.e("DpiVpnService", "Failed to establish VPN interface")
                isRunning = false
                sendStateBroadcast(running = false)
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
                stopSelf()
                return
            }
            vpnInterface = establishedInterface
            
            val fd = vpnInterface?.fd ?: -1

            val hostlistFile = File(cacheDir, "hostlist.txt")
            if (bypassedSites.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    hostlistFile.writeText(bypassedSites.joinToString("\n"))
                }
            }
            
            isRunning = true
            startTime = System.currentTimeMillis()
            sendStateBroadcast(running = true)
            
            vpnJob = serviceScope.launch(Dispatchers.IO) {
                Log.d("DpiVpnService", "Bypass Service started. Strategy: $strategy")
                
                val listArgs = lists["Russia Default"] ?: emptyArray()
                
                val finalArgs = mutableListOf(
                    "byedpi",
                    "-i", host,
                    "-p", port.toString(),
                    "-x", "2",
                )
                
                finalArgs.addAll(strategyArgs)
                
                if (!global) {
                    if (bypassedSites.isNotEmpty()) {
                        finalArgs.add("--hosts")
                        finalArgs.add(hostlistFile.absolutePath)
                    } else {
                        // If not global and no sites selected, desync nothing by using a dummy host
                        finalArgs.add("--hosts")
                        finalArgs.add(":none.internal")
                    }
                }
                
                finalArgs.addAll(listArgs)
                
                finalArgs.add("-P")
                finalArgs.add("protect") 
                
                // Add --transparent to better handle TProxy expectations if needed
                // finalArgs.add("--transparent")
                
                Log.d("DpiVpnService", "Starting ByeDPI with: ${finalArgs.joinToString(" ")}")

                launch {
                    val res = proxy.start(finalArgs.toTypedArray())
                    Log.d("DpiVpnService", "ByeDPI Proxy exited with code $res")
                    if (isRunning) stopVpn("Proxy exit")
                }
                
                launch {
                    if (!waitForProxy(host, port)) {
                        Log.e("DpiVpnService", "Proxy failed to start in time on $host:$port, aborting")
                        if (isRunning) stopVpn("Proxy timeout")
                        return@launch
                    }

                    if (runMode == "VPN") {
                        val configPath = createTunnelConfig(enableIpv6)
                        Log.d("DpiVpnService", "Starting tunnel with config: $configPath")
                        val res = tunnel.start(configPath, fd)
                        Log.d("DpiVpnService", "HevSocks5Tunnel exited with code $res")
                    } else {
                        Log.d("DpiVpnService", "Proxy mode only, keeping service alive")
                        while (isRunning) {
                            delay(1000.milliseconds)
                        }
                    }

                    if (isRunning) {
                        Log.d("DpiVpnService", "Native component returned unexpectedly, stopping VPN")
                        stopVpn("Native exit")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DpiVpnService", "Failed to start VPN", e)
            stopVpn("Start error: ${e.message}")
        }
    }

    private suspend fun createTunnelConfig(enableIpv6: Boolean): String {
        val dataStoreManager = DataStoreManager(this)
        val proxyHost = dataStoreManager.getSetting(DataStoreManager.PROXY_HOST, Config.DEFAULT_PROXY_HOST).first()
        val proxyPortStr = dataStoreManager.getSetting(DataStoreManager.PROXY_PORT, Config.DEFAULT_PROXY_PORT).first()
        val proxyPort = proxyPortStr.toIntOrNull() ?: 1080
        
        val configFile = File(cacheDir, "tunnel.yaml")
        val ipv6Config = if (enableIpv6) "ipv6: fd00::1" else ""
        val configContent = """
            socks5:
              port: $proxyPort
              address: $proxyHost
              udp: udp
              mark: 1
            tunnel:
              mtu: 1400
              ipv4: 10.0.0.1
              $ipv6Config
            misc:
              task-stack-size: 131072
              connect-timeout: 5000
              read-write-timeout: 60000
              udp-read-write-timeout: 10000
              max-session-count: 2048
              log-level: debug
        """.trimIndent()
        
        withContext(Dispatchers.IO) {
            FileOutputStream(configFile).use { it.write(configContent.toByteArray()) }
        }
        return configFile.absolutePath
    }

    private suspend fun waitForProxy(host: String, port: Int, timeoutMs: Long = 15000): Boolean {
        val start = System.currentTimeMillis()
        while ((System.currentTimeMillis() - start) < timeoutMs) {
            if (!isRunning) return false
            try {
                withContext(Dispatchers.IO) {
                    java.net.Socket().use { socket ->
                        // Try connecting WITHOUT protect first, then WITH protect if it fails
                        try {
                            socket.connect(java.net.InetSocketAddress(host, port), 500)
                            true
                        } catch (_: Exception) {
                            java.net.Socket().use { socket2 ->
                                try { protect(socket2) } catch (_: Exception) {}
                                socket2.connect(java.net.InetSocketAddress(host, port), 500)
                                true
                            }
                        }
                    }
                }
                Log.d("DpiVpnService", "Proxy is ready on $host:$port")
                return true
            } catch (_: Exception) {
                delay(200.milliseconds)
            }
        }
        return false
    }



    private fun stopVpn(reason: String = "Requested") {
        val stackTrace = Thread.currentThread().stackTrace.joinToString("\n") { it.toString() }
        Log.d("DpiVpnService", "Stopping VPN. Reason: $reason. Caller Stack:\n$stackTrace")
        serviceScope.launch {
            vpnLock.withLock {
                Log.d("DpiVpnService", "Stopping VPN. Reason: $reason")
                if (!isRunning && (vpnInterface == null)) {
                    Log.d("DpiVpnService", "VPN already stopped or stopping")
                    return@withLock
                }

                // 1. Immediately update state and remove notification for responsiveness
                isRunning = false
                sendStateBroadcast(running = false)
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }

                val localVpnInterface = vpnInterface
                vpnInterface = null

                val jobToCancel = vpnJob
                vpnJob = null

                // 2. Close the TUN interface immediately.
                try {
                    localVpnInterface?.close()
                } catch (e: Exception) {
                    Log.e("DpiVpnService", "Error closing vpnInterface", e)
                }

                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }

                try {
                    // 3. Stop native components
                    tunnel.stop()
                    proxy.forceClose()

                    // 4. Cancel the main job
                    jobToCancel?.cancelAndJoin()
                } catch (e: Exception) {
                    Log.d("DpiVpnService", "Cleanup Note: ${e.message}")
                } finally {
                    // 5. Always ensure the service is stopped
                    withContext(Dispatchers.Main) {
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onRevoke() {
        Log.d("DpiVpnService", "VPN revoked")
        stopVpn("Revoked")
        super.onRevoke()
    }

    override fun onDestroy() {
        Log.d("DpiVpnService", "Service onDestroy")
        jniSetVpnService(null)
        try {
            unregisterReceiver(statusReceiver)
        } catch (_: Exception) {}
        stopVpn("Destroyed")
        super.onDestroy()
    }

    private external fun jniSetVpnService(service: VpnService?)

    private fun sendStateBroadcast(running: Boolean) {
        getSharedPreferences("vpn_state", MODE_PRIVATE).edit {
            putBoolean("is_running", running)
            putLong("start_time", if (running) startTime else 0L)
        }
        val intent = Intent(ACTION_VPN_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_RUNNING, running)
            putExtra(EXTRA_START_TIME, if (running) startTime else 0L)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}

package com.example.nozapret.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.nozapret.MainActivity
import com.example.nozapret.R
import com.example.nozapret.core.ByeDpiProxy
import com.example.nozapret.core.Config
import com.example.nozapret.core.HevSocks5Tunnel
import com.example.nozapret.data.DataStoreManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

class DpiVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var vpnInterface: ParcelFileDescriptor? = null
    private val proxy = ByeDpiProxy()
    private val tunnel = HevSocks5Tunnel()
    private var vpnJob: Job? = null

    private val vpnLock = Mutex()

    private var lastStrategy: String? = null
    private var lastArgs: String? = null
    private var lastGlobal: Boolean? = null
    private var lastBypassedSites: ArrayList<String>? = null

    private val queryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_QUERY_STATUS) {
                sendStateBroadcast(isRunning)
            }
        }
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_VPN_STATE_CHANGED = "com.example.nozapret.VPN_STATE"
        const val EXTRA_IS_RUNNING = "running"
        const val EXTRA_IS_PAUSED = "paused"
        const val EXTRA_START_TIME = "start_time"
        const val ACTION_QUERY_STATUS = "com.example.nozapret.QUERY_STATUS"

        var isRunning = false
            private set
        var isPaused = false
            private set
        var startTime = 0L
            private set
    }

    private external fun jniSetVpnService(vpnService: VpnService?)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter(ACTION_QUERY_STATUS)
        ContextCompat.registerReceiver(this, queryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d("DpiVpnService", "onStartCommand: action=$action (original=${intent?.action})")
        
        when (action) {
            ACTION_START -> {
                val strategy = intent?.getStringExtra("strategy")
                val args = intent?.getStringExtra("args")
                val global = if (intent?.hasExtra("global") == true) intent.getBooleanExtra("global", true) else null
                val bypassedSites = intent?.getStringArrayListExtra("bypassedSites")
                
                serviceScope.launch {
                    vpnLock.withLock {
                        if (vpnInterface != null && !isPaused) {
                            Log.d("DpiVpnService", "VPN already running, updating UI")
                            sendStateBroadcast(isRunning)
                            return@withLock
                        }

                        if (isPaused) {
                            resumeVpn()
                        } else {
                            jniSetVpnService(this@DpiVpnService)
                            startVpn(strategy, args, global, bypassedSites)
                        }
                    }
                }
            }
            ACTION_STOP -> {
                stopVpn("Action Stop")
            }
            ACTION_PAUSE -> {
                pauseVpn()
            }
            ACTION_RESUME -> {
                resumeVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn(strategyIn: String?, argsIn: String?, globalIn: Boolean?, bypassedSitesIn: ArrayList<String>?) {
        isRunning = true
        isPaused = false
        startTime = System.currentTimeMillis()
        
        val notification = createNotification(getString(R.string.notification_connecting))
        startForeground(1, notification)

        serviceScope.launch(Dispatchers.IO) {
            val dataStoreManager = DataStoreManager(applicationContext)
            val prefs = dataStoreManager.getAllSettings().first()
            
            // Store configuration for Resuming
            lastStrategy = strategyIn ?: prefs[DataStoreManager.SELECTED_STRATEGY] ?: "Auto (Recommended)"
            lastArgs = argsIn ?: prefs[DataStoreManager.CUSTOM_ARGS] ?: ""
            lastGlobal = globalIn ?: prefs[DataStoreManager.GLOBAL_MODE] ?: true
            lastBypassedSites = bypassedSitesIn ?: ArrayList(prefs[DataStoreManager.CUSTOM_HOST_LIST]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList())
            
            val mtu = 1400 // Default MTU
            val dns = prefs[DataStoreManager.DNS_SERVER] ?: "1.1.1.1"
            val enableIpv6 = prefs[DataStoreManager.ENABLE_IPV6] ?: true
            val excludeSelf = prefs[DataStoreManager.EXCLUDE_SELF] ?: true
            val runMode = prefs[DataStoreManager.RUN_MODE] ?: "VPN"
            
            val host = prefs[DataStoreManager.PROXY_HOST] ?: Config.DEFAULT_PROXY_HOST
            val portStr = prefs[DataStoreManager.PROXY_PORT] ?: Config.DEFAULT_PROXY_PORT
            val port = try { portStr.toInt() } catch(_: Exception) { 1080 }

            val strategyArgs = Config.getStrategyArgs(lastStrategy!!, lastArgs!!)

            try {
                val builder = Builder()
                    .setSession("NoZapret")
                    .setMtu(mtu)
                    .addAddress("10.0.0.1", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(dns)

                if (enableIpv6) {
                    builder.addAddress("fd00::1", 128)
                    builder.addRoute("::", 0)
                }

                if (excludeSelf) {
                    builder.addDisallowedApplication(packageName)
                }

                val establishedInterface = builder.establish()
                if (establishedInterface == null) {
                    Log.e("DpiVpnService", "Failed to establish VPN interface")
                    stopVpn("Establish failed")
                    return@launch
                }
                vpnInterface = establishedInterface
                
                val fd = vpnInterface?.fd ?: -1

                val hostlistFile = File(cacheDir, "hostlist.txt")
                if (lastBypassedSites!!.isNotEmpty()) {
                    hostlistFile.writeText(lastBypassedSites!!.joinToString("\n"))
                }
                
                vpnJob = launch(Dispatchers.IO) {
                    Log.d("DpiVpnService", "Bypass Service started. Strategy: $lastStrategy")
                    
                    val finalArgs = mutableListOf(
                        "byedpi",
                        "-i", host,
                        "-p", port.toString(),
                        "-x", "1"
                    )
                    
                    // Always add protect global parameter
                    finalArgs.add("-P")
                    finalArgs.add("protect")

                    // 1. Hosts group
                    if (lastGlobal == false && (lastBypassedSites?.isNotEmpty() == true)) {
                        finalArgs.add("-H")
                        finalArgs.add(hostlistFile.absolutePath)
                    }
                    
                    // 2. Strategy args (apply to the current group)
                    finalArgs.addAll(strategyArgs)
                    
                    // 3. Fallback group for non-global (no desync for other sites)
                    if (lastGlobal == false) {
                        finalArgs.add("-A")
                        finalArgs.add("none")
                    }
                    
                    Log.d("DpiVpnService", "Starting ByeDPI with: ${finalArgs.joinToString(" ")}")

                    launch {
                        val res = proxy.start(finalArgs.toTypedArray())
                        Log.d("DpiVpnService", "ByeDPI Proxy exited with code $res")
                        if (isRunning && !isPaused) stopVpn("Proxy exit")
                    }
                    
                    launch {
                        if (!waitForProxy(host, port)) {
                            Log.e("DpiVpnService", "Proxy failed to start in time on $host:$port, aborting")
                            stopVpn("Proxy timeout")
                            return@launch
                        }
                        Log.d("DpiVpnService", "Proxy is ready on $host:$port")
                        startTime = System.currentTimeMillis()
                        isRunning = true
                        sendStateBroadcast(true)
                        updateNotification(getString(R.string.notification_connected))

                        if (runMode == "VPN") {
                            val configPath = createTunnelConfig(enableIpv6, host, port)
                            Log.d("DpiVpnService", "Starting tunnel with config: $configPath")
                            val res = tunnel.start(configPath, fd)
                            Log.d("DpiVpnService", "HevSocks5Tunnel exited with code $res")
                            if (isRunning && !isPaused) stopVpn("Tunnel exit")
                        } else {
                            Log.d("DpiVpnService", "Running in SOCKS5 only mode")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DpiVpnService", "Error starting VPN: ${e.message}")
                stopVpn("Error: ${e.message}")
            }
        }
    }

    private fun pauseVpn() {
        Log.d("DpiVpnService", "Pausing VPN")
        isPaused = true
        isRunning = false
        
        vpnJob?.cancel()
        vpnJob = null
        
        tunnel.stop()
        proxy.stop()
        
        updateNotification(getString(R.string.notification_paused))
        sendStateBroadcast(running = false)
    }

    private fun resumeVpn() {
        Log.d("DpiVpnService", "Resuming VPN")
        startVpn(lastStrategy, lastArgs, lastGlobal, lastBypassedSites)
    }

    private suspend fun waitForProxy(host: String, port: Int): Boolean {
        var attempts = 40
        while (attempts-- > 0) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 500)
                    return true
                }
            } catch (_: Exception) {
                delay(250.milliseconds)
            }
        }
        return false
    }

    private fun createTunnelConfig(enableIpv6: Boolean, proxyHost: String, proxyPort: Int): String {
        val mtu = 1400 // Matches VPN MTU
        val tunnelName = "tun0"
        val config = """
            tunnel:
              name: $tunnelName
              mtu: $mtu
              ipv4:
                address: 10.0.0.1
                gateway: 10.0.0.2
                netmask: 255.255.255.0
              ${if (enableIpv6) "ipv6:\n    address: fd00::1\n    gateway: fd00::2\n    prefix-length: 128" else ""}

            socks5:
              port: $proxyPort
              address: $proxyHost
              udp: udp

            misc:
              task-stack-size: 131072
              connect-timeout: 5000
              read-write-timeout: 60000
              udp-read-write-timeout: 10000
              max-session-count: 2048
              log-level: debug
        """.trimIndent()

        val configFile = File(cacheDir, "tunnel.yaml")
        configFile.writeText(config)
        return configFile.absolutePath
    }

    private fun stopVpn(reason: String) {
        serviceScope.launch {
            vpnLock.withLock {
                if (!isRunning && !isPaused && vpnInterface == null) return@withLock
                Log.d("DpiVpnService", "Stopping VPN (Lock acquired). Reason: $reason")
                
                isRunning = false
                isPaused = false
                vpnJob?.cancel()
                vpnJob = null

                tunnel.stop()
                proxy.stop()
                
                jniSetVpnService(null)

                try {
                    vpnInterface?.close()
                } catch (e: Exception) {
                    Log.e("DpiVpnService", "Error closing vpnInterface: ${e.message}")
                }
                vpnInterface = null

                sendStateBroadcast(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun sendStateBroadcast(running: Boolean) {
        val intent = Intent(ACTION_VPN_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_RUNNING, running)
            putExtra(EXTRA_IS_PAUSED, isPaused)
            putExtra(EXTRA_START_TIME, if (running) startTime else 0L)
            `package` = packageName
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "vpn_channel", "VPN Service", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(content: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, DpiVpnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val pauseResumeAction = if (isPaused) {
            val resumeIntent = Intent(this, DpiVpnService::class.java).apply { action = ACTION_RESUME }
            val resumePendingIntent = PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, getString(R.string.btn_resume), resumePendingIntent
            ).build()
        } else {
            val pauseIntent = Intent(this, DpiVpnService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(this, 3, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, getString(R.string.btn_pause), pausePendingIntent,
            ).build()
        }

        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.btn_stop), stopPendingIntent
        ).build()

        return NotificationCompat.Builder(this, "vpn_channel")
            .setContentTitle("NoZapret Bypass")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentIntent(pendingIntent)
            .setOngoing(false) // Make it clearable
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification(content))
    }

    override fun onDestroy() {
        Log.d("DpiVpnService", "Service onDestroy")
        stopVpn("Destroyed")
        try {
            unregisterReceiver(queryReceiver)
        } catch(_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }
}

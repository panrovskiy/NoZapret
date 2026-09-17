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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

class DpiVpnService : VpnService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private val proxy = ByeDpiProxy()
    private val tunnel = HevSocks5Tunnel()
    private var vpnWorkJob: Job? = null
    private var healthCheckJob: Job? = null

    private val vpnLock = Mutex()
    private val isStopping = AtomicBoolean(false)

    private var lastStrategy: String? = null
    private var lastArgs: String? = null
    private var lastGlobal: Boolean? = null
    private var lastBypassedSites: ArrayList<String>? = null

    private val queryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_QUERY_STATUS) {
                updateVpnState(isRunning, isPaused, isConnecting, isDisconnecting, isError, startTime)
            }
        }
    }

    data class VpnState(
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val isConnecting: Boolean = false,
        val isDisconnecting: Boolean = false,
        val isError: Boolean = false,
        val startTime: Long = 0L,
        val strategy: String? = null
    )

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_VPN_STATE_CHANGED = "com.example.nozapret.VPN_STATE"
        const val EXTRA_IS_RUNNING = "running"
        const val EXTRA_IS_PAUSED = "paused"
        const val EXTRA_IS_CONNECTING = "connecting"
        const val EXTRA_IS_DISCONNECTING = "disconnecting"
        const val EXTRA_IS_ERROR = "error"
        const val EXTRA_START_TIME = "start_time"
        const val ACTION_QUERY_STATUS = "com.example.nozapret.QUERY_STATUS"

        @Volatile
        var isRunning = false
            private set
        @Volatile
        var isPaused = false
            private set
        @Volatile
        var isConnecting = false
            private set
        @Volatile
        var isDisconnecting = false
            private set
        @Volatile
        var isError = false
            private set
        @Volatile
        var startTime = 0L
            private set

        private val _vpnStateFlow = MutableStateFlow(VpnState())
        val vpnStateFlow = _vpnStateFlow.asStateFlow()
    }

    private external fun jniSetVpnService(vpnService: VpnService?)
    private external fun jniCleanup()

    override fun onCreate() {
        super.onCreate()
        Log.d("DpiVpnService", "onCreate")
        createNotificationChannel()
        val filter = IntentFilter(ACTION_QUERY_STATUS)
        ContextCompat.registerReceiver(this, queryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d("DpiVpnService", "onStartCommand: action=$action")
        
        when (action) {
            ACTION_START -> {
                val strategy = intent?.getStringExtra("strategy")
                val args = intent?.getStringExtra("args")
                val global = if (intent?.hasExtra("global") == true) intent.getBooleanExtra("global", true) else null
                val bypassedSites = intent?.getStringArrayListExtra("bypassedSites")

                handleStart(strategy, args, global, bypassedSites)
            }
            ACTION_STOP -> {
                handleStop("Action Stop")
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

    override fun onRevoke() {
        Log.w("DpiVpnService", "VPN permission revoked!")
        handleStop("Revoked")
        super.onRevoke()
    }

    private fun handleStart(strategy: String?, args: String?, global: Boolean?, bypassedSites: ArrayList<String>?) {
        isStopping.set(false)
        val notification = createNotification(getString(R.string.notification_connecting))
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        serviceScope.launch {
            vpnLock.withLock {
                if (isRunning && !isPaused) {
                    Log.d("DpiVpnService", "VPN already running, updating UI")
                    updateVpnState(isRunning, isPaused, isConnecting, isDisconnecting, isError, startTime)
                    return@withLock
                }

                if (isPaused) {
                    resumeVpn()
                } else {
                    isError = false
                    isConnecting = true
                    updateVpnState(false, false, true, false, false, 0L)

                    withContext(Dispatchers.IO) {
                        jniSetVpnService(this@DpiVpnService)
                    }
                    startVpnInternal(strategy, args, global, bypassedSites)
                }
            }
        }
    }

    private suspend fun startVpnInternal(strategyIn: String?, argsIn: String?, globalIn: Boolean?, bypassedSitesIn: ArrayList<String>?) = withContext(Dispatchers.IO) {
        val dataStoreManager = DataStoreManager(applicationContext)
        val prefs = dataStoreManager.getAllSettings().first()
        
        lastStrategy = strategyIn ?: prefs[DataStoreManager.SELECTED_STRATEGY] ?: "Auto (Recommended)"
        lastArgs = argsIn ?: prefs[DataStoreManager.CUSTOM_ARGS] ?: ""
        lastGlobal = globalIn ?: prefs[DataStoreManager.GLOBAL_MODE] ?: true
        lastBypassedSites = bypassedSitesIn ?: ArrayList(prefs[DataStoreManager.CUSTOM_HOST_LIST]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList())
        
        val mtu = 1400 
        val enableIpv6 = prefs[DataStoreManager.ENABLE_IPV6] ?: false
        val excludeSelf = prefs[DataStoreManager.EXCLUDE_SELF] ?: true
        
        val host = prefs[DataStoreManager.PROXY_HOST] ?: Config.DEFAULT_PROXY_HOST
        val portStr = prefs[DataStoreManager.PROXY_PORT] ?: Config.DEFAULT_PROXY_PORT
        val port = try { portStr.toInt() } catch(_: Exception) { 1080 }

        val dnsServer = prefs[DataStoreManager.DNS_SERVER] ?: "1.1.1.1"
        val strategyArgs = Config.getStrategyArgs(lastStrategy!!, lastArgs!!)

        try {
            val builder = Builder()
                .setSession("NoZapret")
                .setMtu(mtu)
                .addAddress("10.1.1.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dnsServer)

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
                isError = true
                stopVpnAsync("Establish failed")
                return@withContext
            }
            vpnInterface = establishedInterface
            
            val fd = vpnInterface?.fd ?: -1
            val hostlistFile = File(cacheDir, "hostlist.txt")
            if (lastBypassedSites!!.isNotEmpty()) {
                hostlistFile.writeText(lastBypassedSites!!.joinToString("\n"))
            }
            
            vpnWorkJob = serviceScope.launch(Dispatchers.IO) {
                Log.d("DpiVpnService", "Bypass Work Job started. Strategy: $lastStrategy")
                
                val finalArgs = mutableListOf(
                    "byedpi",
                    "-i", host,
                    "-p", port.toString(),
                    "-x", "1",
                    "-P", "protect"
                )
                
                if (lastGlobal == false && (lastBypassedSites?.isNotEmpty() == true)) {
                    finalArgs.add("-H")
                    finalArgs.add(hostlistFile.absolutePath)
                }
                
                finalArgs.addAll(strategyArgs)
                
                if (lastGlobal == false) {
                    finalArgs.add("-A")
                    finalArgs.add("none")
                }
                
                Log.d("DpiVpnService", "Starting ByeDPI with: ${finalArgs.joinToString(" ")}")

                val proxyLaunch = launch {
                    val res = proxy.start(finalArgs.toTypedArray())
                    Log.d("DpiVpnService", "ByeDPI Proxy exited with code $res")
                    if (isRunning && !isPaused && !isStopping.get()) {
                        if (res != 0) isError = true
                        stopVpnAsync("Proxy exit")
                    }
                }
                
                val tunnelLaunch = launch {
                    if (!waitForProxy(host, port)) {
                        Log.e("DpiVpnService", "Proxy failed to start in time on $host:$port, aborting")
                        isError = true
                        stopVpnAsync("Proxy timeout")
                        return@launch
                    }
                    
                    Log.d("DpiVpnService", "Proxy is ready on $host:$port")
                    
                    isRunning = true
                    isPaused = false
                    isConnecting = false
                    isError = false
                    startTime = System.currentTimeMillis()
                    
                    updateVpnState(true, false, false, false, false, startTime)
                    updateNotification(getString(R.string.notification_connected))

                    val configPath = createTunnelConfig(enableIpv6, host, port, dnsServer)
                    Log.d("DpiVpnService", "Starting tunnel with config: $configPath")
                    
                    startHealthCheck()
                    
                    val res = tunnel.start(configPath, fd)
                    Log.d("DpiVpnService", "HevSocks5Tunnel exited with code $res")
                    if (isRunning && !isPaused && !isStopping.get()) {
                        if (res != 0) isError = true
                        stopVpnAsync("Tunnel exit")
                    }
                }
                
                joinAll(proxyLaunch, tunnelLaunch)
            }
        } catch (e: Exception) {
            Log.e("DpiVpnService", "Error starting VPN: ${e.message}")
            isError = true
            stopVpnAsync("Error: ${e.message}")
        }
    }

    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5000)
                if (isRunning && !isPaused) {
                    if (!proxy.isRunning() || !tunnel.isRunning()) {
                        Log.w("DpiVpnService", "Health check failed: proxy=${proxy.isRunning()}, tunnel=${tunnel.isRunning()}")
                        isError = true
                        stopVpnAsync("Health check failure")
                        break
                    }
                }
            }
        }
    }

    private fun pauseVpn() {
        Log.d("DpiVpnService", "Pausing VPN")
        serviceScope.launch {
            vpnLock.withLock {
                isPaused = true
                isRunning = false
                isConnecting = false
                isDisconnecting = true
                updateVpnState(false, true, false, true, isError, startTime)
                
                healthCheckJob?.cancel()
                healthCheckJob = null
                
                vpnWorkJob?.cancel()
                vpnWorkJob = null
                
                withContext(Dispatchers.IO) {
                    tunnel.stop()
                    proxy.stop()
                    var wait = 0
                    while ((proxy.isRunning() || tunnel.isRunning()) && wait < 20) {
                        delay(100)
                        wait++
                    }
                }
                
                isDisconnecting = false
                updateNotification(getString(R.string.notification_paused))
                updateVpnState(false, true, false, false, isError, startTime)
            }
        }
    }

    private fun resumeVpn() {
        Log.d("DpiVpnService", "Resuming VPN")
        serviceScope.launch {
            vpnLock.withLock {
                if (isPaused) {
                    isPaused = false
                    isConnecting = true
                    updateVpnState(false, false, true, false, isError, startTime)
                    startVpnInternal(lastStrategy, lastArgs, lastGlobal, lastBypassedSites)
                }
            }
        }
    }

    private suspend fun waitForProxy(host: String, port: Int): Boolean {
        var attempts = 40
        val startWait = System.currentTimeMillis()
        while (attempts-- > 0) {
            if (proxy.isRunning()) {
                try {
                    withContext(Dispatchers.IO) {
                        Socket().use { socket ->
                            protect(socket)
                            socket.connect(InetSocketAddress(host, port), 500)
                        }
                    }
                    Log.d("DpiVpnService", "Proxy ready after ${System.currentTimeMillis() - startWait}ms")
                    return true
                } catch (e: Exception) {
                    if (attempts % 10 == 0) {
                        Log.d("DpiVpnService", "Waiting for proxy on $host:$port... (${e.message})")
                    }
                }
            }
            delay(250.milliseconds)
        }
        return false
    }

    private fun createTunnelConfig(enableIpv6: Boolean, proxyHost: String, proxyPort: Int, dnsServer: String): String {
        val mtu = 1400
        val tunnelName = "tun0"
        val config = """
            tunnel:
              name: $tunnelName
              mtu: $mtu
              ipv4:
                address: 10.1.1.1
                gateway: 10.1.1.2
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
              udp-read-write-timeout: 15000
              max-session-count: 4096
              log-level: info
        """.trimIndent()

        val configFile = File(cacheDir, "tunnel.yaml")
        configFile.writeText(config)
        return configFile.absolutePath
    }

    private fun handleStop(reason: String) {
        serviceScope.launch {
            stopVpnAsync(reason)
        }
    }

    private suspend fun stopVpnAsync(reason: String) {
        Log.d("DpiVpnService", "stopVpnAsync: reason=$reason")
        
        vpnLock.withLock {
            if (isStopping.getAndSet(true)) return@withLock
            
            if (!isRunning && !isPaused && !isConnecting && vpnInterface == null) {
                isRunning = false
                isPaused = false
                isConnecting = false
                isDisconnecting = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isStopping.set(false)
                return@withLock
            }
            
            Log.d("DpiVpnService", "Stopping VPN... (Lock acquired)")
            
            isDisconnecting = true
            updateVpnState(isRunning, isPaused, isConnecting, true, isError, startTime)
            
            healthCheckJob?.cancel()
            healthCheckJob = null
            
            vpnWorkJob?.cancel()
            vpnWorkJob = null

            withContext(Dispatchers.IO) {
                tunnel.stop()
                proxy.stop()
                
                var wait = 0
                while ((proxy.isRunning() || tunnel.isRunning()) && wait < 50) {
                    delay(100)
                    wait++
                }
                
                if (proxy.isRunning()) {
                    Log.w("DpiVpnService", "Proxy still running, forcing close")
                    proxy.forceClose()
                }
                
                jniSetVpnService(null)
                jniCleanup()
            }

            try {
                vpnInterface?.close()
            } catch (e: Exception) {
                Log.e("DpiVpnService", "Error closing vpnInterface: ${e.message}")
            }
            vpnInterface = null

            isRunning = false
            isPaused = false
            isConnecting = false
            isDisconnecting = false
            updateVpnState(false, false, false, false, isError, 0L)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isStopping.set(false)
            Log.d("DpiVpnService", "VPN Stopped.")
        }
    }

    private fun updateVpnState(running: Boolean, paused: Boolean, connecting: Boolean, disconnecting: Boolean, error: Boolean, start: Long) {
        isRunning = running
        isPaused = paused
        isConnecting = connecting
        isDisconnecting = disconnecting
        isError = error
        startTime = start

        _vpnStateFlow.value = VpnState(running, paused, connecting, disconnecting, error, start, lastStrategy)
        
        val intent = Intent(ACTION_VPN_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_RUNNING, running)
            putExtra(EXTRA_IS_PAUSED, paused)
            putExtra(EXTRA_IS_CONNECTING, connecting)
            putExtra(EXTRA_IS_DISCONNECTING, disconnecting)
            putExtra(EXTRA_IS_ERROR, error)
            putExtra(EXTRA_START_TIME, start)
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
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
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
        handleStop("Destroyed")
        try {
            unregisterReceiver(queryReceiver)
        } catch(_: Exception) {}
        
        serviceScope.launch {
            vpnLock.withLock {
                jniSetVpnService(null)
                jniCleanup()
            }
            serviceJob.cancel()
        }
        super.onDestroy()
    }
}

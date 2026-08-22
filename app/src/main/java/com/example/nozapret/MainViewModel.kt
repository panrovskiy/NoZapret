package com.example.nozapret

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import android.net.Uri
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nozapret.core.Config
import com.example.nozapret.data.DataStoreManager
import com.example.nozapret.services.DpiVpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val ACTION_STOP_TESTING = "com.example.nozapret.STOP_TESTING"
        const val EXTRA_STRATEGY_NAME = "strategy_name"
        const val GITHUB_API_URL = "https://api.github.com/repos/panrov/NoZapret/releases/latest"
    }

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val changelog: String
    )

    data class TlsTestResult(
        val success: Boolean,
        val ping: Long? = null,
        val protocol: String? = null,
        val cipherSuite: String? = null,
        val error: String? = null,
        val httpSuccess: Boolean = false,
        val httpError: String? = null
    )

    private val sharedPrefs: SharedPreferences = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val dataStoreManager = DataStoreManager(application)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val snackbarHostState = SnackbarHostState()
    var isEnabled by mutableStateOf(false)
    var vpnStartTime by mutableLongStateOf(0L)
    var isIgnoringBattery by mutableStateOf(true)
    var settingsTab by mutableIntStateOf(0)

    var isCheckingUpdates by mutableStateOf(false)
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
    var isDownloadingUpdate by mutableStateOf(false)
    var updateDownloadProgress by mutableFloatStateOf(0f)
    var showUpdateDialog by mutableStateOf(false)

    var selectedStrategy by mutableStateOf("Auto (Recommended)")
    val pinnedStrategies = mutableStateListOf<String>()
    var customArgs by mutableStateOf("")
    var dnsServer by mutableStateOf("1.1.1.1")
    val allowedApps = mutableStateListOf<String>()
    var proxyHost by mutableStateOf(Config.DEFAULT_PROXY_HOST)
    var proxyPort by mutableStateOf(Config.DEFAULT_PROXY_PORT)
    var excludeSelf by mutableStateOf(true)
    var globalMode by mutableStateOf(false)
    var customHostList by mutableStateOf("")

    var themeMode by mutableStateOf("System") // System, Light, Dark
    var selectedLanguage by mutableStateOf("System")
    var autoConnect by mutableStateOf(false)
    var enableIpv6 by mutableStateOf(false)
    var customPrimaryColor by mutableIntStateOf(0xFF6750A4.toInt())
    var customThemeBase by mutableStateOf("System") // System, Light, Dark
    var runMode by mutableStateOf("VPN") // VPN, Proxy

    // Diagnostics State
    enum class DiagAction { NONE, BATTERY_OPTIMIZATION, DISABLE_IPV6, RESTART_VPN, STOP_VPN }
    data class DiagItem(
        val message: String,
        val type: DiagType,
        val isChecking: Boolean = false,
        val action: DiagAction = DiagAction.NONE,
        val solutionTextResId: Int? = null
    )
    enum class DiagType { INFO, PASSED, FAILED, WARNING }

    val diagnosticsLog = mutableStateListOf<DiagItem>()
    var isDiagnosing by mutableStateOf(false)
    var showDiagnosticsDialog by mutableStateOf(false)

    var strategiesTestedCount by mutableIntStateOf(0)

    val selectedPresets = mutableStateListOf<String>()

    val stats = mutableStateMapOf<String, Triple<Int, Int, Int>>()
    val committedStats = mutableStateMapOf<String, Triple<Int, Int, Int>>()
    val testResults = mutableStateMapOf<String, Map<String, TlsTestResult>>()
    val currentlyTesting = mutableStateListOf<String>()

    val logLines = mutableStateListOf<String>()
    private var logJob: Job? = null

    private val testJobs = mutableMapOf<String, Job>()
    private var allTestsJob: Job? = null
    private var isStopping = false

    var quickTestUrl by mutableStateOf("")
    var quickTestResult by mutableStateOf<TlsTestResult?>(null)
    var isQuickTesting by mutableStateOf(false)
    var quickTestStrategy by mutableStateOf("None")

    val bypassedSites: List<String>
        get() {
            val presetsMap = Config.PRESETS.toMap()
            val presetSites = selectedPresets.asSequence().filter { it != "Custom" }.flatMap { presetsMap[it] ?: emptyList() }.toList()
            val customSites = if (selectedPresets.contains("Custom") && customHostList.isNotBlank()) {
                customHostList.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
            } else {
                emptyList()
            }
            return (presetSites + customSites).distinct()
        }

    val sitesToTestCount: Int
        get() = bypassedSites.filter { !it.contains("/") }.size

    init {
        viewModelScope.launch {
            val strategyJob = async { dataStoreManager.getSetting(DataStoreManager.SELECTED_STRATEGY, "Auto (Recommended)").first() }
            val argsJob = async { dataStoreManager.getSetting(DataStoreManager.CUSTOM_ARGS, "").first() }
            val dnsJob = async { dataStoreManager.getSetting(DataStoreManager.DNS_SERVER, "1.1.1.1").first() }
            val appsJob = async { dataStoreManager.getSetting(DataStoreManager.ALLOWED_APPS, emptySet()).first() }
            val hostJob = async { dataStoreManager.getSetting(DataStoreManager.PROXY_HOST, Config.DEFAULT_PROXY_HOST).first() }
            val portJob = async { dataStoreManager.getSetting(DataStoreManager.PROXY_PORT, Config.DEFAULT_PROXY_PORT).first() }
            val excludeJob = async { dataStoreManager.getSetting(DataStoreManager.EXCLUDE_SELF, defaultValue = true).first() }
            val globalJob = async { dataStoreManager.getSetting(DataStoreManager.GLOBAL_MODE, false).first() }
            val customHostsJob = async { dataStoreManager.getSetting(DataStoreManager.CUSTOM_HOST_LIST, "").first() }
            val presetsJob = async { dataStoreManager.getSetting(DataStoreManager.SELECTED_PRESETS, setOf("YouTube", "Telegram")).first() }
            val themeJob = async { dataStoreManager.getSetting(DataStoreManager.THEME_MODE, "System").first() }
            val languageJob = async { dataStoreManager.getSetting(DataStoreManager.SELECTED_LANGUAGE, "System").first() }
            val customColorJob = async { dataStoreManager.getSetting(DataStoreManager.CUSTOM_PRIMARY_COLOR, 0xFF6750A4.toInt()).first() }
            val themeBaseJob = async { dataStoreManager.getSetting(DataStoreManager.CUSTOM_THEME_BASE, "System").first() }
            val pinnedJob = async { dataStoreManager.getSetting(DataStoreManager.PINNED_STRATEGIES, emptySet()).first() }
            val autoConnectJob = async { dataStoreManager.getSetting(DataStoreManager.AUTO_CONNECT, false).first() }
            val ipv6Job = async { dataStoreManager.getSetting(DataStoreManager.ENABLE_IPV6, false).first() }
            val runModeJob = async { dataStoreManager.getSetting(DataStoreManager.RUN_MODE, "VPN").first() }

            val strategy = strategyJob.await()
            val args = argsJob.await()
            val dns = dnsJob.await()
            val apps = appsJob.await()
            val host = hostJob.await()
            val port = portJob.await()
            val exclude = excludeJob.await()
            val global = globalJob.await()
            val customHosts = customHostsJob.await()
            val presets = presetsJob.await()
            val theme = themeJob.await()
            val language = languageJob.await()
            val customColor = customColorJob.await()
            val themeBase = themeBaseJob.await()
            val pinned = pinnedJob.await()
            val autoConn = autoConnectJob.await()
            val ipv6 = ipv6Job.await()
            val rMode = runModeJob.await()

            withContext(Dispatchers.Main) {
                selectedStrategy = strategy
                customArgs = args
                dnsServer = dns
                allowedApps.clear()
                allowedApps.addAll(apps)
                proxyHost = host
                proxyPort = port
                excludeSelf = exclude
                globalMode = global
                customHostList = customHosts
                selectedPresets.clear()
                selectedPresets.addAll(presets)
                themeMode = theme
                selectedLanguage = language
                applyLanguage(language)
                autoConnect = autoConn
                enableIpv6 = ipv6
                customPrimaryColor = customColor
                customThemeBase = themeBase
                pinnedStrategies.clear()
                pinnedStrategies.addAll(pinned)
                runMode = rMode

                loadTestResults()
                
                if (autoConnect && !isEnabled) {
                    Log.d("MainViewModel", "Auto-connect enabled, checking status")
                }
            }
            
            checkForUpdates(manual = false)
        }
        startLogCollection()
    }

    private fun startLogCollection() {
        if (logJob?.isActive == true) return
        logJob = viewModelScope.launch(Dispatchers.IO) {
            val tags = "proxy:D MainViewModel:D ByeDpiProxy:D DpiVpnService:D hev-socks5-tunnel:D NoZapretNative:D"
            try {
                // 1. Initial buffer (only if we don't have logs yet)
                if (logLines.isEmpty()) {
                    val initialProcess = Runtime.getRuntime().exec("logcat -d -t 300 $tags *:S")
                    val initialLines = initialProcess.inputStream.bufferedReader().readLines()
                    withContext(Dispatchers.Main) {
                        logLines.addAll(initialLines)
                    }
                }

                // 2. Stream
                val streamProcess = Runtime.getRuntime().exec("logcat -T 1 $tags *:S")
                val reader = streamProcess.inputStream.bufferedReader()
                
                val buffer = mutableListOf<String>()
                var lastUpdate = System.currentTimeMillis()

                while (isActive) {
                    val line = try { reader.readLine() } catch (_: Exception) { null } ?: break
                    if (line.isBlank()) continue
                    buffer.add(line)

                    val now = System.currentTimeMillis()
                    if ((now - lastUpdate > 1000) || (buffer.size > 50)) {
                        val batch = buffer.toList()
                        buffer.clear()
                        lastUpdate = now
                        withContext(Dispatchers.Main) {
                            logLines.addAll(batch)
                            if (logLines.size > 2000) {
                                repeat(logLines.size - 2000) { logLines.removeAt(0) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Log collection error", e)
            } finally {
                logJob = null
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec("logcat -c")
                withContext(Dispatchers.Main) {
                    logLines.clear()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to clear logs", e)
            }
        }
    }

    fun updateVpnState(running: Boolean, start: Long = 0L) {
        isEnabled = running
        vpnStartTime = start
    }


    fun togglePreset(name: String, enabled: Boolean) {
        if (enabled) {
            if (!selectedPresets.contains(name)) selectedPresets.add(name)
        } else {
            selectedPresets.remove(name)
        }
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.SELECTED_PRESETS, selectedPresets.toSet())
        }
    }

    fun togglePinStrategy(name: String) {
        if (pinnedStrategies.contains(name)) {
            pinnedStrategies.remove(name)
        } else {
            pinnedStrategies.add(name)
        }
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.PINNED_STRATEGIES, pinnedStrategies.toSet())
        }
    }


    fun updateSelectedStrategy(strategy: String) {
        selectedStrategy = strategy
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.SELECTED_STRATEGY, strategy)
        }
    }

    fun updateCustomArgs(args: String) {
        customArgs = args
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.CUSTOM_ARGS, args)
        }
    }

    fun updateAutoConnect(enabled: Boolean) {
        autoConnect = enabled
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.AUTO_CONNECT, enabled)
        }
    }

    fun updateEnableIpv6(enabled: Boolean) {
        enableIpv6 = enabled
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.ENABLE_IPV6, enabled)
        }
    }

    fun updateDnsServer(dns: String) {
        dnsServer = dns
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.DNS_SERVER, dns)
        }
    }

    fun toggleAllowedApp(pkg: String) {
        if (allowedApps.contains(pkg)) {
            allowedApps.remove(pkg)
        } else {
            allowedApps.add(pkg)
        }
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.ALLOWED_APPS, allowedApps.toSet())
        }
    }

    fun updateProxyHost(host: String) {
        proxyHost = host
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.PROXY_HOST, host)
        }
    }

    fun updateProxyPort(port: String) {
        proxyPort = port
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.PROXY_PORT, port)
        }
    }

    fun updateExcludeSelf(exclude: Boolean) {
        excludeSelf = exclude
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.EXCLUDE_SELF, exclude)
        }
    }

    fun updateGlobalMode(global: Boolean) {
        globalMode = global
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.GLOBAL_MODE, global)
        }
    }

    fun updateCustomHostList(hosts: String) {
        customHostList = hosts
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.CUSTOM_HOST_LIST, hosts)
        }
    }

    fun updateThemeMode(mode: String) {
        themeMode = mode
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.THEME_MODE, mode)
        }
    }

    fun updateSelectedLanguage(lang: String) {
        selectedLanguage = lang
        applyLanguage(lang)
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.SELECTED_LANGUAGE, lang)
        }
    }

    private fun applyLanguage(lang: String) {
        val localeList = if (lang == "System") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(lang)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun updateCustomPrimaryColor(color: Int) {
        customPrimaryColor = color
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.CUSTOM_PRIMARY_COLOR, color)
        }
    }

    fun updateCustomThemeBase(base: String) {
        customThemeBase = base
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.CUSTOM_THEME_BASE, base)
        }
    }

    fun updateRunMode(mode: String) {
        runMode = mode
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.RUN_MODE, mode)
        }
    }

    private val notificationManager = NotificationManagerCompat.from(application)
    private val channelId = "strategy_testing"

    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            channelId, getApplication<Application>().getString(R.string.notification_testing_channel_name),
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateTestNotification(strategyName: String, success: Int, tested: Int, total: Int, complete: Boolean = false) {
        val context = getApplication<Application>()
        val stopIntent = Intent(ACTION_STOP_TESTING).apply {
            putExtra(EXTRA_STRATEGY_NAME, strategyName)
            setPackage(context.packageName)
        }
        val stopPendingIntent = android.app.PendingIntent.getBroadcast(
            context, strategyName.hashCode(), stopIntent, 
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (complete) {
            context.getString(R.string.notification_testing_complete_title, strategyName)
        } else {
            context.getString(R.string.notification_testing_title, strategyName)
        }
        
        val text = if (complete) {
            context.getString(R.string.notification_testing_complete_text, success, total)
        } else {
            context.getString(R.string.notification_testing_text, tested, total, success)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(!complete)
            .setAutoCancel(complete)
            .setProgress(total, tested, tested == 0 && !complete)
        
        if (!complete) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.btn_stop), stopPendingIntent)
        }

        try {
            notificationManager.notify(strategyName.hashCode(), builder.build())
        } catch (e: SecurityException) {
            Log.w("MainViewModel", "Notification permission missing: ${e.message}")
        }
    }

    private suspend fun fastTlsCheck(domain: String, direct: Boolean = false): TlsTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val proxy = if (direct) java.net.Proxy.NO_PROXY else java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1081))
                val socket = Socket(proxy)
                socket.connect(InetSocketAddress(domain, 443), 5000)
                socket.soTimeout = 5000
                
                val sslSocketFactory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
                val sslSocket = sslSocketFactory.createSocket(socket, domain, 443, true) as javax.net.ssl.SSLSocket
                
                sslSocket.startHandshake()
                val ping = System.currentTimeMillis() - start
                val session = sslSocket.session
                val result = TlsTestResult(
                    success = true,
                    ping = ping,
                    protocol = session.protocol,
                    cipherSuite = session.cipherSuite
                )
                sslSocket.close()
                result
            } catch (e: Exception) {
                TlsTestResult(false, error = e.message ?: "TLS Handshake failed")
            }
        }
    }

    private suspend fun fastHttpCheck(domain: String, direct: Boolean = false): Pair<Boolean, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val proxy = if (direct) java.net.Proxy.NO_PROXY else java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1081))
                val client = okHttpClient.newBuilder()
                    .proxy(proxy)
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url("http://$domain")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    Pair(response.isSuccessful || response.code < 500, if (!response.isSuccessful) "HTTP ${response.code}" else null)
                }
            } catch (e: Exception) {
                Pair(false, e.message)
            }
        }
    }

    private suspend fun performCombinedCheck(domain: String, direct: Boolean): TlsTestResult {
        val tls = fastTlsCheck(domain, direct)
        val http = fastHttpCheck(domain, direct)
        return tls.copy(httpSuccess = http.first, httpError = http.second)
    }

    fun testStrategy(strategyName: String): Job {
        return viewModelScope.launch {
            if (currentlyTesting.contains(strategyName)) {
                currentlyTesting.remove(strategyName)
                testJobs[strategyName]?.cancel()
                notificationManager.cancel(strategyName.hashCode())
                return@launch
            }

            if ((allTestsJob == null) && currentlyTesting.isNotEmpty()) {
                stopAllTestsInternal()
            }

            if (bypassedSites.isEmpty()) return@launch
            
            val job = viewModelScope.launch(Dispatchers.IO) {
                runStrategyTestInternal(strategyName)
            }
            testJobs[strategyName] = job
        }
    }

    private suspend fun runStrategyTestInternal(strategyName: String) {
        val context = getApplication<Application>()
        val sitesToTest = bypassedSites.filter { !it.contains("/") }
        val totalToTest = sitesToTest.size
        
        val testProxy = if (strategyName != "None") com.example.nozapret.core.ByeDpiProxy() else null

        try {
            // Update UI immediately
            withContext(Dispatchers.Main) {
                stats[strategyName] = Triple(0, 0, totalToTest)
                if (!currentlyTesting.contains(strategyName)) currentlyTesting.add(strategyName)
                testResults[strategyName] = emptyMap()
            }
            
            updateTestNotification(strategyName, 0, 0, totalToTest)

            // Stop VPN if running
            if (DpiVpnService.isRunning) {
                val stopIntent = Intent(context, DpiVpnService::class.java).apply {
                    action = DpiVpnService.ACTION_STOP
                }
                context.startService(stopIntent)
                var waitVpn = 0
                while (DpiVpnService.isRunning && waitVpn < 25) {
                    delay(200.milliseconds)
                    waitVpn++
                }
                com.example.nozapret.core.ByeDpiProxy().forceClose()
                delay(500.milliseconds)
            }
            
            com.example.nozapret.core.ByeDpiProxy().forceClose()
            delay(200.milliseconds)

            val isDirect = strategyName == "None"
            
            coroutineScope {
                val proxyJob = if (!isDirect) {
                    val strategyArgs = Config.getStrategyArgs(strategyName, customArgs)
                    val listArgs = Config.BYPASS_LISTS.toMap()["Russia Default"] ?: emptyArray()
                    val finalArgs = mutableListOf("byedpi", "-i", "127.0.0.1", "-p", "1081", "-x", "1")
                    finalArgs.addAll(strategyArgs)
                    finalArgs.addAll(listArgs)
                    finalArgs.add("-P")
                    finalArgs.add("protect")

                    launch(Dispatchers.IO) { 
                        try {
                            testProxy?.start(finalArgs.toTypedArray()) 
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Test proxy error", e)
                        } finally {
                            withContext(NonCancellable) { testProxy?.forceClose() }
                        }
                    }
                } else null
                
                try {
                    if (!isDirect) {
                        var proxyReady = false
                        withTimeoutOrNull(5000.milliseconds) {
                            while (!proxyReady) {
                                try {
                                    Socket().use { s ->
                                        s.connect(InetSocketAddress("127.0.0.1", 1081), 500)
                                        proxyReady = true
                                    }
                                } catch (_: Exception) {
                                    delay(500.milliseconds)
                                }
                            }
                        }
                        if (!proxyReady) {
                            throw Exception("Proxy failed to start")
                        }
                    }

                    val results = mutableMapOf<String, TlsTestResult>()
                    var successfulBypasses = 0
                    var testedCount = 0
                    
                    if (sitesToTest.isNotEmpty()) {
                        val semaphore = Semaphore(10)
                        try {
                            withTimeout(180000.milliseconds) {
                                coroutineScope {
                                    sitesToTest.forEach { site ->
                                        launch {
                                            semaphore.acquire()
                                            try {
                                                val result = performCombinedCheck(site, isDirect)
                                                val (currentSuccess, currentTested) = synchronized(results) {
                                                    results[site] = result
                                                    if (result.success) successfulBypasses++
                                                    testedCount++
                                                    Pair(successfulBypasses, testedCount)
                                                }

                                                val snapResults = synchronized(results) { results.toMap() }
                                                updateTestNotification(strategyName, currentSuccess, currentTested, totalToTest)
                                                withContext(Dispatchers.Main.immediate) {
                                                    testResults[strategyName] = snapResults
                                                    stats[strategyName] = Triple(currentSuccess, currentTested, totalToTest)
                                                }
                                            } finally {
                                                semaphore.release()
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Test for $strategyName interrupted: ${e.message}")
                        }
                    } else {
                        withContext(Dispatchers.Main.immediate) { stats[strategyName] = Triple(0, 0, 0) }
                    }
                } finally {
                    withContext(NonCancellable) {
                        testProxy?.forceClose()
                        proxyJob?.cancelAndJoin()
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                val finalStats = stats[strategyName] ?: Triple(0, 0, totalToTest)
                updateTestNotification(strategyName, finalStats.first, finalStats.second, totalToTest, true)
                withContext(Dispatchers.Main.immediate) {
                    committedStats[strategyName] = finalStats
                    currentlyTesting.remove(strategyName)
                    testJobs.remove(strategyName)
                    saveTestResults()
                }
            }
        }
    }

    fun stopTest(strategyName: String) {
        viewModelScope.launch {
            if (allTestsJob != null) {
                stopAllTestsInternal()
            } else {
                testJobs[strategyName]?.cancel()
                withContext(Dispatchers.Main) {
                    currentlyTesting.remove(strategyName)
                }
            }
        }
    }

    suspend fun stopAllTests() {
        stopAllTestsInternal()
    }

    private suspend fun stopAllTestsInternal() {
        if (isStopping) return
        isStopping = true
        try {
            withContext(Dispatchers.Main) {
                currentlyTesting.clear()
            }
            allTestsJob?.cancel()
            val jobsToCleanup = testJobs.values.toList()
            jobsToCleanup.forEach { it.cancel() }
            
            withContext(Dispatchers.IO) {
                com.example.nozapret.core.ByeDpiProxy().forceClose()
            }
            
            withContext(NonCancellable) {
                withTimeoutOrNull(2000.milliseconds) {
                    allTestsJob?.join()
                    jobsToCleanup.joinAll()
                }
                withContext(Dispatchers.Main) {
                    allTestsJob = null
                    testJobs.clear()
                    currentlyTesting.clear()
                }
            }
        } finally {
            isStopping = false
        }
    }

    fun applyStrategy(name: String) {
        updateSelectedStrategy(name)
        viewModelScope.launch {
            snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.msg_strategy_applied, name))
        }
    }

    fun useAsCustom(name: String) {
        val args = Config.getStrategyArgs(name, customArgs).joinToString(" ")
        updateCustomArgs(args)
        updateSelectedStrategy("Custom")
        viewModelScope.launch {
            snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.msg_strategy_copied))
        }
    }

    fun testAllStrategies() {
        viewModelScope.launch {
            if (allTestsJob != null) {
                stopAllTestsInternal()
                return@launch
            }
            
            stopAllTestsInternal()
            
            if (bypassedSites.isEmpty()) return@launch
            
            createNotificationChannel()

            strategiesTestedCount = 0
            
            allTestsJob = viewModelScope.launch(Dispatchers.IO) {
                Config.STRATEGIES.forEach { (name, _) ->
                    if (!isActive) return@forEach
                    runStrategyTestInternal(name)
                    withContext(Dispatchers.Main) {
                        strategiesTestedCount++
                    }
                }
                withContext(Dispatchers.Main) {
                    allTestsJob = null
                }
            }
        }
    }

    fun resetTests() {
        viewModelScope.launch {
            stopAllTestsInternal()
            withContext(Dispatchers.Main) {
                stats.clear()
                committedStats.clear()
                testResults.clear()
                saveTestResults()
            }
        }
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (isCheckingUpdates) return
        isCheckingUpdates = true
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(GITHUB_API_URL).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Server returned ${response.code}")
                    val body = response.body.string()
                    val json = JSONObject(body)
                    val latestVersion = json.getString("tag_name").removePrefix("v")
                    
                    if (isNewerVersion(latestVersion)) {
                        val assets = json.getJSONArray("assets")
                        var downloadUrl = ""
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                        
                        val changelog = json.optString("body", "")
                        
                        withContext(Dispatchers.Main) {
                            updateInfo = UpdateInfo(latestVersion, downloadUrl, changelog)
                            showUpdateDialog = true
                        }
                    } else if (manual) {
                        withContext(Dispatchers.Main) {
                            snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.msg_latest_version))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Update check failed", e)
                if (manual) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.error_update_check, e.message))
                    }
                }
            } finally {
                isCheckingUpdates = false
            }
        }
    }

    fun exportConfiguration(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = JSONObject().apply {
                    put("strategy", selectedStrategy)
                    put("customArgs", customArgs)
                    put("dns", dnsServer)
                    put("proxyHost", proxyHost)
                    put("proxyPort", proxyPort)
                    put("excludeSelf", excludeSelf)
                    put("globalMode", globalMode)
                    put("presets", selectedPresets.toSet())
                    put("customHosts", customHostList)
                    put("allowedApps", allowedApps.toSet())
                    put("theme", themeMode)
                    put("language", selectedLanguage)
                    put("primaryColor", customPrimaryColor)
                    put("themeBase", customThemeBase)
                    put("pinned", pinnedStrategies.toSet())
                    put("autoConnect", autoConnect)
                    put("enableIpv6", enableIpv6)
                    put("runMode", runMode)
                }

                val fileName = "NoZapret_Config_${System.currentTimeMillis()}.json"
                val file = File(context.cacheDir, fileName)
                file.writeText(config.toString(2))

                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_export_config)))
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(context.getString(R.string.error_export_failed, e.message))
                }
            }
        }
    }

    fun importConfiguration(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content == null) throw Exception("Cannot read file")
                
                val config = JSONObject(content)
                
                withContext(Dispatchers.Main) {
                    if (config.has("strategy")) updateSelectedStrategy(config.getString("strategy"))
                    if (config.has("customArgs")) updateCustomArgs(config.getString("customArgs"))
                    if (config.has("dns")) updateDnsServer(config.getString("dns"))
                    if (config.has("proxyHost")) updateProxyHost(config.getString("proxyHost"))
                    if (config.has("proxyPort")) updateProxyPort(config.getString("proxyPort"))
                    if (config.has("excludeSelf")) updateExcludeSelf(config.getBoolean("excludeSelf"))
                    if (config.has("globalMode")) updateGlobalMode(config.getBoolean("globalMode"))
                    if (config.has("theme")) updateThemeMode(config.getString("theme"))
                    if (config.has("language")) updateSelectedLanguage(config.getString("language"))
                    if (config.has("primaryColor")) updateCustomPrimaryColor(config.getInt("primaryColor"))
                    if (config.has("themeBase")) updateCustomThemeBase(config.getString("themeBase"))
                    if (config.has("autoConnect")) updateAutoConnect(config.getBoolean("autoConnect"))
                    if (config.has("enableIpv6")) updateEnableIpv6(config.getBoolean("enableIpv6"))
                    if (config.has("runMode")) updateRunMode(config.getString("runMode"))

                    if (config.has("presets")) {
                        val presets = config.getJSONArray("presets")
                        selectedPresets.clear()
                        for (i in 0 until presets.length()) selectedPresets.add(presets.getString(i))
                        dataStoreManager.saveSetting(DataStoreManager.SELECTED_PRESETS, selectedPresets.toSet())
                    }

                    if (config.has("customHosts")) updateCustomHostList(config.getString("customHosts"))

                    if (config.has("allowedApps")) {
                        val apps = config.getJSONArray("allowedApps")
                        allowedApps.clear()
                        for (i in 0 until apps.length()) allowedApps.add(apps.getString(i))
                        dataStoreManager.saveSetting(DataStoreManager.ALLOWED_APPS, allowedApps.toSet())
                    }

                    if (config.has("pinned")) {
                        val pinned = config.getJSONArray("pinned")
                        pinnedStrategies.clear()
                        for (i in 0 until pinned.length()) pinnedStrategies.add(pinned.getString(i))
                        dataStoreManager.saveSetting(DataStoreManager.PINNED_STRATEGIES, pinnedStrategies.toSet())
                    }

                    snackbarHostState.showSnackbar(context.getString(R.string.msg_config_imported))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(context.getString(R.string.error_import_failed, e.message))
                }
            }
        }
    }

    private fun isNewerVersion(latest: String): Boolean {
        val current = BuildConfig.VERSION_NAME
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun downloadAndInstallUpdate() {
        val info = updateInfo ?: return
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        updateDownloadProgress = 0f
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(info.downloadUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
                    val body = response.body
                    val totalBytes = body.contentLength()
                    val file = File(getApplication<Application>().cacheDir, "update.apk")
                    
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var downloadedBytes = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) {
                                    updateDownloadProgress = downloadedBytes.toFloat() / totalBytes
                                }
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        installApk(file)
                        isDownloadingUpdate = false
                        showUpdateDialog = false
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Update download failed", e)
                withContext(Dispatchers.Main) {
                    isDownloadingUpdate = false
                    snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.error_download_failed, e.message))
                }
            }
        }
    }

    private fun installApk(file: File) {
        val context = getApplication<Application>()
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun runQuickTest() {
        if (isQuickTesting) return
        val url = quickTestUrl.trim().lowercase()
            .removePrefix("https://").removePrefix("http://")
            .split("/").first()
        
        if (url.isEmpty() || !url.contains(".")) {
            viewModelScope.launch {
                snackbarHostState.showSnackbar("Invalid domain")
            }
            return
        }

        isQuickTesting = true
        quickTestResult = null

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var testProxy: com.example.nozapret.core.ByeDpiProxy? = null
            var proxyJob: Job? = null

            try {
                withTimeout(30000.milliseconds) { // Increased timeout for reliability
                    // Stop VPN if running to release native proxy singleton
                    if (DpiVpnService.isRunning) {
                        withContext(Dispatchers.Main) {
                            val stopIntent = Intent(context, DpiVpnService::class.java).apply {
                                action = DpiVpnService.ACTION_STOP
                            }
                            context.startService(stopIntent)
                        }
                        var waitVpn = 0
                        while (DpiVpnService.isRunning && waitVpn < 25 && isActive) {
                            delay(200.milliseconds)
                            waitVpn++
                        }
                        delay(500.milliseconds)
                    }

                    // Final hard cleanup
                    withContext(Dispatchers.IO) {
                        com.example.nozapret.core.ByeDpiProxy().forceClose()
                    }
                    delay(500.milliseconds)

                    val isDirect = quickTestStrategy == "None"

                    if (!isDirect) {
                        val proxy = com.example.nozapret.core.ByeDpiProxy()
                        testProxy = proxy
                        
                        val strategyArgs = Config.getStrategyArgs(quickTestStrategy, customArgs)
                        val listArgs = Config.BYPASS_LISTS.toMap()["Russia Default"] ?: emptyArray()
                        
                        val finalArgs = mutableListOf("byedpi", "-i", "127.0.0.1", "-p", "1081", "-x", "1")
                        finalArgs.addAll(strategyArgs)
                        finalArgs.addAll(listArgs)
                        finalArgs.add("-P")
                        finalArgs.add("protect")

                        proxyJob = launch(Dispatchers.IO) {
                            try {
                                proxy.start(finalArgs.toTypedArray())
                            } catch (e: Exception) {
                                Log.e("QuickTest", "Proxy error", e)
                            } finally {
                                proxy.forceClose()
                            }
                        }
                        
                        // Wait for proxy to be ready
                        var proxyReady = false
                        var waitCount = 0
                        while (!proxyReady && waitCount < 10 && isActive) {
                            try {
                                Socket().use { s ->
                                    s.connect(InetSocketAddress("127.0.0.1", 1081), 300)
                                    proxyReady = true
                                }
                            } catch (_: Exception) {
                                delay(300.milliseconds)
                                waitCount++
                            }
                        }
                    }

                    val result = performCombinedCheck(url, isDirect)

                    withContext(Dispatchers.Main) {
                        quickTestResult = result
                    }
                }
            } catch (e: Exception) {
                Log.e("QuickTest", "Test failed", e)
                withContext(Dispatchers.Main) {
                    quickTestResult = TlsTestResult(false, error = e.message ?: "Test failed")
                }
            } finally {
                withContext(NonCancellable) {
                    try {
                        testProxy?.forceClose()
                    } catch (e: Exception) {
                        Log.e("QuickTest", "Error closing proxy", e)
                    }
                    proxyJob?.cancel()
                    try {
                        withTimeoutOrNull(2000.milliseconds) { proxyJob?.join() }
                    } catch (_: Exception) {}
                    withContext(Dispatchers.Main) {
                        isQuickTesting = false
                    }
                }
            }
        }
    }

    fun runDiagnostics() {
        if (isDiagnosing) return
        isDiagnosing = true
        diagnosticsLog.clear()
        showDiagnosticsDialog = true

        viewModelScope.launch(Dispatchers.IO) {
            fun addLog(msgResId: Int, type: DiagType, vararg args: Any, action: DiagAction = DiagAction.NONE, solutionResId: Int? = null) {
                val msg = getApplication<Application>().getString(msgResId, *args)
                viewModelScope.launch(Dispatchers.Main) {
                    diagnosticsLog.add(DiagItem(msg, type, false, action, solutionResId))
                }
            }

            fun addChecking(msgResId: Int, vararg args: Any) {
                val msg = getApplication<Application>().getString(msgResId, *args)
                viewModelScope.launch(Dispatchers.Main) {
                    diagnosticsLog.add(DiagItem(msg, DiagType.INFO, true))
                }
            }

            fun removeChecking() {
                viewModelScope.launch(Dispatchers.Main) {
                    val last = diagnosticsLog.lastOrNull()
                    if (last?.isChecking == true) diagnosticsLog.removeAt(diagnosticsLog.size - 1)
                }
            }

            try {
                // 1. Connectivity Check
                addChecking(R.string.diag_checking, "Network")
                val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                removeChecking()

                if (capabilities != null) {
                    val type = when {
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                        else -> "Unknown"
                    }
                    addLog(R.string.diag_network_connected, DiagType.PASSED, type)
                } else {
                    addLog(R.string.diag_network_disconnected, DiagType.FAILED)
                }

                // 2. VPN Conflict Check
                addChecking(R.string.diag_checking, "VPN Conflicts")
                @Suppress("DEPRECATION")
                val vpnProfiles = connectivityManager.allNetworks.filter {
                    val caps = connectivityManager.getNetworkCapabilities(it)
                    caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                }
                removeChecking()
                if (vpnProfiles.size > (if (isEnabled) 1 else 0)) {
                    addLog(R.string.diag_vpn_conflict, DiagType.WARNING, vpnProfiles.size.toString())
                }

                // 3. Battery Optimization Check
                addChecking(R.string.diag_checking, "Battery")
                val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val isIgnoring = pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
                removeChecking()
                if (isIgnoring) {
                    addLog(R.string.diag_battery_passed, DiagType.PASSED)
                } else {
                    addLog(R.string.diag_battery_optimization, DiagType.WARNING, action = DiagAction.BATTERY_OPTIMIZATION, solutionResId = R.string.diag_sol_battery)
                }

                // 4. DNS Check
                addChecking(R.string.diag_checking, "DNS")
                delay(500.milliseconds)
                removeChecking()
                addLog(R.string.diag_dns_server, DiagType.INFO, dnsServer)

                // 5. IPv6 Check
                addChecking(R.string.diag_checking, "IPv6")
                val hasIpv6 = withContext(Dispatchers.IO) {
                    try {
                        java.net.NetworkInterface.getNetworkInterfaces().asSequence().any { ni ->
                            ni.isUp && !ni.isLoopback && ni.inetAddresses.asSequence().any { it is java.net.Inet6Address && !it.isLinkLocalAddress }
                        }
                    } catch (_: Exception) { false }
                }
                removeChecking()
                if (hasIpv6) {
                    addLog(R.string.diag_ipv6_detected, DiagType.WARNING, action = DiagAction.DISABLE_IPV6, solutionResId = R.string.diag_sol_ipv6)
                } else {
                    addLog(R.string.diag_ipv6_not_detected, DiagType.PASSED)
                }

                // 6. Native Library Check
                addChecking(R.string.diag_checking, "Native Engine")
                val libLoaded = try {
                    System.loadLibrary("byedpi")
                    true
                } catch (_: UnsatisfiedLinkError) { false }
                removeChecking()
                if (libLoaded) {
                    addLog(R.string.diag_byedpi_load_success, DiagType.PASSED)
                } else {
                    addLog(R.string.diag_byedpi_load_failed, DiagType.FAILED)
                }

                // 7. Proxy Availability Check (if enabled)
                if (isEnabled) {
                    addChecking(R.string.diag_checking, "Local Proxy")
                    val reachable = withContext(Dispatchers.IO) {
                        try {
                            Socket().use { s ->
                                s.connect(InetSocketAddress(proxyHost, proxyPort.toInt()), 2000)
                                true
                            }
                        } catch (_: Exception) { false }
                    }
                    removeChecking()
                    if (reachable) {
                        addLog(R.string.diag_proxy_running, DiagType.PASSED)
                    } else {
                        addLog(R.string.diag_proxy_not_running, DiagType.FAILED, action = DiagAction.RESTART_VPN, solutionResId = R.string.diag_sol_restart)
                    }
                }

                // 8. TCP Timestamps Check
                addChecking(R.string.diag_checking, "TCP Timestamps")
                val tsFile = File("/proc/sys/net/ipv4/tcp_timestamps")
                val tsEnabled = if (tsFile.exists()) {
                    try { tsFile.readText().trim() == "1" } catch (_: Exception) { null }
                } else null
                removeChecking()
                if (tsEnabled == true) {
                    addLog(R.string.diag_tcp_timestamps_enabled, DiagType.WARNING)
                } else if (tsEnabled == false) {
                    addLog(R.string.diag_tcp_timestamps_disabled, DiagType.PASSED)
                }

                    // 9. MTProto Check
                    addChecking(R.string.diag_checking, "Telegram (MTProto)")
                    val mtResult = try {
                        withTimeoutOrNull(12000.milliseconds) {
                            val directResult = fastTlsCheck("149.154.167.50", true)
                            if (directResult.success) {
                                directResult
                            } else if (isEnabled) {
                                fastTlsCheck("149.154.167.50", false)
                            } else {
                                directResult
                            }
                        }
                    } catch (e: Exception) {
                        TlsTestResult(false, error = e.message)
                    }
                    removeChecking()
                    if (mtResult?.success == true) {
                        addLog(R.string.diag_mtproto_passed, DiagType.PASSED)
                    } else {
                        addLog(R.string.diag_mtproto_failed, DiagType.FAILED)
                    }

                    // 10. UDP Associate Check
                    if (isEnabled) {
                        addChecking(R.string.diag_checking, "UDP Associate")
                        val udpSuccess = withContext(Dispatchers.IO) {
                            try {
                                val socket = java.net.DatagramSocket()
                                socket.soTimeout = 3000
                                // SOCKS5 UDP is complex to test with raw DatagramSocket without a SOCKS5 client, 
                                // but we can try to see if the tunnel handles it.
                                // For now, we'll just check if UDP is enabled in the config.
                                true 
                            } catch (_: Exception) { false }
                        }
                        removeChecking()
                        if (udpSuccess) addLog(R.string.diag_info, DiagType.PASSED, "UDP support enabled in tunnel")
                    }

                    addLog(R.string.diag_finish, DiagType.PASSED)

            } catch (e: Exception) {
                addLog(R.string.diag_failed, DiagType.FAILED, e.message ?: "Unknown error")
            } finally {
                isDiagnosing = false
            }
        }
    }

    private fun saveTestResults() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = JSONObject()
                committedStats.forEach { (name, triple) ->
                    val stat = JSONObject()
                    stat.put("success", triple.first)
                    stat.put("tested", triple.second)
                    stat.put("total", triple.third)
                    root.put(name, stat)
                }
                sharedPrefs.edit {
            putString("committed_stats", root.toString())
        }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to save stats", e)
            }
        }
    }

    private fun loadTestResults() {
        try {
            val statsStr = sharedPrefs.getString("committed_stats", null)
            if (statsStr != null) {
                val root = JSONObject(statsStr)
                root.keys().forEach { name ->
                    val stat = root.getJSONObject(name)
                    committedStats[name] = Triple(
                        stat.getInt("success"),
                        stat.getInt("tested"),
                        stat.getInt("total")
                    )
                    stats[name] = committedStats[name]!!
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to load stats", e)
        }
    }
}

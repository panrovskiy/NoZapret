package com.example.nozapret

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nozapret.core.*
import com.example.nozapret.data.DataStoreManager
import com.example.nozapret.data.SettingsManager
import com.example.nozapret.services.DpiVpnService
import com.example.nozapret.services.TestingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val ACTION_STOP_TESTING = "com.example.nozapret.STOP_TESTING"
        const val EXTRA_STRATEGY_NAME = "strategy_name"
    }

    data class UpdateInfo(val version: String, val downloadUrl: String, val changelog: String)
    data class TlsTestResult(
        val success: Boolean, val ping: Long? = null, val protocol: String? = null,
        val cipherSuite: String? = null, val error: String? = null,
        val httpSuccess: Boolean = false, val httpError: String? = null
    )

    private val settingsManager = SettingsManager(application, viewModelScope)
    private val strategyTester = StrategyTester(application)
    private val updateManager = UpdateManager(application)
    private val sharedPrefs: SharedPreferences = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Expose settings properties for UI compatibility
    var selectedStrategy by settingsManager::selectedStrategy
    val pinnedStrategies get() = settingsManager.pinnedStrategies
    var customArgs by settingsManager::customArgs
    var dnsServer by settingsManager::dnsServer
    val allowedApps get() = settingsManager.allowedApps
    var proxyHost by settingsManager::proxyHost
    var proxyPort by settingsManager::proxyPort
    var excludeSelf by settingsManager::excludeSelf
    var globalMode by settingsManager::globalMode
    var customHostList by settingsManager::customHostList
    val selectedPresets get() = settingsManager.selectedPresets
    var themeMode by settingsManager::themeMode
    var selectedLanguage by settingsManager::selectedLanguage
    var autoConnect by settingsManager::autoConnect
    var enableIpv6 by settingsManager::enableIpv6
    var customPrimaryColor by settingsManager::customPrimaryColor
    var customThemeBase by settingsManager::customThemeBase
    var runMode by settingsManager::runMode

    // UI State
    val snackbarHostState = SnackbarHostState()
    var isEnabled by mutableStateOf(false)
    var isPaused by mutableStateOf(false)
    var isConnecting by mutableStateOf(false)
    var isDisconnecting by mutableStateOf(false)
    var isError by mutableStateOf(false)
    var vpnStartTime by mutableLongStateOf(0L)
    var isIgnoringBattery by mutableStateOf(true)
    var settingsTab by mutableIntStateOf(0)

    var isCheckingUpdates by mutableStateOf(false)
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
    var isDownloadingUpdate by mutableStateOf(false)
    var updateDownloadProgress by mutableStateOf(0f)
    var showUpdateDialog by mutableStateOf(false)

    // Diagnostics State
    enum class DiagAction { NONE, BATTERY_OPTIMIZATION, DISABLE_IPV6, RESTART_VPN, STOP_VPN }
    enum class DiagType { INFO, PASSED, FAILED, WARNING }
    data class DiagItem(val message: String, val type: DiagType, val isChecking: Boolean = false, val action: DiagAction = DiagAction.NONE, val solutionTextResId: Int? = null)

    val diagnosticsLog = mutableStateListOf<DiagItem>()
    var isDiagnosing by mutableStateOf(false)
    var showDiagnosticsDialog by mutableStateOf(false)

    var strategiesTestedCount by mutableIntStateOf(0)
    val stats = mutableStateMapOf<String, Triple<Int, Int, Int>>()
    val committedStats = mutableStateMapOf<String, Triple<Int, Int, Int>>()
    val testResults = mutableStateMapOf<String, Map<String, TlsTestResult>>()
    val currentlyTesting = mutableStateListOf<String>()

    val logLines = mutableStateListOf<String>()
    private var logJob: Job? = null
    private var allTestsJob: Job? = null

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

    val sitesToTestCount: Int get() = bypassedSites.filter { !it.contains("/") }.size

    init {
        // Consolidate initialization
        viewModelScope.launch {
            VpnController.stateFlow.collectLatest { state ->
                isEnabled = state.isRunning
                isPaused = state.isPaused
                isConnecting = state.isConnecting
                isDisconnecting = state.isDisconnecting
                isError = state.isError
                vpnStartTime = state.startTime
            }
        }
        loadTestResults()
        startLogCollection()
        checkForUpdates(manual = false)
        observeTestingService()
    }

    private fun startLogCollection() {
        if (logJob?.isActive == true) return
        logJob = viewModelScope.launch(Dispatchers.IO) {
            val tags = "proxy:D MainViewModel:D ByeDpiProxy:D DpiVpnService:D TestingService:D StrategyTester:D NoZapretNative:D"
            try {
                // Use -T 200 to get recent history
                val process = ProcessBuilder("logcat", "-v", "time", "-T", "200", "*:S", "proxy:D", "MainViewModel:D", "ByeDpiProxy:D", "DpiVpnService:D", "TestingService:D", "StrategyTester:D", "NoZapretNative:D")
                    .redirectErrorStream(true)
                    .start()
                
                val reader = process.inputStream.bufferedReader()
                val buffer = mutableListOf<String>()
                var lastUpdate = System.currentTimeMillis()

                while (isActive) {
                    val line = try { reader.readLine() } catch (_: Exception) { null } ?: break
                    if (line.isBlank()) continue
                    buffer.add(line)
                    val now = System.currentTimeMillis()
                    if ((now - lastUpdate > 1000) || (buffer.size > 100)) {
                        val batch = buffer.toList()
                        buffer.clear()
                        lastUpdate = now
                        withContext(Dispatchers.Main) {
                            logLines.addAll(batch)
                            if (logLines.size > 3000) repeat(logLines.size - 3000) { logLines.removeAt(0) }
                        }
                    }
                }
                process.destroy()
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
                ProcessBuilder("logcat", "-c").start().waitFor()
                withContext(Dispatchers.Main) { logLines.clear() }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to clear logs", e)
            }
        }
    }

    fun updateVpnState(running: Boolean, paused: Boolean = false, connecting: Boolean = false, disconnecting: Boolean = false, error: Boolean = false, start: Long = 0L) {
        isEnabled = running
        isPaused = paused
        isConnecting = connecting
        isDisconnecting = disconnecting
        isError = error
        vpnStartTime = start
    }

    fun togglePreset(name: String, enabled: Boolean) = settingsManager.togglePreset(name, enabled)
    fun togglePinStrategy(name: String) = settingsManager.togglePinStrategy(name)
    fun updateSelectedStrategy(s: String) = settingsManager.updateSelectedStrategy(s)
    fun updateCustomArgs(a: String) = settingsManager.updateCustomArgs(a)
    
    fun onPasteCustomArgs() {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotBlank()) {
                updateCustomArgs(text)
            }
        }
    }

    fun onClearCustomArgs() {
        updateCustomArgs("")
    }

    fun updateAutoConnect(e: Boolean) = settingsManager.updateAutoConnect(e)
    fun updateEnableIpv6(e: Boolean) = settingsManager.updateEnableIpv6(e)
    fun updateDnsServer(d: String) = settingsManager.updateDnsServer(d)
    fun toggleAllowedApp(p: String) = settingsManager.toggleAllowedApp(p)
    fun updateProxyHost(h: String) = settingsManager.updateProxyHost(h)
    fun updateProxyPort(p: String) = settingsManager.updateProxyPort(p)
    fun updateExcludeSelf(e: Boolean) = settingsManager.updateExcludeSelf(e)
    fun updateGlobalMode(g: Boolean) = settingsManager.updateGlobalMode(g)
    fun updateCustomHostList(h: String) = settingsManager.updateCustomHostList(h)
    fun updateThemeMode(m: String) = settingsManager.updateThemeMode(m)
    fun updateSelectedLanguage(l: String) {
        settingsManager.updateSelectedLanguage(l)
        applyLanguage(l)
    }

    private fun applyLanguage(lang: String) {
        val localeList = if (lang == "System") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun updateCustomPrimaryColor(c: Int) = settingsManager.updateCustomPrimaryColor(c)
    fun updateCustomThemeBase(b: String) = settingsManager.updateCustomThemeBase(b)
    fun updateRunMode(m: String) = settingsManager.updateRunMode(m)

    private fun observeTestingService() {
        var lastReportedStrategy = ""
        viewModelScope.launch {
            TestingService.statusFlow.collectLatest { status ->
                val strategy = status.strategyName
                if (strategy.isEmpty()) return@collectLatest
                
                if (status.isRunning) {
                    if (lastReportedStrategy.isNotEmpty() && lastReportedStrategy != strategy) {
                        // Previous strategy in batch finished
                        currentlyTesting.remove(lastReportedStrategy)
                        committedStats[lastReportedStrategy] = stats[lastReportedStrategy] ?: Triple(0, 0, 0)
                    }
                    
                    if (!currentlyTesting.contains(strategy)) currentlyTesting.add(strategy)
                    stats[strategy] = Triple(status.success, status.tested, status.total)
                    lastReportedStrategy = strategy
                    
                    status.lastResult?.let { result ->
                        val currentMap = testResults[strategy] ?: emptyMap()
                        testResults[strategy] = currentMap + (status.lastSite to result)
                    }
                } else {
                    // Batch or single test finished
                    currentlyTesting.forEach { name ->
                        committedStats[name] = stats[name] ?: Triple(0, 0, 0)
                    }
                    currentlyTesting.clear()
                    lastReportedStrategy = ""
                    saveTestResults()
                }
            }
        }
    }

    fun testStrategy(strategyName: String) {
        if (currentlyTesting.contains(strategyName)) {
            stopTest(strategyName)
            return
        }
        
        stopAllTests()
        if (bypassedSites.isEmpty()) {
            viewModelScope.launch {
                snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.error_no_sites_testing))
            }
            return
        }
        
        viewModelScope.launch {
            val success = VpnController.runWithVpnStopped(getApplication()) {
                val intent = Intent(getApplication(), TestingService::class.java).apply {
                    action = TestingService.ACTION_START_TEST
                    putExtra(TestingService.EXTRA_STRATEGY, strategyName)
                    putExtra(TestingService.EXTRA_CUSTOM_ARGS, customArgs)
                    putStringArrayListExtra(TestingService.EXTRA_SITES, ArrayList(bypassedSites.filter { it.isNotBlank() && !it.contains("/") }))
                }
                getApplication<Application>().startForegroundService(intent)
            }
            if (!success) {
                snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.error_vpn_stop_failed))
            }
        }
    }

    fun stopTest(strategyName: String) {
        val intent = Intent(getApplication(), TestingService::class.java).apply {
            action = TestingService.ACTION_STOP_TEST
        }
        getApplication<Application>().startService(intent)
        currentlyTesting.remove(strategyName)
    }

    fun stopAllTests() {
        allTestsJob?.cancel()
        val intent = Intent(getApplication(), TestingService::class.java).apply {
            action = TestingService.ACTION_STOP_TEST
        }
        getApplication<Application>().startService(intent)
        currentlyTesting.clear()
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
        if (allTestsJob?.isActive == true || TestingService.isRunning) {
            stopAllTests()
            return
        }
        if (bypassedSites.isEmpty()) {
            viewModelScope.launch {
                snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.error_no_sites_testing))
            }
            return
        }
        
        allTestsJob = viewModelScope.launch {
            VpnController.runWithVpnStopped(getApplication()) {
                val strategies = Config.STRATEGIES.map { it.first }
                val intent = Intent(getApplication(), TestingService::class.java).apply {
                    action = TestingService.ACTION_START_TEST_BATCH
                    putExtra(TestingService.EXTRA_STRATEGIES, ArrayList(strategies))
                    putExtra(TestingService.EXTRA_CUSTOM_ARGS, customArgs)
                    putStringArrayListExtra(TestingService.EXTRA_SITES, ArrayList(bypassedSites.filter { it.isNotBlank() && !it.contains("/") }))
                }
                getApplication<Application>().startForegroundService(intent)
            }
        }
    }


    fun resetTests() {
        viewModelScope.launch {
            stopAllTests()
            stats.clear()
            committedStats.clear()
            testResults.clear()
            saveTestResults()
        }
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (isCheckingUpdates) return
        isCheckingUpdates = true
        viewModelScope.launch {
            val info = updateManager.checkForUpdates()
            if (info != null) {
                updateInfo = info
                showUpdateDialog = true
            } else if (manual) {
                snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.msg_latest_version))
            }
            isCheckingUpdates = false
        }
    }

    fun downloadAndInstallUpdate() {
        val info = updateInfo ?: return
        viewModelScope.launch {
            isDownloadingUpdate = true
            updateManager.downloadProgress.collectLatest { updateDownloadProgress = it }
            val success = updateManager.downloadAndInstall(info)
            if (!success) {
                snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.error_download_failed, "Unknown error"))
            }
            isDownloadingUpdate = false
        }
    }

    fun runQuickTest() {
        if (isQuickTesting) return
        val url = quickTestUrl.trim().lowercase().removePrefix("https://").removePrefix("http://").split("/").first()
        if (url.isEmpty() || !url.contains(".")) {
            viewModelScope.launch { snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.error_invalid_domain)) }
            return
        }
        isQuickTesting = true
        quickTestResult = null
        viewModelScope.launch {
            VpnController.runWithVpnStopped(getApplication()) {
                strategyTester.testStrategy(quickTestStrategy, listOf(url), customArgs,
                    onResult = { _, result, _, _, _ -> quickTestResult = result }
                )
            }
            isQuickTesting = false
        }
    }

    fun runDiagnostics() {
        if (isDiagnosing) return
        isDiagnosing = true
        diagnosticsLog.clear()
        showDiagnosticsDialog = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                addLog(R.string.diag_checking, DiagType.INFO, "Connectivity")
                val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val active = cm.activeNetwork
                if (active != null) addLog(R.string.diag_network_connected, DiagType.PASSED, "OK")
                else addLog(R.string.diag_network_disconnected, DiagType.FAILED)

                addLog(R.string.diag_checking, DiagType.INFO, "Native Engine")
                try {
                    System.loadLibrary("byedpi")
                    addLog(R.string.diag_byedpi_load_success, DiagType.PASSED)
                } catch (e: Exception) {
                    addLog(R.string.diag_byedpi_load_failed, DiagType.FAILED)
                }
                
                addLog(R.string.diag_checking, DiagType.INFO, "VPN Permission")
                if (android.net.VpnService.prepare(getApplication()) == null) {
                    addLog(R.string.diag_vpn_prepared, DiagType.PASSED)
                } else {
                    addLog(R.string.diag_vpn_not_prepared, DiagType.FAILED)
                }

                addLog(R.string.diag_finish, DiagType.PASSED)
            } finally {
                isDiagnosing = false
            }
        }
    }

    private fun addLog(resId: Int, type: DiagType, vararg args: Any) {
        val msg = getApplication<Application>().getString(resId, *args)
        viewModelScope.launch(Dispatchers.Main) {
            diagnosticsLog.add(DiagItem(msg, type))
        }
    }

    private fun saveTestResults() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = JSONObject()
                committedStats.forEach { (name, triple) ->
                    val stat = JSONObject().apply {
                        put("success", triple.first)
                        put("tested", triple.second)
                        put("total", triple.third)
                    }
                    root.put(name, stat)
                }
                sharedPrefs.edit { putString("committed_stats", root.toString()) }
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
                    committedStats[name] = Triple(stat.getInt("success"), stat.getInt("tested"), stat.getInt("total"))
                    stats[name] = committedStats[name]!!
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to load stats", e)
        }
    }

    fun exportConfiguration(context: Context) {
        // ...
    }

    fun exportLogs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logFiles = AppLogger.getLogFiles(context)
                if (logFiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(context.getString(R.string.error_no_logs))
                    }
                    return@launch
                }

                // Create a temporary zip file or just send the latest log
                val latestLog = logFiles.first()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", latestLog)
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_export_logs)))
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
                    if (config.has("customHosts")) updateCustomHostList(config.getString("customHosts"))

                    if (config.has("presets")) {
                        val presets = config.getJSONArray("presets")
                        selectedPresets.clear()
                        for (i in 0 until presets.length()) selectedPresets.add(presets.getString(i))
                    }
                    if (config.has("allowedApps")) {
                        val apps = config.getJSONArray("allowedApps")
                        allowedApps.clear()
                        for (i in 0 until apps.length()) allowedApps.add(apps.getString(i))
                    }
                    if (config.has("pinned")) {
                        val pinned = config.getJSONArray("pinned")
                        pinnedStrategies.clear()
                        for (i in 0 until pinned.length()) pinnedStrategies.add(pinned.getString(i))
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
}

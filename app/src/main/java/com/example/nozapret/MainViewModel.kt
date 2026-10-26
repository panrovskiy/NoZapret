package com.example.nozapret

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Uri
import android.net.VpnService
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        val httpSuccess: Boolean = false, val httpError: String? = null,
        val dnsResults: List<DnsTestResult> = emptyList()
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
    var enableQuic by settingsManager::enableQuic
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
    var updateDownloadProgress by mutableFloatStateOf(0f)
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

    fun getPresetDomains(name: String): List<String> = settingsManager.getPresetDomains(name)
    fun addDomainToPreset(name: String, rawDomain: String): Result<Unit> = settingsManager.addDomainToPreset(name, rawDomain)
    fun removeDomainFromPreset(name: String, domain: String) = settingsManager.removeDomainFromPreset(name, domain)
    fun editDomainInPreset(name: String, oldDomain: String, newRawDomain: String): Result<Unit> = settingsManager.editDomainInPreset(name, oldDomain, newRawDomain)
    fun clearPresetDomains(name: String) = settingsManager.clearPresetDomains(name)
    fun resetPresetDomainsToDefault(name: String) = settingsManager.resetPresetDomainsToDefault(name)

    val fakeSniPool get() = settingsManager.fakeSniPool
    val customStrategies get() = settingsManager.customStrategies

    fun addFakeSniHost(rawHost: String): Result<Unit> = settingsManager.addFakeSniHost(rawHost)
    fun removeFakeSniHost(host: String) = settingsManager.removeFakeSniHost(host)
    fun editFakeSniHost(oldHost: String, newRawHost: String): Result<Unit> = settingsManager.editFakeSniHost(oldHost, newRawHost)
    fun resetFakeSniPoolToDefault() = settingsManager.resetFakeSniPoolToDefault()

    fun getFakeSniForStrategy(strategyName: String): String? = settingsManager.getFakeSniForStrategy(strategyName)
    fun setFakeSniForStrategy(strategyName: String, rawFakeSni: String?): Result<Unit> = settingsManager.setFakeSniForStrategy(strategyName, rawFakeSni)
    fun resetFakeSniForStrategy(strategyName: String) = settingsManager.resetFakeSniForStrategy(strategyName)

    fun updateEnableQuic(enabled: Boolean) = settingsManager.updateEnableQuic(enabled)

    fun getEffectiveDnsForStrategy(strategyName: String): String {
        val customStrat = getCustomStrategyByName(strategyName)
        if (customStrat != null && !customStrat.customDns.isNullOrBlank()) {
            return customStrat.customDns.trim()
        }
        return dnsServer.trim().ifEmpty { "1.1.1.1" }
    }

    fun addCustomStrategy(strategy: Config.CustomStrategy): Result<Unit> = settingsManager.addCustomStrategy(strategy)
    fun updateCustomStrategy(strategy: Config.CustomStrategy): Result<Unit> = settingsManager.updateCustomStrategy(strategy)
    fun deleteCustomStrategy(id: String) = settingsManager.deleteCustomStrategy(id)
    fun duplicateCustomStrategy(id: String): Config.CustomStrategy? = settingsManager.duplicateCustomStrategy(id)
    fun getCustomStrategyById(id: String): Config.CustomStrategy? = settingsManager.getCustomStrategyById(id)
    fun getCustomStrategyByName(name: String): Config.CustomStrategy? = settingsManager.getCustomStrategyByName(name)

    fun getStrategyAveragePing(strategyName: String): Double? {
        val results = testResults[strategyName] ?: return null
        val validPings = results.values.filter { it.success && it.ping != null && it.ping > 0 }.mapNotNull { it.ping }
        if (validPings.isEmpty()) return null
        val avg = validPings.average()
        return if (avg.isNaN() || avg <= 0) null else avg
    }

    val bypassedSites: List<String>
        get() {
            val presetSites = selectedPresets.asSequence()
                .filter { it != "Custom" }
                .flatMap { presetName -> settingsManager.getPresetDomains(presetName) }
                .toList()
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
            try {
                val initialAppLogs = AppLogger.getRecentLogs().map { entry ->
                    val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
                    "[$timeStr] [${entry.level}] [${entry.tag}] ${entry.message}"
                }
                withContext(Dispatchers.Main) {
                    logLines.clear()
                    logLines.addAll(initialAppLogs)
                }

                val process = ProcessBuilder("logcat", "-v", "time", "-T", "100")
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
                    if ((now - lastUpdate > 1000) || (buffer.size > 50)) {
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

    val vpnState get() = VpnController.stateFlow.value

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

    fun getBestStrategy(): String {
        val candidateStats = Config.STRATEGIES_DATA
            .filter { it.name != "Auto (Recommended)" && it.name != "Custom" }
            .mapNotNull { strat ->
                val name = strat.name
                val stat = committedStats[name] ?: stats[name]
                if (stat != null && stat.first > 0) {
                    val results = testResults[name] ?: emptyMap()
                    val pings = results.values.mapNotNull { it.ping }
                    val avgPing = if (pings.isNotEmpty()) pings.average() else Double.MAX_VALUE
                    Triple(name, stat.first, avgPing)
                } else null
            }

        if (candidateStats.isEmpty()) {
            return "Modern Ultra"
        }

        return candidateStats.sortedWith(
            compareByDescending<Triple<String, Int, Double>> { it.second }
                .thenBy { it.third }
        ).first().first
    }

    private fun observeTestingService() {
        var lastReportedStrategy = ""
        viewModelScope.launch {
            TestingService.statusFlow.collect { status ->
                val strategy = status.strategyName
                if (strategy.isEmpty()) return@collect
                
                withContext(Dispatchers.Main) {
                    if (status.isRunning) {
                        if (lastReportedStrategy.isNotEmpty() && lastReportedStrategy != strategy) {
                            currentlyTesting.remove(lastReportedStrategy)
                            committedStats[lastReportedStrategy] = stats[lastReportedStrategy] ?: Triple(0, 0, 0)
                        }
                        
                        if (!currentlyTesting.contains(strategy)) currentlyTesting.add(strategy)
                        stats[strategy] = Triple(status.success, status.tested, status.total)
                        lastReportedStrategy = strategy
                        
                        status.lastResult?.let { result ->
                            if (status.lastSite.isNotEmpty()) {
                                val currentMap = testResults[strategy] ?: emptyMap()
                                testResults[strategy] = currentMap + (status.lastSite to result)
                            }
                        }
                    } else {
                        if (strategy.isNotEmpty()) {
                            stats[strategy] = Triple(status.success, status.tested, status.total)
                            status.lastResult?.let { result ->
                                if (status.lastSite.isNotEmpty()) {
                                    val currentMap = testResults[strategy] ?: emptyMap()
                                    testResults[strategy] = currentMap + (status.lastSite to result)
                                }
                            }
                            committedStats[strategy] = stats[strategy] ?: Triple(0, 0, 0)
                        }
                        if (lastReportedStrategy.isNotEmpty()) {
                            committedStats[lastReportedStrategy] = stats[lastReportedStrategy] ?: Triple(0, 0, 0)
                        }
                        currentlyTesting.toList().forEach { name ->
                            committedStats[name] = stats[name] ?: Triple(0, 0, 0)
                        }
                        currentlyTesting.clear()
                        lastReportedStrategy = ""
                        saveTestResults()
                    }
                }
            }
        }
    }

    var isSubmittingTest by mutableStateOf(false)
        private set

    fun testCustomStrategyFromUi(strategyName: String) {
        if (isSubmittingTest) return
        isSubmittingTest = true

        try {
            // Always switch to Strategy Test tab (settingsTab = 1)
            settingsTab = 1

            if (currentlyTesting.contains(strategyName)) {
                return
            }

            testStrategy(strategyName)
        } finally {
            viewModelScope.launch {
                delay(300.milliseconds)
                isSubmittingTest = false
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
                val effectiveDns = getEffectiveDnsForStrategy(strategyName)
                val intent = Intent(getApplication(), TestingService::class.java).apply {
                    action = TestingService.ACTION_START_TEST
                    putExtra(TestingService.EXTRA_STRATEGY, strategyName)
                    putExtra(TestingService.EXTRA_CUSTOM_ARGS, customArgs)
                    putExtra(TestingService.EXTRA_ENABLE_QUIC, enableQuic)
                    putExtra(TestingService.EXTRA_DNS_SERVER, effectiveDns)
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
        val customStrat = getCustomStrategyByName(name)
        val args = customStrat?.userArgs ?: customArgs

        if (DpiVpnService.isRunning) {
            VpnController.startVpn(
                getApplication(),
                name,
                args,
                globalMode,
                bypassedSites
            )
        }

        viewModelScope.launch {
            snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.msg_strategy_applied, name))
        }
    }

    fun useAsCustom(name: String) {
        val args = Config.getStrategyArgs(name, customArgs, fakeSni = null, enableQuic = enableQuic).joinToString(" ")
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
                    putExtra(TestingService.EXTRA_ENABLE_QUIC, enableQuic)
                    putExtra(TestingService.EXTRA_DNS_SERVER, dnsServer)
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

    val updateState get() = updateManager.updateState
    val updateDownloadBytesFormatted get() = updateManager.downloadBytesFormatted
    val updateError get() = updateManager.updateError

    fun cancelUpdateDownload() {
        updateManager.cancelDownload()
        isDownloadingUpdate = false
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (isCheckingUpdates) return
        isCheckingUpdates = true
        updateManager.resetState()
        viewModelScope.launch {
            val info = updateManager.checkForUpdates()
            if (info != null) {
                updateInfo = info
                showUpdateDialog = true
            } else if (manual) {
                val err = updateManager.updateError.value
                if (err != null) {
                    snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.error_update_check, err))
                } else {
                    snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.msg_latest_version))
                }
            }
            isCheckingUpdates = false
        }
    }

    fun downloadAndInstallUpdate() {
        val info = updateInfo ?: return
        viewModelScope.launch {
            isDownloadingUpdate = true
            val progressJob = launch {
                updateManager.downloadProgress.collectLatest { updateDownloadProgress = it }
            }
            val success = updateManager.downloadAndInstall(info)
            progressJob.cancel()
            isDownloadingUpdate = false
            if (!success && updateManager.updateState.value == UpdateState.FAILED) {
                val err = updateManager.updateError.value ?: "Download failed"
                snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.error_download_failed, err))
            }
        }
    }

    fun openInstallPermissionSettings() {
        updateManager.openInstallPermissionSettings()
    }

    fun retryInstallApk() {
        viewModelScope.launch {
            updateManager.installDownloadedApk()
        }
    }

    fun onResumeCheckUpdatePermissions() {
        if (updateManager.updateState.value == UpdateState.INSTALL_PERMISSION_REQUIRED && updateManager.canRequestPackageInstalls()) {
            updateManager.installDownloadedApk()
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
                // 1. Network Connectivity
                val netCheckingMsg = getApplication<Application>().getString(R.string.diag_checking, "Network")
                val netIndex = addLogItemAndReturnIndex(netCheckingMsg, DiagType.INFO, isChecking = true)
                delay(400.milliseconds)
                val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val active = cm.activeNetwork
                val netMsg = if (active != null) getApplication<Application>().getString(R.string.diag_network_connected, "Active") else getApplication<Application>().getString(R.string.diag_network_disconnected)
                updateLogItem(netIndex, netMsg, if (active != null) DiagType.PASSED else DiagType.FAILED)

                // 2. Native Engine
                delay(300.milliseconds)
                val engineCheckingMsg = getApplication<Application>().getString(R.string.diag_checking, "Native Engine (ByeDPI)")
                val engineIndex = addLogItemAndReturnIndex(engineCheckingMsg, DiagType.INFO, isChecking = true)
                delay(400.milliseconds)
                val engineOk = try {
                    System.loadLibrary("byedpi")
                    true
                } catch (_: Exception) {
                    false
                }
                val engineMsg = if (engineOk) getApplication<Application>().getString(R.string.diag_byedpi_load_success) else getApplication<Application>().getString(R.string.diag_byedpi_load_failed)
                updateLogItem(engineIndex, engineMsg, if (engineOk) DiagType.PASSED else DiagType.FAILED)

                // 3. VPN Permission
                delay(300.milliseconds)
                val vpnCheckingMsg = getApplication<Application>().getString(R.string.diag_checking, "VPN Permission")
                val vpnIndex = addLogItemAndReturnIndex(vpnCheckingMsg, DiagType.INFO, isChecking = true)
                delay(400.milliseconds)
                val vpnPrepared = VpnService.prepare(getApplication()) == null
                val vpnMsg = if (vpnPrepared) getApplication<Application>().getString(R.string.diag_vpn_prepared) else getApplication<Application>().getString(R.string.diag_vpn_not_prepared)
                updateLogItem(vpnIndex, vpnMsg, if (vpnPrepared) DiagType.PASSED else DiagType.WARNING)

                // 4. DNS Server check
                delay(300.milliseconds)
                val dnsCheckingMsg = getApplication<Application>().getString(R.string.diag_checking, "DNS Resolution")
                val dnsIndex = addLogItemAndReturnIndex(dnsCheckingMsg, DiagType.INFO, isChecking = true)
                delay(400.milliseconds)
                val dnsOk = try {
                    val addr = InetAddress.getByName("google.com")
                    addr != null
                } catch (_: Exception) {
                    false
                }
                val dnsMsg = if (dnsOk) getApplication<Application>().getString(R.string.diag_dns_server, dnsServer) else "DNS Resolution Failed"
                updateLogItem(dnsIndex, dnsMsg, if (dnsOk) DiagType.PASSED else DiagType.FAILED)

                // Finish
                delay(300.milliseconds)
                addLog(R.string.diag_finish, DiagType.PASSED)
            } finally {
                isDiagnosing = false
            }
        }
    }

    private suspend fun addLogItemAndReturnIndex(msg: String, type: DiagType, isChecking: Boolean): Int {
        return withContext(Dispatchers.Main) {
            diagnosticsLog.add(DiagItem(msg, type, isChecking = isChecking))
            diagnosticsLog.size - 1
        }
    }

    private suspend fun updateLogItem(index: Int, msg: String, type: DiagType) {
        withContext(Dispatchers.Main) {
            if (index in diagnosticsLog.indices) {
                diagnosticsLog[index] = DiagItem(msg, type, isChecking = false)
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
        val statsSnapshot = committedStats.toMap()
        val resultsSnapshot = testResults.mapValues { it.value.toMap() }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = JSONObject()
                statsSnapshot.forEach { (name, triple) ->
                    val statObj = JSONObject().apply {
                        put("success", triple.first)
                        put("tested", triple.second)
                        put("total", triple.third)

                        val stratResults = resultsSnapshot[name]
                        if (!stratResults.isNullOrEmpty()) {
                            val resultsObj = JSONObject()
                            var stratDnsResults: List<DnsTestResult> = emptyList()

                            stratResults.forEach { (site, res) ->
                                if (res.dnsResults.isNotEmpty()) {
                                    stratDnsResults = res.dnsResults
                                }
                                val resObj = JSONObject().apply {
                                    put("success", res.success)
                                    res.ping?.let { put("ping", it) }
                                    res.protocol?.let { put("protocol", it) }
                                    res.cipherSuite?.let { put("cipherSuite", it) }
                                    res.error?.let { put("error", it) }
                                    put("httpSuccess", res.httpSuccess)
                                    res.httpError?.let { put("httpError", it) }

                                    if (res.dnsResults.isNotEmpty()) {
                                        val dnsArr = JSONArray()
                                        res.dnsResults.forEach { dns ->
                                            val dnsObj = JSONObject().apply {
                                                put("name", dns.name)
                                                put("ip", dns.ip)
                                                put("success", dns.success)
                                                dns.pingMs?.let { put("pingMs", it) }
                                                dns.error?.let { put("error", it) }
                                                dns.transport?.let { put("transport", it) }
                                            }
                                            dnsArr.put(dnsObj)
                                        }
                                        put("dnsResults", dnsArr)
                                    }
                                }
                                resultsObj.put(site, resObj)
                            }
                            put("results", resultsObj)

                            if (stratDnsResults.isNotEmpty()) {
                                val dnsArr = JSONArray()
                                stratDnsResults.forEach { dns ->
                                    val dnsObj = JSONObject().apply {
                                        put("name", dns.name)
                                        put("ip", dns.ip)
                                        put("success", dns.success)
                                        dns.pingMs?.let { put("pingMs", it) }
                                        dns.error?.let { put("error", it) }
                                        dns.transport?.let { put("transport", it) }
                                    }
                                    dnsArr.put(dnsObj)
                                }
                                put("dnsResults", dnsArr)
                            }
                        }
                    }
                    root.put(name, statObj)
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
                    val success = stat.getInt("success")
                    val tested = stat.getInt("tested")
                    val total = stat.getInt("total")
                    committedStats[name] = Triple(success, tested, total)
                    stats[name] = Triple(success, tested, total)

                    val topDnsList = mutableListOf<DnsTestResult>()
                    if (stat.has("dnsResults")) {
                        val dnsArr = stat.getJSONArray("dnsResults")
                        for (i in 0 until dnsArr.length()) {
                            val dObj = dnsArr.getJSONObject(i)
                            topDnsList.add(
                                DnsTestResult(
                                    name = dObj.getString("name"),
                                    ip = dObj.getString("ip"),
                                    success = dObj.getBoolean("success"),
                                    pingMs = if (dObj.has("pingMs")) dObj.getLong("pingMs") else null,
                                    error = if (dObj.has("error")) dObj.getString("error") else null,
                                    transport = if (dObj.has("transport")) dObj.getString("transport") else null
                                )
                            )
                        }
                    }

                    if (stat.has("results")) {
                        val resultsObj = stat.getJSONObject("results")
                        val resultMap = mutableMapOf<String, TlsTestResult>()
                        resultsObj.keys().forEach { site ->
                            val r = resultsObj.getJSONObject(site)
                            val siteDnsList = mutableListOf<DnsTestResult>()
                            if (r.has("dnsResults")) {
                                val dnsArr = r.getJSONArray("dnsResults")
                                for (i in 0 until dnsArr.length()) {
                                    val dObj = dnsArr.getJSONObject(i)
                                    siteDnsList.add(
                                        DnsTestResult(
                                            name = dObj.getString("name"),
                                            ip = dObj.getString("ip"),
                                            success = dObj.getBoolean("success"),
                                            pingMs = if (dObj.has("pingMs")) dObj.getLong("pingMs") else null,
                                            error = if (dObj.has("error")) dObj.getString("error") else null,
                                            transport = if (dObj.has("transport")) dObj.getString("transport") else null
                                        )
                                    )
                                }
                            }
                            val finalDnsList = if (siteDnsList.isNotEmpty()) siteDnsList else topDnsList
                            val res = TlsTestResult(
                                success = r.getBoolean("success"),
                                ping = if (r.has("ping")) r.getLong("ping") else null,
                                protocol = if (r.has("protocol")) r.getString("protocol") else null,
                                cipherSuite = if (r.has("cipherSuite")) r.getString("cipherSuite") else null,
                                error = if (r.has("error")) r.getString("error") else null,
                                httpSuccess = if (r.has("httpSuccess")) r.getBoolean("httpSuccess") else false,
                                httpError = if (r.has("httpError")) r.getString("httpError") else null,
                                dnsResults = finalDnsList
                            )
                            resultMap[site] = res
                        }
                        testResults[name] = resultMap
                    }
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
                val reportFile = AppLogger.createConsolidatedReport(context)
                if (reportFile == null || !reportFile.exists()) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(context.getString(R.string.error_no_logs))
                    }
                    return@launch
                }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", reportFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, context.getString(R.string.btn_export_logs)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "Failed to export logs", e)
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Export failed: ${e.message}")
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

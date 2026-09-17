package com.example.nozapret.data

import android.app.Application
import androidx.compose.runtime.*
import com.example.nozapret.core.Config
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Manages UI state and persistence for app settings.
 */
class SettingsManager(private val application: Application, private val scope: CoroutineScope) {
    private val dataStoreManager = DataStoreManager(application)

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
    val selectedPresets = mutableStateListOf<String>()
    var themeMode by mutableStateOf("System")
    var selectedLanguage by mutableStateOf("System")
    var autoConnect by mutableStateOf(false)
    var enableIpv6 by mutableStateOf(false)
    var customPrimaryColor by mutableIntStateOf(0xFF6750A4.toInt())
    var customThemeBase by mutableStateOf("System")
    var runMode by mutableStateOf("VPN")

    init {
        scope.launch {
            val prefs = dataStoreManager.getAllSettings().first()
            selectedStrategy = prefs[DataStoreManager.SELECTED_STRATEGY] ?: "Auto (Recommended)"
            customArgs = prefs[DataStoreManager.CUSTOM_ARGS] ?: ""
            dnsServer = prefs[DataStoreManager.DNS_SERVER] ?: "1.1.1.1"
            proxyHost = prefs[DataStoreManager.PROXY_HOST] ?: Config.DEFAULT_PROXY_HOST
            proxyPort = prefs[DataStoreManager.PROXY_PORT] ?: Config.DEFAULT_PROXY_PORT
            excludeSelf = prefs[DataStoreManager.EXCLUDE_SELF] ?: true
            globalMode = prefs[DataStoreManager.GLOBAL_MODE] ?: false
            customHostList = prefs[DataStoreManager.CUSTOM_HOST_LIST] ?: ""
            themeMode = prefs[DataStoreManager.THEME_MODE] ?: "System"
            selectedLanguage = prefs[DataStoreManager.SELECTED_LANGUAGE] ?: "System"
            autoConnect = prefs[DataStoreManager.AUTO_CONNECT] ?: false
            enableIpv6 = prefs[DataStoreManager.ENABLE_IPV6] ?: false
            customPrimaryColor = prefs[DataStoreManager.CUSTOM_PRIMARY_COLOR] ?: 0xFF6750A4.toInt()
            customThemeBase = prefs[DataStoreManager.CUSTOM_THEME_BASE] ?: "System"
            runMode = prefs[DataStoreManager.RUN_MODE] ?: "VPN"

            prefs[DataStoreManager.PINNED_STRATEGIES]?.let { pinnedStrategies.addAll(it) }
            prefs[DataStoreManager.ALLOWED_APPS]?.let { allowedApps.addAll(it) }
            prefs[DataStoreManager.SELECTED_PRESETS]?.let { selectedPresets.addAll(it) } ?: run {
                selectedPresets.addAll(listOf("YouTube", "Telegram"))
            }
        }
    }

    fun updateSelectedStrategy(value: String) {
        selectedStrategy = value
        save(DataStoreManager.SELECTED_STRATEGY, value)
    }

    fun updateCustomArgs(value: String) {
        customArgs = value
        save(DataStoreManager.CUSTOM_ARGS, value)
    }
    
    fun updateDnsServer(value: String) {
        dnsServer = value
        save(DataStoreManager.DNS_SERVER, value)
    }

    fun updateProxyHost(value: String) {
        proxyHost = value
        save(DataStoreManager.PROXY_HOST, value)
    }

    fun updateProxyPort(value: String) {
        proxyPort = value
        save(DataStoreManager.PROXY_PORT, value)
    }

    fun updateExcludeSelf(value: Boolean) {
        excludeSelf = value
        save(DataStoreManager.EXCLUDE_SELF, value)
    }

    fun updateGlobalMode(value: Boolean) {
        globalMode = value
        save(DataStoreManager.GLOBAL_MODE, value)
    }

    fun updateCustomHostList(value: String) {
        customHostList = value
        save(DataStoreManager.CUSTOM_HOST_LIST, value)
    }

    fun updateThemeMode(value: String) {
        themeMode = value
        save(DataStoreManager.THEME_MODE, value)
    }

    fun updateSelectedLanguage(value: String) {
        selectedLanguage = value
        save(DataStoreManager.SELECTED_LANGUAGE, value)
    }

    fun updateAutoConnect(value: Boolean) {
        autoConnect = value
        save(DataStoreManager.AUTO_CONNECT, value)
    }

    fun updateEnableIpv6(value: Boolean) {
        enableIpv6 = value
        save(DataStoreManager.ENABLE_IPV6, value)
    }

    fun updateCustomPrimaryColor(value: Int) {
        customPrimaryColor = value
        save(DataStoreManager.CUSTOM_PRIMARY_COLOR, value)
    }

    fun updateCustomThemeBase(value: String) {
        customThemeBase = value
        save(DataStoreManager.CUSTOM_THEME_BASE, value)
    }

    fun updateRunMode(value: String) {
        runMode = value
        save(DataStoreManager.RUN_MODE, value)
    }

    fun togglePreset(name: String, enabled: Boolean) {
        if (enabled) {
            if (!selectedPresets.contains(name)) selectedPresets.add(name)
        } else {
            selectedPresets.remove(name)
        }
        save(DataStoreManager.SELECTED_PRESETS, selectedPresets.toSet())
    }

    fun togglePinStrategy(name: String) {
        if (pinnedStrategies.contains(name)) pinnedStrategies.remove(name)
        else pinnedStrategies.add(name)
        save(DataStoreManager.PINNED_STRATEGIES, pinnedStrategies.toSet())
    }

    fun toggleAllowedApp(pkg: String) {
        if (allowedApps.contains(pkg)) allowedApps.remove(pkg)
        else allowedApps.add(pkg)
        save(DataStoreManager.ALLOWED_APPS, allowedApps.toSet())
    }

    private fun <T> save(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        scope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(key, value)
        }
    }
}

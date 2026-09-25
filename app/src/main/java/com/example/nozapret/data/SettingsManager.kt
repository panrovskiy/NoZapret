package com.example.nozapret.data

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.nozapret.core.Config
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

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

    val presetDomains = mutableStateMapOf<String, SnapshotStateList<String>>()
    val fakeSniPool = mutableStateListOf<String>()

    private val defaultFakeSniList = listOf("www.google.com", "yandex.ru", "apple.com", "wikipedia.org")

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

            // Load Fake SNI Pool
            prefs[DataStoreManager.FAKE_SNI_POOL]?.let { set ->
                if (set.isNotEmpty()) {
                    fakeSniPool.clear()
                    fakeSniPool.addAll(set)
                } else {
                    fakeSniPool.clear()
                    fakeSniPool.addAll(defaultFakeSniList)
                }
            } ?: run {
                fakeSniPool.clear()
                fakeSniPool.addAll(defaultFakeSniList)
            }

            // Load Preset Overrides JSON
            val defaultMap = Config.PRESETS.toMap()
            val overridesJsonStr = prefs[DataStoreManager.PRESET_OVERRIDES]
            val overridesMap = mutableMapOf<String, List<String>>()
            if (!overridesJsonStr.isNullOrBlank()) {
                try {
                    val jsonObj = JSONObject(overridesJsonStr)
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val arr = jsonObj.getJSONArray(key)
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            list.add(arr.getString(i))
                        }
                        overridesMap[key] = list
                    }
                } catch (_: Exception) {}
            }

            // Initialize presetDomains map for all known default presets plus any custom overridden presets
            defaultMap.forEach { (name, defaultSites) ->
                val list = mutableStateListOf<String>()
                val loadedList = overridesMap[name] ?: defaultSites
                list.addAll(loadedList)
                presetDomains[name] = list
            }
            overridesMap.forEach { (name, list) ->
                if (!presetDomains.containsKey(name)) {
                    val stateList = mutableStateListOf<String>()
                    stateList.addAll(list)
                    presetDomains[name] = stateList
                }
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

    fun getPresetDomains(name: String): List<String> {
        return presetDomains[name] ?: Config.PRESETS.toMap()[name] ?: emptyList()
    }

    fun addDomainToPreset(name: String, rawDomain: String): Result<Unit> {
        val validated = Config.validateDomain(rawDomain)
        if (validated.isFailure) return Result.failure(validated.exceptionOrNull()!!)
        val domain = validated.getOrThrow()

        val list = presetDomains[name] ?: mutableStateListOf<String>().also { presetDomains[name] = it }
        if (list.contains(domain)) {
            return Result.failure(IllegalArgumentException("Domain already exists in preset"))
        }

        list.add(domain)
        savePresetOverrides()
        return Result.success(Unit)
    }

    fun removeDomainFromPreset(name: String, domain: String) {
        val list = presetDomains[name] ?: return
        if (list.remove(domain)) {
            savePresetOverrides()
        }
    }

    fun editDomainInPreset(name: String, oldDomain: String, newRawDomain: String): Result<Unit> {
        val validated = Config.validateDomain(newRawDomain)
        if (validated.isFailure) return Result.failure(validated.exceptionOrNull()!!)
        val newDomain = validated.getOrThrow()

        val list = presetDomains[name] ?: mutableStateListOf<String>().also { presetDomains[name] = it }
        if (newDomain != oldDomain && list.contains(newDomain)) {
            return Result.failure(IllegalArgumentException("Domain already exists in preset"))
        }

        val idx = list.indexOf(oldDomain)
        if (idx >= 0) {
            list[idx] = newDomain
        } else {
            list.add(newDomain)
        }
        savePresetOverrides()
        return Result.success(Unit)
    }

    fun clearPresetDomains(name: String) {
        val list = presetDomains[name] ?: return
        list.clear()
        savePresetOverrides()
    }

    fun resetPresetDomainsToDefault(name: String) {
        val defaultList = Config.PRESETS.toMap()[name] ?: emptyList()
        val list = presetDomains[name] ?: mutableStateListOf<String>().also { presetDomains[name] = it }
        list.clear()
        list.addAll(defaultList)
        savePresetOverrides()
    }

    fun addFakeSniHost(rawHost: String): Result<Unit> {
        val validated = Config.validateDomain(rawHost)
        if (validated.isFailure) return Result.failure(validated.exceptionOrNull()!!)
        val host = validated.getOrThrow()

        if (fakeSniPool.contains(host)) {
            return Result.failure(IllegalArgumentException("Host already exists in Fake SNI pool"))
        }

        fakeSniPool.add(host)
        saveFakeSniPool()
        return Result.success(Unit)
    }

    fun removeFakeSniHost(host: String) {
        if (fakeSniPool.size <= 1) {
            return
        }
        if (fakeSniPool.remove(host)) {
            saveFakeSniPool()
        }
    }

    fun editFakeSniHost(oldHost: String, newRawHost: String): Result<Unit> {
        val validated = Config.validateDomain(newRawHost)
        if (validated.isFailure) return Result.failure(validated.exceptionOrNull()!!)
        val newHost = validated.getOrThrow()

        if (newHost != oldHost && fakeSniPool.contains(newHost)) {
            return Result.failure(IllegalArgumentException("Host already exists in Fake SNI pool"))
        }

        val idx = fakeSniPool.indexOf(oldHost)
        if (idx >= 0) {
            fakeSniPool[idx] = newHost
        } else {
            fakeSniPool.add(newHost)
        }
        saveFakeSniPool()
        return Result.success(Unit)
    }

    fun resetFakeSniPoolToDefault() {
        fakeSniPool.clear()
        fakeSniPool.addAll(defaultFakeSniList)
        saveFakeSniPool()
    }

    private fun savePresetOverrides() {
        scope.launch(Dispatchers.IO) {
            try {
                val jsonObj = JSONObject()
                for (entry in presetDomains.entries) {
                    val name = entry.key
                    val domainList: List<String> = entry.value
                    val arr = JSONArray()
                    for (d in domainList) {
                        arr.put(d)
                    }
                    jsonObj.put(name, arr)
                }
                dataStoreManager.saveSetting(DataStoreManager.PRESET_OVERRIDES, jsonObj.toString())
            } catch (_: Exception) {}
        }
    }

    private fun saveFakeSniPool() {
        scope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(DataStoreManager.FAKE_SNI_POOL, fakeSniPool.toSet())
        }
    }

    private fun <T> save(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        scope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(key, value)
        }
    }
}

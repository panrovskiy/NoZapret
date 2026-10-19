package com.example.nozapret.data

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.datastore.preferences.core.Preferences
import com.example.nozapret.core.Config
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
    var enableQuic by mutableStateOf(true)
    var customPrimaryColor by mutableIntStateOf(0xFF6750A4.toInt())
    var customThemeBase by mutableStateOf("System")
    var runMode by mutableStateOf("VPN")

    val presetDomains = mutableStateMapOf<String, SnapshotStateList<String>>()
    val fakeSniPool = mutableStateListOf<String>()
    val customStrategies = mutableStateListOf<Config.CustomStrategy>()
    val strategyFakeSniMap = mutableStateMapOf<String, String>()

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
            enableQuic = prefs[DataStoreManager.ENABLE_QUIC] ?: true
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

            // Load Strategy Fake SNI Map JSON
            val strategyFakeSniJsonStr = prefs[DataStoreManager.STRATEGY_FAKE_SNI_MAP]
            if (!strategyFakeSniJsonStr.isNullOrBlank()) {
                try {
                    val jsonObj = JSONObject(strategyFakeSniJsonStr)
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val valStr = jsonObj.getString(key)
                        if (valStr.isNotBlank()) {
                            strategyFakeSniMap[key] = valStr
                        }
                    }
                } catch (_: Exception) {}
            }

            // Load Custom Strategies JSON (empty by default on fresh install)
            val customStratsJsonStr = prefs[DataStoreManager.CUSTOM_STRATEGIES_JSON]
            if (!customStratsJsonStr.isNullOrBlank()) {
                try {
                    val array = JSONArray(customStratsJsonStr)
                    customStrategies.clear()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        customStrategies.add(
                            Config.CustomStrategy(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                name = obj.optString("name", "Custom Strategy"),
                                description = obj.optString("description", ""),
                                backend = obj.optString("backend", "ByeDPI"),
                                userArgs = obj.optString("userArgs", ""),
                                fakeSni = if (obj.has("fakeSni") && obj.getString("fakeSni").isNotBlank()) obj.getString("fakeSni") else null,
                                customDns = if (obj.has("customDns") && obj.getString("customDns").isNotBlank()) obj.getString("customDns") else null,
                                category = obj.optString("category", "User Custom"),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                } catch (_: Exception) {}
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

    fun updateEnableQuic(value: Boolean) {
        enableQuic = value
        save(DataStoreManager.ENABLE_QUIC, value)
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

    fun getFakeSniForStrategy(strategyName: String): String? {
        val customStrat = getCustomStrategyByName(strategyName)
        if (customStrat != null && customStrat.fakeSni != null) {
            return if (customStrat.fakeSni.isBlank()) null else customStrat.fakeSni
        }
        return Config.resolveFakeSni(strategyName, strategyFakeSniMap, fakeSniPool, globalMode)
    }

    fun setFakeSniForStrategy(strategyName: String, rawFakeSni: String?): Result<Unit> {
        val trimmed = rawFakeSni?.trim() ?: ""
        if (trimmed.isEmpty()) {
            strategyFakeSniMap.remove(strategyName)
            saveStrategyFakeSniMap()
            return Result.success(Unit)
        }

        val validated = Config.validateDomain(trimmed)
        if (validated.isFailure) return Result.failure(validated.exceptionOrNull()!!)
        val domain = validated.getOrThrow()

        strategyFakeSniMap[strategyName] = domain
        saveStrategyFakeSniMap()
        return Result.success(Unit)
    }

    fun resetFakeSniForStrategy(strategyName: String) {
        strategyFakeSniMap.remove(strategyName)
        saveStrategyFakeSniMap()
    }

    fun getCustomStrategyById(id: String): Config.CustomStrategy? = customStrategies.find { it.id == id }
    fun getCustomStrategyByName(name: String): Config.CustomStrategy? = customStrategies.find { it.name == name }

    fun addCustomStrategy(strategy: Config.CustomStrategy): Result<Unit> {
        if (strategy.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Strategy name cannot be empty"))
        }
        if (customStrategies.any { it.id != strategy.id && it.name.equals(strategy.name, ignoreCase = true) } ||
            Config.STRATEGIES_DATA.any { it.name.equals(strategy.name, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("A strategy with this name already exists"))
        }
        val valResult = Config.validateStrategyArgs(Config.parseCustomArgs(strategy.userArgs))
        if (valResult.isFailure) {
            return Result.failure(valResult.exceptionOrNull()!!)
        }
        customStrategies.add(strategy)
        saveCustomStrategies()
        return Result.success(Unit)
    }

    fun updateCustomStrategy(strategy: Config.CustomStrategy): Result<Unit> {
        if (strategy.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Strategy name cannot be empty"))
        }
        if (customStrategies.any { it.id != strategy.id && it.name.equals(strategy.name, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("A strategy with this name already exists"))
        }
        val valResult = Config.validateStrategyArgs(Config.parseCustomArgs(strategy.userArgs))
        if (valResult.isFailure) {
            return Result.failure(valResult.exceptionOrNull()!!)
        }
        val idx = customStrategies.indexOfFirst { it.id == strategy.id }
        if (idx >= 0) {
            customStrategies[idx] = strategy.copy(updatedAt = System.currentTimeMillis())
            saveCustomStrategies()
            return Result.success(Unit)
        }
        return Result.failure(IllegalArgumentException("Custom strategy not found"))
    }

    fun deleteCustomStrategy(id: String) {
        if (customStrategies.removeIf { it.id == id }) {
            saveCustomStrategies()
        }
    }

    fun duplicateCustomStrategy(id: String): Config.CustomStrategy? {
        val existing = getCustomStrategyById(id) ?: return null
        val newId = UUID.randomUUID().toString()
        val newName = "${existing.name} (Copy)"
        val newStrategy = existing.copy(
            id = newId,
            name = newName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        customStrategies.add(newStrategy)
        saveCustomStrategies()
        return newStrategy
    }

    private fun saveCustomStrategies() {
        scope.launch(Dispatchers.IO) {
            try {
                val array = JSONArray()
                customStrategies.forEach { item ->
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("description", item.description)
                        put("backend", item.backend)
                        put("userArgs", item.userArgs)
                        put("fakeSni", item.fakeSni ?: "")
                        put("customDns", item.customDns ?: "")
                        put("category", item.category)
                        put("createdAt", item.createdAt)
                        put("updatedAt", item.updatedAt)
                    }
                    array.put(obj)
                }
                dataStoreManager.saveSetting(DataStoreManager.CUSTOM_STRATEGIES_JSON, array.toString())
            } catch (_: Exception) {}
        }
    }

    private fun saveStrategyFakeSniMap() {
        scope.launch(Dispatchers.IO) {
            try {
                val jsonObj = JSONObject()
                strategyFakeSniMap.forEach { (name, domain) ->
                    jsonObj.put(name, domain)
                }
                dataStoreManager.saveSetting(DataStoreManager.STRATEGY_FAKE_SNI_MAP, jsonObj.toString())
            } catch (_: Exception) {}
        }
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

    private fun <T> save(key: Preferences.Key<T>, value: T) {
        scope.launch(Dispatchers.IO) {
            dataStoreManager.saveSetting(key, value)
        }
    }
}

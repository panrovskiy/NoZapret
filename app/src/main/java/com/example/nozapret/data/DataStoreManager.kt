package com.example.nozapret.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val SELECTED_STRATEGY = stringPreferencesKey("selected_strategy")
        val CUSTOM_ARGS = stringPreferencesKey("custom_args")
        val DNS_SERVER = stringPreferencesKey("dns_server")
        val ALLOWED_APPS = stringSetPreferencesKey("allowed_apps")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = stringPreferencesKey("proxy_port")
        val EXCLUDE_SELF = booleanPreferencesKey("exclude_self")
        val GLOBAL_MODE = booleanPreferencesKey("global_mode")
        val CUSTOM_HOST_LIST = stringPreferencesKey("custom_host_list")
        val SELECTED_PRESETS = stringSetPreferencesKey("selected_presets")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val CUSTOM_PRIMARY_COLOR = intPreferencesKey("custom_primary_color")
        val CUSTOM_THEME_BASE = stringPreferencesKey("custom_theme_base")
        val PINNED_STRATEGIES = stringSetPreferencesKey("pinned_strategies")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val ENABLE_IPV6 = booleanPreferencesKey("enable_ipv6")
        val RUN_MODE = stringPreferencesKey("run_mode") // VPN or Proxy
    }

    suspend fun <T> saveSetting(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    fun <T> getSetting(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return dataStore.data.map { it[key] ?: defaultValue }
    }

    fun getAllSettings(): Flow<Preferences> {
        return dataStore.data
    }
}

package com.example.nozapret.core

import android.app.Application
import android.util.Log
import com.example.nozapret.MainViewModel
import com.example.nozapret.core.AppLogger
import com.example.nozapret.data.DataStoreManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.time.Duration.Companion.milliseconds

class StrategyTester(private val application: Application) {
    private val TAG = "StrategyTester"
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _isTesting = MutableStateFlow(false)
    val isTesting = _isTesting.asStateFlow()

    private val proxy = ByeDpiProxy()
    private val dnsCache = ConcurrentHashMap<String, String>()

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 100)
                false // If connect succeeds, port is NOT available
            }
        } catch (_: Exception) {
            true // If connect fails, port is likely available
        }
    }

    suspend fun testStrategy(
        strategyName: String,
        bypassedSites: List<String>,
        customArgs: String,
        enableQuic: Boolean = true,
        effectiveDns: String = "1.1.1.1",
        onResult: (String, MainViewModel.TlsTestResult, Int, Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (strategyName == "None") {
            return@withContext runDirectTest(bypassedSites, effectiveDns, onResult)
        }

        val sitesToTest = bypassedSites.filter { it.isNotBlank() && !it.contains("/") }
        if (sitesToTest.isEmpty()) {
            Log.w(TAG, "No sites to test for $strategyName")
            return@withContext true
        }

        val dataStoreManager = DataStoreManager(application)
        val prefs = dataStoreManager.getAllSettings().first()

        val mapJsonStr = prefs[DataStoreManager.STRATEGY_FAKE_SNI_MAP]
        val strategyFakeSniMap = mutableMapOf<String, String>()
        if (!mapJsonStr.isNullOrBlank()) {
            try {
                val obj = JSONObject(mapJsonStr)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val valStr = obj.getString(key)
                    if (valStr.isNotBlank()) strategyFakeSniMap[key] = valStr
                }
            } catch (_: Exception) {}
        }

        val globalMode = prefs[DataStoreManager.GLOBAL_MODE] ?: false
        val fakeSniPool = prefs[DataStoreManager.FAKE_SNI_POOL]?.toList() ?: listOf("www.google.com", "yandex.ru")

        val resolvedFakeSni = Config.resolveFakeSni(strategyName, strategyFakeSniMap, fakeSniPool, globalMode)

        val strategyArgs = try {
            Config.getStrategyArgs(strategyName, customArgs, resolvedFakeSni, enableQuic)
        } catch (e: Exception) {
            AppLogger.e("TEST", "[TEST] Failed to generate args for $strategyName: ${e.message}")
            return@withContext false
        }

        val validation = Config.validateStrategyArgs(strategyArgs)
        if (validation.isFailure) {
            AppLogger.e("TEST", "[TEST] Validation failed for $strategyName: ${validation.exceptionOrNull()?.message}")
            return@withContext false
        }

        if (!isPortAvailable(1081)) {
            AppLogger.e("TEST", "[TEST] Port 1081 is already in use")
            // Try to force close once more
            proxy.forceClose()
            delay(200.milliseconds)
            if (!isPortAvailable(1081)) {
                return@withContext false
            }
        }

        _isTesting.value = true
        val total = sitesToTest.size
        val testedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)

        val startTime = System.currentTimeMillis()
        try {
            val hostlistFile = File(application.cacheDir, "test_hostlist.txt")
            hostlistFile.writeText(sitesToTest.joinToString("\n"))

            val finalArgs = mutableListOf("byedpi", "-i", "127.0.0.1", "-p", "1081", "-x", "1")
            finalArgs.add("--hosts")
            finalArgs.add(hostlistFile.absolutePath)
            finalArgs.addAll(strategyArgs)
            finalArgs.add("-P")
            finalArgs.add("protect")

            AppLogger.d("TEST", "[PERF] Starting test proxy for $strategyName")
            
            // Ensure any old proxy is closed
            proxy.forceClose()
            delay(100.milliseconds)

            val proxyStartTime = System.currentTimeMillis()
            val proxyJob = launch {
                try {
                    val res = proxy.start(finalArgs.toTypedArray())
                    AppLogger.d("TEST", "[TEST] Test proxy for $strategyName exited with code $res")
                } catch (e: Exception) {
                    AppLogger.e("TEST", "[TEST] Exception in test proxy for $strategyName", e)
                }
            }

            // Wait for proxy to be ready
            var ready = false
            var wait = 0
            while (!ready && wait < 40 && isActive) { // Reduced wait time 10s max
                if (proxy.isRunning()) {
                    try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress("127.0.0.1", 1081), 200) // Reduced connect timeout
                            ready = true
                        }
                    } catch (_: Exception) {}
                }
                if (!ready) {
                    delay(250.milliseconds)
                }
                wait++
            }
            AppLogger.d("TEST", "[PERF] Proxy ready in ${System.currentTimeMillis() - proxyStartTime}ms")

            if (!ready) {
                AppLogger.e("TEST", "[TEST] Proxy failed to start for $strategyName")
                proxyJob.cancelAndJoin()
                return@withContext false
            }

            val strategyClient = okHttpClient.newBuilder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1081)))
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()

            val checksStartTime = System.currentTimeMillis()
            val dnsResults = performAllDnsDiagnostics(effectiveDns)
            val semaphore = Semaphore(6) // Bounded concurrency

            coroutineScope {
                sitesToTest.forEach { site ->
                    launch {
                        semaphore.acquire()
                        try {
                            if (!isActive) return@launch
                            val checkStart = System.currentTimeMillis()
                            val result = try {
                                performCombinedCheck(site, false, strategyClient)
                            } catch (e: Exception) {
                                AppLogger.w("TEST", "Check failed for $site: ${e.message}")
                                MainViewModel.TlsTestResult(false, error = "Check crashed: ${e.message}")
                            }

                            if (isActive) {
                                val currentTested = testedCount.incrementAndGet()
                                if (result.success) successCount.incrementAndGet()
                                val resultWithDns = result.copy(dnsResults = dnsResults)
                                onResult(site, resultWithDns, successCount.get(), currentTested, total)
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                }
            }

            AppLogger.d("TEST", "[PERF] All checks for $strategyName finished in ${System.currentTimeMillis() - checksStartTime}ms")
            
            val cleanupStartTime = System.currentTimeMillis()
            proxy.stop()
            proxy.forceClose()
            proxyJob.cancelAndJoin()
            
            // Wait for port to be released
            var releaseWait = 0
            while (proxy.isRunning() && releaseWait < 20) {
                delay(100.milliseconds)
                releaseWait++
            }
            AppLogger.d("TEST", "[PERF] Cleanup took ${System.currentTimeMillis() - cleanupStartTime}ms. Total strategy time: ${System.currentTimeMillis() - startTime}ms")
            
            delay(100.milliseconds) // Reduced pause
            true
        } catch (e: Exception) {
            Log.e(TAG, "[TEST] Test failed for $strategyName", e)
            false
        } finally {
            _isTesting.value = false
            Log.d(TAG, "[CLEANUP] Test state reset for $strategyName")
        }
    }

    private suspend fun runDirectTest(
        bypassedSites: List<String>,
        effectiveDns: String = "1.1.1.1",
        onResult: (String, MainViewModel.TlsTestResult, Int, Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val sitesToTest = bypassedSites.filter { it.isNotBlank() && !it.contains("/") }
        if (sitesToTest.isEmpty()) return@withContext true

        val total = sitesToTest.size
        val testedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val dnsResults = performAllDnsDiagnostics(effectiveDns)
        val semaphore = Semaphore(10) // More concurrency for direct tests

        coroutineScope {
            sitesToTest.forEach { site ->
                launch {
                    semaphore.acquire()
                    try {
                        if (!isActive) return@launch
                        val result = try {
                            performCombinedCheck(site, true)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            AppLogger.w("TEST", "Direct check failed for $site: ${e.message}")
                            MainViewModel.TlsTestResult(false, error = "Direct check crashed: ${e.message}")
                        }
                        if (isActive) {
                            val currentTested = testedCount.incrementAndGet()
                            if (result.success) successCount.incrementAndGet()
                            val resultWithDns = result.copy(dnsResults = dnsResults)
                            onResult(site, resultWithDns, successCount.get(), currentTested, total)
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
        true
    }

    private suspend fun performCombinedCheck(domain: String, direct: Boolean, client: OkHttpClient? = null): MainViewModel.TlsTestResult = coroutineScope {
        val tlsDeferred = async { fastTlsCheck(domain, direct) }
        val httpDeferred = async { fastHttpCheck(domain, direct, client) }
        val tls = tlsDeferred.await()
        val http = httpDeferred.await()
        tls.copy(httpSuccess = http.first, httpError = http.second)
    }

    private suspend fun fastTlsCheck(domain: String, direct: Boolean): MainViewModel.TlsTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            var rawSocket: Socket? = null
            var sslSocket: SSLSocket? = null
            try {
                val proxyAddr = if (direct) Proxy.NO_PROXY else Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1081))
                rawSocket = Socket(proxyAddr)
                
                val targetAddr = if (direct) {
                    val ip = dnsCache.getOrPut(domain) { InetAddress.getByName(domain).hostAddress!! }
                    InetSocketAddress(ip, 443)
                } else {
                    InetSocketAddress.createUnresolved(domain, 443)
                }

                rawSocket.connect(targetAddr, 3000)
                rawSocket.soTimeout = 3000
                
                val sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                sslSocket = sslSocketFactory.createSocket(rawSocket, domain, 443, true) as SSLSocket
                
                sslSocket.startHandshake()
                val ping = System.currentTimeMillis() - start
                val session = sslSocket.session
                MainViewModel.TlsTestResult(
                    success = true,
                    ping = ping,
                    protocol = session.protocol,
                    cipherSuite = session.cipherSuite
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                MainViewModel.TlsTestResult(false, error = e.message ?: "TLS Handshake failed")
            } finally {
                try { sslSocket?.close() } catch (_: Exception) {}
                try { rawSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun fastHttpCheck(domain: String, direct: Boolean, strategyClient: OkHttpClient? = null): Pair<Boolean, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val client = if (direct) {
                    okHttpClient.newBuilder()
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(3, TimeUnit.SECONDS)
                        .build()
                } else {
                    strategyClient ?: okHttpClient.newBuilder()
                        .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1081)))
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(3, TimeUnit.SECONDS)
                        .build()
                }
                
                val request = Request.Builder()
                    .url("http://$domain")
                    .header("User-Agent", "Mozilla/5.0 (Android 14)")
                    .header("Connection", "close") // Don't keep connections alive for testing
                    .build()
                
                client.newCall(request).execute().use { response ->
                    Pair(response.isSuccessful || response.code < 500, if (!response.isSuccessful) "HTTP ${response.code}" else null)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Pair(false, e.message)
            }
        }
    }

    suspend fun performAllDnsDiagnostics(effectiveDns: String): List<DnsTestResult> = coroutineScope {
        val endpoints = mutableListOf(
            "Quad9 Primary" to "9.9.9.9",
            "Quad9 Backup" to "149.112.112.112",
            "Cloudflare Primary" to "1.1.1.1",
            "Cloudflare Backup" to "1.0.0.1",
            "Google Primary" to "8.8.8.8",
            "Google Backup" to "8.8.4.4"
        )
        val trimmedEffective = effectiveDns.trim()
        if (trimmedEffective.isNotEmpty() && !endpoints.any { it.second == trimmedEffective }) {
            endpoints.add("Effective DNS ($trimmedEffective)" to trimmedEffective)
        }

        val deferreds = endpoints.map { (name, ip) ->
            async { performRawDnsQuery(name, ip) }
        }
        deferreds.awaitAll()
    }

    private suspend fun performRawDnsQuery(name: String, ip: String, timeoutMs: Int = 2500): DnsTestResult = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            val randomId = (System.currentTimeMillis().toInt() and 0xFFFF)
            val domain = "wikipedia.org"
            val queryBytes = buildDnsQueryBytes(randomId, domain)

            socket = DatagramSocket()
            socket.soTimeout = timeoutMs

            val startNano = System.nanoTime()
            val targetAddr = InetAddress.getByName(ip)
            val sendPacket = DatagramPacket(queryBytes, queryBytes.size, targetAddr, 53)
            socket.send(sendPacket)

            val receiveBuf = ByteArray(512)
            val receivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
            socket.receive(receivePacket)

            val latencyMs = (System.nanoTime() - startNano) / 1_000_000

            if (receivePacket.length >= 12) {
                val respId = ((receiveBuf[0].toInt() and 0xFF) shl 8) or (receiveBuf[1].toInt() and 0xFF)
                val flags = ((receiveBuf[2].toInt() and 0xFF) shl 8) or (receiveBuf[3].toInt() and 0xFF)
                val isResponse = (flags and 0x8000) != 0
                val rcode = flags and 0x000F

                if (respId == randomId && isResponse && (rcode == 0 || rcode == 3)) {
                    return@withContext DnsTestResult(
                        name = name,
                        ip = ip,
                        success = true,
                        pingMs = maxOf(1L, latencyMs)
                    )
                }
            }

            DnsTestResult(name = name, ip = ip, success = false, error = "Invalid DNS response")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DnsTestResult(name = name, ip = ip, success = false, error = e.message ?: "DNS Timeout")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun buildDnsQueryBytes(id: Int, domain: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((id shr 8) and 0xFF)
        out.write(id and 0xFF)
        out.write(0x01)
        out.write(0x00)
        out.write(0x00)
        out.write(0x01)
        out.write(0x00)
        out.write(0x00)
        out.write(0x00)
        out.write(0x00)
        out.write(0x00)
        out.write(0x00)

        for (label in domain.split(".")) {
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0x00)

        out.write(0x00)
        out.write(0x01)
        out.write(0x00)
        out.write(0x01)

        return out.toByteArray()
    }
}

data class DnsTestResult(
    val name: String,
    val ip: String,
    val success: Boolean,
    val pingMs: Long? = null,
    val error: String? = null
)

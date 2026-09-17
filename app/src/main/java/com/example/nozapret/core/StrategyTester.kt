package com.example.nozapret.core

import android.app.Application
import android.util.Log
import com.example.nozapret.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

    suspend fun testStrategy(
        strategyName: String,
        bypassedSites: List<String>,
        customArgs: String,
        onResult: (String, MainViewModel.TlsTestResult) -> Unit,
        onProgress: (Int, Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (strategyName == "None") {
            return@withContext runDirectTest(bypassedSites, onResult, onProgress)
        }

        val sitesToTest = bypassedSites.filter { it.isNotBlank() && !it.contains("/") }
        if (sitesToTest.isEmpty()) {
            Log.w(TAG, "No sites to test for $strategyName")
            return@withContext true
        }

        _isTesting.value = true
        val total = sitesToTest.size
        val testedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)

        try {
            val strategyArgs = Config.getStrategyArgs(strategyName, customArgs)
            val hostlistFile = File(application.cacheDir, "test_hostlist.txt")
            hostlistFile.writeText(sitesToTest.joinToString("\n"))

            val finalArgs = mutableListOf("byedpi", "-i", "127.0.0.1", "-p", "1081", "-x", "1")
            finalArgs.add("--hosts")
            finalArgs.add(hostlistFile.absolutePath)
            finalArgs.addAll(strategyArgs)
            finalArgs.add("-A")
            finalArgs.add("none")
            finalArgs.add("-P")
            finalArgs.add("protect")

            Log.d(TAG, "[TEST] Starting test proxy for $strategyName with args: ${finalArgs.joinToString(" ")}")
            
            // Ensure any old proxy is closed
            proxy.forceClose()
            delay(200.milliseconds)

            val proxyJob = launch {
                try {
                    val res = proxy.start(finalArgs.toTypedArray())
                    Log.d(TAG, "[TEST] Test proxy for $strategyName exited with code $res")
                } catch (e: Exception) {
                    Log.e(TAG, "[TEST] Exception in test proxy for $strategyName", e)
                }
            }

            // Wait for proxy to be ready
            var ready = false
            var wait = 0
            while (!ready && wait < 60 && isActive) {
                if (proxy.isRunning()) {
                    try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress("127.0.0.1", 1081), 400)
                            ready = true
                        }
                    } catch (_: Exception) {}
                }
                if (!ready) {
                    if (wait % 4 == 0) Log.d(TAG, "[TEST] Waiting for test proxy ($wait)...")
                    delay(250.milliseconds)
                }
                wait++
            }

            if (!ready) {
                Log.e(TAG, "[TEST] Proxy failed to start for $strategyName")
                proxyJob.cancelAndJoin()
                return@withContext false
            }

            Log.d(TAG, "[TEST] Test proxy ready for $strategyName. Starting checks.")

            val semaphore = Semaphore(2) // Low concurrency for better stability on low-end devices

            coroutineScope {
                sitesToTest.forEach { site ->
                    launch {
                        semaphore.acquire()
                        try {
                            if (!isActive) return@launch
                            val result = try {
                                performCombinedCheck(site, false)
                            } catch (e: Exception) {
                                Log.w(TAG, "Check failed for $site: ${e.message}")
                                MainViewModel.TlsTestResult(false, error = "Check crashed: ${e.message}")
                            }
                            
                            if (isActive) {
                                onResult(site, result)
                                val currentTested = testedCount.incrementAndGet()
                                if (result.success) successCount.incrementAndGet()
                                onProgress(successCount.get(), currentTested, total)
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                }
            }

            Log.d(TAG, "[TEST] Checks finished for $strategyName. Cleaning up.")
            proxy.stop()
            proxy.forceClose()
            proxyJob.cancelAndJoin()
            
            // Wait for port to be released
            var releaseWait = 0
            while (proxy.isRunning() && releaseWait < 20) {
                delay(100.milliseconds)
                releaseWait++
            }
            Log.d(TAG, "[CLEANUP] Test proxy cleanup done. Port 1081 released=${!proxy.isRunning()}")
            
            delay(200.milliseconds) // Small pause between strategies
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
        onResult: (String, MainViewModel.TlsTestResult) -> Unit,
        onProgress: (Int, Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val sitesToTest = bypassedSites.filter { it.isNotBlank() && !it.contains("/") }
        if (sitesToTest.isEmpty()) return@withContext true

        val total = sitesToTest.size
        val testedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val semaphore = Semaphore(5)

        coroutineScope {
            sitesToTest.forEach { site ->
                launch {
                    semaphore.acquire()
                    try {
                        if (!isActive) return@launch
                        val result = performCombinedCheck(site, true)
                        if (isActive) {
                            onResult(site, result)
                            val currentTested = testedCount.incrementAndGet()
                            if (result.success) successCount.incrementAndGet()
                            onProgress(successCount.get(), currentTested, total)
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
        true
    }

    private suspend fun performCombinedCheck(domain: String, direct: Boolean): MainViewModel.TlsTestResult = coroutineScope {
        val tlsDeferred = async { fastTlsCheck(domain, direct) }
        val httpDeferred = async { fastHttpCheck(domain, direct) }
        val tls = tlsDeferred.await()
        val http = httpDeferred.await()
        tls.copy(httpSuccess = http.first, httpError = http.second)
    }

    private suspend fun fastTlsCheck(domain: String, direct: Boolean): MainViewModel.TlsTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val proxyAddr = if (direct) java.net.Proxy.NO_PROXY else java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1081))
                val socket = Socket(proxyAddr)
                socket.connect(InetSocketAddress.createUnresolved(domain, 443), 5000)
                socket.soTimeout = 5000
                
                val sslSocketFactory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
                val sslSocket = sslSocketFactory.createSocket(socket, domain, 443, true) as javax.net.ssl.SSLSocket
                
                sslSocket.startHandshake()
                val ping = System.currentTimeMillis() - start
                val session = sslSocket.session
                val result = MainViewModel.TlsTestResult(
                    success = true,
                    ping = ping,
                    protocol = session.protocol,
                    cipherSuite = session.cipherSuite
                )
                sslSocket.close()
                result
            } catch (e: Exception) {
                MainViewModel.TlsTestResult(false, error = e.message ?: "TLS Handshake failed")
            }
        }
    }

    private suspend fun fastHttpCheck(domain: String, direct: Boolean): Pair<Boolean, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val proxyAddr = if (direct) null else java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1081))
                val client = okHttpClient.newBuilder()
                    .apply { if (proxyAddr != null) proxy(proxyAddr) }
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url("http://$domain")
                    .header("User-Agent", "Mozilla/5.0 (Android 14)")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    Pair(response.isSuccessful || response.code < 500, if (!response.isSuccessful) "HTTP ${response.code}" else null)
                }
            } catch (e: Exception) {
                Pair(false, e.message)
            }
        }
    }
}

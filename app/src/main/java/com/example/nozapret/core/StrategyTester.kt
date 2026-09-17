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
import kotlin.time.Duration.Companion.milliseconds

class StrategyTester(private val application: Application) {
    private val TAG = "StrategyTester"
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val _isTesting = MutableStateFlow(false)
    val isTesting = _isTesting.asStateFlow()

    private val proxy = ByeDpiProxy()

    suspend fun testStrategy(
        strategyName: String,
        bypassedSites: List<String>,
        customArgs: String,
        onResult: (String, MainViewModel.TlsTestResult) -> Unit,
        onProgress: (Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (strategyName == "None") {
            return@withContext runDirectTest(bypassedSites, onResult, onProgress)
        }

        val sitesToTest = bypassedSites.filter { !it.contains("/") }
        if (sitesToTest.isEmpty()) return@withContext true

        _isTesting.value = true
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

            Log.d(TAG, "Starting test proxy for $strategyName")
            val proxyJob = launch {
                proxy.start(finalArgs.toTypedArray())
            }

            // Wait for proxy
            var ready = false
            var wait = 0
            while (!ready && wait < 40 && isActive) {
                if (proxy.isRunning()) {
                    try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress("127.0.0.1", 1081), 200)
                            ready = true
                        }
                    } catch (_: Exception) {}
                }
                if (!ready) delay(200.milliseconds)
                wait++
            }

            if (!ready) {
                Log.e(TAG, "Proxy failed to start for $strategyName")
                return@withContext false
            }

            val semaphore = Semaphore(5)
            val testedCount = mutableListOf<Int>()
            val successCount = mutableListOf<Int>()

            coroutineScope {
                sitesToTest.forEach { site ->
                    launch {
                        semaphore.acquire()
                        try {
                            val result = performCombinedCheck(site, false)
                            onResult(site, result)
                            synchronized(testedCount) {
                                testedCount.add(1)
                                if (result.success) successCount.add(1)
                                onProgress(successCount.size, testedCount.size)
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                }
            }

            proxy.stop()
            proxy.forceClose()
            proxyJob.cancelAndJoin()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Test failed for $strategyName", e)
            false
        } finally {
            _isTesting.value = false
        }
    }

    private suspend fun runDirectTest(
        bypassedSites: List<String>,
        onResult: (String, MainViewModel.TlsTestResult) -> Unit,
        onProgress: (Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val sitesToTest = bypassedSites.filter { !it.contains("/") }
        if (sitesToTest.isEmpty()) return@withContext true

        val testedCount = mutableListOf<Int>()
        val successCount = mutableListOf<Int>()
        val semaphore = Semaphore(10)

        coroutineScope {
            sitesToTest.forEach { site ->
                launch {
                    semaphore.acquire()
                    try {
                        val result = performCombinedCheck(site, true)
                        onResult(site, result)
                        synchronized(testedCount) {
                            testedCount.add(1)
                            if (result.success) successCount.add(1)
                            onProgress(successCount.size, testedCount.size)
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

package com.example.nozapret.core

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.nozapret.BuildConfig
import com.example.nozapret.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

enum class UpdateState {
    IDLE,
    CHECKING,
    UPDATE_AVAILABLE,
    CONNECTING,
    DOWNLOADING,
    VERIFYING,
    READY_TO_INSTALL,
    INSTALL_PERMISSION_REQUIRED,
    INSTALLER_LAUNCHED,
    INSTALLING,
    COMPLETED,
    FAILED,
    CANCELLED
}

class UpdateManager(private val application: Application) {
    private val TAG = "UpdateManager"
    private val GITHUB_API_URL = "https://api.github.com/repos/panrovskiy/NoZapret/releases/latest"

    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState = _updateState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadBytesFormatted = MutableStateFlow("")
    val downloadBytesFormatted = _downloadBytesFormatted.asStateFlow()

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError = _updateError.asStateFlow()

    @Volatile
    private var activeCall: Call? = null

    fun cancelDownload() {
        AppLogger.i("UPDATE", "[UPDATE] User requested download cancellation")
        try {
            activeCall?.cancel()
        } catch (e: Exception) {
            AppLogger.w("UPDATE", "[UPDATE] Exception cancelling active HTTP call: ${e.message}")
        }
        activeCall = null
        _updateState.value = UpdateState.CANCELLED
        _downloadProgress.value = 0f
        _downloadBytesFormatted.value = ""
        cleanupPartFile()
    }

    fun resetState() {
        _updateState.value = UpdateState.IDLE
        _downloadProgress.value = 0f
        _downloadBytesFormatted.value = ""
        _updateError.value = null
    }

    private fun isLocalProxyReachable(): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", 1080), 200)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildHttpClient(useProxy: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (useProxy && isLocalProxyReachable()) {
            AppLogger.i("UPDATE", "[UPDATE] Configuring SOCKS5 Proxy fallback (127.0.0.1:1080)")
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080)))
        } else {
            AppLogger.i("UPDATE", "[UPDATE] Configuring Direct Connection (No Proxy)")
        }

        return builder.build()
    }

    private suspend fun executeWithDualPath(
        url: String,
        onCallCreated: (Call) -> Unit = {}
    ): Response = withContext(Dispatchers.IO) {
        val directClient = buildHttpClient(useProxy = false)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NoZapret-Updater/${BuildConfig.VERSION_NAME}")
            .build()

        AppLogger.i("UPDATE", "[UPDATE] Attempting direct connection to: $url")
        val directCall = directClient.newCall(request)
        onCallCreated(directCall)

        try {
            val response = directCall.execute()
            if (response.isSuccessful) {
                AppLogger.i("UPDATE", "[UPDATE] Direct connection succeeded. HTTP ${response.code}")
                return@withContext response
            }
            AppLogger.w("UPDATE", "[UPDATE] Direct connection returned HTTP ${response.code}")
            response.close()
        } catch (e: Exception) {
            AppLogger.w("UPDATE", "[UPDATE] Direct connection failed: ${e.javaClass.simpleName} (${e.message})")
        }

        if (isLocalProxyReachable()) {
            AppLogger.i("UPDATE", "[UPDATE] Direct connection failed. Retrying via SOCKS5 Proxy...")
            val proxyClient = buildHttpClient(useProxy = true)
            val proxyCall = proxyClient.newCall(request)
            onCallCreated(proxyCall)
            try {
                val proxyResponse = proxyCall.execute()
                AppLogger.i("UPDATE", "[UPDATE] Proxy connection response: HTTP ${proxyResponse.code}")
                return@withContext proxyResponse
            } catch (e: Exception) {
                AppLogger.e("UPDATE", "[UPDATE] Proxy connection failed: ${e.javaClass.simpleName} (${e.message})")
                throw e
            }
        } else {
            AppLogger.w("UPDATE", "[UPDATE] SOCKS5 local proxy not reachable, throwing original error")
            throw ConnectException("Failed to connect to update server directly and local proxy is unavailable")
        }
    }

    suspend fun checkForUpdates(): MainViewModel.UpdateInfo? = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.CHECKING
        _updateError.value = null
        AppLogger.i("UPDATE", "[UPDATE] Checking for updates at: $GITHUB_API_URL")

        try {
            val response = executeWithDualPath(GITHUB_API_URL) { call -> activeCall = call }
            if (!response.isSuccessful) {
                val err = "HTTP ${response.code}: ${response.message}"
                AppLogger.e("UPDATE", "[UPDATE][ERROR] Update check failed: $err")
                _updateError.value = err
                _updateState.value = UpdateState.FAILED
                return@withContext null
            }

            val body = response.body.string()
            val json = JSONObject(body)
            val latestVersion = json.getString("tag_name").removePrefix("v")
            AppLogger.i("UPDATE", "[UPDATE] Installed version: ${BuildConfig.VERSION_NAME}, Latest release: $latestVersion")

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
                AppLogger.i("UPDATE", "[UPDATE] Update available! APK URL: $downloadUrl")
                _updateState.value = UpdateState.UPDATE_AVAILABLE
                return@withContext MainViewModel.UpdateInfo(latestVersion, downloadUrl, changelog)
            } else {
                AppLogger.i("UPDATE", "[UPDATE] Current version is up to date")
                _updateState.value = UpdateState.IDLE
            }
        } catch (e: Exception) {
            val err = e.message ?: e.javaClass.simpleName
            AppLogger.e("UPDATE", "[UPDATE][ERROR] Update check failed: $err", e)
            _updateError.value = err
            _updateState.value = UpdateState.FAILED
        } finally {
            activeCall = null
        }
        null
    }

    suspend fun downloadAndInstall(info: MainViewModel.UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        if (_updateState.value == UpdateState.CONNECTING || _updateState.value == UpdateState.DOWNLOADING) {
            AppLogger.w("UPDATE", "[UPDATE] Download already in progress, ignoring duplicate request")
            return@withContext false
        }

        _updateState.value = UpdateState.CONNECTING
        _downloadProgress.value = 0f
        _downloadBytesFormatted.value = ""
        _updateError.value = null

        AppLogger.i("UPDATE", "[UPDATE] Starting download for v${info.version} from: ${info.downloadUrl}")

        var partFile: File? = null
        var destFile: File? = null

        try {
            val response = executeWithDualPath(info.downloadUrl) { call -> activeCall = call }

            if (!response.isSuccessful) {
                val err = "HTTP ${response.code}: ${response.message}"
                AppLogger.e("UPDATE", "[UPDATE][ERROR] Download failed: $err")
                _updateError.value = err
                _updateState.value = UpdateState.FAILED
                return@withContext false
            }

            _updateState.value = UpdateState.DOWNLOADING

            val body = response.body
            if (body == null) {
                AppLogger.e("UPDATE", "[UPDATE][ERROR] Empty response body")
                _updateError.value = "Empty response body"
                _updateState.value = UpdateState.FAILED
                return@withContext false
            }

            val totalBytes = body.contentLength()
            destFile = File(application.cacheDir, "update.apk")
            partFile = File(application.cacheDir, "update.apk.part")

            if (partFile.exists()) partFile.delete()

            AppLogger.i("UPDATE", "[UPDATE] Downloading stream to: ${partFile.absolutePath} (Content-Length: $totalBytes)")

            var lastProgressTime = System.currentTimeMillis()
            var lastReadBytes = 0L

            body.byteStream().use { input ->
                partFile.outputStream().use { output ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    var downloadedBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (_updateState.value == UpdateState.CANCELLED) {
                            AppLogger.i("UPDATE", "[UPDATE] Download loop detected cancellation")
                            return@withContext false
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (downloadedBytes > lastReadBytes) {
                            lastReadBytes = downloadedBytes
                            lastProgressTime = now
                        } else if (now - lastProgressTime > 20000) {
                            AppLogger.e("UPDATE", "[UPDATE][ERROR] Download stalled for >20s, aborting")
                            _updateError.value = "Download stalled (Network timeout)"
                            _updateState.value = UpdateState.FAILED
                            return@withContext false
                        }

                        val formatted = if (totalBytes > 0) {
                            val pct = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            _downloadProgress.value = pct
                            val dlMb = String.format("%.1f", downloadedBytes / 1048576.0)
                            val totMb = String.format("%.1f", totalBytes / 1048576.0)
                            "$dlMb MB / $totMb MB"
                        } else {
                            val dlMb = String.format("%.1f", downloadedBytes / 1048576.0)
                            "$dlMb MB"
                        }
                        _downloadBytesFormatted.value = formatted
                    }
                }
            }

            if (_updateState.value == UpdateState.CANCELLED) {
                return@withContext false
            }

            if (totalBytes > 0 && partFile.length() < totalBytes) {
                val err = "Download incomplete: ${partFile.length()} / $totalBytes bytes"
                AppLogger.e("UPDATE", "[UPDATE][ERROR] $err")
                _updateError.value = err
                _updateState.value = UpdateState.FAILED
                return@withContext false
            }

            _updateState.value = UpdateState.VERIFYING
            AppLogger.i("UPDATE", "[UPDATE] Download complete (${partFile.length()} bytes). Verifying APK package...")

            val pkgInfo = application.packageManager.getPackageArchiveInfo(partFile.absolutePath, 0)
            if (pkgInfo == null) {
                AppLogger.e("UPDATE", "[UPDATE][ERROR] APK package verification failed: Invalid or corrupted APK file")
                _updateError.value = "Downloaded file is not a valid APK"
                _updateState.value = UpdateState.FAILED
                return@withContext false
            }

            if (pkgInfo.packageName != application.packageName) {
                AppLogger.e("UPDATE", "[UPDATE][ERROR] Package name mismatch: ${pkgInfo.packageName} vs ${application.packageName}")
                _updateError.value = "Package name mismatch"
                _updateState.value = UpdateState.FAILED
                return@withContext false
            }

            AppLogger.i("UPDATE", "[UPDATE] APK verified: package=${pkgInfo.packageName}, version=${pkgInfo.versionName}")

            if (destFile.exists()) destFile.delete()
            if (!partFile.renameTo(destFile)) {
                AppLogger.e("UPDATE", "[UPDATE][ERROR] Failed to rename .part to .apk")
                _updateError.value = "Failed to finalize update file"
                _updateState.value = UpdateState.FAILED
                return@withContext false
            }

            _updateState.value = UpdateState.READY_TO_INSTALL
            AppLogger.i("UPDATE", "[UPDATE] Finalized APK ready: ${destFile.absolutePath}")

            installDownloadedApk()
        } catch (e: Exception) {
            if (_updateState.value != UpdateState.CANCELLED) {
                val err = e.message ?: e.javaClass.simpleName
                AppLogger.e("UPDATE", "[UPDATE][ERROR] Download failed: $err", e)
                _updateError.value = err
                _updateState.value = UpdateState.FAILED
            }
            false
        } finally {
            activeCall = null
            if (_updateState.value == UpdateState.CANCELLED || _updateState.value == UpdateState.FAILED) {
                cleanupPartFile()
            }
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${application.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                AppLogger.i("UPDATE", "[UPDATE] Opening unknown app sources settings")
                application.startActivity(intent)
            } catch (e: Exception) {
                AppLogger.e("UPDATE", "[UPDATE][ERROR] Failed to open unknown app sources settings: ${e.message}", e)
            }
        }
    }

    fun installDownloadedApk(): Boolean {
        val destFile = File(application.cacheDir, "update.apk")
        if (!destFile.exists() || destFile.length() <= 0) {
            AppLogger.e("UPDATE", "[UPDATE][ERROR] Cannot install: update.apk missing or empty")
            _updateError.value = "Update file not found"
            _updateState.value = UpdateState.FAILED
            return false
        }

        if (!canRequestPackageInstalls()) {
            AppLogger.w("UPDATE", "[UPDATE] Installation permission required (canRequestPackageInstalls = false)")
            _updateState.value = UpdateState.INSTALL_PERMISSION_REQUIRED
            return false
        }

        _updateState.value = UpdateState.INSTALLING
        val installSuccess = installApk(destFile)
        if (installSuccess) {
            _updateState.value = UpdateState.INSTALLER_LAUNCHED
        } else {
            _updateState.value = UpdateState.FAILED
            _updateError.value = "Unable to launch system package installer"
        }
        return installSuccess
    }

    private fun installApk(file: File): Boolean {
        return try {
            AppLogger.i("UPDATE", "[UPDATE] Preparing FileProvider URI for installation...")
            val uri = FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            AppLogger.i("UPDATE", "[UPDATE] Launching system package installer with URI: $uri")
            application.startActivity(intent)
            true
        } catch (e: Exception) {
            AppLogger.e("UPDATE", "[UPDATE][ERROR] Failed to launch package installer: ${e.message}", e)
            false
        }
    }

    private fun cleanupPartFile() {
        try {
            val partFile = File(application.cacheDir, "update.apk.part")
            if (partFile.exists()) {
                partFile.delete()
                AppLogger.d("UPDATE", "[UPDATE] Deleted temporary .part file")
            }
        } catch (_: Exception) {}
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
}

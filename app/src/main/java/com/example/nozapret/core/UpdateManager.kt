package com.example.nozapret.core

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.nozapret.BuildConfig
import com.example.nozapret.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

class UpdateManager(private val application: Application) {
    private val TAG = "UpdateManager"
    private val GITHUB_API_URL = "https://api.github.com/repos/panrovskiy/NoZapret/releases/latest"
    
    private val okHttpClient = OkHttpClient()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    suspend fun checkForUpdates(): MainViewModel.UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(GITHUB_API_URL).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body.string()
                val json = JSONObject(body)
                val latestVersion = json.getString("tag_name").removePrefix("v")
                
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
                    return@withContext MainViewModel.UpdateInfo(latestVersion, downloadUrl, changelog)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
        }
        null
    }

    suspend fun downloadAndInstall(info: MainViewModel.UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        if (_isDownloading.value) return@withContext false
        _isDownloading.value = true
        _downloadProgress.value = 0f
        
        try {
            val request = Request.Builder().url(info.downloadUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body
                val totalBytes = body.contentLength()
                val file = File(application.cacheDir, "update.apk")
                
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                _downloadProgress.value = downloadedBytes.toFloat() / totalBytes
                            }
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    installApk(file)
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            false
        } finally {
            _isDownloading.value = false
        }
    }

    private fun installApk(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
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

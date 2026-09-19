package com.example.nozapret.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_LOG_FILES = 5
    private const val MAX_FILE_SIZE = 2 * 1024 * 1024 // 2MB
    
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logChannel = Channel<String>(100)
    private var logFile: File? = null
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        
        logFile = File(logDir, "nozapret.log")
        rotateLogsIfNeeded(logDir)
        
        logScope.launch {
            for (message in logChannel) {
                writeToLogFile(message)
            }
        }
        
        i("APP", "Logger initialized. App version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
    }

    fun d(category: String, message: String) {
        log("DEBUG", category, message)
        Log.d(category, message)
    }

    fun i(category: String, message: String) {
        log("INFO", category, message)
        Log.i(category, message)
    }

    fun w(category: String, message: String) {
        log("WARN", category, message)
        Log.w(category, message)
    }

    fun e(category: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        log("ERROR", category, fullMessage)
        Log.e(category, fullMessage)
    }

    private fun log(level: String, category: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedMessage = "[$timestamp] [$level] [$category] $message"
        logScope.launch {
            logChannel.send(formattedMessage)
        }
    }

    private fun writeToLogFile(message: String) {
        val file = logFile ?: return
        try {
            if (file.length() > MAX_FILE_SIZE) {
                rotateLogs(file.parentFile!!)
            }
            FileOutputStream(file, true).use { out ->
                PrintWriter(out).apply {
                    println(message)
                    flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    private fun rotateLogsIfNeeded(logDir: File) {
        val file = logFile ?: return
        if (file.exists() && file.length() > MAX_FILE_SIZE) {
            rotateLogs(logDir)
        }
    }

    private fun rotateLogs(logDir: File) {
        for (i in MAX_LOG_FILES - 1 downTo 1) {
            val oldFile = File(logDir, "nozapret.$i.log")
            val newFile = File(logDir, "nozapret.${i + 1}.log")
            if (oldFile.exists()) {
                if (newFile.exists()) newFile.delete()
                oldFile.renameTo(newFile)
            }
        }
        val currentFile = File(logDir, "nozapret.log")
        if (currentFile.exists()) {
            val firstBackup = File(logDir, "nozapret.1.log")
            if (firstBackup.exists()) firstBackup.delete()
            currentFile.renameTo(firstBackup)
        }
    }
    
    fun getLogFiles(context: Context): List<File> {
        val logDir = File(context.filesDir, "logs")
        return logDir.listFiles()?.filter { it.name.endsWith(".log") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}

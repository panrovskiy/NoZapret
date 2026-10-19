package com.example.nozapret.core

import android.content.Context
import android.os.Build
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
import java.util.Collections
import java.util.Date
import java.util.Locale

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val threadName: String = Thread.currentThread().name,
    val strategyName: String? = null,
    val sessionId: String? = null,
    val durationMs: Long? = null,
    val throwable: Throwable? = null,
    val metadata: Map<String, String> = emptyMap()
)

object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_LOG_FILES = 5
    private const val MAX_FILE_SIZE = 2 * 1024 * 1024 // 2MB
    private const val MAX_IN_MEMORY_LOGS = 500

    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logChannel = Channel<LogEntry>(500)
    private var logDir: File? = null
    private var logFile: File? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val inMemoryLogs = Collections.synchronizedList(mutableListOf<LogEntry>())

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        logDir = dir
        logFile = File(dir, "nozapret.log")

        rotateLogsIfNeeded(dir)

        logScope.launch {
            for (entry in logChannel) {
                writeLogEntryToFile(entry)
            }
        }

        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        i("APP", "AppLogger initialized. Version: $versionName, Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), Model: ${Build.MODEL}")
    }

    fun v(category: String, message: String, strategyName: String? = null, sessionId: String? = null) {
        log(LogLevel.VERBOSE, category, message, strategyName = strategyName, sessionId = sessionId)
        Log.v(category, message)
    }

    fun d(category: String, message: String, strategyName: String? = null, sessionId: String? = null) {
        log(LogLevel.DEBUG, category, message, strategyName = strategyName, sessionId = sessionId)
        Log.d(category, message)
    }

    fun i(category: String, message: String, strategyName: String? = null, sessionId: String? = null) {
        log(LogLevel.INFO, category, message, strategyName = strategyName, sessionId = sessionId)
        Log.i(category, message)
    }

    fun w(category: String, message: String, throwable: Throwable? = null, strategyName: String? = null, sessionId: String? = null) {
        log(LogLevel.WARN, category, message, throwable = throwable, strategyName = strategyName, sessionId = sessionId)
        Log.w(category, message, throwable)
    }

    fun e(category: String, message: String, throwable: Throwable? = null, strategyName: String? = null, sessionId: String? = null) {
        log(LogLevel.ERROR, category, message, throwable = throwable, strategyName = strategyName, sessionId = sessionId)
        Log.e(category, message, throwable)
    }

    fun fatal(category: String, message: String, throwable: Throwable? = null, strategyName: String? = null, sessionId: String? = null) {
        val entry = LogEntry(
            level = LogLevel.FATAL,
            tag = category,
            message = redactSensitiveData(message),
            strategyName = strategyName,
            sessionId = sessionId,
            throwable = throwable
        )
        addInMemory(entry)
        Log.e(category, "FATAL: $message", throwable)
        writeCrashDumpSync(entry)
    }

    private fun log(
        level: LogLevel,
        category: String,
        message: String,
        durationMs: Long? = null,
        throwable: Throwable? = null,
        strategyName: String? = null,
        sessionId: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val entry = LogEntry(
            level = level,
            tag = category,
            message = redactSensitiveData(message),
            durationMs = durationMs,
            throwable = throwable,
            strategyName = strategyName,
            sessionId = sessionId,
            metadata = metadata
        )
        addInMemory(entry)
        logScope.launch {
            logChannel.send(entry)
        }
    }

    private fun addInMemory(entry: LogEntry) {
        synchronized(inMemoryLogs) {
            inMemoryLogs.add(entry)
            if (inMemoryLogs.size > MAX_IN_MEMORY_LOGS) {
                inMemoryLogs.removeAt(0)
            }
        }
    }

    fun getRecentLogs(
        minLevel: LogLevel = LogLevel.VERBOSE,
        tagFilter: String? = null,
        searchQuery: String? = null
    ): List<LogEntry> {
        val query = searchQuery?.lowercase()?.trim()
        return synchronized(inMemoryLogs) {
            inMemoryLogs.filter { entry ->
                entry.level.ordinal >= minLevel.ordinal &&
                        (tagFilter == null || entry.tag.equals(tagFilter, ignoreCase = true)) &&
                        (query.isNullOrEmpty() || entry.message.lowercase().contains(query) || entry.tag.lowercase().contains(query))
            }.toList()
        }
    }

    private fun writeLogEntryToFile(entry: LogEntry) {
        val file = logFile ?: return
        try {
            if (file.length() > MAX_FILE_SIZE) {
                rotateLogs(file.parentFile ?: return)
            }
            val timeStr = dateFormat.format(Date(entry.timestamp))
            val stratStr = if (!entry.strategyName.isNullOrEmpty()) " [Strat:${entry.strategyName}]" else ""
            val sessStr = if (!entry.sessionId.isNullOrEmpty()) " [Sess:${entry.sessionId}]" else ""
            val durStr = if (entry.durationMs != null) " [Duration:${entry.durationMs}ms]" else ""
            val line = "[$timeStr] [${entry.level}] [${entry.tag}]$stratStr$sessStr$durStr [Thread:${entry.threadName}] ${entry.message}"

            FileOutputStream(file, true).use { out ->
                PrintWriter(out).apply {
                    println(line)
                    entry.throwable?.let {
                        println(Log.getStackTraceString(it))
                    }
                    flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    private fun writeCrashDumpSync(entry: LogEntry) {
        val dir = logDir ?: return
        val crashFile = File(dir, "crash_dump.log")
        try {
            val timeStr = dateFormat.format(Date(entry.timestamp))
            val line = "[$timeStr] [FATAL] [${entry.tag}] [Thread:${entry.threadName}] ${entry.message}"
            FileOutputStream(crashFile, true).use { out ->
                PrintWriter(out).apply {
                    println("==================== FATAL CRASH REPORT ====================")
                    println(line)
                    entry.throwable?.let {
                        println(Log.getStackTraceString(it))
                    }
                    println("============================================================")
                    println()
                    flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write synchronous crash dump", e)
        }
    }

    private fun rotateLogsIfNeeded(dir: File) {
        val file = logFile ?: return
        if (file.exists() && file.length() > MAX_FILE_SIZE) {
            rotateLogs(dir)
        }
    }

    private fun rotateLogs(dir: File) {
        for (i in MAX_LOG_FILES - 1 downTo 1) {
            val oldFile = File(dir, "nozapret.$i.log")
            val newFile = File(dir, "nozapret.${i + 1}.log")
            if (oldFile.exists()) {
                if (newFile.exists()) newFile.delete()
                oldFile.renameTo(newFile)
            }
        }
        val currentFile = File(dir, "nozapret.log")
        if (currentFile.exists()) {
            val firstBackup = File(dir, "nozapret.1.log")
            if (firstBackup.exists()) firstBackup.delete()
            currentFile.renameTo(firstBackup)
        }
    }

    fun redactSensitiveData(input: String): String {
        return input
            .replace(Regex("(?i)bearer\\s+[a-zA-Z0-9._~+/-]+=*"), "Bearer [REDACTED]")
            .replace(Regex("(?i)(password|secret|token|auth)=[^&\\s]+"), "$1=[REDACTED]")
            .replace(Regex("https?://[^:\\s]+:[^@\\s]+@"), "https://[REDACTED_CREDS]@")
    }

    fun getLogFiles(context: Context): List<File> {
        val dir = File(context.filesDir, "logs")
        return dir.listFiles()?.filter { it.name.endsWith(".log") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun createConsolidatedReport(context: Context): File? {
        return try {
            val cacheDir = context.cacheDir
            val reportFile = File(cacheDir, "nozapret_diagnostics_report.txt")
            if (reportFile.exists()) reportFile.delete()

            PrintWriter(FileOutputStream(reportFile)).use { writer ->
                writer.println("============================================================")
                writer.println("NOZAPRET DIAGNOSTICS REPORT")
                writer.println("Generated: ${dateFormat.format(Date())}")
                writer.println("App Package: ${context.packageName}")
                writer.println("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                writer.println("Device Model: ${Build.MANUFACTURER} ${Build.MODEL}")
                writer.println("============================================================")
                writer.println()

                val files = getLogFiles(context)
                files.forEach { file ->
                    writer.println("--- FILE: ${file.name} (Size: ${file.length()} bytes) ---")
                    try {
                        file.useLines { lines ->
                            lines.forEach { line ->
                                writer.println(redactSensitiveData(line))
                            }
                        }
                    } catch (e: Exception) {
                        writer.println("[Error reading file ${file.name}: ${e.message}]")
                    }
                    writer.println()
                }
            }
            reportFile
        } catch (e: Exception) {
            e("APP", "Failed to create consolidated diagnostics report", e)
            null
        }
    }
}

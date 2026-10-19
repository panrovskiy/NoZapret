package com.example.nozapret.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozapret.BuildConfig
import com.example.nozapret.MainViewModel
import com.example.nozapret.R
import com.example.nozapret.ui.components.FadeEntrance
import com.example.nozapret.ui.getLocalizedStrategyName

@Composable
fun LogViewer(
    logLines: List<String>,
    onClearLogs: () -> Unit,
    onExportDiagnostics: (() -> Unit)? = null,
    selectedStrategy: String = "Auto (Recommended)",
    effectiveDns: String = "1.1.1.1",
    enableQuic: Boolean = true,
    vpnStateText: String = "Idle",
    diagnosticsLog: List<MainViewModel.DiagItem> = emptyList(),
    isDiagnosing: Boolean = false,
    onRunDiagnostics: (() -> Unit)? = null,
    onDiagAction: ((MainViewModel.DiagAction) -> Unit)? = null
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val msgLogsCopied = stringResource(R.string.msg_logs_copied)

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Diagnostics, 1 = Raw Logs
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevelFilter by remember { mutableStateOf("ALL") }

    val filteredLines = remember(logLines, searchQuery, selectedLevelFilter) {
        logLines.filter { line ->
            val matchesLevel = when (selectedLevelFilter) {
                "ERROR" -> line.contains(" E/") || line.contains("ERROR") || line.contains("FATAL")
                "WARN" -> line.contains(" W/") || line.contains("WARN")
                "INFO" -> line.contains(" I/") || line.contains("INFO")
                "DEBUG" -> line.contains(" D/") || line.contains("DEBUG")
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() || line.contains(searchQuery.trim(), ignoreCase = true)
            matchesLevel && matchesQuery
        }
    }

    LaunchedEffect(filteredLines.size, selectedTab) {
        if (selectedTab == 1 && filteredLines.isNotEmpty()) {
            listState.scrollToItem(filteredLines.size - 1)
        }
    }

    fun copyLogsToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("NoZapret Logs", logLines.joinToString("\n"))
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, msgLogsCopied, Toast.LENGTH_SHORT).show()
    }

    fun shareLogs() {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, logLines.joinToString("\n"))
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    FadeEntrance {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Diagnostics", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Rounded.Assessment, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("App Logs", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Rounded.Terminal, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                // Diagnostics Summary Overview
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("System & Device Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                HorizontalDivider()
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("App Version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Android Version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Device Model", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Engine & Network Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                HorizontalDivider()
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("VPN Service State", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(vpnStateText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Selected Strategy", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(getLocalizedStrategyName(selectedStrategy), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("QUIC Support", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (enableQuic) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (enableQuic) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Effective DNS", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(effectiveDns, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("System Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Button(
                                        onClick = { onRunDiagnostics?.invoke() },
                                        enabled = !isDiagnosing,
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        if (isDiagnosing) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(if (isDiagnosing) "Checking…" else stringResource(R.string.btn_run_diagnostics))
                                    }
                                }
                                HorizontalDivider()

                                if (diagnosticsLog.isEmpty()) {
                                    Text(
                                        "Tap 'Run Diagnostics' to test system network, native engine, VPN permissions, and DNS resolution.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        diagnosticsLog.forEach { item ->
                                            val color = when (item.type) {
                                                MainViewModel.DiagType.PASSED -> Color(0xFF4CAF50)
                                                MainViewModel.DiagType.FAILED -> MaterialTheme.colorScheme.error
                                                MainViewModel.DiagType.WARNING -> Color(0xFFFF9800)
                                                MainViewModel.DiagType.INFO -> MaterialTheme.colorScheme.outline
                                            }
                                            val prefix = when (item.type) {
                                                MainViewModel.DiagType.PASSED -> "[+]"
                                                MainViewModel.DiagType.FAILED -> "[-]"
                                                MainViewModel.DiagType.WARNING -> "[!]"
                                                MainViewModel.DiagType.INFO -> if (item.isChecking) "[?]" else "[*]"
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    "$prefix ${item.message}",
                                                    color = color,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (item.action != MainViewModel.DiagAction.NONE && item.solutionTextResId != null) {
                                                    OutlinedButton(
                                                        onClick = { onDiagAction?.invoke(item.action) },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        shape = MaterialTheme.shapes.small
                                                    ) {
                                                        Text(stringResource(item.solutionTextResId), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { onExportDiagnostics?.invoke() ?: shareLogs() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Diagnostics Report", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Logs Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter logs…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        shape = MaterialTheme.shapes.medium
                    )
                    IconButton(onClick = { copyLogsToClipboard() }) {
                        Icon(Icons.Rounded.ContentCopy, stringResource(R.string.btn_copy_logs))
                    }
                    IconButton(onClick = { shareLogs() }) {
                        Icon(Icons.Rounded.Share, stringResource(R.string.btn_share_logs))
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL", "INFO", "WARN", "ERROR").forEach { level ->
                        FilterChip(
                            selected = selectedLevelFilter == level,
                            onClick = { selectedLevelFilter = level },
                            label = { Text(level) }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, start = 12.dp, end = 12.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredLines) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 16.sp,
                                    letterSpacing = 0.sp
                                ),
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    line.contains(" E/") || line.contains("ERROR") || line.contains("FATAL") -> MaterialTheme.colorScheme.error
                                    line.contains(" W/") || line.contains("WARN") -> MaterialTheme.colorScheme.tertiary
                                    line.contains(" D/") -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

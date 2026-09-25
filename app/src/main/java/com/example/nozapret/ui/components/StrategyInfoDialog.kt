package com.example.nozapret.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nozapret.MainViewModel
import com.example.nozapret.R
import com.example.nozapret.core.Config
import com.example.nozapret.ui.getLocalizedStrategyDesc
import com.example.nozapret.ui.getLocalizedStrategyName

@Composable
fun StrategyInfoDialog(
    strategyName: String,
    customArgs: String,
    testStat: Triple<Int, Int, Int>? = null,
    testResults: Map<String, MainViewModel.TlsTestResult>? = null,
    isTesting: Boolean = false,
    fakeSniPool: List<String> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strategy = Config.getStrategyByName(strategyName)
    val resolvedArgs = try {
        Config.getStrategyArgs(strategyName, customArgs, fakeSniPool).joinToString(" ")
    } catch (_: Exception) {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                getLocalizedStrategyName(strategyName),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ID Section
                InfoSection(label = stringResource(R.string.label_strategy_id), value = strategy?.id ?: "custom")

                // Preset/Category Section
                InfoSection(label = stringResource(R.string.label_preset), value = strategy?.category ?: "Custom")

                // Description Section
                InfoSection(
                    label = stringResource(R.string.changelog_title),
                    value = getLocalizedStrategyDesc(strategyName, strategy?.description ?: "")
                )

                // Status Section
                val statusText = when {
                    isTesting -> stringResource(R.string.status_testing)
                    testStat != null && testStat.second > 0 -> {
                        if (testStat.first > 0) stringResource(R.string.status_success) else stringResource(R.string.status_failed)
                    }
                    else -> stringResource(R.string.status_not_tested)
                }
                InfoSection(label = stringResource(R.string.label_status), value = statusText)

                // Arguments Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.label_strategy_arguments),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        if (resolvedArgs != null && resolvedArgs.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Strategy Arguments", resolvedArgs)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.msg_logs_copied), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = resolvedArgs ?: stringResource(R.string.error_arg_gen),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (resolvedArgs == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Test Results Section
                if (testStat != null && testStat.second > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.label_test_result),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val ok = testStat.first
                        val checked = testStat.second
                        val total = testStat.third
                        val fail = checked - ok
                        
                        Text(
                            stringResource(R.string.result_sites_format, total, ok, fail),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        // Calculate average ping if available
                        val pings = testResults?.values?.mapNotNull { it.ping } ?: emptyList()
                        if (pings.isNotEmpty()) {
                            val avgPing = pings.average().toInt()
                            Text(
                                stringResource(R.string.label_ping_format, avgPing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            Text(
                                stringResource(R.string.label_unknown_ping),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_dismiss))
            }
        }
    )
}

@Composable
private fun InfoSection(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

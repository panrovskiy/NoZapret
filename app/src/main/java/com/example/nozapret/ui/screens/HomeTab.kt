package com.example.nozapret.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozapret.R
import com.example.nozapret.ui.components.AnimatedVpnButton
import com.example.nozapret.ui.components.StatusCard
import com.example.nozapret.ui.components.VpnButtonState
import com.example.nozapret.ui.components.bouncingClickable
import com.example.nozapret.ui.getLocalizedPresetName
import com.example.nozapret.ui.getLocalizedStrategyName
import com.example.nozapret.ui.theme.MotionConstants
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HomeTab(
    isEnabled: Boolean,
    vpnStartTime: Long,
    selectedStrategy: String,
    pinnedStrategies: List<String>,
    selectedPresets: List<String>,
    onToggleVpn: () -> Unit,
    proxyHost: String,
    proxyPort: String,
    globalMode: Boolean,
    committedStats: Map<String, Triple<Int, Int, Int>>,
    bypassedSitesCount: Int,
    onStrategySelected: (String) -> Unit,
    // New states from MainViewModel
    isConnecting: Boolean = false,
    isDisconnecting: Boolean = false,
    isError: Boolean = false,
    dnsServer: String = "1.1.1.1"
) {
    var showStrategyDialog by remember { mutableStateOf(value = false) }
    var uptimeMillis by remember { mutableLongStateOf(0L) }

    val vpnButtonState = when {
        isError -> VpnButtonState.ERROR
        isConnecting -> VpnButtonState.CONNECTING
        isDisconnecting -> VpnButtonState.DISCONNECTING
        isEnabled -> VpnButtonState.CONNECTED
        else -> VpnButtonState.DISCONNECTED
    }

    LaunchedEffect(isEnabled, vpnStartTime) {
        if (isEnabled && (vpnStartTime > 0)) {
            while (true) {
                uptimeMillis = System.currentTimeMillis() - vpnStartTime
                kotlinx.coroutines.delay(1000.milliseconds)
            }
        } else {
            uptimeMillis = 0
        }
    }

    fun formatUptime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    if (showStrategyDialog) {
        StrategySelectionDialog(
            current = selectedStrategy,
            pinned = pinnedStrategies,
            onSelect = {
                onStrategySelected(it)
                showStrategyDialog = false
            },
            onDismiss = { showStrategyDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Active Config Card with sequential entrance
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(MotionConstants.DurationEmphasis)) + 
                    slideInVertically(tween(MotionConstants.DurationEmphasis)) { -40 }
        ) {
            ConfigCard(
                strategy = selectedStrategy,
                stats = committedStats[selectedStrategy],
                globalMode = globalMode,
                presets = selectedPresets,
                proxyHost = proxyHost,
                proxyPort = proxyPort,
                uptime = if (isEnabled) formatUptime(uptimeMillis) else null,
                onClick = { showStrategyDialog = true }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Central Button Section
        Box(
            modifier = Modifier.wrapContentSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVpnButton(
                state = vpnButtonState,
                onClick = onToggleVpn
            )
        }

        Spacer(modifier = Modifier.weight(1.2f))

        // Status Details
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(MotionConstants.DurationLong, delayMillis = 200)) + 
                    expandVertically(tween(MotionConstants.DurationLong, delayMillis = 200))
        ) {
            StatusCard(
                vpnActive = isEnabled,
                backendActive = isEnabled && !isConnecting,
                networkType = "Default", 
                dnsServer = dnsServer
            )
        }

        Text(
            if (isEnabled) stringResource(R.string.home_hint_connected) else stringResource(R.string.home_hint_disconnected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
fun ConfigCard(
    strategy: String,
    stats: Triple<Int, Int, Int>?,
    globalMode: Boolean,
    presets: List<String>,
    proxyHost: String,
    proxyPort: String,
    uptime: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bouncingClickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Bolt,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    getLocalizedStrategyName(strategy),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (stats != null && stats.second > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape
                ) {
                    Text(
                        stringResource(R.string.test_progress_short, stats.first, stats.third, stats.second),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (globalMode) {
                Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(stringResource(R.string.label_global_mode), modifier = Modifier.padding(4.dp))
                }
            } else if (presets.isNotEmpty()) {
                val localizedPresets = mutableListOf<String>()
                for (preset in presets) {
                    localizedPresets.add(getLocalizedPresetName(preset))
                }
                Text(
                    localizedPresets.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Lan, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("$proxyHost:$proxyPort", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                }

                if (uptime != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(uptime, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun StrategySelectionDialog(
    current: String,
    pinned: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_select_strategy)) },
        text = {
            val strategies = if (pinned.isNotEmpty()) pinned else com.example.nozapret.core.Config.STRATEGIES.map { it.first }
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(strategies) { strategy ->
                    val isSelected = strategy == current
                    Surface(
                        onClick = { onSelect(strategy) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(getLocalizedStrategyName(strategy), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

package com.example.nozapret.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nozapret.R
import com.example.nozapret.ui.getLocalizedPresetName
import com.example.nozapret.ui.getLocalizedStrategyName
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
    onStrategySelected: (String) -> Unit,
) {
    var showStrategyDialog by remember { mutableStateOf(value = false) }
    var uptimeMillis by remember { mutableLongStateOf(0L) }

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
        AlertDialog(
            onDismissRequest = { showStrategyDialog = false },
            title = { Text(stringResource(R.string.title_select_strategy)) },
            text = {
                val displayStrategies = if (pinnedStrategies.isNotEmpty()) {
                    pinnedStrategies
                } else {
                    listOf("Auto (Recommended)") + com.example.nozapret.core.Config.STRATEGIES.map { it.first }
                }
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(displayStrategies) { strategy ->
                        val isApplied = strategy == selectedStrategy
                        Surface(
                            onClick = {
                                onStrategySelected(strategy)
                                showStrategyDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (isApplied) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    getLocalizedStrategyName(strategy),
                                    fontWeight = if (isApplied) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                                if (isApplied) {
                                    Text(
                                        stringResource(R.string.label_applied),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStrategyDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Active Config Card
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.home_active_config),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showStrategyDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Build,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                getLocalizedStrategyName(selectedStrategy),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            stringResource(R.string.label_change_strategy),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    }

                    if (globalMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                stringResource(R.string.label_global_mode),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    } else if (selectedPresets.isNotEmpty()) {
                        val localizedPresets = mutableListOf<String>()
                        for (preset in selectedPresets) {
                            localizedPresets.add(getLocalizedPresetName(preset))
                        }
                        Text(
                            localizedPresets.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Hub,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$proxyHost:$proxyPort",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (isEnabled && (uptimeMillis > 0)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Timer,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatUptime(uptimeMillis),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // VPN Toggle Section
            val statusColor by animateColorAsState(
                if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                label = "StatusColor"
            )
            val containerColor by animateColorAsState(
                if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                label = "ContainerColor"
            )
            val iconColor by animateColorAsState(
                if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "IconColor"
            )
            
            val pulseScale by animateFloatAsState(
                targetValue = if (isEnabled) 1.4f else 1f,
                animationSpec = if (isEnabled) {
                    infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    )
                } else {
                    snap()
                },
                label = "PulseScale"
            )

            Box(
                contentAlignment = Alignment.Center
            ) {
                if (isEnabled) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .drawBehind {
                                drawCircle(
                                    color = statusColor.copy(alpha = 0.2f * (1f - (pulseScale - 1f) / 0.4f)),
                                    radius = size.minDimension / 2 * pulseScale
                                )
                            }
                    )
                }

                Surface(
                    modifier = Modifier.size(200.dp),
                    shape = CircleShape,
                    color = containerColor,
                    onClick = { onToggleVpn() },
                    tonalElevation = if (isEnabled) 4.dp else 0.dp,
                    shadowElevation = if (isEnabled) 8.dp else 2.dp,
                    border = BorderStroke(
                        width = 4.dp,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (isEnabled) Icons.Rounded.Shield else Icons.Rounded.ShieldMoon,
                                null,
                                modifier = Modifier.size(80.dp),
                                tint = iconColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            AnimatedContent(
                                targetState = isEnabled,
                                transitionSpec = {
                                    fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                                },
                                label = "StatusText"
                            ) { enabled ->
                                Text(
                                    if (enabled) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = iconColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                if (isEnabled) stringResource(R.string.home_hint_connected) else stringResource(R.string.home_hint_disconnected),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

package com.example.nozapret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nozapret.core.Config
import com.example.nozapret.ui.getLocalizedPresetName
import com.example.nozapret.ui.getPresetIcon
import com.example.nozapret.ui.components.SectionHeader
import com.example.nozapret.ui.components.SettingsGroup
import com.example.nozapret.ui.screens.HomeTab
import com.example.nozapret.ui.theme.NoZapretTheme

@Preview(showBackground = true, name = "Home Tab - Disconnected")
@Composable
fun PreviewHomeTabDisconnected() {
    NoZapretTheme {
        HomeTab(
            isEnabled = false,
            vpnStartTime = 0L,
            selectedStrategy = "Auto (Recommended)",
            pinnedStrategies = emptyList(),
            selectedPresets = listOf("YouTube", "Discord"),
            onToggleVpn = {},
            proxyHost = "127.0.0.1",
            proxyPort = "1080",
            globalMode = false,
            onStrategySelected = {}
        )
    }
}

@Preview(showBackground = true, name = "Home Tab - Connected")
@Composable
fun PreviewHomeTabConnected() {
    NoZapretTheme {
        HomeTab(
            isEnabled = true,
            vpnStartTime = System.currentTimeMillis() - 3600000L, // 1 hour ago
            selectedStrategy = "YouTube Fix",
            pinnedStrategies = emptyList(),
            selectedPresets = listOf("YouTube"),
            onToggleVpn = {},
            proxyHost = "127.0.0.1",
            proxyPort = "1080",
            globalMode = false,
            onStrategySelected = {}
        )
    }
}

@Preview(showBackground = true, name = "Settings - General Section")
@Composable
fun PreviewSettingsGeneral() {
    NoZapretTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsGroup(
                    title = "Bypass Presets",
                    icon = Icons.Rounded.Build
                ) {
                    Config.PRESETS.forEach { (name, _) ->
                        SectionHeader(
                            icon = getPresetIcon(name),
                            title = getLocalizedPresetName(name)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Home Tab - Global Mode")
@Composable
fun PreviewHomeTabGlobal() {
    NoZapretTheme {
        HomeTab(
            isEnabled = true,
            vpnStartTime = System.currentTimeMillis() - 600000L, // 10 minutes ago
            selectedStrategy = "Auto (Recommended)",
            pinnedStrategies = emptyList(),
            selectedPresets = emptyList(),
            onToggleVpn = {},
            proxyHost = "127.0.0.1",
            proxyPort = "1080",
            globalMode = true,
            onStrategySelected = {}
        )
    }
}

@Preview(showBackground = true, name = "Strategy Item - Testing")
@Composable
fun PreviewStrategyItemTesting() {
    NoZapretTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            // Mocking a single strategy item from the LazyColumn
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                ListItem(
                    headlineContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("YouTube Fix", fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = {},
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    supportingContent = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                "Optimized for YouTube (Split, OOB, Fake).",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { 0.5f },
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "5/10 (tested 5)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {},
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(
                                Icons.Rounded.Stop,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

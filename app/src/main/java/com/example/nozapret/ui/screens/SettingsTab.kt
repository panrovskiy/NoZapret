package com.example.nozapret.ui.screens


import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast



import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.ui.unit.dp

import com.example.nozapret.ui.getLocalizedPresetName
import com.example.nozapret.ui.getLocalizedStrategyName
import com.example.nozapret.ui.getLocalizedStrategyDesc
import com.example.nozapret.ui.getPresetIcon
import com.example.nozapret.R
import com.example.nozapret.MainViewModel
import com.example.nozapret.core.Config
import com.example.nozapret.ui.components.PresetEditorDialog
import com.example.nozapret.ui.components.AppPickerDialog
import com.example.nozapret.ui.components.FadeEntrance
import com.example.nozapret.ui.components.SettingsGroup
import com.example.nozapret.ui.components.bouncingClickable

enum class StrategySortMode {
    PRESET,
    PING_ASC,
    PING_DESC,
    SUCCESS_ASC,
    SUCCESS_DESC,
    DEFAULT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    settingsTab: Int,
    onSettingsTabChange: (Int) -> Unit,
    scrollState: ScrollState,
    highlightPresetsTrigger: Int,
    selectedStrategy: String,
    onStrategyChange: (String) -> Unit,
    selectedPresets: List<String>,
    onPresetToggle: (String, Boolean) -> Unit,
    dnsServer: String,
    onDnsChange: (String) -> Unit,
    customArgs: String,
    onCustomArgsChange: (String) -> Unit,
    excludeSelf: Boolean,
    onExcludeSelfChange: (Boolean) -> Unit,
    globalMode: Boolean,
    onGlobalModeChange: (Boolean) -> Unit,
    customHostList: String,
    onCustomHostListChange: (String) -> Unit,
    isIgnoringBattery: Boolean,
    stats: Map<String, Triple<Int, Int, Int>>,
    committedStats: Map<String, Triple<Int, Int, Int>>,
    currentlyTesting: List<String>,
    onTestStrategy: (String) -> Unit,
    onShowDetails: (String) -> Unit,
    onShowStrategyArgsInfo: (String) -> Unit,
    onResetTests: () -> Unit,
    onTestAll: () -> Unit,
    pinnedStrategies: List<String>,
    onPinStrategy: (String) -> Unit,
    bypassedSitesCount: Int,
    strategiesTestedCount: Int,
    proxyHost: String,
    onProxyHostChange: (String) -> Unit,
    proxyPort: String,
    onProxyPortChange: (String) -> Unit,
    allowedApps: List<String>,
    onToggleAllowedApp: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") testResults: Map<String, Map<String, MainViewModel.TlsTestResult>>,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    customPrimaryColor: Int,
    onCustomPrimaryColorChange: (Int) -> Unit,
    customThemeBase: String,
    onCustomThemeBaseChange: (String) -> Unit,
    quickTestUrl: String,
    onQuickTestUrlChange: (String) -> Unit,
    quickTestResult: MainViewModel.TlsTestResult?,
    isQuickTesting: Boolean,
    onRunQuickTest: () -> Unit,
    quickTestStrategy: String,
    onQuickTestStrategyChange: (String) -> Unit,
    onRunDiagnostics: () -> Unit,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    autoConnect: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    enableIpv6: Boolean,
    onEnableIpv6Change: (Boolean) -> Unit,
    runMode: String,
    onRunModeChange: (String) -> Unit,
    isCheckingUpdates: Boolean,
    onCheckUpdates: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onPasteCustomArgs: () -> Unit,
    onClearCustomArgs: () -> Unit,
    onExportLogs: () -> Unit,
    getPresetDomains: (String) -> List<String>,
    onAddDomainToPreset: (String, String) -> Result<Unit>,
    onRemoveDomainFromPreset: (String, String) -> Unit,
    onEditDomainInPreset: (String, String, String) -> Result<Unit>,
    onClearPresetDomains: (String) -> Unit,
    onResetPresetDomainsToDefault: (String) -> Unit,
    fakeSniPool: List<String>,
    onAddFakeSniHost: (String) -> Result<Unit>,
    onRemoveFakeSniHost: (String) -> Unit,
    onEditFakeSniHost: (String, String) -> Result<Unit>,
    onResetFakeSniPoolToDefault: () -> Unit,
) {
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }

    if (showAppPicker) {
        AppPickerDialog(
            selectedApps = allowedApps,
            onToggleApp = onToggleAllowedApp,
            onDismiss = { showAppPicker = false }
        )
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = settingsTab == 0,
                onClick = { onSettingsTabChange(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = SegmentedButtonDefaults.colors(),
                icon = { SegmentedButtonDefaults.Icon(active = settingsTab == 0) { Icon(Icons.Rounded.Tune, null) } }
            ) {
                Text(stringResource(R.string.tab_general))
            }
            SegmentedButton(
                selected = settingsTab == 1,
                onClick = { onSettingsTabChange(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = SegmentedButtonDefaults.colors(),
                icon = { SegmentedButtonDefaults.Icon(active = settingsTab == 1) { Icon(Icons.Rounded.Speed, null) } }
            ) {
                Text(stringResource(R.string.tab_tests))
            }
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = settingsTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }.using(SizeTransform(clip = false))
                },
                label = "TabTransition"
            ) { targetTab ->
                if (targetTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        FadeEntrance(index = 0) {
                            SettingsGroup(
                                title = stringResource(R.string.section_strategies),
                                icon = Icons.Rounded.Build
                            ) {
                                var strategyExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = strategyExpanded,
                                    onExpandedChange = { strategyExpanded = it },
                                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = getLocalizedStrategyName(selectedStrategy),
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.label_strategy)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = strategyExpanded) },
                                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        ),
                                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    ExposedDropdownMenu(
                                        expanded = strategyExpanded,
                                        onDismissRequest = { strategyExpanded = false }
                                    ) {
                                        Config.STRATEGIES_DATA.forEach { strat ->
                                            val name = strat.name
                                            val desc = strat.description
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(
                                                            onClick = { onShowStrategyArgsInfo(name) },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Rounded.Info, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                        Spacer(Modifier.width(8.dp))
                                                        Column {
                                                            Text(getLocalizedStrategyName(name), fontWeight = FontWeight.Bold)
                                                            Text(getLocalizedStrategyDesc(name, desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    onStrategyChange(name)
                                                    strategyExpanded = false
                                                },
                                                leadingIcon = {
                                                    val isPinned = pinnedStrategies.contains(name)
                                                    IconButton(onClick = { onPinStrategy(name) }) {
                                                        Icon(
                                                            imageVector = if (isPinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                            contentDescription = null,
                                                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = selectedStrategy == "Custom",
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    OutlinedTextField(
                                        value = customArgs,
                                        onValueChange = onCustomArgsChange,
                                        label = { Text(stringResource(R.string.label_custom_arguments)) },
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        leadingIcon = { Icon(Icons.Rounded.Code, null) },
                                        trailingIcon = {
                                            Row {
                                                IconButton(onClick = onPasteCustomArgs) {
                                                    Icon(Icons.Rounded.ContentPaste, stringResource(R.string.btn_paste))
                                                }
                                                IconButton(onClick = onClearCustomArgs) {
                                                    Icon(Icons.Rounded.Clear, stringResource(R.string.btn_clear))
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (highlightPresetsTrigger > 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = tween(durationMillis = 500),
                            label = "presets_highlight"
                        )

                        var editingPreset by remember { mutableStateOf<String?>(null) }
                        if (editingPreset != null) {
                            val pName = editingPreset!!
                            PresetEditorDialog(
                                presetName = pName,
                                domains = getPresetDomains(pName),
                                onAddDomain = { onAddDomainToPreset(pName, it) },
                                onRemoveDomain = { onRemoveDomainFromPreset(pName, it) },
                                onEditDomain = { old, new -> onEditDomainInPreset(pName, old, new) },
                                onClearDomains = { onClearPresetDomains(pName) },
                                onRestoreDefaults = { onResetPresetDomainsToDefault(pName) },
                                onDismiss = { editingPreset = null }
                            )
                        }

                        FadeEntrance(index = 1) {
                            SettingsGroup(
                                title = stringResource(R.string.section_presets),
                                icon = Icons.Rounded.Extension,
                                border = if (highlightPresetsTrigger > 0) BorderStroke(2.dp, animatedBorderColor) else null
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Config.PRESETS.forEach { (name, _) ->
                                        val siteCount = getPresetDomains(name).size
                                        ListItem(
                                            headlineContent = { Text(getLocalizedPresetName(name)) },
                                            supportingContent = { Text(stringResource(R.string.preset_sites_count, siteCount)) },
                                            leadingContent = { Icon(getPresetIcon(name), null) },
                                            trailingContent = { 
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(onClick = { editingPreset = name }) {
                                                        Icon(Icons.Rounded.Edit, stringResource(R.string.btn_edit_preset), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Switch(checked = selectedPresets.contains(name), onCheckedChange = { onPresetToggle(name, it) }) 
                                                }
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                            modifier = Modifier.bouncingClickable { onPresetToggle(name, !selectedPresets.contains(name)) }
                                        )
                                    }
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.preset_custom)) },
                                        trailingContent = { 
                                            Switch(checked = selectedPresets.contains("Custom"), onCheckedChange = { onPresetToggle("Custom", it) }) 
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.bouncingClickable { onPresetToggle("Custom", !selectedPresets.contains("Custom")) }
                                    )
                                    AnimatedVisibility(visible = selectedPresets.contains("Custom")) {
                                        OutlinedTextField(
                                            value = customHostList,
                                            onValueChange = onCustomHostListChange,
                                            label = { Text(stringResource(R.string.label_custom_domains)) },
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            shape = MaterialTheme.shapes.medium
                                        )
                                    }
                                }
                            }
                        }

                        FadeEntrance(index = 2) {
                            SettingsGroup(
                                title = stringResource(R.string.section_connection),
                                icon = Icons.Rounded.Link
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.label_run_mode)) },
                                        supportingContent = {
                                            Text(if (runMode == "VPN") stringResource(R.string.run_mode_vpn) else stringResource(R.string.run_mode_proxy))
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        trailingContent = {
                                            var runModeExpanded by remember { mutableStateOf(false) }
                                            Box {
                                                IconButton(onClick = { runModeExpanded = true }) {
                                                    Icon(Icons.Rounded.MoreVert, null)
                                                }
                                                DropdownMenu(expanded = runModeExpanded, onDismissRequest = { runModeExpanded = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.run_mode_vpn)) },
                                                        onClick = { onRunModeChange("VPN"); runModeExpanded = false }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.run_mode_proxy)) },
                                                        onClick = { onRunModeChange("Proxy"); runModeExpanded = false }
                                                    )
                                                }
                                            }
                                        }
                                    )
                                    if (runMode == "Proxy") {
                                        Text(
                                            stringResource(R.string.label_proxy_mode_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                        )
                                    }

                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.label_auto_connect)) },
                                        trailingContent = { Switch(checked = autoConnect, onCheckedChange = onAutoConnectChange) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.bouncingClickable { onAutoConnectChange(!autoConnect) }
                                    )
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.label_enable_ipv6)) },
                                        trailingContent = { Switch(checked = enableIpv6, onCheckedChange = onEnableIpv6Change) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.bouncingClickable { onEnableIpv6Change(!enableIpv6) }
                                    )
                                }
                            }
                        }

                        FadeEntrance(index = 3) {
                            SettingsGroup(
                                title = stringResource(R.string.section_advanced),
                                icon = Icons.Rounded.Settings
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.label_global_mode)) },
                                        trailingContent = { Switch(checked = globalMode, onCheckedChange = onGlobalModeChange) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.bouncingClickable { onGlobalModeChange(!globalMode) }
                                    )
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.label_exclude_self)) },
                                        trailingContent = { Switch(checked = excludeSelf, onCheckedChange = onExcludeSelfChange) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.bouncingClickable { onExcludeSelfChange(!excludeSelf) }
                                    )
                                    OutlinedTextField(
                                        value = dnsServer,
                                        onValueChange = onDnsChange,
                                        label = { Text(stringResource(R.string.label_dns_server)) },
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    
                                    if (runMode == "Proxy") {
                                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = proxyHost,
                                                onValueChange = onProxyHostChange,
                                                label = { Text(stringResource(R.string.label_proxy_host)) },
                                                modifier = Modifier.weight(2f),
                                                shape = MaterialTheme.shapes.medium
                                            )
                                            OutlinedTextField(
                                                value = proxyPort,
                                                onValueChange = onProxyPortChange,
                                                label = { Text(stringResource(R.string.label_proxy_port)) },
                                                modifier = Modifier.weight(1f),
                                                shape = MaterialTheme.shapes.medium,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { showAppPicker = true },
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                                    ) {
                                        Icon(Icons.Rounded.Apps, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.label_allowed_apps))
                                    }

                                    if (!isIgnoringBattery) {
                                        Button(
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(Icons.Rounded.BatteryAlert, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.battery_optimization_title))
                                        }
                                    }
                                }
                            }
                        }

                        FadeEntrance(index = 4) {
                            SettingsGroup(
                                title = stringResource(R.string.section_appearance),
                                icon = Icons.Rounded.Palette
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    var langExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = langExpanded,
                                        onExpandedChange = { langExpanded = it },
                                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = when(selectedLanguage) {
                                                "en" -> stringResource(R.string.lang_en)
                                                "ru" -> stringResource(R.string.lang_ru)
                                                "uk" -> stringResource(R.string.lang_uk)
                                                "kk" -> stringResource(R.string.lang_kk)
                                                "tr" -> stringResource(R.string.lang_tr)
                                                else -> stringResource(R.string.lang_system)
                                            },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(stringResource(R.string.label_language)) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        )
                                        ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                                            listOf("System", "en", "ru", "uk", "kk", "tr").forEach { lang ->
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(when(lang) {
                                                            "en" -> stringResource(R.string.lang_en)
                                                            "ru" -> stringResource(R.string.lang_ru)
                                                            "uk" -> stringResource(R.string.lang_uk)
                                                            "kk" -> stringResource(R.string.lang_kk)
                                                            "tr" -> stringResource(R.string.lang_tr)
                                                            else -> stringResource(R.string.lang_system)
                                                        })
                                                    },
                                                    onClick = { onLanguageChange(lang); langExpanded = false }
                                                )
                                            }
                                        }
                                    }

                                    var themeExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = themeExpanded,
                                        onExpandedChange = { themeExpanded = it },
                                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = when(themeMode) {
                                                "Light" -> stringResource(R.string.theme_light)
                                                "Dark" -> stringResource(R.string.theme_dark)
                                                else -> stringResource(R.string.theme_system)
                                            },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(stringResource(R.string.label_theme)) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        )
                                        ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                                            listOf("System", "Light", "Dark").forEach { mode ->
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(when(mode) {
                                                            "Light" -> stringResource(R.string.theme_light)
                                                            "Dark" -> stringResource(R.string.theme_dark)
                                                            else -> stringResource(R.string.theme_system)
                                                        })
                                                    },
                                                    onClick = { onThemeModeChange(mode); themeExpanded = false }
                                                )
                                            }
                                        }
                                    }

                                    var themeBaseExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = themeBaseExpanded,
                                        onExpandedChange = { themeBaseExpanded = it },
                                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customThemeBase,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(stringResource(R.string.label_theme_base)) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeBaseExpanded) },
                                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        )
                                        ExposedDropdownMenu(expanded = themeBaseExpanded, onDismissRequest = { themeBaseExpanded = false }) {
                                            listOf("Dynamic", "M3", "Slate").forEach { base ->
                                                DropdownMenuItem(
                                                    text = { Text(base) },
                                                    onClick = { onCustomThemeBaseChange(base); themeBaseExpanded = false }
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = String.format("#%06X", customPrimaryColor and 0xFFFFFF),
                                        onValueChange = {
                                            try {
                                                val cleaned = it.replace("#", "")
                                                if (cleaned.length <= 6) {
                                                    val colorInt = cleaned.toInt(16) or (0xFF shl 24)
                                                    onCustomPrimaryColorChange(colorInt)
                                                }
                                            } catch(_: Exception) {}
                                        },
                                        label = { Text(stringResource(R.string.label_custom_color)) },
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        leadingIcon = { Icon(Icons.Rounded.Colorize, null) }
                                    )
                                }
                            }
                        }

                        FadeEntrance(index = 3) {
                            SettingsGroup(title = stringResource(R.string.section_troubleshooting), icon = Icons.Rounded.Report) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = onRunDiagnostics, 
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.btn_run_diagnostics))
                                    }
                                    FilledTonalButton(
                                        onClick = onCheckUpdates, 
                                        enabled = !isCheckingUpdates, 
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        if (isCheckingUpdates) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        else Icon(Icons.Rounded.Update, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.btn_check_updates))
                                    }

                                    OutlinedButton(
                                        onClick = onExportLogs,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                                    ) {
                                        Icon(Icons.Rounded.Description, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.btn_export_logs))
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                    Text(
                                        text = stringResource(R.string.tester_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                    )

                                    var testUrlExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = testUrlExpanded,
                                        onExpandedChange = { testUrlExpanded = it },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = if (quickTestStrategy == "None") stringResource(R.string.tester_strategy_none) else getLocalizedStrategyName(quickTestStrategy),
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(stringResource(R.string.label_strategy)) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = testUrlExpanded) },
                                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        )
                                        ExposedDropdownMenu(expanded = testUrlExpanded, onDismissRequest = { testUrlExpanded = false }) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.tester_strategy_none)) },
                                                onClick = { onQuickTestStrategyChange("None"); testUrlExpanded = false }
                                            )
                                            Config.STRATEGIES_DATA.forEach { strat ->
                                                val stratName = strat.name
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            IconButton(
                                                                onClick = { onShowStrategyArgsInfo(stratName) },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                                            }
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(getLocalizedStrategyName(stratName))
                                                        }
                                                    },
                                                    onClick = { onQuickTestStrategyChange(stratName); testUrlExpanded = false }
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = quickTestUrl,
                                        onValueChange = onQuickTestUrlChange,
                                        label = { Text(stringResource(R.string.tester_placeholder)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                        singleLine = true
                                    )

                                    Button(
                                        onClick = onRunQuickTest,
                                        enabled = !isQuickTesting && quickTestUrl.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        if (isQuickTesting) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.tester_testing))
                                        } else {
                                            Icon(Icons.Rounded.Bolt, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.tester_btn_test))
                                        }
                                    }

                                    quickTestResult?.let { res ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (res.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (res.success) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                                        contentDescription = null,
                                                        tint = if (res.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = if (res.success) stringResource(R.string.tester_status_passed) else stringResource(R.string.tester_status_blocked),
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                }
                                                res.ping?.let { ping ->
                                                    Text(stringResource(R.string.tester_latency, ping), style = MaterialTheme.typography.bodySmall)
                                                }
                                                res.protocol?.let { proto ->
                                                    if (proto.isNotEmpty()) {
                                                        Text(stringResource(R.string.tester_protocol, proto), style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }
                                                res.cipherSuite?.let { cipher ->
                                                    if (cipher.isNotEmpty()) {
                                                        Text(stringResource(R.string.tester_cipher, cipher), style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }
                                                res.error?.let { err ->
                                                    if (err.isNotEmpty() && !res.success) {
                                                        Text("Error: $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                    Text(
                                        text = stringResource(R.string.testing_strategies_count, strategiesTestedCount, Config.STRATEGIES.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }

                        FadeEntrance(index = 6) {
                            SettingsGroup(title = stringResource(R.string.section_backup_restore), icon = Icons.Rounded.Storage) {
                                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = onExportConfig, 
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                                    ) { Text(stringResource(R.string.btn_export)) }
                                    OutlinedButton(
                                        onClick = onImportConfig, 
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                                    ) { Text(stringResource(R.string.btn_import)) }
                                }
                            }
                        }

                        FadeEntrance(index = 7) {
                            SettingsGroup(
                                title = stringResource(R.string.section_fake_sni),
                                icon = Icons.Rounded.Security
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.fake_sni_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    var newFakeSniInput by remember { mutableStateOf("") }
                                    var fakeSniError by remember { mutableStateOf<String?>(null) }
                                    var editingFakeSniHost by remember { mutableStateOf<String?>(null) }
                                    var editFakeSniInput by remember { mutableStateOf("") }

                                    val localContext = context
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newFakeSniInput,
                                            onValueChange = {
                                                newFakeSniInput = it
                                                fakeSniError = null
                                            },
                                            label = { Text(stringResource(R.string.label_add_fake_sni)) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = MaterialTheme.shapes.medium
                                        )
                                        IconButton(
                                            onClick = {
                                                if (newFakeSniInput.isNotBlank()) {
                                                    val res = onAddFakeSniHost(newFakeSniInput)
                                                    if (res.isSuccess) {
                                                        newFakeSniInput = ""
                                                        fakeSniError = null
                                                    } else {
                                                        fakeSniError = res.exceptionOrNull()?.message ?: localContext.getString(R.string.error_invalid_domain)
                                                    }
                                                }
                                            },
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Rounded.Add, null)
                                        }
                                    }

                                    fakeSniError?.let { err ->
                                        Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            fakeSniPool.forEach { host ->
                                                ListItem(
                                                    headlineContent = { Text(host, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
                                                    trailingContent = {
                                                        Row {
                                                            IconButton(onClick = {
                                                                editingFakeSniHost = host
                                                                editFakeSniInput = host
                                                            }) {
                                                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                                            }
                                                            if (fakeSniPool.size > 1) {
                                                                IconButton(onClick = { onRemoveFakeSniHost(host) }) {
                                                                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                                )
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = onResetFakeSniPoolToDefault,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.btn_restore_defaults))
                                    }

                                    val invalidDomainText = stringResource(R.string.error_invalid_domain)
                                    editingFakeSniHost?.let { oldHost ->
                                        AlertDialog(
                                            onDismissRequest = { editingFakeSniHost = null },
                                            title = { Text(stringResource(R.string.title_edit_domain)) },
                                            text = {
                                                OutlinedTextField(
                                                    value = editFakeSniInput,
                                                    onValueChange = { editFakeSniInput = it },
                                                    label = { Text(stringResource(R.string.label_domain)) },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            },
                                            confirmButton = {
                                                Button(onClick = {
                                                    val res = onEditFakeSniHost(oldHost, editFakeSniInput)
                                                    if (res.isSuccess) {
                                                        editingFakeSniHost = null
                                                    } else {
                                                        Toast.makeText(context, res.exceptionOrNull()?.message ?: invalidDomainText, Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Text(stringResource(R.string.btn_save))
                                                }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { editingFakeSniHost = null }) {
                                                    Text(stringResource(R.string.btn_cancel))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        FadeEntrance(index = 8) {
                            SettingsGroup(title = stringResource(R.string.section_about), icon = Icons.Rounded.Info) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val pkgInfo = try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
                                    val appVer = pkgInfo?.versionName ?: "2.3.5"

                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.app_version)) },
                                        supportingContent = { Text("v$appVer") },
                                        trailingContent = { Icon(Icons.Rounded.Launch, null) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.bouncingClickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/panrovskiy/NoZapret"))
                                            context.startActivity(intent)
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.byedpi_version)) },
                                        supportingContent = { Text("v0.17.3") },
                                        trailingContent = { Icon(Icons.Rounded.Launch, null) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.bouncingClickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hufrea/byedpi"))
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                } else {
                    // Tests Tab
                    var sortMode by remember { mutableStateOf(StrategySortMode.DEFAULT) }
                    var filterPreset by remember { mutableStateOf("All") }
                    var showSortMenu by remember { mutableStateOf(false) }

                    val allStrats = Config.STRATEGIES_DATA.filter { strat ->
                        filterPreset == "All" || strat.category == filterPreset
                    }.sortedWith { a, b ->
                        val aName = a.name
                        val bName = b.name
                        
                        val aTesting = currentlyTesting.contains(aName)
                        val bTesting = currentlyTesting.contains(bName)
                        
                        // 1. Testing strategies always at the top
                        if (aTesting != bTesting) {
                            return@sortedWith if (aTesting) -1 else 1
                        }
                        
                        val aStat = stats[aName] ?: committedStats[aName] ?: Triple(0, 0, 0)
                        val bStat = stats[bName] ?: committedStats[bName] ?: Triple(0, 0, 0)

                        val aResults = testResults[aName] ?: emptyMap()
                        val bResults = testResults[bName] ?: emptyMap()
                        val aAvgPing = if (aResults.isNotEmpty()) aResults.values.mapNotNull { it.ping }.average() else Double.MAX_VALUE
                        val bAvgPing = if (bResults.isNotEmpty()) bResults.values.mapNotNull { it.ping }.average() else Double.MAX_VALUE

                        when (sortMode) {
                            StrategySortMode.PRESET -> {
                                val catComp = a.category.compareTo(b.category)
                                if (catComp != 0) return@sortedWith catComp
                            }
                            StrategySortMode.PING_ASC -> {
                                if (aAvgPing != bAvgPing) return@sortedWith aAvgPing.compareTo(bAvgPing)
                            }
                            StrategySortMode.PING_DESC -> {
                                if (aAvgPing != bAvgPing) return@sortedWith bAvgPing.compareTo(aAvgPing)
                            }
                            StrategySortMode.SUCCESS_ASC -> {
                                if (aStat.first != bStat.first) return@sortedWith aStat.first.compareTo(bStat.first)
                            }
                            StrategySortMode.SUCCESS_DESC -> {
                                if (aStat.first != bStat.first) return@sortedWith bStat.first.compareTo(aStat.first)
                            }
                            StrategySortMode.DEFAULT -> {}
                        }

                        // Fallback/Secondary sort
                        if (aStat.first != bStat.first) {
                            return@sortedWith bStat.first.compareTo(aStat.first)
                        }
                        
                        if (aStat.second != bStat.second) {
                            return@sortedWith bStat.second.compareTo(aStat.second)
                        }

                        aName.compareTo(bName)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        item {
                            FadeEntrance(index = 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), 
                                    horizontalArrangement = Arrangement.SpaceBetween, 
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(stringResource(R.string.title_verification), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.testing_sites_count, bypassedSitesCount), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box {
                                            IconButton(onClick = { showSortMenu = true }) {
                                                Icon(Icons.Rounded.Sort, null)
                                            }
                                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                                Text(
                                                    stringResource(R.string.label_sort_by), 
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.sort_preset)) },
                                                    onClick = { sortMode = StrategySortMode.PRESET; showSortMenu = false },
                                                    trailingIcon = { if (sortMode == StrategySortMode.PRESET) Icon(Icons.Rounded.Check, null) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.sort_ping) + " (" + stringResource(R.string.sort_asc) + ")") },
                                                    onClick = { sortMode = StrategySortMode.PING_ASC; showSortMenu = false },
                                                    trailingIcon = { if (sortMode == StrategySortMode.PING_ASC) Icon(Icons.Rounded.Check, null) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.sort_ping) + " (" + stringResource(R.string.sort_desc) + ")") },
                                                    onClick = { sortMode = StrategySortMode.PING_DESC; showSortMenu = false },
                                                    trailingIcon = { if (sortMode == StrategySortMode.PING_DESC) Icon(Icons.Rounded.Check, null) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.sort_success) + " (" + stringResource(R.string.sort_asc) + ")") },
                                                    onClick = { sortMode = StrategySortMode.SUCCESS_ASC; showSortMenu = false },
                                                    trailingIcon = { if (sortMode == StrategySortMode.SUCCESS_ASC) Icon(Icons.Rounded.Check, null) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.sort_success) + " (" + stringResource(R.string.sort_desc) + ")") },
                                                    onClick = { sortMode = StrategySortMode.SUCCESS_DESC; showSortMenu = false },
                                                    trailingIcon = { if (sortMode == StrategySortMode.SUCCESS_DESC) Icon(Icons.Rounded.Check, null) }
                                                )
                                                HorizontalDivider()
                                                Text(
                                                    stringResource(R.string.label_filter_sort), 
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                val categories = listOf("All") + Config.STRATEGIES_DATA.map { it.category }.distinct()
                                                categories.forEach { cat ->
                                                    DropdownMenuItem(
                                                        text = { Text(cat) },
                                                        onClick = { filterPreset = cat; showSortMenu = false },
                                                        trailingIcon = { if (filterPreset == cat) Icon(Icons.Rounded.Check, null) }
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(onClick = onTestAll) { Icon(if (currentlyTesting.isNotEmpty()) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null) }
                                        IconButton(onClick = onResetTests) { Icon(Icons.Rounded.Refresh, null) }
                                    }
                                }
                            }
                        }

                        items(allStrats.size, key = { i -> allStrats[i].name }) { i ->
                            val strat = allStrats[i]
                            val name = strat.name
                            val isTesting = currentlyTesting.contains(name)
                            val stat = stats[name] ?: committedStats[name] ?: Triple(0, 0, bypassedSitesCount)
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .bouncingClickable { onShowDetails(name) }
                            ) {
                                ListItem(
                                    headlineContent = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { onShowStrategyArgsInfo(name) },
                                                modifier = Modifier.size(32.dp).padding(end = 8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Info, 
                                                    null, 
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(getLocalizedStrategyName(name), fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    supportingContent = {
                                        Column {
                                            LinearProgressIndicator(
                                                progress = { if (stat.third > 0) stat.second.toFloat() / stat.third else 0f },
                                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(
                                                    stringResource(R.string.test_stat_format, stat.first, stat.second, stat.third),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                
                                                val results = testResults[name] ?: emptyMap()
                                                if (results.isNotEmpty()) {
                                                    val avgPing = results.values.mapNotNull { it.ping }.average()
                                                    if (!avgPing.isNaN()) {
                                                        Text(
                                                            stringResource(R.string.tester_latency, avgPing.toInt()),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    trailingContent = {
                                        if (isTesting) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        else IconButton(onClick = { onTestStrategy(name) }) { Icon(Icons.Rounded.PlayArrow, null) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.nozapret.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.example.nozapret.ui.getLocalizedPresetName
import com.example.nozapret.ui.getLocalizedStrategyName
import com.example.nozapret.ui.getLocalizedStrategyDesc
import com.example.nozapret.ui.getPresetIcon
import com.example.nozapret.ui.parseSimpleHtml
import com.example.nozapret.R
import com.example.nozapret.MainViewModel
import com.example.nozapret.BuildConfig
import com.example.nozapret.core.Config
import com.example.nozapret.ui.components.AppPickerDialog
import com.example.nozapret.ui.components.SectionHeader
import com.example.nozapret.ui.components.SettingsGroup
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

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
    testResults: Map<String, Map<String, MainViewModel.TlsTestResult>>,
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
) {
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
                icon = { SegmentedButtonDefaults.Icon(active = settingsTab == 0) { Icon(Icons.Rounded.Tune, null) } }
            ) {
                Text(stringResource(R.string.tab_general))
            }
            SegmentedButton(
                selected = settingsTab == 1,
                onClick = { onSettingsTabChange(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { SegmentedButtonDefaults.Icon(active = settingsTab == 1) { Icon(Icons.Rounded.Speed, null) } }
            ) {
                Text(stringResource(R.string.tab_tests))
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (settingsTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    SettingsGroup(
                        title = stringResource(R.string.section_strategies),
                        icon = Icons.Rounded.Build
                    ) {
                        var strategyExpanded by remember { mutableStateOf(value = false) }
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
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            )
                            ExposedDropdownMenu(
                                expanded = strategyExpanded,
                                onDismissRequest = { strategyExpanded = false }
                            ) {
                                Config.STRATEGIES.forEach { (name, desc) ->
                                    val isApplied = name == selectedStrategy
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(getLocalizedStrategyName(name), fontWeight = FontWeight.Bold)
                                                if (isApplied) {
                                                    Text(
                                                        stringResource(R.string.label_applied),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(getLocalizedStrategyDesc(name, desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            onStrategyChange(name)
                                            strategyExpanded = false
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { onShowStrategyArgsInfo(name) }) {
                                                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(20.dp))
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
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    value = customArgs,
                                    onValueChange = onCustomArgsChange,
                                    label = { Text(stringResource(R.string.label_custom_arguments)) },
                                    placeholder = { Text(stringResource(R.string.placeholder_custom_args)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    supportingText = { Text(stringResource(R.string.supporting_custom_args)) },
                                    shape = MaterialTheme.shapes.medium,
                                    leadingIcon = { Icon(Icons.Rounded.Code, null) },
                                    trailingIcon = {
                                        if (customArgs.isNotEmpty()) {
                                            IconButton(onClick = { onCustomArgsChange("") }) {
                                                Icon(Icons.Rounded.Clear, null)
                                            }
                                        }
                                    }
                                )

                                val context = LocalContext.current
                                val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }
                                val msgImported = stringResource(R.string.msg_imported)
                                val msgErrorFile = stringResource(R.string.error_file_read)
                                val msgPasted = stringResource(R.string.msg_pasted)

                                val filePickerLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.GetContent()
                                ) { uri ->
                                    uri?.let {
                                        try {
                                            context.contentResolver.openInputStream(it)?.use { stream ->
                                                val text = stream.bufferedReader().readText()
                                                onCustomArgsChange(text)
                                                Toast.makeText(context, msgImported, Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (_: Exception) {
                                            Toast.makeText(context, msgErrorFile, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val clip = clipboardManager.primaryClip
                                            if ((clip != null) && (clip.itemCount > 0)) {
                                                val text = clip.getItemAt(0).text?.toString() ?: ""
                                                if (text.isNotEmpty()) {
                                                    onCustomArgsChange(text)
                                                    Toast.makeText(context, msgPasted, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.btn_paste), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    OutlinedButton(
                                        onClick = { filePickerLauncher.launch("text/plain") },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.FileOpen, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.btn_import_file), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    FilledTonalButton(
                                        onClick = { onCustomArgsChange("") },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.btn_clear), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    val presetsColor = remember { Animatable(Color.Transparent) }
                    var presetsOffset by remember { mutableFloatStateOf(0f) }
                    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

                    LaunchedEffect(highlightPresetsTrigger) {
                        if (highlightPresetsTrigger > 0) {
                            delay(100.milliseconds)
                            scrollState.animateScrollTo(presetsOffset.toInt() - 100)
                            presetsColor.animateTo(highlightColor, animationSpec = tween(400))
                            presetsColor.animateTo(Color.Transparent, animationSpec = tween(600))
                        }
                    }

                    SettingsGroup(
                        modifier = Modifier
                            .onGloballyPositioned { presetsOffset = it.positionInParent().y }
                            .drawBehind { drawRect(presetsColor.value) }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                SectionHeader(Icons.Rounded.Extension, stringResource(R.string.section_presets))
                            }

                            Config.PRESETS.forEach { (name, sites) ->
                                var showEditDialog by remember { mutableStateOf(value = false) }
                                if (showEditDialog) {
                                    var editedSites by remember { mutableStateOf(sites.joinToString("\n")) }
                                    AlertDialog(
                                        onDismissRequest = { showEditDialog = false },
                                        title = { Text(getLocalizedPresetName(name)) },
                                        text = {
                                            OutlinedTextField(
                                                value = editedSites,
                                                onValueChange = { editedSites = it },
                                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                                                label = { Text(stringResource(R.string.placeholder_sites_per_line)) }
                                            )
                                        },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                showEditDialog = false
                                            }) { Text(stringResource(R.string.btn_save)) }
                                        }
                                    )
                                }
                                ListItem(
                                    headlineContent = { Text(getLocalizedPresetName(name)) },
                                    leadingContent = {
                                        Icon(
                                            imageVector = getPresetIcon(name),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { showEditDialog = true }) {
                                                Icon(
                                                    Icons.Rounded.Edit,
                                                    null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                )
                                            }
                                            Switch(checked = selectedPresets.contains(name), onCheckedChange = { onPresetToggle(name, it) })
                                        }
                                    },
                                    modifier = Modifier.clickable { onPresetToggle(name, !selectedPresets.contains(name)) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }

                            val customSitesCount = if (customHostList.isNotBlank()) customHostList.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }.size else 0
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.preset_custom)) },
                                supportingContent = { 
                                    Text(if (customHostList.isBlank()) stringResource(R.string.no_custom_sites) else stringResource(R.string.custom_sites_count, customSitesCount)) 
                                },
                                trailingContent = { 
                                    Switch(
                                        checked = selectedPresets.contains("Custom"), 
                                        onCheckedChange = { onPresetToggle("Custom", it) }
                                    )
                                },
                                modifier = Modifier.clickable { onPresetToggle("Custom", !selectedPresets.contains("Custom")) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            AnimatedVisibility(
                                visible = selectedPresets.contains("Custom"),
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    val isError = customHostList.contains(Regex("[^a-zA-Z0-9.\\-\\s,;]"))
                                    OutlinedTextField(
                                        value = customHostList,
                                        onValueChange = onCustomHostListChange,
                                        label = { Text(stringResource(R.string.label_custom_domains)) },
                                        placeholder = { Text(stringResource(R.string.placeholder_custom_domains)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        supportingText = {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(if (isError) stringResource(R.string.error_invalid_chars) else stringResource(R.string.supporting_custom_domains))
                                                Text(stringResource(R.string.label_chars, customHostList.length))
                                            }
                                        },
                                        isError = isError,
                                        shape = MaterialTheme.shapes.medium,
                                        leadingIcon = { Icon(Icons.Rounded.Language, null) },
                                        trailingIcon = {
                                            if (customHostList.isNotEmpty()) {
                                                IconButton(onClick = { onCustomHostListChange("") }) {
                                                    Icon(Icons.Rounded.Clear, null)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    SettingsGroup(
                        title = stringResource(R.string.tester_title),
                        icon = Icons.Rounded.Speed
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = quickTestUrl,
                                onValueChange = onQuickTestUrlChange,
                                label = { Text(stringResource(R.string.tester_title)) },
                                placeholder = { Text(stringResource(R.string.tester_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                leadingIcon = { Icon(Icons.Rounded.Language, null) },
                                trailingIcon = {
                                    if (quickTestUrl.isNotEmpty()) {
                                        IconButton(onClick = { onQuickTestUrlChange("") }) {
                                            Icon(Icons.Rounded.Clear, null)
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = { onRunQuickTest() }
                                )
                            )

                            var strategyExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = strategyExpanded,
                                onExpandedChange = { strategyExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = if (quickTestStrategy == "None") stringResource(R.string.tester_strategy_none) else getLocalizedStrategyName(quickTestStrategy),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.label_strategy)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = strategyExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    leadingIcon = { Icon(Icons.Rounded.Build, null) }
                                )
                                ExposedDropdownMenu(
                                    expanded = strategyExpanded,
                                    onDismissRequest = { strategyExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(stringResource(R.string.tester_strategy_none))
                                                if (selectedStrategy == "None") {
                                                    Text(
                                                        stringResource(R.string.label_applied),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            onQuickTestStrategyChange("None")
                                            strategyExpanded = false
                                        }
                                    )
                                    Config.STRATEGIES.forEach { (name, desc) ->
                                        val isApplied = name == selectedStrategy
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(getLocalizedStrategyName(name), fontWeight = FontWeight.Bold)
                                                    if (isApplied) {
                                                        Text(
                                                            stringResource(R.string.label_applied),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Text(getLocalizedStrategyDesc(name, desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            },
                                            onClick = {
                                                onQuickTestStrategyChange(name)
                                                strategyExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            FilledTonalButton(
                                onClick = onRunQuickTest,
                                enabled = !isQuickTesting && quickTestUrl.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                if (isQuickTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.tester_testing))
                                } else {
                                    Icon(Icons.Rounded.PlayArrow, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.tester_btn_test))
                                }
                            }

                            AnimatedVisibility(
                                visible = quickTestResult != null && quickTestUrl.isNotBlank(),
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                quickTestResult?.let { result ->
                                    val success = result.success
                                    val ping = result.ping
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (success || result.httpSuccess) 
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f) 
                                            else 
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                            contentColor = if (success || result.httpSuccess) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(
                                            1.dp, 
                                            if (success || result.httpSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                                            else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    if (success || result.httpSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                                                    null,
                                                    modifier = Modifier.size(24.dp),
                                                    tint = if (success || result.httpSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (success || result.httpSuccess) stringResource(R.string.tester_status_passed) else stringResource(R.string.tester_status_blocked),
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Text("TLS:", style = MaterialTheme.typography.labelSmall)
                                                        Icon(
                                                            if (success) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                                            null,
                                                            modifier = Modifier.size(14.dp),
                                                            tint = if (success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                                        )
                                                        if (success && ping != null) {
                                                            Text(
                                                                text = "${ping}ms",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        Text("HTTP:", style = MaterialTheme.typography.labelSmall)
                                                        Icon(
                                                            if (result.httpSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                                            null,
                                                            modifier = Modifier.size(14.dp),
                                                            tint = if (result.httpSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                                        )
                                                    }

                                                    if (success) {
                                                        result.protocol?.let {
                                                            Text(
                                                                text = stringResource(R.string.tester_protocol, it),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                        result.cipherSuite?.let {
                                                            Text(
                                                                text = stringResource(R.string.tester_cipher, it),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    } else {
                                                        result.error?.let {
                                                            Text(
                                                                text = it,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.error,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    
                                                    if (!result.httpSuccess && result.httpError != null) {
                                                        Text(
                                                            text = "HTTP Err: ${result.httpError}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.error,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsGroup(
                        title = stringResource(R.string.section_advanced),
                        icon = Icons.Rounded.Settings
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            val contextLocal = LocalContext.current

                            ListItem(
                                headlineContent = { Text(stringResource(R.string.label_auto_connect)) },
                                supportingContent = { Text(stringResource(R.string.supporting_auto_connect)) },
                                leadingContent = { Icon(Icons.Rounded.FlashOn, null) },
                                trailingContent = { Switch(checked = autoConnect, onCheckedChange = onAutoConnectChange) },
                                modifier = Modifier.clickable { onAutoConnectChange(!autoConnect) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            ListItem(
                                headlineContent = { Text(stringResource(R.string.label_enable_ipv6)) },
                                supportingContent = { Text(stringResource(R.string.supporting_enable_ipv6)) },
                                leadingContent = { Icon(Icons.Rounded.Language, null) },
                                trailingContent = { Switch(checked = enableIpv6, onCheckedChange = onEnableIpv6Change) },
                                modifier = Modifier.clickable { onEnableIpv6Change(!enableIpv6) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            // Run Mode
                            var runModeExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = runModeExpanded,
                                onExpandedChange = { runModeExpanded = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = if (runMode == "VPN") stringResource(R.string.run_mode_vpn) else stringResource(R.string.run_mode_proxy),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.label_run_mode)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = runModeExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    leadingIcon = { Icon(Icons.Rounded.SettingsEthernet, null) }
                                )
                                ExposedDropdownMenu(
                                    expanded = runModeExpanded,
                                    onDismissRequest = { runModeExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.run_mode_vpn)) },
                                        onClick = {
                                            onRunModeChange("VPN")
                                            runModeExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.run_mode_proxy)) },
                                        onClick = {
                                            onRunModeChange("Proxy")
                                            runModeExpanded = false
                                        }
                                    )
                                }
                            }

                            if (!isIgnoringBattery) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                                    shape = MaterialTheme.shapes.large,
                                    onClick = {
                                        try {
                                            @android.annotation.SuppressLint("BatteryLife")
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = "package:${contextLocal.packageName}".toUri()
                                            }
                                            contextLocal.startActivity(intent)
                                        } catch (_: Exception) {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            contextLocal.startActivity(intent)
                                        }
                                    }
                                ) {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.battery_optimization_title), fontWeight = FontWeight.Bold) },
                                        supportingContent = { Text(stringResource(R.string.battery_optimization_desc)) },
                                        leadingContent = { Icon(Icons.Rounded.BatteryAlert, null, tint = MaterialTheme.colorScheme.error) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }

                            var dnsExpanded by remember { mutableStateOf(false) }
                            val dnsOptions = listOf(
                                "8.8.8.8" to stringResource(R.string.dns_google),
                                "1.1.1.1" to stringResource(R.string.dns_cloudflare),
                                "9.9.9.9" to stringResource(R.string.dns_quad9),
                                "94.140.14.14" to stringResource(R.string.dns_adguard),
                                "Custom" to stringResource(R.string.dns_custom)
                            )
                            var showCustomDnsDialog by remember { mutableStateOf(false) }
                            
                            ExposedDropdownMenuBox(
                                expanded = dnsExpanded,
                                onExpandedChange = { dnsExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = dnsOptions.find { it.first == dnsServer }?.second ?: dnsServer,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.label_dns_server)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dnsExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    leadingIcon = { Icon(Icons.Rounded.Dns, null) }
                                )
                                ExposedDropdownMenu(
                                    expanded = dnsExpanded,
                                    onDismissRequest = { dnsExpanded = false }
                                ) {
                                    dnsOptions.forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                if (value == "Custom") {
                                                    showCustomDnsDialog = true
                                                } else {
                                                    onDnsChange(value)
                                                }
                                                dnsExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (showCustomDnsDialog) {
                                var customDns by remember { mutableStateOf(if (dnsOptions.any { it.first == dnsServer }) "" else dnsServer) }
                                AlertDialog(
                                    onDismissRequest = { showCustomDnsDialog = false },
                                    title = { Text(stringResource(R.string.dns_custom)) },
                                    text = {
                                        OutlinedTextField(
                                            value = customDns,
                                            onValueChange = { customDns = it },
                                            label = { Text(stringResource(R.string.label_dns_server)) },
                                            placeholder = { Text("1.1.1.1") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            if (customDns.isNotBlank()) {
                                                onDnsChange(customDns.trim())
                                                showCustomDnsDialog = false
                                            }
                                        }) { Text(stringResource(R.string.btn_save)) }
                                    },
                                        dismissButton = {
                                            TextButton(
                                                onClick = { showCustomDnsDialog = false },
                                            ) {
                                                Text(stringResource(R.string.btn_cancel))
                                            }
                                        }
                                )
                            }

                            // App Selection
                            var showAppPicker by remember { mutableStateOf(false) }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Rounded.Apps,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.label_allowed_apps),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = if (allowedApps.isEmpty()) 
                                                    stringResource(R.string.supporting_allowed_apps)
                                                else 
                                                    stringResource(R.string.apps_selected_count, allowedApps.size),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(onClick = { showAppPicker = true }) {
                                            Text(stringResource(R.string.btn_select_apps))
                                        }
                                    }
                                }
                            }

                            if (showAppPicker) {
                                AppPickerDialog(
                                    selectedApps = allowedApps,
                                    onToggleApp = onToggleAllowedApp
                                ) {
                                    showAppPicker = false
                                }
                            }

                            ListItem(
                                headlineContent = { Text(stringResource(R.string.label_exclude_self)) },
                                supportingContent = { Text(stringResource(R.string.supporting_exclude_self)) },
                                leadingContent = { Icon(Icons.Rounded.Security, null) },
                                trailingContent = { Switch(checked = excludeSelf, onCheckedChange = onExcludeSelfChange) },
                                modifier = Modifier.clickable { onExcludeSelfChange(!excludeSelf) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.label_global_mode)) },
                                supportingContent = { Text(stringResource(R.string.supporting_global_mode)) },
                                leadingContent = { Icon(Icons.Rounded.Public, null) },
                                trailingContent = { Switch(checked = globalMode, onCheckedChange = onGlobalModeChange) },
                                modifier = Modifier.clickable { onGlobalModeChange(!globalMode) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedTextField(
                                    value = proxyHost,
                                    onValueChange = onProxyHostChange,
                                    label = { Text(stringResource(R.string.label_proxy_host)) },
                                    modifier = Modifier.weight(1f),
                                    leadingIcon = { Icon(Icons.Rounded.Hub, null) },
                                    shape = MaterialTheme.shapes.medium
                                )
                                OutlinedTextField(
                                    value = proxyPort,
                                    onValueChange = onProxyPortChange,
                                    label = { Text(stringResource(R.string.label_proxy_port)) },
                                    modifier = Modifier.weight(0.6f),
                                    leadingIcon = { Icon(Icons.Rounded.Tag, null) },
                                    shape = MaterialTheme.shapes.medium
                                )
                            }
                        }
                    }

                    SettingsGroup(
                        title = stringResource(R.string.section_backup_restore),
                        icon = Icons.Rounded.Storage
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onExportConfig,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Rounded.FileUpload, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_export_config))
                            }

                            OutlinedButton(
                                onClick = onImportConfig,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Rounded.FileDownload, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_import_config))
                            }
                        }
                    }

                    SettingsGroup(
                        title = stringResource(R.string.section_troubleshooting),
                        icon = Icons.Rounded.Report
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onRunDiagnostics,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_run_diagnostics))
                            }

                            FilledTonalButton(
                                onClick = onCheckUpdates,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                enabled = !isCheckingUpdates
                            ) {
                                if (isCheckingUpdates) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Update, null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_check_updates))
                            }
                        }
                    }

                    SettingsGroup(
                        title = stringResource(R.string.section_appearance),
                        icon = Icons.Rounded.Palette
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Language Selector
                            var langExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = langExpanded,
                                onExpandedChange = { langExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = when(selectedLanguage) {
                                        "en" -> stringResource(R.string.lang_en)
                                        "ru" -> stringResource(R.string.lang_ru)
                                        "uk" -> stringResource(R.string.lang_uk)
                                        "kk" -> stringResource(R.string.lang_kk)
                                        else -> stringResource(R.string.lang_system)
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.label_language)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    leadingIcon = { Icon(Icons.Rounded.Language, null) }
                                )
                                ExposedDropdownMenu(
                                    expanded = langExpanded,
                                    onDismissRequest = { langExpanded = false }
                                ) {
                                    listOf("System", "en", "ru", "uk", "kk").forEach { lang ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(when(lang) {
                                                    "en" -> stringResource(R.string.lang_en)
                                                    "ru" -> stringResource(R.string.lang_ru)
                                                    "uk" -> stringResource(R.string.lang_uk)
                                                    "kk" -> stringResource(R.string.lang_kk)
                                                    else -> stringResource(R.string.lang_system)
                                                })
                                            },
                                            onClick = {
                                                onLanguageChange(lang)
                                                langExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Theme Mode Selector
                            var themeExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = themeExpanded,
                                onExpandedChange = { themeExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = when(themeMode) {
                                        "Light" -> stringResource(R.string.theme_light)
                                        "Dark" -> stringResource(R.string.theme_dark)
                                        "Custom" -> stringResource(R.string.theme_custom)
                                        else -> stringResource(R.string.theme_system)
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.label_theme)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    leadingIcon = { Icon(Icons.Rounded.BrightnessMedium, null) }
                                )
                                ExposedDropdownMenu(
                                    expanded = themeExpanded,
                                    onDismissRequest = { themeExpanded = false }
                                ) {
                                    listOf("System", "Light", "Dark", "Custom").forEach { mode ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(when(mode) {
                                                    "Light" -> stringResource(R.string.theme_light)
                                                    "Dark" -> stringResource(R.string.theme_dark)
                                                    "Custom" -> stringResource(R.string.theme_custom)
                                                    else -> stringResource(R.string.theme_system)
                                                })
                                            },
                                            onClick = {
                                                onThemeModeChange(mode)
                                                themeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = themeMode == "Custom",
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Theme Base (only for Custom)
                                    var baseExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = baseExpanded,
                                        onExpandedChange = { baseExpanded = it },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = when(customThemeBase) {
                                                "Light" -> stringResource(R.string.theme_light)
                                                "Dark" -> stringResource(R.string.theme_dark)
                                                else -> stringResource(R.string.theme_system)
                                            },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(stringResource(R.string.label_theme_base)) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = baseExpanded) },
                                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium,
                                            leadingIcon = { Icon(Icons.Rounded.Contrast, null) }
                                        )
                                        ExposedDropdownMenu(
                                            expanded = baseExpanded,
                                            onDismissRequest = { baseExpanded = false }
                                        ) {
                                            listOf("System", "Light", "Dark").forEach { base ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(when(base) {
                                                            "Light" -> stringResource(R.string.theme_light)
                                                            "Dark" -> stringResource(R.string.theme_dark)
                                                            else -> stringResource(R.string.theme_system)
                                                        })
                                                    },
                                                    onClick = {
                                                        onCustomThemeBaseChange(base)
                                                        baseExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Color Picker
                                    Text(
                                        stringResource(R.string.label_custom_color),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    
                                    val colors = listOf(
                                        0xFF6750A4, 0xFFD0BCFF, 0xFF388E3C, 0xFF1976D2,
                                        0xFFE91E63, 0xFFFF9800, 0xFF795548, 0xFF607D8B,
                                        0xFF009688, 0xFFF44336, 0xFF9C27B0, 0xFF3F51B5
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(48.dp),
                                            shape = CircleShape,
                                            color = Color(customPrimaryColor),
                                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                                        ) {}
                                        
                                        var hexValue by remember(customPrimaryColor) { 
                                            mutableStateOf(String.format("#%06X", (0xFFFFFF and customPrimaryColor))) 
                                        }
                                        
                                        OutlinedTextField(
                                            value = hexValue,
                                            onValueChange = { 
                                                hexValue = it
                                                if (it.length == 7 && it.startsWith("#")) {
                                                    try {
                                                        val color = it.toColorInt()
                                                        onCustomPrimaryColorChange(color)
                                                    } catch (_: Exception) {}
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = MaterialTheme.shapes.medium,
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                                        )
                                    }

                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(40.dp),
                                        modifier = Modifier.height(100.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(colors) { colorLong ->
                                            val colorInt = colorLong.toInt()
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(colorInt))
                                                    .clickable { onCustomPrimaryColorChange(colorInt) }
                                                    .then(
                                                        if (customPrimaryColor == colorInt) {
                                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                        } else Modifier
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (customPrimaryColor == colorInt) {
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        null,
                                                        tint = if (Color(colorInt).luminance() > 0.5f) Color.Black else Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    var showChangelog by remember { mutableStateOf(false) }
                    var showFullHistory by remember { mutableStateOf(false) }

                    if (showChangelog) {
                        AlertDialog(
                            onDismissRequest = { showChangelog = false },
                            modifier = Modifier.fillMaxWidth(0.95f),
                            icon = { 
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome, 
                                        null, 
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            title = { 
                                Text(
                                    stringResource(if (showFullHistory) R.string.changelog_title_full else R.string.changelog_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) 
                            },
                            text = {
                                val content = stringResource(
                                    if (showFullHistory) R.string.changelog_history 
                                    else R.string.changelog_content
                                ).trimIndent()
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    val sections = if (showFullHistory) content.split("<hr/>") else listOf(content)
                                    sections.filter { it.trim().isNotEmpty() }.forEachIndexed { index, section ->
                                        val lines = section.trim().lines().filter { it.isNotBlank() || it.isEmpty() }
                                        
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                                            ),
                                            shape = RoundedCornerShape(24.dp),
                                            border = if (index == 0 && !showFullHistory) 
                                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                                else null
                                        ) {
                                            Column(modifier = Modifier.padding(20.dp)) {
                                                if (index == 0 && !showFullHistory) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.padding(bottom = 12.dp)
                                                    ) {
                                                        Text(
                                                            stringResource(R.string.label_latest),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                lines.forEachIndexed { lineIndex, line ->
                                                    val isBullet = line.trim().startsWith("•")
                                                    val styledText = parseSimpleHtml(line.trim())
                                                    
                                                    Text(
                                                        text = styledText,
                                                        style = when {
                                                            lineIndex == 0 -> MaterialTheme.typography.titleMedium.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 18.sp
                                                            )
                                                            line.trim().startsWith("<i>") -> MaterialTheme.typography.bodySmall.copy(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontStyle = FontStyle.Italic
                                                            )
                                                            else -> MaterialTheme.typography.bodyMedium
                                                        },
                                                        modifier = Modifier.padding(
                                                            bottom = if (lineIndex == 0) 4.dp else 2.dp,
                                                            start = if (isBullet) 8.dp else 0.dp
                                                        ),
                                                        lineHeight = if (isBullet) 22.sp else 24.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showFullHistory = !showFullHistory },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (showFullHistory) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.History,
                                            null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(if (showFullHistory) R.string.btn_back else R.string.btn_history),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { 
                                        showChangelog = false
                                        showFullHistory = false 
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Text(stringResource(R.string.btn_dismiss), fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }

                    Text(
                        text = stringResource(R.string.credits_text, BuildConfig.VERSION_NAME),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChangelog = true }
                            .padding(vertical = 16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(100.dp))
                }
            } else {
                var filterPreset by remember { mutableStateOf<String?>(null) }
                var showFilterMenu by remember { mutableStateOf(false) }

                val onShowDetailsWithFilter: (String) -> Unit = { strategy ->
                    onShowDetails(strategy + (if (filterPreset != null) "|$filterPreset" else ""))
                }

                val sortedStrategies = remember(filterPreset, committedStats, bypassedSitesCount, testResults.size, currentlyTesting.size) {
                    Config.STRATEGIES.sortedWith { a, b ->
                        val nameA = a.first
                        val nameB = b.first

                        // 1. Priority: Currently testing
                        val testingA = if (currentlyTesting.contains(nameA)) 1 else 0
                        val testingB = if (currentlyTesting.contains(nameB)) 1 else 0
                        if (testingA != testingB) return@sortedWith testingB - testingA

                        // 2. Success Rate
                        val rateA = if (filterPreset == null) {
                            val stat = committedStats[nameA] ?: Triple(0, 0, bypassedSitesCount)
                            if (stat.second > 0) stat.first.toFloat() / stat.second else -1f
                        } else {
                            val results = testResults[nameA] ?: emptyMap()
                            val presetSites = Config.PRESETS.find { it.first == filterPreset }?.second?.filter { !it.contains("/") } ?: emptyList()
                            val successCount = presetSites.count { results[it]?.success == true }
                            if (presetSites.isNotEmpty()) successCount.toFloat() / presetSites.size else -1f
                        }

                        val rateB = if (filterPreset == null) {
                            val stat = committedStats[nameB] ?: Triple(0, 0, bypassedSitesCount)
                            if (stat.second > 0) stat.first.toFloat() / stat.second else -1f
                        } else {
                            val results = testResults[nameB] ?: emptyMap()
                            val presetSites = Config.PRESETS.find { it.first == filterPreset }?.second?.filter { !it.contains("/") } ?: emptyList()
                            val successCount = presetSites.count { results[it]?.success == true }
                            if (presetSites.isNotEmpty()) successCount.toFloat() / presetSites.size else -1f
                        }
                        if (rateA != rateB) return@sortedWith rateB.compareTo(rateA)

                        // 3. Success Count
                        val countA = if (filterPreset == null) {
                            committedStats[nameA]?.first ?: 0
                        } else {
                            val results = testResults[nameA] ?: emptyMap()
                            val presetSites = Config.PRESETS.find { it.first == filterPreset }?.second?.filter { !it.contains("/") } ?: emptyList()
                            presetSites.count { results[it]?.success == true }
                        }
                        val countB = if (filterPreset == null) {
                            committedStats[nameB]?.first ?: 0
                        } else {
                            val results = testResults[nameB] ?: emptyMap()
                            val presetSites = Config.PRESETS.find { it.first == filterPreset }?.second?.filter { !it.contains("/") } ?: emptyList()
                            presetSites.count { results[it]?.success == true }
                        }
                        countB.compareTo(countA)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.title_verification),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (filterPreset == null) 
                                        stringResource(R.string.testing_sites_count, bypassedSitesCount)
                                    else
                                        "${stringResource(R.string.btn_filter)}: $filterPreset",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (filterPreset == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                                )
                                if (currentlyTesting.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.testing_strategies_count, strategiesTestedCount, Config.STRATEGIES.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    if (strategiesTestedCount == 0) {
                                        Text(
                                            stringResource(R.string.testing_starting_hint),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Normal,
                                                fontStyle = FontStyle.Italic
                                            ),
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                            Row {
                                val isAnyTesting = currentlyTesting.isNotEmpty()
                                Box {
                                    IconButton(onClick = { showFilterMenu = true }) {
                                        Icon(
                                            Icons.Rounded.FilterList,
                                            stringResource(R.string.btn_filter),
                                            tint = if (filterPreset != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showFilterMenu,
                                        onDismissRequest = { showFilterMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.filter_all_sites)) },
                                            onClick = {
                                                filterPreset = null
                                                showFilterMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Rounded.ClearAll, null) }
                                        )
                                        HorizontalDivider()
                                        Config.PRESETS.forEach { (name, _) ->
                                            DropdownMenuItem(
                                                text = { Text(getLocalizedPresetName(name)) },
                                                onClick = {
                                                    filterPreset = name
                                                    showFilterMenu = false
                                                },
                                                leadingIcon = { 
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(getPresetIcon(name), null, modifier = Modifier.size(20.dp))
                                                        if (filterPreset == name) {
                                                            Spacer(Modifier.width(8.dp))
                                                            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = onTestAll) {
                                    Icon(
                                        if (isAnyTesting) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                        null,
                                        tint = if (isAnyTesting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = onResetTests) {
                                    Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }

                    if (sortedStrategies.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Info, 
                                    null, 
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                                Text(
                                    if (filterPreset == null) 
                                        stringResource(R.string.no_results_yet)
                                    else 
                                        stringResource(R.string.msg_no_results_preset, filterPreset ?: ""),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    items(sortedStrategies, key = { it.first }) { (name, desc) ->
                        val isApplied = name == selectedStrategy
                        val (success, tested, total) = if (filterPreset == null) {
                            stats[name] ?: Triple(0, 0, bypassedSitesCount)
                        } else {
                            val results = testResults[name] ?: emptyMap()
                            val presetSites = Config.PRESETS.find { it.first == filterPreset }?.second?.filter { !it.contains("/") } ?: emptyList()
                            val pSuccess = presetSites.count { results[it]?.success == true }
                            val pTested = presetSites.count { results.containsKey(it) }
                            Triple(pSuccess, pTested, presetSites.size)
                        }
                        val isTesting = currentlyTesting.contains(name)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            shape = MaterialTheme.shapes.medium,
                            onClick = { onShowDetailsWithFilter(name) }
                        ) {
                            ListItem(
                                headlineContent = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(getLocalizedStrategyName(name), fontWeight = FontWeight.Bold)
                                            IconButton(
                                                onClick = { onShowStrategyArgsInfo(name) },
                                                modifier = Modifier.size(16.dp)
                                            ) {
                                                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                                            }
                                            val isPinned = pinnedStrategies.contains(name)
                                            IconButton(
                                                onClick = { onPinStrategy(name) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    if (isPinned) Icons.Rounded.PushPin else Icons.Rounded.PushPin,
                                                    null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                        if (isApplied) {
                                            Text(
                                                stringResource(R.string.label_applied),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        Text(
                                            if (name == "Custom") customArgs.ifBlank { stringResource(R.string.no_args_set) } else getLocalizedStrategyDesc(name, desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val progressValue = if (total > 0) success.toFloat() / total else 0f
                                            val animatedProgress by animateFloatAsState(
                                                targetValue = progressValue,
                                                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                                                label = "progress"
                                            )
                                            
                                            if (isTesting) {
                                                val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                                                val alpha by infiniteTransition.animateFloat(
                                                    initialValue = 0.3f,
                                                    targetValue = 1f,
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(1000, easing = FastOutSlowInEasing),
                                                        repeatMode = RepeatMode.Reverse
                                                    ),
                                                    label = "alpha"
                                                )
                                                LinearProgressIndicator(
                                                    progress = { animatedProgress },
                                                    modifier = Modifier.weight(1f).graphicsLayer { this.alpha = alpha }
                                                )
                                            } else {
                                                LinearProgressIndicator(
                                                    progress = { animatedProgress },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Text(
                                                stringResource(R.string.test_progress_short, success, total, tested),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = { onTestStrategy(name) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (isTesting) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            if (isTesting) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                            null,
                                            tint = if (isTesting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                    item {
                        Box(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}

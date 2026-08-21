package com.example.nozapret

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.example.nozapret.core.Config
import com.example.nozapret.services.DpiVpnService
import com.example.nozapret.ui.getLocalizedStrategyName
import com.example.nozapret.ui.screens.HomeTab
import com.example.nozapret.ui.screens.LogViewer
import com.example.nozapret.ui.screens.SettingsTab
import com.example.nozapret.ui.screens.TestDetailsView
import com.example.nozapret.ui.theme.NoZapretTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : AppCompatActivity() {
    private fun setupShortcuts() {
        val shortcutManager = getSystemService<ShortcutManager>() ?: return
        
        // Use consistent IDs that don't conflict with static manifest shortcuts
        // But since we want to override or ensure they exist, we use unique ones here
        // The previous crash was caused by using "stop_vpn" which was also in Manifest.
        
        val startShortcut = ShortcutInfo.Builder(this, "dynamic_start_vpn_id")
            .setShortLabel(getString(R.string.btn_start))
            .setLongLabel(getString(R.string.status_vpn_started))
            .setIcon(Icon.createWithResource(this, R.mipmap.app_icon))
            .setIntent(Intent(this, MainActivity::class.java).apply {
                action = "com.example.nozapret.START_VPN"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            .build()

        val stopShortcut = ShortcutInfo.Builder(this, "dynamic_stop_vpn_id")
            .setShortLabel(getString(R.string.btn_stop))
            .setLongLabel(getString(R.string.status_vpn_stopped))
            .setIcon(Icon.createWithResource(this, R.mipmap.app_icon))
            .setIntent(Intent(this, MainActivity::class.java).apply {
                action = "com.example.nozapret.STOP_VPN"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            .build()

        try {
            shortcutManager.dynamicShortcuts = listOf(startShortcut, stopShortcut)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to set dynamic shortcuts", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupShortcuts()
        handleIntent(intent)
        setContent {
            val viewModel: MainViewModel = viewModel()
            NoZapretTheme(
                themeMode = viewModel.themeMode,
                customPrimaryColor = Color(viewModel.customPrimaryColor),
                customThemeBase = viewModel.customThemeBase,
            ) {
                MainScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action
        if (action == "com.example.nozapret.TOGGLE_VPN" || 
            action == "com.example.nozapret.START_VPN" || 
            action == "com.example.nozapret.STOP_VPN") {
            Log.d("MainActivity", "Captured shortcut action: $action")
        }
    }
}

fun startVpnService(context: Context) {
    val intent = Intent(context, DpiVpnService::class.java)
    context.startService(intent)
}

fun stopVpnService(context: Context) {
    val intent = Intent(context, DpiVpnService::class.java).apply {
        action = DpiVpnService.ACTION_STOP
    }
    context.startService(intent)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val requestPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var selectedTestStrategyForDetails by remember { mutableStateOf<String?>(null) }
    var strategyForArgsInfo by remember { mutableStateOf<String?>(null) }
    var showNoSitesWarning by remember { mutableStateOf(value = false) }
    val settingsScrollState = rememberScrollState()
    var highlightPresetsTrigger by remember { mutableIntStateOf(0) }
    var toggleJob by remember { mutableStateOf<Job?>(null) }

    val configImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importConfiguration(context, it) }
    }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            toggleJob = scope.launch {
                viewModel.stopAllTests()
                delay(1500.milliseconds)
                startVpnService(context)
            }
        }
    }

    fun onToggleVpn() {
        if (!viewModel.globalMode && viewModel.bypassedSites.isEmpty()) {
            showNoSitesWarning = true
            return
        }
        
        toggleJob?.cancel()
        if (viewModel.isEnabled) {
            stopVpnService(context)
            viewModel.updateVpnState(running = false)
        } else {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                vpnPrepareLauncher.launch(intent)
            } else {
                toggleJob = scope.launch {
                    viewModel.stopAllTests()
                    delay(1500.milliseconds) // Give it even more time to release native resources
                    startVpnService(context)
                }
            }
        }
    }

    val isKeepScreenOn = viewModel.isDiagnosing || viewModel.isQuickTesting || viewModel.currentlyTesting.isNotEmpty()
    DisposableEffect(context, isKeepScreenOn) {
        val window = (context as? Activity)?.window
        if (isKeepScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        viewModel.isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    DpiVpnService.ACTION_VPN_STATE_CHANGED -> {
                        viewModel.updateVpnState(
                            intent.getBooleanExtra(DpiVpnService.EXTRA_IS_RUNNING, false),
                            intent.getLongExtra(DpiVpnService.EXTRA_START_TIME, 0L)
                        )
                    }
                    MainViewModel.ACTION_STOP_TESTING -> {
                        val strategyName = intent.getStringExtra(MainViewModel.EXTRA_STRATEGY_NAME)
                        strategyName?.let { viewModel.stopTest(it) }
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(DpiVpnService.ACTION_VPN_STATE_CHANGED)
            addAction(MainViewModel.ACTION_STOP_TESTING)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val observer = LifecycleEventObserver { _, event: Lifecycle.Event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val vpnStatePrefs = context.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)
                viewModel.updateVpnState(
                    vpnStatePrefs.getBoolean("is_running", false),
                    vpnStatePrefs.getLong("start_time", 0L)
                )
                viewModel.isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
                context.sendBroadcast(
                    Intent(DpiVpnService.ACTION_QUERY_STATUS).apply {
                        setPackage(context.packageName)
                    },
                )

                // Auto-start VPN if requested by TileService
                val activity = context as AppCompatActivity
                if (activity.intent.getBooleanExtra("start_vpn_on_resume", false)) {
                    activity.intent.removeExtra("start_vpn_on_resume")
                    onToggleVpn()
                }
            }
        }

        val lifecycle = (context as AppCompatActivity).lifecycle
        lifecycle.addObserver(observer)

        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    // Handle shortcut action
    val currentActivity = context as? Activity
    val action = currentActivity?.intent?.action
    LaunchedEffect(action) {
        if (currentActivity != null) {
            if (action == "com.example.nozapret.TOGGLE_VPN") {
                currentActivity.intent?.action = null
                onToggleVpn()
            } else if (action == "com.example.nozapret.START_VPN") {
                currentActivity.intent?.action = null
                if (!viewModel.isEnabled) onToggleVpn()
            } else if (action == "com.example.nozapret.STOP_VPN") {
                currentActivity.intent?.action = null
                if (viewModel.isEnabled) onToggleVpn()
            }
        }
    }

    val pagerState = rememberPagerState { 3 }
    val hazeState = remember { HazeState() }
    val snackbarHostState = viewModel.snackbarHostState
    val sheetState = rememberModalBottomSheetState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 92.dp)
            )
        }
    ) { innerPadding ->
        if (showNoSitesWarning) {
            AlertDialog(
                onDismissRequest = { showNoSitesWarning = false },
                icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.warning_no_sites_title)) },
                text = { Text(stringResource(R.string.warning_no_sites_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNoSitesWarning = false
                            scope.launch {
                                viewModel.settingsTab = 0
                                pagerState.animateScrollToPage(1)
                                delay(300.milliseconds)
                                highlightPresetsTrigger++
                            }
                        },
                    ) {
                        Text(stringResource(R.string.btn_go_to_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNoSitesWarning = false }) {
                        Text(stringResource(R.string.btn_dismiss))
                    }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .hazeSource(state = hazeState),
                beyondViewportPageCount = 1,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 0.dp
                )
            ) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (page) {
                        0 -> HomeTab(
                            isEnabled = viewModel.isEnabled,
                            vpnStartTime = viewModel.vpnStartTime,
                            selectedStrategy = viewModel.selectedStrategy,
                            pinnedStrategies = viewModel.pinnedStrategies,
                            selectedPresets = viewModel.selectedPresets,
                            onToggleVpn = { onToggleVpn() },
                            proxyHost = viewModel.proxyHost,
                            proxyPort = viewModel.proxyPort,
                            globalMode = viewModel.globalMode,
                            onStrategySelected = viewModel::applyStrategy
                        )

                        1 -> {
                            SettingsTab(
                                settingsTab = viewModel.settingsTab,
                                onSettingsTabChange = { viewModel.settingsTab = it },
                                scrollState = settingsScrollState,
                                highlightPresetsTrigger = highlightPresetsTrigger,
                                selectedStrategy = viewModel.selectedStrategy,
                                onStrategyChange = viewModel::updateSelectedStrategy,
                                selectedPresets = viewModel.selectedPresets,
                                onPresetToggle = viewModel::togglePreset,
                                dnsServer = viewModel.dnsServer,
                                onDnsChange = viewModel::updateDnsServer,
                                customArgs = viewModel.customArgs,
                                onCustomArgsChange = viewModel::updateCustomArgs,
                                excludeSelf = viewModel.excludeSelf,
                                onExcludeSelfChange = viewModel::updateExcludeSelf,
                                globalMode = viewModel.globalMode,
                                onGlobalModeChange = viewModel::updateGlobalMode,
                                customHostList = viewModel.customHostList,
                                onCustomHostListChange = viewModel::updateCustomHostList,
                                isIgnoringBattery = viewModel.isIgnoringBattery,
                                stats = viewModel.stats,
                                committedStats = viewModel.committedStats,
                                currentlyTesting = viewModel.currentlyTesting,
                                onTestStrategy = { viewModel.testStrategy(it) },
                                onShowDetails = { selectedTestStrategyForDetails = it },
                                onShowStrategyArgsInfo = { strategyForArgsInfo = it },
                                onResetTests = { viewModel.resetTests() },
                                onTestAll = { viewModel.testAllStrategies() },
                                pinnedStrategies = viewModel.pinnedStrategies,
                                onPinStrategy = viewModel::togglePinStrategy,
                                bypassedSitesCount = viewModel.sitesToTestCount,
                                strategiesTestedCount = viewModel.strategiesTestedCount,
                                proxyHost = viewModel.proxyHost,
                                onProxyHostChange = viewModel::updateProxyHost,
                                proxyPort = viewModel.proxyPort,
                                onProxyPortChange = viewModel::updateProxyPort,
                                allowedApps = viewModel.allowedApps,
                                onToggleAllowedApp = viewModel::toggleAllowedApp,
                                testResults = viewModel.testResults,
                                themeMode = viewModel.themeMode,
                                onThemeModeChange = viewModel::updateThemeMode,
                                customPrimaryColor = viewModel.customPrimaryColor,
                                onCustomPrimaryColorChange = viewModel::updateCustomPrimaryColor,
                                customThemeBase = viewModel.customThemeBase,
                                onCustomThemeBaseChange = viewModel::updateCustomThemeBase,
                                quickTestUrl = viewModel.quickTestUrl,
                                onQuickTestUrlChange = { 
                                    viewModel.quickTestUrl = it
                                    if (it.isBlank()) {
                                        viewModel.quickTestResult = null
                                    }
                                },
                                quickTestResult = viewModel.quickTestResult,
                                isQuickTesting = viewModel.isQuickTesting,
                                onRunQuickTest = { viewModel.runQuickTest() },
                                quickTestStrategy = viewModel.quickTestStrategy,
                                onQuickTestStrategyChange = {
                                    viewModel.quickTestStrategy = it
                                    viewModel.quickTestResult = null
                                },
                                onRunDiagnostics = viewModel::runDiagnostics,
                                selectedLanguage = viewModel.selectedLanguage,
                                onLanguageChange = viewModel::updateSelectedLanguage,
                                autoConnect = viewModel.autoConnect,
                                onAutoConnectChange = viewModel::updateAutoConnect,
                                enableIpv6 = viewModel.enableIpv6,
                                onEnableIpv6Change = viewModel::updateEnableIpv6,
                                runMode = viewModel.runMode,
                                onRunModeChange = viewModel::updateRunMode,
                                isCheckingUpdates = viewModel.isCheckingUpdates,
                                onCheckUpdates = viewModel::checkForUpdates,
                                onExportConfig = { viewModel.exportConfiguration(context) },
                                onImportConfig = { configImportLauncher.launch("application/json") }
                            )
                        }

                        2 -> LogViewer(
                            logLines = viewModel.logLines,
                            onClearLogs = { viewModel.clearLogs() }
                        )
                    }
                }
            }

            // Floating Navigation Bar with Haze Blur
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .height(80.dp)
                        .shadow(elevation = 20.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                tints = listOf(
                                    HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)),
                                    HazeTint(Color.White.copy(alpha = 0.1f))
                                ),
                                blurRadius = 30.dp,
                                noiseFactor = 0.05f
                            )
                        )
                        .background(Color.Transparent)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), CircleShape)
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        modifier = Modifier.fillMaxSize(),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        tonalElevation = 0.dp
                    ) {
                        listOf(
                            Triple(0, Icons.Rounded.Home, R.string.nav_home),
                            Triple(1, Icons.Rounded.Settings, R.string.nav_settings),
                            Triple(2, Icons.AutoMirrored.Rounded.List, R.string.nav_logs)
                        ).forEach { (index, icon, labelRes) ->
                            val isSelected = pagerState.currentPage == index
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            
                            val bounceScale by animateFloatAsState(
                                targetValue = if (isPressed) 0.88f else 1f,
                                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                                label = "Bounce"
                            )

                            val animColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                animationSpec = tween(600, easing = FastOutSlowInEasing),
                                label = "NavColor"
                            )
                            val contentColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(600, easing = FastOutSlowInEasing),
                                label = "ContentColor"
                            )

                            NavigationBarItem(
                                selected = isSelected,
                                interactionSource = interactionSource,
                                onClick = { 
                                    if (!isSelected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scope.launch { 
                                            pagerState.animateScrollToPage(index) 
                                        }
                                    }
                                },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = bounceScale
                                                scaleY = bounceScale
                                            }
                                            .clip(CircleShape)
                                            .background(animColor)
                                            .border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = 0.2f),
                                                shape = CircleShape
                                            )
                                            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                icon, 
                                                null, 
                                                tint = contentColor, 
                                                modifier = Modifier.size(24.dp)
                                            )
                                            AnimatedVisibility(
                                                visible = isSelected,
                                                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                                                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
                                            ) {
                                                Text(
                                                    stringResource(labelRes),
                                                    modifier = Modifier.padding(start = 8.dp),
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.1.sp
                                                    ),
                                                    color = contentColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Visible,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                },
                                label = null,
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }

        if (strategyForArgsInfo != null) {
            AlertDialog(
                onDismissRequest = { strategyForArgsInfo = null },
                icon = { Icon(Icons.Rounded.Info, null) },
                title = { Text(getLocalizedStrategyName(strategyForArgsInfo!!)) },
                text = {
                    val args = Config.getStrategyArgs(strategyForArgsInfo!!, viewModel.customArgs).joinToString(" ")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.label_strategy_arguments), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = args.ifEmpty { stringResource(R.string.no_args_set) },
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { strategyForArgsInfo = null }) {
                        Text(stringResource(R.string.btn_dismiss))
                    }
                }
            )
        }

        if (selectedTestStrategyForDetails != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedTestStrategyForDetails = null },
                sheetState = sheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                TestDetailsView(
                    strategyName = selectedTestStrategyForDetails!!.split("|").first(),
                    results = run {
                        val parts = selectedTestStrategyForDetails!!.split("|")
                        val strategy = parts.first()
                        val allResults = viewModel.testResults[strategy] ?: emptyMap()
                        if (parts.size > 1) {
                            val presetName = parts[1]
                            val presetSites = Config.PRESETS.find { it.first == presetName }?.second?.filter { !it.contains("/") } ?: emptyList()
                            allResults.filterKeys { site -> presetSites.contains(site) }
                        } else {
                            allResults
                        }
                    },
                    onBack = { 
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                selectedTestStrategyForDetails = null
                            }
                        }
                    },
                    onApply = { strategy ->
                        viewModel.applyStrategy(strategy)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            selectedTestStrategyForDetails = null
                        }
                    },
                    onUseAsCustom = { strategy ->
                        viewModel.useAsCustom(strategy)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            selectedTestStrategyForDetails = null
                        }
                    },
                )
            }
        }

        if (viewModel.showDiagnosticsDialog) {
            AlertDialog(
                onDismissRequest = { if (!viewModel.isDiagnosing) viewModel.showDiagnosticsDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.diag_title))
                    }
                },
                text = {
                    val listState = rememberLazyListState()
                    LaunchedEffect(viewModel.diagnosticsLog.size) {
                        if (viewModel.diagnosticsLog.isNotEmpty()) {
                            listState.animateScrollToItem(viewModel.diagnosticsLog.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(viewModel.diagnosticsLog) { item ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
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

                                    Text(
                                        text = "$prefix ${item.message}",
                                        color = color,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if ((item.action != MainViewModel.DiagAction.NONE) && (item.solutionTextResId != null)) {
                                    Button(
                                        onClick = {
                                            when (item.action) {
                                                MainViewModel.DiagAction.BATTERY_OPTIMIZATION -> {
                                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                    context.startActivity(intent)
                                                }
                                                MainViewModel.DiagAction.DISABLE_IPV6 -> {
                                                    // Typically involves opening APN settings or similar,
                                                    // but we can at least guide them to the right settings page.
                                                    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                                    context.startActivity(intent)
                                                }
                                                MainViewModel.DiagAction.RESTART_VPN -> {
                                                    onToggleVpn() // Stop
                                                    scope.launch {
                                                        delay(300.milliseconds)
                                                        onToggleVpn() // Start
                                                    }
                                                }
                                                MainViewModel.DiagAction.STOP_VPN -> {
                                                    onToggleVpn()
                                                }
                                                MainViewModel.DiagAction.NONE -> {}
                                            }
                                        },
                                        modifier = Modifier.padding(start = 28.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = MaterialTheme.shapes.small,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Rounded.Build, null, modifier = Modifier.size(14.dp))
                                            Text(
                                                stringResource(item.solutionTextResId),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (viewModel.isDiagnosing) {
                            item {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.showDiagnosticsDialog = false },
                        enabled = !viewModel.isDiagnosing
                    ) {
                        Text(stringResource(R.string.btn_dismiss))
                    }
                }
            )
        }

        if (viewModel.showUpdateDialog && (viewModel.updateInfo != null)) {
            UpdateDialog(
                info = viewModel.updateInfo!!,
                isDownloading = viewModel.isDownloadingUpdate,
                progress = viewModel.updateDownloadProgress,
                onDownload = { viewModel.downloadAndInstallUpdate() },
                onDismiss = { viewModel.showUpdateDialog = false }
            )
        }
    }
}

@Composable
fun UpdateDialog(
    info: MainViewModel.UpdateInfo,
    isDownloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.btn_update_now), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(stringResource(R.string.btn_dismiss), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.update_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "v${info.version}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (info.changelog.isNotEmpty() && !isDownloading) {
                    Text(
                        stringResource(R.string.changelog_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                info.changelog,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                if (isDownloading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val animatedProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                            label = "DownloadProgress"
                        )
                        
                        Text(
                            stringResource(R.string.update_downloading),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Box(contentAlignment = Alignment.Center) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                        
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    )
}



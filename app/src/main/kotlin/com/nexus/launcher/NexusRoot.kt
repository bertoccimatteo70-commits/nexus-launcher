package com.nexus.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.common.AutomationRules
import com.nexus.core.data.BackupScheduler
import com.nexus.core.data.NotificationBadges
import com.nexus.core.designsystem.NexusBackdrop
import com.nexus.core.designsystem.NexusLoading
import com.nexus.core.designsystem.NexusLogo
import com.nexus.core.designsystem.NexusTheme
import com.nexus.core.designsystem.NexusThemeConfig
import com.nexus.core.model.LauncherApp
import com.nexus.core.model.NexusNavigator
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.ThemeMode
import com.nexus.core.model.ToolDestination
import com.nexus.feature.automations.AutomationScheduler
import com.nexus.feature.automations.AutomationsScreen
import com.nexus.feature.backup.BackupScreen
import com.nexus.feature.drawer.AppDrawerScreen
import com.nexus.feature.home.DashboardScreen
import com.nexus.feature.home.HomeScreen
import com.nexus.feature.search.GlobalSearchScreen
import com.nexus.feature.settings.SettingsFlow
import com.nexus.feature.tools.ToolsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun NexusRoot(
    container: NexusContainer,
    homeRequests: Flow<Unit>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val loaded by container.settings.isLoaded.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isDefaultLauncher by remember { mutableStateOf(isNexusDefaultLauncher(context)) }
    val view = LocalView.current

    fun refreshRole() {
        isDefaultLauncher = isNexusDefaultLauncher(context)
    }

    fun openLauncherSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .recoverCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            .onFailure {
                Toast.makeText(context, "Impostazioni Home non disponibili su questo dispositivo", Toast.LENGTH_LONG).show()
            }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshRole() }

    fun requestRole() {
        val manager = context.getSystemService(RoleManager::class.java)
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            openLauncherSettings()
            return
        }
        runCatching {
            roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }.onFailure { openLauncherSettings() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshRole()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(60_000)
        }
    }

    LaunchedEffect(loaded) {
        if (loaded) {
            container.diagnostics.info("APP", "Configurazione caricata, launcher=${isDefaultLauncher}")
            container.timer.snapshot.value?.let { timer ->
                if (timer.targetAtEpochMillis > System.currentTimeMillis()) {
                    container.timerScheduler.schedule(timer)
                }
            }
        }
    }

    LaunchedEffect(
        loaded,
        settings.automationsEnabled,
        settings.dayStartHour,
        settings.nightStartHour,
    ) {
        if (loaded) AutomationScheduler(context).schedule(settings)
    }

    LaunchedEffect(loaded, settings.autoBackupEnabled) {
        if (loaded) {
            val backupScheduler = BackupScheduler(context)
            if (settings.autoBackupEnabled) backupScheduler.scheduleDaily() else backupScheduler.cancel()
        }
    }

    LaunchedEffect(loaded, settings.onboardingCompleted) {
        if (!loaded || !settings.onboardingCompleted) return@LaunchedEffect
        val last = settings.lastUpdateCheckAt
        if (System.currentTimeMillis() - last < 24 * 60 * 60_000L) return@LaunchedEffect
        container.updates.check(settings.updateChannel).onSuccess { info ->
            container.settings.update { current ->
                current.copy(
                    lastUpdateCheckAt = System.currentTimeMillis(),
                    availableVersion = info?.version.orEmpty(),
                    availableVersionNotes = info?.notes.orEmpty().take(6_000),
                    availableVersionUrl = info?.downloadUrl ?: info?.htmlUrl.orEmpty(),
                )
            }
        }.onFailure {
            container.diagnostics.warn("UPDATE", "Controllo automatico non riuscito: ${it.message}")
        }
    }

    val effective = remember(settings, nowTick) {
        AutomationRules.effectiveAppearance(
            settings,
            LocalDateTime.now(),
        )
    }
    val dark = when (effective.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val config = NexusThemeConfig(
        darkTheme = dark,
        palette = settings.palette,
        useDynamicColor = settings.useDynamicColor,
        highContrast = effective.highContrast,
        fontScale = settings.fontScale,
        largeTouchTargets = settings.largeTouchTargets,
        blurEnabled = settings.blurEnabled,
        reduceMotion = effective.reduceMotion,
        animationSpeed = settings.animationSpeed,
    )
    val wallpaper = rememberWallpaper(settings.wallpaperUri)

    NexusTheme(config) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
        NexusBackdrop(
            config = config,
            style = settings.backgroundStyle,
            wallpaper = wallpaper,
        ) {
            when {
                !loaded -> Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    NexusLogo(Modifier.width(72.dp).height(72.dp))
                    Spacer(Modifier.height(14.dp))
                    NexusLoading("Caricamento Nexus…")
                    Spacer(Modifier.weight(1f))
                }
                !settings.onboardingCompleted -> OnboardingScreen(
                    settings = settings,
                    settingsRepository = container.settings,
                    isDefaultLauncher = isDefaultLauncher,
                    onRequestDefaultLauncher = ::requestRole,
                )
                else -> LauncherShell(
                    container = container,
                    settings = settings,
                    isDefaultLauncher = isDefaultLauncher,
                    homeRequests = homeRequests,
                    onRequestDefaultLauncher = ::requestRole,
                    onOpenLauncherSettings = ::openLauncherSettings,
                )
            }
        }
    }
}

@Composable
private fun LauncherShell(
    container: NexusContainer,
    settings: NexusSettings,
    isDefaultLauncher: Boolean,
    homeRequests: Flow<Unit>,
    onRequestDefaultLauncher: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var surface by rememberSaveable { mutableStateOf("home") }
    var settingsTarget by rememberSaveable { mutableStateOf(SettingsTargets.ROOT) }
    var toolName by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }

    fun closeOverlays() {
        searchOpen = false
        drawerOpen = false
    }

    fun launchApp(app: LauncherApp) {
        closeOverlays()
        scope.launch { container.usage.recordLaunch(app.key) }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        runCatching { context.startActivity(intent) }.onFailure { failure ->
            container.diagnostics.error("LAUNCH", "Impossibile avviare ${app.key}", failure)
            Toast.makeText(context, "Impossibile aprire ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWeb(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(context, "Nessuna app per aprire il link", Toast.LENGTH_SHORT).show()
        }
    }

    val navigator = NexusNavigator(
        openHome = {
            surface = "home"
            settingsTarget = SettingsTargets.ROOT
            closeOverlays()
        },
        openSearch = { query ->
            searchQuery = query
            searchOpen = true
            drawerOpen = false
        },
        openDrawer = {
            drawerOpen = true
            searchOpen = false
        },
        openDashboard = {
            surface = "dashboard"
            closeOverlays()
        },
        openTools = { destination ->
            toolName = destination?.name
            surface = "tools"
            closeOverlays()
        },
        openSettings = { target ->
            settingsTarget = target
            surface = "settings"
            closeOverlays()
        },
        openDefaultLauncherSettings = onOpenLauncherSettings,
        requestDefaultLauncher = onRequestDefaultLauncher,
    )

    LaunchedEffect(homeRequests) {
        homeRequests.collect {
            surface = "home"
            settingsTarget = SettingsTargets.ROOT
            closeOverlays()
        }
    }

    BackHandler(enabled = searchOpen || drawerOpen) {
        if (searchOpen) searchOpen = false else drawerOpen = false
    }
    BackHandler(enabled = surface == "dashboard") { surface = "home" }
    BackHandler(
        enabled = surface == "settings" &&
            (settingsTarget == SettingsTargets.AUTOMATIONS || settingsTarget == SettingsTargets.BACKUP),
    ) {
        surface = "settings"
        settingsTarget = SettingsTargets.ROOT
    }

    Box(Modifier.fillMaxSize()) {
        when (surface) {
            "settings" -> when (settingsTarget) {
                SettingsTargets.AUTOMATIONS -> AutomationsScreen(
                    settings = settings,
                    settingsRepository = container.settings,
                    onOpenThemeStudio = {
                        settingsTarget = SettingsTargets.APPEARANCE
                    },
                    onBack = {
                        surface = "settings"
                        settingsTarget = SettingsTargets.ROOT
                    },
                )
                SettingsTargets.BACKUP -> BackupScreen(
                    settings = settings,
                    settingsRepository = container.settings,
                    backupRepository = container.backup,
                    buildInfo = container.buildInfo,
                    onBack = {
                        surface = "settings"
                        settingsTarget = SettingsTargets.ROOT
                    },
                )
                else -> SettingsFlow(
                    settings = settings,
                    settingsRepository = container.settings,
                    diagnostics = container.diagnostics,
                    catalog = container.catalog,
                    usageRepository = container.usage,
                    updateRepository = container.updates,
                    buildInfo = container.buildInfo,
                    isDefaultLauncher = isDefaultLauncher,
                    initialTarget = settingsTarget,
                    navigator = navigator,
                    onBack = {
                        surface = "home"
                        settingsTarget = SettingsTargets.ROOT
                    },
                )
            }
            "tools" -> ToolsScreen(
                initialTool = toolName?.let { name ->
                    runCatching { ToolDestination.valueOf(name) }.getOrNull()
                },
                settings = settings,
                settingsRepository = container.settings,
                notesRepository = container.notes,
                calendarRepository = container.calendar,
                weatherRepository = container.weather,
                timerRepository = container.timer,
                timerScheduler = container.timerScheduler,
                onBack = { surface = "home" },
            )
            "dashboard" -> DashboardScreen(
                settings = settings,
                catalog = container.catalog,
                usageRepository = container.usage,
                weatherRepository = container.weather,
                calendarRepository = container.calendar,
                isDefaultLauncher = isDefaultLauncher,
                navigator = navigator,
                onBack = { surface = "home" },
            )
            else -> HomeScreen(
                settings = settings,
                settingsRepository = container.settings,
                catalog = container.catalog,
                usageRepository = container.usage,
                weatherRepository = container.weather,
                calendarRepository = container.calendar,
                timerRepository = container.timer,
                isDefaultLauncher = isDefaultLauncher,
                navigator = navigator,
                onLaunchApp = ::launchApp,
            )
        }

        if (drawerOpen) {
            AppDrawerScreen(
                settings = settings,
                settingsRepository = container.settings,
                catalog = container.catalog,
                usageRepository = container.usage,
                navigator = navigator,
                onClose = { drawerOpen = false },
                onLaunchApp = ::launchApp,
            )
        }

        if (searchOpen) {
            GlobalSearchScreen(
                initialQuery = searchQuery,
                settings = settings,
                settingsRepository = container.settings,
                catalog = container.catalog,
                usageRepository = container.usage,
                navigator = navigator,
                onClose = { searchOpen = false },
                onLaunchApp = ::launchApp,
                onOpenWeb = ::openWeb,
            )
        }
    }
}

private fun isNexusDefaultLauncher(context: android.content.Context): Boolean = runCatching {
    context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
}.getOrDefault(
    context.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName == context.packageName,
)

@Composable
private fun rememberWallpaper(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = if (uri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = Uri.parse(uri)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(parsed)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sample = 1
                    while (bounds.outWidth > 0 && bounds.outWidth / (sample * 2) >= 1440) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(parsed)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}

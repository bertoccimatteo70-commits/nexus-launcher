package com.nexus.feature.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.common.AppOrdering
import com.nexus.core.common.NexusFormatters
import com.nexus.core.data.AppCatalogRepository
import com.nexus.core.data.CalendarRepository
import com.nexus.core.data.NotificationBadges
import com.nexus.core.data.SettingsRepository
import com.nexus.core.data.TimerRepository
import com.nexus.core.data.UsageRepository
import com.nexus.core.data.WeatherRepository
import com.nexus.core.designsystem.LocalNexusMetrics
import com.nexus.core.designsystem.NexusAppIcon
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusIconButton
import com.nexus.core.designsystem.NexusLogo
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.model.BadgeMode
import com.nexus.core.model.HomeItem
import com.nexus.core.model.HomeItemType
import com.nexus.core.model.LauncherApp
import com.nexus.core.model.NexusNavigator
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.UsageStat
import com.nexus.core.model.WidgetSpan
import com.nexus.core.model.WidgetType
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    settings: NexusSettings,
    settingsRepository: SettingsRepository,
    catalog: AppCatalogRepository,
    usageRepository: UsageRepository,
    weatherRepository: WeatherRepository,
    calendarRepository: CalendarRepository,
    timerRepository: TimerRepository,
    isDefaultLauncher: Boolean,
    navigator: NexusNavigator,
    onLaunchApp: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val apps by catalog.apps.collectAsStateWithLifecycle()
    val usageMap by usageRepository.usage.collectAsStateWithLifecycle()
    val weather by weatherRepository.state.collectAsStateWithLifecycle()
    val events by calendarRepository.events.collectAsStateWithLifecycle()
    val badgeCounts by NotificationBadges.counts.collectAsStateWithLifecycle()
    val activeTimer by timerRepository.snapshot.collectAsStateWithLifecycle()
    val battery = rememberBatteryInfo()
    val now = rememberMinuteNow()
    val lifecycleOwner = LocalLifecycleOwner.current

    var editMode by rememberSaveable { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var appPickerTarget by remember { mutableStateOf<AppPickerTarget?>(null) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    var pageManagerOpen by remember { mutableStateOf(false) }
    var dockEditorOpen by remember { mutableStateOf(false) }
    var itemEditor by remember { mutableStateOf<HomeItem?>(null) }
    var dockAppEditor by remember { mutableStateOf<LauncherApp?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }

    val pageCount = settings.homePages.coerceIn(1, 9)
    val centeredStart = remember(pageCount) {
        val half = Int.MAX_VALUE / 2
        half - half % pageCount
    }
    val pagerState = rememberPagerState(
        initialPage = centeredStart,
        pageCount = { Int.MAX_VALUE },
    )
    var trackedPageCount by rememberSaveable { mutableIntStateOf(pageCount) }

    LaunchedEffect(pageCount) {
        if (trackedPageCount != pageCount) {
            trackedPageCount = pageCount
            pagerState.scrollToPage(centeredStart)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        currentPage = ((pagerState.currentPage % pageCount) + pageCount) % pageCount
    }
    LaunchedEffect(Unit) {
        weatherRepository.refresh()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) weatherRepository.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val update: ((NexusSettings) -> NexusSettings) -> Unit = { transform ->
        settingsRepository.updateAsync(transform)
    }
    val installedByKey = remember(apps) { apps.associateBy { it.key } }
    val dockApps = remember(settings.dockApps, settings.dockSize, apps, usageMap) {
        effectiveDock(settings, apps, usageMap)
    }

    LaunchedEffect(settings.autoLayoutApplied, apps.size) {
        if (!settings.autoLayoutApplied && apps.isNotEmpty()) {
            val capacity = (settings.gridColumns * settings.gridRows).coerceAtLeast(9)
            val seeded = AppOrdering.mostUsed(apps, usageMap).take(capacity * 2)
            val items = buildList {
                add(
                    HomeItem(
                        id = "seed-dashboard",
                        type = HomeItemType.WIDGET,
                        key = WidgetType.DASHBOARD.name,
                        page = 0,
                        order = 0,
                        span = WidgetSpan.LARGE,
                    ),
                )
                seeded.forEachIndexed { index, app ->
                    add(
                        HomeItem(
                            id = "seed-${app.key}",
                            type = HomeItemType.APP,
                            key = app.key,
                            page = index / capacity,
                            order = index,
                            span = WidgetSpan.MEDIUM,
                        ),
                    )
                }
            }
            settingsRepository.updateAsync { current ->
                if (current.autoLayoutApplied) return@updateAsync current
                current.copy(
                    homeItems = current.homeItems + items,
                    dockApps = seeded.take(current.dockSize).map { it.key },
                    homePages = max(current.homePages, items.maxOf { it.page } + 1).coerceAtMost(9),
                    autoLayoutApplied = true,
                )
            }
        }
    }

    fun addApp(app: LauncherApp, target: AppPickerTarget) {
        val page = currentPage
        if (target == AppPickerTarget.HOME) {
            update { current ->
                val order = (current.homeItems.filter { it.page == page }.maxOfOrNull { it.order } ?: -1) + 1
                current.withItem(
                    HomeItem(
                        id = UUID.randomUUID().toString(),
                        type = HomeItemType.APP,
                        key = app.key,
                        page = page,
                        order = order,
                    ),
                )
            }
        } else {
            update { current ->
                current.copy(
                    dockApps = (current.dockApps + app.key).distinct().takeLast(current.dockSize),
                )
            }
        }
        appPickerTarget = null
    }

    fun addWidget(type: WidgetType) {
        val page = currentPage
        update { current ->
            val order = (current.homeItems.filter { it.page == page }.maxOfOrNull { it.order } ?: -1) + 1
            current.withItem(
                HomeItem(
                    id = UUID.randomUUID().toString(),
                    type = HomeItemType.WIDGET,
                    key = type.name,
                    page = page,
                    order = order,
                    span = when (type) {
                        WidgetType.DASHBOARD -> WidgetSpan.LARGE
                        WidgetType.BATTERY -> WidgetSpan.SMALL
                        else -> WidgetSpan.MEDIUM
                    },
                ),
            )
        }
        widgetPickerOpen = false
    }

    fun moveItem(item: HomeItem, delta: Int) {
        update { current ->
            val siblings = current.homeItems
                .filter { it.page == item.page && it.type == item.type }
                .sortedBy { it.order }
            val index = siblings.indexOfFirst { it.id == item.id }
            val target = index + delta
            if (index < 0 || target !in siblings.indices) return@update current
            val reordered = siblings.toMutableList().apply { add(target, removeAt(index)) }
            val normalized = reordered.mapIndexed { position, entry -> entry.copy(order = position) }
            current.copy(
                homeItems = current.homeItems.filterNot {
                    it.page == item.page && it.type == item.type
                } + normalized,
            )
        }
        itemEditor = item
    }

    fun moveItemToPage(item: HomeItem, page: Int) {
        update { current ->
            val nextOrder = (current.homeItems.filter { it.page == page && it.type == item.type }
                .maxOfOrNull { it.order } ?: -1) + 1
            current.withItem(item.copy(page = page.coerceIn(0, current.homePages - 1), order = nextOrder))
        }
        itemEditor = null
    }

    fun setDockMembership(key: String, include: Boolean) {
        update { current ->
            val visible = effectiveDock(current, apps, usageMap).map { it.key }
            val next = if (include) (visible + key).distinct() else visible.filterNot { it == key }
            current.copy(dockApps = next.takeLast(current.dockSize))
        }
    }

    fun removeLastPage() {
        update { current ->
            if (current.homePages <= 1) return@update current
            val removedPage = current.homePages - 1
            val previous = removedPage - 1
            val moved = current.homeItems
                .filter { it.page == removedPage }
                .mapIndexed { index, item ->
                    item.copy(
                        page = previous,
                        order = (current.homeItems.filter { it.page == previous }
                            .maxOfOrNull { it.order } ?: -1) + 1 + index,
                    )
                }
            current.copy(
                homeItems = current.homeItems.filterNot { it.page == removedPage } + moved,
                homePages = removedPage,
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        HomeTopBar(
            now = now,
            settings = settings,
            editMode = editMode,
            onOpenPanel = { panelOpen = true },
            onOpenSearch = { navigator.openSearch("") },
            onOpenSettings = { navigator.openSettings(SettingsTargets.ROOT) },
            onToggleEdit = { editMode = !editMode },
        )
        if (editMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NexusButton("App", { appPickerTarget = AppPickerTarget.HOME }, leading = NexusGlyphId.PLUS, filled = false)
                NexusButton("Widget", { widgetPickerOpen = true }, leading = NexusGlyphId.GRID, filled = false)
                NexusButton("Pagine", { pageManagerOpen = true }, leading = NexusGlyphId.DASHBOARD, filled = false)
                NexusButton("Dock", { dockEditorOpen = true }, leading = NexusGlyphId.APPS, filled = false)
                NexusButton("Fine", { editMode = false }, leading = NexusGlyphId.CHECK)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            key = { it },
            beyondViewportPageCount = 1,
        ) { physicalPage ->
            val logicalPage = ((physicalPage % pageCount) + pageCount) % pageCount
            HomePageContent(
                page = logicalPage,
                settings = settings,
                appsByKey = installedByKey,
                catalog = catalog,
                weather = weather,
                events = events,
                battery = battery,
                now = now,
                badgeCounts = badgeCounts,
                editMode = editMode,
                navigator = navigator,
                onLaunch = onLaunchApp,
                onEditItem = { itemEditor = it },
                onRefreshWeather = { weatherRepository.refresh(force = true) },
                activeTimer = activeTimer,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                val selected = index == currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                        )
                        .clickable {
                            val target = pagerState.currentPage + (index - currentPage)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        .semantics {
                            contentDescription = "Pagina ${index + 1} di $pageCount"
                        },
                )
            }
        }

        HomeDock(
            dockApps = dockApps,
            settings = settings,
            catalog = catalog,
            badgeCounts = badgeCounts,
            editMode = editMode,
            onLaunch = onLaunchApp,
            onEditApp = { dockAppEditor = it },
            onOpenDrawer = { navigator.openDrawer() },
            onOpenPicker = { appPickerTarget = AppPickerTarget.DOCK },
        )
    }

    if (panelOpen) {
        NexusPanelSheet(
            settings = settings,
            isDefaultLauncher = isDefaultLauncher,
            navigator = navigator,
            activeTimer = activeTimer != null,
            onDismiss = { panelOpen = false },
            onEditHome = {
                panelOpen = false
                editMode = true
            },
        )
    }

    appPickerTarget?.let { target ->
        AppPickerSheet(
            target = target,
            apps = apps,
            settings = settings,
            homeKeys = settings.homeItems.filter { it.type == HomeItemType.APP }.map { it.key }.toSet(),
            dockKeys = dockApps.map { it.key }.toSet(),
            pageLabel = if (target == AppPickerTarget.HOME) "Pagina ${currentPage + 1}" else "Dock",
            catalog = catalog,
            onDismiss = { appPickerTarget = null },
            onPick = { app -> addApp(app, target) },
        )
    }

    if (widgetPickerOpen) {
        WidgetPickerSheet(
            existingTypes = settings.homeItems
                .filter { it.page == currentPage && it.type == HomeItemType.WIDGET }
                .mapNotNull { runCatching { WidgetType.valueOf(it.key) }.getOrNull() }
                .toSet(),
            pageLabel = "Pagina ${currentPage + 1}",
            onDismiss = { widgetPickerOpen = false },
            onPick = ::addWidget,
        )
    }

    if (pageManagerOpen) {
        PageManagerSheet(
            pageCount = pageCount,
            currentPage = currentPage,
            onAddPage = {
                update { it.copy(homePages = (it.homePages + 1).coerceAtMost(9)) }
            },
            onRemovePage = { removeLastPage() },
            onDismiss = { pageManagerOpen = false },
        )
    }

    if (dockEditorOpen) {
        DockEditorSheet(
            dockApps = dockApps,
            catalog = catalog,
            onRemove = { app -> setDockMembership(app.key, false) },
            onAdd = {
                dockEditorOpen = false
                appPickerTarget = AppPickerTarget.DOCK
            },
            onDismiss = { dockEditorOpen = false },
        )
    }

    itemEditor?.let { item ->
        val currentItem = settings.homeItems.firstOrNull { it.id == item.id } ?: item
        HomeItemEditorSheet(
            item = currentItem,
            app = if (currentItem.type == HomeItemType.APP) installedByKey[currentItem.key] else null,
            pageCount = pageCount,
            inDock = item.key in dockApps.map { it.key },
            onDismiss = { itemEditor = null },
            onOpenApp = { app ->
                itemEditor = null
                onLaunchApp(app)
            },
            onMove = { delta -> moveItem(currentItem, delta) },
            onMoveToPage = { page -> moveItemToPage(currentItem, page) },
            onUpdateSpan = { span ->
                update { current -> current.withItem(currentItem.copy(span = span)) }
                itemEditor = currentItem.copy(span = span)
            },
            onToggleDock = { include -> setDockMembership(currentItem.key, include) },
            onRemove = {
                update { current -> current.withoutItem(currentItem.id) }
                itemEditor = null
            },
            onOpenAppInfo = { app ->
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:${app.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
    }

    dockAppEditor?.let { app ->
        AlertDialog(
            onDismissRequest = { dockAppEditor = null },
            title = { Text(app.label) },
            text = {
                Column {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    NexusNotice(
                        "Le app nel dock possono essere rimosse o aperte. Il dock si riempie automaticamente con le app più usate.",
                        tone = NexusNoticeTone.INFO,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dockAppEditor = null
                    onLaunchApp(app)
                }) { Text("Apri") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        setDockMembership(app.key, false)
                        dockAppEditor = null
                    }) { Text("Rimuovi") }
                    TextButton(onClick = { dockAppEditor = null }) { Text("Chiudi") }
                }
            },
        )
    }
}

@Composable
private fun HomeTopBar(
    now: Long,
    settings: NexusSettings,
    editMode: Boolean,
    onOpenPanel: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenPanel)
                .semantics { contentDescription = "Apri Nexus Panel" },
            contentAlignment = Alignment.Center,
        ) {
            NexusLogo(Modifier.size(27.dp))
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                NexusFormatters.time(now, settings.use24HourClock),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                NexusFormatters.dateLong(now).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NexusIconButton(NexusGlyphId.SEARCH, "Cerca", onOpenSearch)
        NexusIconButton(
            NexusGlyphId.EDIT,
            if (editMode) "Esci dalla modifica Home" else "Modifica Home",
            onToggleEdit,
        )
        NexusIconButton(NexusGlyphId.SETTINGS, "Impostazioni Nexus", onOpenSettings)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePageContent(
    page: Int,
    settings: NexusSettings,
    appsByKey: Map<String, LauncherApp>,
    catalog: AppCatalogRepository,
    weather: com.nexus.core.data.WeatherUiState,
    events: List<com.nexus.core.model.CalendarEvent>,
    battery: BatteryInfo,
    now: Long,
    badgeCounts: Map<String, Int>,
    editMode: Boolean,
    navigator: NexusNavigator,
    onLaunch: (LauncherApp) -> Unit,
    onEditItem: (HomeItem) -> Unit,
    onRefreshWeather: () -> Unit,
    activeTimer: com.nexus.core.model.TimerSnapshot?,
) {
    val pageItems = settings.homeItems.filter { it.page == page }
    val widgets = pageItems.filter { it.type == HomeItemType.WIDGET }.sortedBy { it.order }
    val appItems = pageItems
        .filter { it.type == HomeItemType.APP }
        .sortedBy { it.order }
        .mapNotNull { item -> appsByKey[item.key]?.let { item to it } }
    val adaptiveTint = if (settings.adaptiveIcons) MaterialTheme.colorScheme.primary else null

    LazyVerticalGrid(
        columns = GridCells.Fixed(settings.gridColumns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (page == 0) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search-pill") {
                SearchPill(onClick = { navigator.openSearch("") })
            }
        }
        widgets.forEach { widget ->
            item(span = { GridItemSpan(maxLineSpan) }, key = widget.id) {
                NexusWidgetContent(
                    item = widget,
                    settings = settings,
                    now = now,
                    battery = battery,
                    weather = weather,
                    events = events,
                    navigator = navigator,
                    onRefreshWeather = onRefreshWeather,
                    onEdit = { onEditItem(widget) },
                )
            }
        }
        if (activeTimer != null && page == 0) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "timer-chip") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigator.openTools(com.nexus.core.model.ToolDestination.TIMER) },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NexusGlyph(NexusGlyphId.TIMER, contentDescription = null, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Timer attivo · ${NexusFormatters.timer(activeTimer.targetAtEpochMillis - now)}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.weight(1f))
                        Text("Apri", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        items(items = appItems, key = { it.first.id }) { (item, app) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val badge = if (settings.badgeMode == BadgeMode.OFF) 0 else badgeCounts[app.packageName] ?: 0
                val icon = rememberAppIcon(catalog, app.key, settings.iconSizeDp)
                NexusAppIcon(
                    bitmap = icon,
                    label = app.label,
                    sizeDp = settings.iconSizeDp,
                    tint = adaptiveTint,
                    badgeCount = badge,
                    badgeMode = settings.badgeMode,
                    onClick = { if (editMode) onEditItem(item) else onLaunch(app) },
                    onLongClick = { onEditItem(item) },
                )
                if (settings.showAppLabels) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 1.dp, bottom = 2.dp),
                    )
                }
            }
        }
        if (pageItems.isEmpty() && !editMode) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                NexusNotice(
                    "Questa pagina è vuota. Usa Modifica Home nella barra in alto per aggiungere app o widget.",
                    tone = NexusNoticeTone.INFO,
                )
            }
        }
        if (editMode) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "edit-hint") {
                NexusNotice(
                    "Tocca un'icona per aprirla o toccala in modifica per spostarla. Tieni premuto per modificare un widget.",
                    tone = NexusNoticeTone.INFO,
                )
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NexusGlyph(
            NexusGlyphId.SEARCH,
            contentDescription = null,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Cerca app, web e strumenti",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        NexusTag("Nexus")
    }
}

@Composable
private fun HomeDock(
    dockApps: List<LauncherApp>,
    settings: NexusSettings,
    catalog: AppCatalogRepository,
    badgeCounts: Map<String, Int>,
    editMode: Boolean,
    onLaunch: (LauncherApp) -> Unit,
    onEditApp: (LauncherApp) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    val density = LocalDensity.current
    val metrics = LocalNexusMetrics.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .navigationBarsPadding()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dragAmount -> total += dragAmount },
                    onDragEnd = {
                        if (total < with(density) { -42.dp.toPx() }) onOpenDrawer()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                    RoundedCornerShape(30.dp),
                )
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            dockApps.forEach { app ->
                val icon = rememberAppIcon(catalog, app.key, 40)
                val badge = if (settings.badgeMode == BadgeMode.OFF) 0 else badgeCounts[app.packageName] ?: 0
                NexusAppIcon(
                    bitmap = icon,
                    label = app.label,
                    sizeDp = 40,
                    tint = if (settings.adaptiveIcons) MaterialTheme.colorScheme.primary else null,
                    badgeCount = badge,
                    badgeMode = settings.badgeMode,
                    onClick = { if (editMode) onEditApp(app) else onLaunch(app) },
                    onLongClick = { onEditApp(app) },
                )
            }
            Box(
                modifier = Modifier
                    .size(metrics.touchTargetDp.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenDrawer)
                    .semantics { contentDescription = "Apri tutte le applicazioni" },
                contentAlignment = Alignment.Center,
            ) {
                NexusGlyph(
                    NexusGlyphId.APPS,
                    contentDescription = null,
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (editMode) {
                NexusIconButton(NexusGlyphId.PLUS, "Modifica dock", onOpenPicker)
            }
        }
    }
}

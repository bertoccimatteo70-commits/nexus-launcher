package com.nexus.feature.drawer

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.common.AppOrdering
import com.nexus.core.common.NexusFormatters
import com.nexus.core.data.AppCatalogRepository
import com.nexus.core.data.NotificationBadges
import com.nexus.core.data.SettingsRepository
import com.nexus.core.data.UsageRepository
import com.nexus.core.designsystem.NexusAppIcon
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusEmptyState
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusIconButton
import com.nexus.core.designsystem.NexusLogo
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.model.AppCategory
import com.nexus.core.model.BadgeMode
import com.nexus.core.model.DrawerSort
import com.nexus.core.model.LauncherApp
import com.nexus.core.model.NexusNavigator
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.UsageStat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    settings: NexusSettings,
    settingsRepository: SettingsRepository,
    catalog: AppCatalogRepository,
    usageRepository: UsageRepository,
    navigator: NexusNavigator,
    onClose: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val apps by catalog.apps.collectAsStateWithLifecycle()
    val usage by usageRepository.usage.collectAsStateWithLifecycle()
    val badges by NotificationBadges.counts.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var appInfo by remember { mutableStateOf<LauncherApp?>(null) }

    val hidden = remember(apps, settings.hiddenApps) { apps.filterNot { it.key in settings.hiddenApps } }
    val category = selectedCategory?.let { name ->
        runCatching { AppCategory.valueOf(name) }.getOrNull()
    }
    val filtered = remember(hidden, query, category, settings.drawerSort, usage) {
        var list = hidden
        if (category != null) list = list.filter { it.category == category }
        if (query.isNotBlank()) list = list.filter { it.label.contains(query.trim(), ignoreCase = true) }
        when (settings.drawerSort) {
            DrawerSort.MOST_USED -> AppOrdering.mostUsed(list, usage)
            DrawerSort.ALPHABETICAL -> AppOrdering.alphabetical(list)
        }
    }
    val sections = remember(filtered) { AppOrdering.sections(filtered) }
    val folders = remember(hidden) { AppOrdering.groupedByCategory(hidden) }
    val adaptiveTint = if (settings.adaptiveIcons) MaterialTheme.colorScheme.primary else null
    val showFolders = settings.drawerCategoryFolders && query.isBlank() && category == null

    LaunchedEffect(query, selectedCategory) {
        gridState.scrollToItem(0)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexusIconButton(NexusGlyphId.BACK, "Chiudi applicazioni", onClose)
                NexusLogo(Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Applicazioni", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${hidden.size} installate · ${settings.hiddenApps.size} nascoste",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NexusIconButton(NexusGlyphId.SEARCH, "Ricerca globale Nexus", {
                    navigator.openSearch(query)
                })
                NexusIconButton(NexusGlyphId.SETTINGS, "Impostazioni drawer", {
                    navigator.openSettings(SettingsTargets.DRAWER_SEARCH)
                })
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Cerca nelle applicazioni installate") },
                leadingIcon = {
                    NexusGlyph(NexusGlyphId.SEARCH, contentDescription = null, size = 18.dp)
                },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { query = "" }
                                .semantics { contentDescription = "Cancella ricerca" },
                            contentAlignment = Alignment.Center,
                        ) {
                            NexusGlyph(NexusGlyphId.CLOSE, contentDescription = null, size = 18.dp)
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexusButton(
                    "A → Z",
                    {
                        settingsRepository.updateAsync { it.copy(drawerSort = DrawerSort.ALPHABETICAL) }
                    },
                    filled = settings.drawerSort == DrawerSort.ALPHABETICAL,
                )
                NexusButton(
                    "Più usate",
                    {
                        settingsRepository.updateAsync { it.copy(drawerSort = DrawerSort.MOST_USED) }
                    },
                    filled = settings.drawerSort == DrawerSort.MOST_USED,
                )
                NexusButton(
                    if (settings.drawerCategoryFolders) "Cartelle attive" else "Cartelle off",
                    {
                        settingsRepository.updateAsync {
                            it.copy(drawerCategoryFolders = !it.drawerCategoryFolders)
                        }
                    },
                    filled = false,
                )
                if (category != null) {
                    NexusButton(
                        "Tutte",
                        { selectedCategory = null },
                        filled = false,
                    )
                }
                NexusButton(
                    "Impostazioni",
                    { navigator.openSettings(SettingsTargets.DRAWER_SEARCH) },
                    filled = false,
                )
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showFolders) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "folders-title") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Cartelle automatiche",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            NexusTag("${folders.size} categorie")
                        }
                    }
                    if (folders.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "folders-empty") {
                            NexusEmptyState(
                                title = "Nessuna categoria",
                                message = "Le cartelle compaiono quando sono installate applicazioni classificabili.",
                            )
                        }
                    } else {
                        items(
                            items = folders,
                            span = { GridItemSpan(2) },
                            key = { "folder-${it.first.name}" },
                        ) { (folderCategory, folderApps) ->
                            CategoryFolder(
                                category = folderCategory,
                                apps = folderApps,
                                catalog = catalog,
                                onClick = { selectedCategory = folderCategory.name },
                            )
                        }
                    }
                }

                if (query.isNotBlank() || category != null || !showFolders) {
                    val headerTitle = when {
                        query.isNotBlank() -> "Risultati per «$query»"
                        category != null -> category.label
                        else -> "Tutte le applicazioni"
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "result-header") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                headerTitle,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            NexusTag("${filtered.size}")
                            if (category != null) {
                                Spacer(Modifier.width(6.dp))
                                NexusButton("Tutte", { selectedCategory = null }, filled = false)
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        NexusEmptyState(
                            title = if (query.isBlank()) "Nessuna app" else "Nessun risultato",
                            message = if (query.isBlank()) {
                                "Nessuna applicazione avviabile trovata per questo filtro."
                            } else {
                                "Prova con un altro nome o usa la ricerca globale Nexus."
                            },
                            actionLabel = "Ricerca globale",
                            onAction = { navigator.openSearch(query) },
                        )
                    }
                } else if (query.isBlank() && category == null) {
                    sections.forEach { (letter, sectionApps) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "section-$letter") {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    letter.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .height(1.dp)
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                        }
                        items(items = sectionApps, key = { it.key }) { app ->
                            DrawerAppCell(
                                app = app,
                                settings = settings,
                                catalog = catalog,
                                badgeCount = if (settings.badgeMode == BadgeMode.OFF) 0
                                else badges[app.packageName] ?: 0,
                                tint = adaptiveTint,
                                onClick = { onLaunchApp(app) },
                                onLongClick = { appInfo = app },
                            )
                        }
                    }
                } else {
                    items(items = filtered, key = { it.key }) { app ->
                        DrawerAppCell(
                            app = app,
                            settings = settings,
                            catalog = catalog,
                            badgeCount = if (settings.badgeMode == BadgeMode.OFF) 0
                            else badges[app.packageName] ?: 0,
                            tint = adaptiveTint,
                            onClick = { onLaunchApp(app) },
                            onLongClick = { appInfo = app },
                        )
                    }
                }

                if (query.isNotBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "web-search") {
                        NexusButton(
                            text = "Cerca «${query.trim()}» con Nexus",
                            onClick = { navigator.openSearch(query) },
                            leading = NexusGlyphId.SEARCH,
                            filled = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp).navigationBarsPadding()) {
                        Text(
                            "Le app sono lette dal sistema tramite PackageManager. Nexus non usa elenchi dimostrativi.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (settings.hiddenApps.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            NexusButton(
                                "Gestisci ${settings.hiddenApps.size} app nascoste",
                                { navigator.openSettings(SettingsTargets.DRAWER_SEARCH) },
                                filled = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    appInfo?.let { app ->
        AlertDialog(
            onDismissRequest = { appInfo = null },
            title = { Text(app.label) },
            text = {
                Column {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${app.category.label} · ${if (app.isSystem) "app di sistema" else "app installata"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    appInfo = null
                    onLaunchApp(app)
                }) { Text("Apri") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        settingsRepository.updateAsync { current ->
                            current.copy(hiddenApps = current.hiddenApps + app.key)
                        }
                        appInfo = null
                    }) { Text("Nascondi") }
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(android.net.Uri.parse("package:${app.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        appInfo = null
                    }) { Text("Sistema") }
                }
            },
        )
    }
}

@Composable
private fun CategoryFolder(
    category: AppCategory,
    apps: List<LauncherApp>,
    catalog: AppCatalogRepository,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                NexusTag("${apps.size}")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                apps.take(4).forEach { app ->
                    val icon: ImageBitmap? = rememberFolderIcon(catalog, app.key)
                    NexusAppIcon(
                        bitmap = icon,
                        label = app.label,
                        sizeDp = 30,
                        onClick = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberFolderIcon(catalog: AppCatalogRepository, key: String): ImageBitmap? {
    val density = LocalDensity.current
    val sizePx = with(density) { 30.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, key, sizePx) {
        value = catalog.icon(key, sizePx)?.asImageBitmap()
    }
    return bitmap
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerAppCell(
    app: LauncherApp,
    settings: NexusSettings,
    catalog: AppCatalogRepository,
    badgeCount: Int,
    tint: androidx.compose.ui.graphics.Color?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { 48.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, app.key, sizePx) {
        value = catalog.icon(app.key, sizePx)?.asImageBitmap()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NexusAppIcon(
            bitmap = bitmap,
            label = app.label,
            sizeDp = 48,
            tint = tint,
            badgeCount = badgeCount,
            badgeMode = settings.badgeMode,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        Text(
            app.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

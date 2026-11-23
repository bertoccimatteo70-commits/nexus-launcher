package com.nexus.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.core.common.AppOrdering
import com.nexus.core.data.AppCatalogRepository
import com.nexus.core.designsystem.NexusAppIcon
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusChoiceRow
import com.nexus.core.designsystem.NexusDivider
import com.nexus.core.designsystem.NexusEmptyState
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusIconButton
import com.nexus.core.designsystem.NexusLogo
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusSectionHeader
import com.nexus.core.designsystem.NexusSettingRow
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.designsystem.NexusToggleRow
import com.nexus.core.model.AppCategory
import com.nexus.core.model.HomeItem
import com.nexus.core.model.HomeItemType
import com.nexus.core.model.LauncherApp
import com.nexus.core.model.NexusNavigator
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.ToolDestination
import com.nexus.core.model.WidgetSpan
import com.nexus.core.model.WidgetType

internal enum class AppPickerTarget { HOME, DOCK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
internal fun NexusPanelSheet(
    settings: NexusSettings,
    isDefaultLauncher: Boolean,
    navigator: NexusNavigator,
    activeTimer: Boolean,
    onDismiss: () -> Unit,
    onEditHome: () -> Unit,
) {
    HomeSheet(
        title = "Nexus Panel",
        subtitle = "Collegamenti rapidi, stato del launcher e personalizzazione.",
        onDismiss = onDismiss,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexusLogo(Modifier.width(36.dp).height(36.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Nexus Launcher", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Home · Drawer · Widget · Dashboard",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isDefaultLauncher) {
            NexusNotice("Nexus è impostato come launcher predefinito.", tone = NexusNoticeTone.SUCCESS)
        } else {
            NexusNotice(
                "Nexus non è ancora il launcher predefinito.",
                tone = NexusNoticeTone.WARNING,
                actionLabel = "Imposta",
                onAction = navigator.requestDefaultLauncher,
            )
        }
        if (settings.availableVersion.isNotBlank()) {
            NexusNotice(
                "Aggiornamento Nexus ${settings.availableVersion} disponibile.",
                tone = NexusNoticeTone.INFO,
                actionLabel = "Dettagli",
                onAction = { navigator.openSettings(SettingsTargets.UPDATES) },
            )
        }
        if (activeTimer) {
            NexusNotice(
                "Hai un timer attivo. Aprilo per controllarlo.",
                tone = NexusNoticeTone.INFO,
                actionLabel = "Timer",
                onAction = { navigator.openTools(ToolDestination.TIMER) },
            )
        }
        NexusSectionHeader("Accessi", "Tutto a portata di mano")
        QuickLinksGrid(
            items = listOf(
                QuickLinkItem(NexusGlyphId.SEARCH, "Cerca") { navigator.openSearch("") },
                QuickLinkItem(NexusGlyphId.APPS, "App") { navigator.openDrawer() },
                QuickLinkItem(NexusGlyphId.DASHBOARD, "Dashboard") { navigator.openDashboard() },
                QuickLinkItem(NexusGlyphId.CALCULATOR, "Calcolatrice") {
                    navigator.openTools(ToolDestination.CALCULATOR)
                },
                QuickLinkItem(NexusGlyphId.NOTES, "Note") { navigator.openTools(ToolDestination.NOTES) },
                QuickLinkItem(NexusGlyphId.TIMER, "Timer") { navigator.openTools(ToolDestination.TIMER) },
                QuickLinkItem(NexusGlyphId.CALENDAR, "Calendario") {
                    navigator.openTools(ToolDestination.CALENDAR)
                },
                QuickLinkItem(NexusGlyphId.WEATHER, "Meteo") { navigator.openTools(ToolDestination.WEATHER) },
                QuickLinkItem(NexusGlyphId.SETTINGS, "Setup") {
                    navigator.openSettings(SettingsTargets.ROOT)
                },
                QuickLinkItem(NexusGlyphId.BACKUP, "Backup") {
                    navigator.openSettings(SettingsTargets.BACKUP)
                },
                QuickLinkItem(NexusGlyphId.AUTOMATION, "Automazioni") {
                    navigator.openSettings(SettingsTargets.AUTOMATIONS)
                },
                QuickLinkItem(NexusGlyphId.DIAGNOSTICS, "Diagnostica") {
                    navigator.openSettings(SettingsTargets.DIAGNOSTICS)
                },
            ),
        )
        NexusButton("Modifica Home", onEditHome, leading = NexusGlyphId.EDIT)
        NexusDivider()
        NexusSettingRow(
            title = "Impostazioni launcher Android",
            subtitle = "Per scegliere un altro launcher o tornare al precedente",
            glyph = NexusGlyphId.HOME,
            onClick = navigator.openDefaultLauncherSettings,
        )
        NexusSettingRow(
            title = "Impostazioni Nexus",
            subtitle = "Tema, Home, privacy, backup e diagnostica",
            glyph = NexusGlyphId.SETTINGS,
            onClick = { navigator.openSettings(SettingsTargets.ROOT) },
        )
        Text(
            "Nexus non invia dati personali a server esterni. Meteo e aggiornamenti usano la rete solo quando richiesto.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPickerSheet(
    target: AppPickerTarget,
    apps: List<LauncherApp>,
    settings: NexusSettings,
    homeKeys: Set<String>,
    dockKeys: Set<String>,
    pageLabel: String,
    catalog: AppCatalogRepository,
    onDismiss: () -> Unit,
    onPick: (LauncherApp) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, apps, settings.hiddenApps) {
        AppOrdering.alphabetical(
            apps.filter { app ->
                app.key !in settings.hiddenApps &&
                    (query.isBlank() || app.label.contains(query, ignoreCase = true))
            },
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                if (target == AppPickerTarget.HOME) "Aggiungi app alla Home" else "Aggiungi app al dock",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "$pageLabel · ${apps.size} applicazioni installate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cerca un'applicazione") },
                leadingIcon = {
                    NexusGlyph(NexusGlyphId.SEARCH, contentDescription = null, size = 18.dp)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(items = filtered, key = { it.key }) { app ->
                    val alreadyThere = when (target) {
                        AppPickerTarget.HOME -> app.key in homeKeys
                        AppPickerTarget.DOCK -> app.key in dockKeys
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = rememberAppIcon(catalog, app.key, 40)
                        NexusAppIcon(icon, app.label, sizeDp = 40, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                app.category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (alreadyThere) {
                            NexusTag("Presente")
                        } else {
                            NexusButton(
                                text = "Aggiungi",
                                onClick = { onPick(app) },
                                filled = false,
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        NexusEmptyState(
                            title = "Nessuna app trovata",
                            message = "Prova con un altro nome oppure controlla le app nascoste nelle impostazioni del drawer.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WidgetPickerSheet(
    existingTypes: Set<WidgetType>,
    pageLabel: String,
    onDismiss: () -> Unit,
    onPick: (WidgetType) -> Unit,
) {
    HomeSheet(
        title = "Aggiungi un widget Nexus",
        subtitle = "I widget usano dati reali del dispositivo. Puoi ridimensionarli in seguito.",
        onDismiss = onDismiss,
    ) {
        Text(pageLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        WidgetType.entries.forEach { type ->
            val exists = type in existingTypes
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NexusGlyph(
                    glyphForWidget(type),
                    contentDescription = null,
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(type.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        descriptionForWidget(type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (exists) {
                    NexusTag("Presente")
                } else {
                    TextButton(onClick = { onPick(type) }) { Text("Aggiungi") }
                }
            }
            NexusDivider()
        }
        Text(
            "Trascina o tieni premuto un widget sulla Home per ridimensionarlo o rimuoverlo.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun glyphForWidget(type: WidgetType): NexusGlyphId = when (type) {
    WidgetType.CLOCK -> NexusGlyphId.TIMER
    WidgetType.WEATHER -> NexusGlyphId.WEATHER
    WidgetType.BATTERY -> NexusGlyphId.BATTERY
    WidgetType.CALENDAR -> NexusGlyphId.CALENDAR
    WidgetType.DASHBOARD -> NexusGlyphId.DASHBOARD
}

private fun descriptionForWidget(type: WidgetType): String = when (type) {
    WidgetType.CLOCK -> "Ora e data, aggiornate automaticamente."
    WidgetType.WEATHER -> "Meteo corrente con città e fallback offline."
    WidgetType.BATTERY -> "Livello batteria, ricarica e temperatura."
    WidgetType.CALENDAR -> "Prossimi eventi del calendario Nexus."
    WidgetType.DASHBOARD -> "Orologio, meteo, batteria ed eventi in un unico widget."
}

@Composable
internal fun HomeItemEditorSheet(
    item: HomeItem,
    app: LauncherApp?,
    pageCount: Int,
    inDock: Boolean,
    onDismiss: () -> Unit,
    onOpenApp: (LauncherApp) -> Unit,
    onMove: (Int) -> Unit,
    onMoveToPage: (Int) -> Unit,
    onUpdateSpan: (WidgetSpan) -> Unit,
    onToggleDock: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onOpenAppInfo: (LauncherApp) -> Unit,
) {
    val title = when (item.type) {
        HomeItemType.APP -> app?.label ?: "Applicazione non disponibile"
        HomeItemType.WIDGET -> runCatching { WidgetType.valueOf(item.key).label }.getOrDefault("Widget")
    }
    HomeSheet(
        title = title,
        subtitle = when (item.type) {
            HomeItemType.APP -> app?.packageName
            HomeItemType.WIDGET -> "Widget Nexus · dimensione ${item.span.label}"
        },
        onDismiss = onDismiss,
    ) {
        if (item.type == HomeItemType.APP && app != null) {
            NexusSettingRow(
                title = "Apri applicazione",
                glyph = NexusGlyphId.PLAY,
                onClick = { onOpenApp(app) },
            )
            NexusToggleRow(
                title = "Mostra nel dock",
                subtitle = "Il dock può contenere al massimo ${pageCount.coerceAtLeast(1)} elementi configurati",
                checked = inDock,
                onCheckedChange = onToggleDock,
            )
            NexusDivider()
            NexusSettingRow(
                title = "Informazioni dell'app",
                subtitle = "Apri la scheda di sistema dell'applicazione",
                glyph = NexusGlyphId.INFO,
                onClick = { onOpenAppInfo(app) },
            )
        } else if (item.type == HomeItemType.APP) {
            NexusNotice("L'app non è più installata. Puoi rimuovere questo elemento dalla Home.", tone = NexusNoticeTone.WARNING)
        }

        if (item.type == HomeItemType.WIDGET) {
            Text("Dimensione widget", style = MaterialTheme.typography.titleSmall)
            NexusChoiceRow(
                options = WidgetSpan.entries,
                selected = item.span,
                label = { it.label },
                onSelect = onUpdateSpan,
            )
        }

        Text("Posizione nella pagina", style = MaterialTheme.typography.titleSmall)
        NexusChoiceRow(
            options = (0 until pageCount).toList(),
            selected = item.page,
            label = { "Pagina ${it + 1}" },
            onSelect = onMoveToPage,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexusButton("Sposta su", { onMove(-1) }, filled = false)
            NexusButton("Sposta giù", { onMove(1) }, filled = false)
        }
        NexusDivider()
        NexusButton(
            text = "Rimuovi dalla Home",
            onClick = onRemove,
            leading = NexusGlyphId.TRASH,
            filled = false,
        )
    }
}

@Composable
internal fun PageManagerSheet(
    pageCount: Int,
    currentPage: Int,
    onAddPage: () -> Unit,
    onRemovePage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    HomeSheet(
        title = "Organizza le pagine",
        subtitle = "Puoi avere fino a 9 pagine. Lo scorrimento tra le pagine è ciclico.",
        onDismiss = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pageCount) { index ->
                NexusTag(if (index == currentPage) "Pagina ${index + 1} · attuale" else "Pagina ${index + 1}")
            }
        }
        NexusButton(
            "Aggiungi una pagina",
            onAddPage,
            leading = NexusGlyphId.PLUS,
            enabled = pageCount < 9,
        )
        NexusButton(
            "Rimuovi l'ultima pagina",
            { confirmRemove = true },
            leading = NexusGlyphId.TRASH,
            filled = false,
            enabled = pageCount > 1,
        )
        Text(
            "Rimuovendo una pagina, le app e i widget presenti vengono spostati nella pagina precedente.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Rimuovere la pagina $pageCount?") },
            text = {
                Text("Gli elementi della pagina saranno spostati nella pagina ${pageCount - 1}.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemovePage()
                    confirmRemove = false
                }) { Text("Rimuovi") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
internal fun DockEditorSheet(
    dockApps: List<LauncherApp>,
    catalog: AppCatalogRepository,
    onRemove: (LauncherApp) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    HomeSheet(
        title = "Personalizza il dock",
        subtitle = "Il dock mostra prima le app scelte e completa gli spazi con le app più usate.",
        onDismiss = onDismiss,
    ) {
        if (dockApps.isEmpty()) {
            NexusEmptyState("Dock vuoto", "Aggiungi applicazioni per riempire il dock.")
        } else {
            dockApps.forEach { app ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val icon = rememberAppIcon(catalog, app.key, 40)
                    NexusAppIcon(icon, app.label, sizeDp = 40, onClick = null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (app.category == AppCategory.OTHER) "Applicazione" else app.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    NexusIconButton(NexusGlyphId.TRASH, "Rimuovi ${app.label} dal dock", {
                        onRemove(app)
                    })
                }
                NexusDivider()
            }
        }
        NexusButton("Aggiungi app al dock", onAdd, leading = NexusGlyphId.PLUS, filled = false)
    }
}

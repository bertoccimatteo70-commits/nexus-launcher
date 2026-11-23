package com.nexus.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.common.SearchRanking
import com.nexus.core.data.AppCatalogRepository
import com.nexus.core.data.SettingsRepository
import com.nexus.core.data.UsageRepository
import com.nexus.core.designsystem.NexusAppIcon
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusEmptyState
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusIconButton
import com.nexus.core.designsystem.NexusLogo
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusSectionHeader
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.model.LauncherApp
import com.nexus.core.model.NexusNavigator
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.SearchHit
import com.nexus.core.model.SearchHitKind
import com.nexus.core.model.SearchProvider
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.ToolDestination
import java.net.URLEncoder

@Composable
fun GlobalSearchScreen(
    initialQuery: String,
    settings: NexusSettings,
    settingsRepository: SettingsRepository,
    catalog: AppCatalogRepository,
    usageRepository: UsageRepository,
    navigator: NexusNavigator,
    onClose: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onOpenWeb: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val apps by catalog.apps.collectAsStateWithLifecycle()
    val usage by usageRepository.usage.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val appsByKey = remember(apps) { apps.associateBy { it.key } }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) query = initialQuery
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val hits = remember(query, apps, usage, settings.searchProviders, settings.webEngine) {
        SearchRanking.buildHits(
            query = query,
            apps = apps,
            usage = usage,
            providers = settings.searchProviders,
            webEngine = settings.webEngine,
        )
    }

    fun openWebSearch() {
        if (query.isBlank()) return
        val url = settings.webEngine.queryUrl.format(URLEncoder.encode(query.trim(), "UTF-8"))
        onOpenWeb(url)
        onClose()
    }

    fun activate(hit: SearchHit) {
        when (hit.kind) {
            SearchHitKind.APP -> {
                val app = hit.appKey?.let { appsByKey[it] } ?: return
                onLaunchApp(app)
                onClose()
            }
            SearchHitKind.WEB -> {
                hit.url?.let(onOpenWeb)
                onClose()
            }
            SearchHitKind.CALCULATOR, SearchHitKind.TOOL -> {
                val destination = hit.tool ?: return
                navigator.openTools(destination)
                onClose()
            }
            SearchHitKind.SETTING -> {
                val target = hit.settingsTarget ?: SettingsTargets.ROOT
                navigator.openSettings(target)
                onClose()
            }
        }
    }

    fun submit() {
        val first = hits.firstOrNull()
        if (first != null) {
            activate(first)
        } else if (query.isNotBlank() && SearchProvider.WEB in settings.searchProviders) {
            openWebSearch()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexusIconButton(NexusGlyphId.BACK, "Chiudi ricerca", onClose)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text("App, web, strumenti, impostazioni") },
                    leadingIcon = {
                        NexusGlyph(NexusGlyphId.SEARCH, contentDescription = null, size = 18.dp)
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
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
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchProvider.entries.forEach { provider ->
                    val enabled = provider in settings.searchProviders
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            )
                            .border(
                                1.dp,
                                if (enabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                RoundedCornerShape(50),
                            )
                            .clickable(role = Role.Checkbox) {
                                settingsRepository.updateAsync { current ->
                                    val providers = if (enabled) {
                                        current.searchProviders - provider
                                    } else {
                                        current.searchProviders + provider
                                    }
                                    current.copy(searchProviders = providers)
                                }
                            }
                            .semantics {
                                selected = enabled
                                contentDescription = "Provider ${provider.label}"
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            provider.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                NexusButton(
                    "Impostazioni",
                    { navigator.openSettings(SettingsTargets.DRAWER_SEARCH) },
                    filled = false,
                )
            }

            if (query.isBlank()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "hero") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NexusLogo(Modifier.size(48.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Ricerca Nexus", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    "Un unico campo per app, web, strumenti e impostazioni.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item(key = "tips") {
                        NexusNotice(
                            "Prova «12*3» per un calcolo immediato, «meteo» per lo strumento meteo o il nome di un'app.",
                            tone = NexusNoticeTone.INFO,
                        )
                    }
                    item(key = "tools") {
                        NexusSectionHeader("Strumenti Nexus", "Accesso rapido")
                    }
                    item(key = "quick") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickToolRow(
                                "Calcolatrice", NexusGlyphId.CALCULATOR,
                                onOpen = { navigator.openTools(ToolDestination.CALCULATOR); onClose() },
                            )
                            QuickToolRow(
                                "Note cifrate", NexusGlyphId.NOTES,
                                onOpen = { navigator.openTools(ToolDestination.NOTES); onClose() },
                            )
                            QuickToolRow(
                                "Timer", NexusGlyphId.TIMER,
                                onOpen = { navigator.openTools(ToolDestination.TIMER); onClose() },
                            )
                            QuickToolRow(
                                "Calendario", NexusGlyphId.CALENDAR,
                                onOpen = { navigator.openTools(ToolDestination.CALENDAR); onClose() },
                            )
                            QuickToolRow(
                                "Meteo", NexusGlyphId.WEATHER,
                                onOpen = { navigator.openTools(ToolDestination.WEATHER); onClose() },
                            )
                        }
                    }
                    item(key = "privacy") {
                        Text(
                            "La ricerca viene eseguita sul dispositivo. La query viene inviata al motore web solo quando scegli un risultato web o premi Cerca.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                if (settings.searchProviders.isEmpty()) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        NexusNotice(
                            "Nessun provider di ricerca abilitato. Attiva almeno un suggerimento qui sopra.",
                            tone = NexusNoticeTone.WARNING,
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "result-count") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Comandi • ${hits.size} risultati",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            NexusTag(settings.webEngine.label)
                        }
                    }
                    items(items = hits, key = { it.id }) { hit ->
                        SearchResultRow(
                            hit = hit,
                            catalog = catalog,
                            onClick = { activate(hit) },
                        )
                    }
                    if (hits.isEmpty()) {
                        item(key = "no-results") {
                            NexusEmptyState(
                                title = "Nessun risultato locale",
                                message = if (SearchProvider.WEB in settings.searchProviders) {
                                    "Premi Cerca sulla tastiera per interrogare ${settings.webEngine.label}."
                                } else {
                                    "Attiva il provider Web o modifica la query."
                                },
                                actionLabel = if (SearchProvider.WEB in settings.searchProviders) "Cerca sul web" else null,
                                onAction = if (SearchProvider.WEB in settings.searchProviders) {
                                    { openWebSearch() }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    item(key = "submit-web") {
                        if (SearchProvider.WEB in settings.searchProviders) {
                            NexusButton(
                                text = "Cerca «${query.trim()}» su ${settings.webEngine.label}",
                                onClick = { openWebSearch() },
                                leading = NexusGlyphId.SEARCH,
                                filled = false,
                                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolRow(label: String, glyph: NexusGlyphId, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NexusGlyph(glyph, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        NexusGlyph(NexusGlyphId.FORWARD, contentDescription = null, size = 16.dp)
    }
}

@Composable
private fun SearchResultRow(
    hit: SearchHit,
    catalog: AppCatalogRepository,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val appKey = hit.appKey
        if (hit.kind == SearchHitKind.APP && appKey != null) {
            val density = LocalDensity.current
            val sizePx = with(density) { 34.dp.roundToPx() }
            val bitmap by produceState<ImageBitmap?>(null, appKey) {
                value = catalog.icon(appKey, sizePx)?.asImageBitmap()
            }
            NexusAppIcon(bitmap = bitmap, label = hit.title, sizeDp = 34, onClick = null)
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                NexusGlyph(
                    glyphForHit(hit),
                    contentDescription = null,
                    size = 19.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hit.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                hit.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            when (hit.kind) {
                SearchHitKind.APP -> "App"
                SearchHitKind.WEB -> "Web"
                SearchHitKind.CALCULATOR -> "Calcolo"
                SearchHitKind.TOOL -> "Tool"
                SearchHitKind.SETTING -> "Setup"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        NexusGlyph(NexusGlyphId.FORWARD, contentDescription = null, size = 15.dp)
    }
}

private fun glyphForHit(hit: SearchHit): NexusGlyphId = when (hit.kind) {
    SearchHitKind.CALCULATOR -> NexusGlyphId.CALCULATOR
    SearchHitKind.SETTING -> NexusGlyphId.SETTINGS
    SearchHitKind.WEB -> NexusGlyphId.SEARCH
    SearchHitKind.TOOL -> when (hit.tool) {
        ToolDestination.NOTES -> NexusGlyphId.NOTES
        ToolDestination.TIMER -> NexusGlyphId.TIMER
        ToolDestination.CALENDAR -> NexusGlyphId.CALENDAR
        ToolDestination.WEATHER -> NexusGlyphId.WEATHER
        else -> NexusGlyphId.CALCULATOR
    }
    SearchHitKind.APP -> NexusGlyphId.APPS
}

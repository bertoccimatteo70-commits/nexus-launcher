package com.nexus.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.common.AppOrdering
import com.nexus.core.data.AppCatalogRepository
import com.nexus.core.designsystem.NexusAppIcon
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusChoiceRow
import com.nexus.core.designsystem.NexusDetailScreen
import com.nexus.core.designsystem.NexusDivider
import com.nexus.core.designsystem.NexusEmptyState
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusLoading
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusPanel
import com.nexus.core.designsystem.NexusSectionHeader
import com.nexus.core.designsystem.NexusSettingRow
import com.nexus.core.designsystem.NexusSliderRow
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.designsystem.NexusToggleRow
import com.nexus.core.designsystem.NexusTopBar
import com.nexus.core.model.AccentPalette
import com.nexus.core.model.BackgroundStyle
import com.nexus.core.model.BadgeMode
import com.nexus.core.model.DrawerSort
import com.nexus.core.model.HomeItemType
import com.nexus.core.model.LauncherApp
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.SearchProvider
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.ThemeMode
import com.nexus.core.model.WebEngine
import kotlin.math.roundToInt

@Composable
internal fun AppearanceSettingsScreen(context: SettingsContext, onBack: () -> Unit) {
    val settings = context.settings
    val repository = context.settingsRepository
    val appContext = LocalContext.current
    var wallpaperError by remember { mutableStateOf<String?>(null) }

    val pickWallpaper = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure {
            wallpaperError = "Accesso persistente allo sfondo non disponibile. Verrà comunque usato finché il sistema lo consente."
        }
        repository.updateAsync { it.copy(wallpaperUri = uri.toString()) }
    }

    NexusDetailScreen(
        title = "Theme Studio",
        subtitle = "Basic",
        onBack = onBack,
    ) {
        NexusNotice(
            "Le modifiche sono applicate subito e salvate in locale. Il tema può cambiare automaticamente in base all'orario se le automazioni sono attive.",
            tone = NexusNoticeTone.INFO,
        )
        SettingsSection("Tema", "Modalità colore") {
            NexusChoiceRow(
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = { it.label },
                onSelect = { mode -> repository.updateAsync { it.copy(themeMode = mode) } },
            )
            Spacer(Modifier.height(4.dp))
            NexusToggleRow(
                title = "Colori dinamici di sistema",
                subtitle = "Usa la palette di Android 12+ quando disponibile",
                checked = settings.useDynamicColor,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(useDynamicColor = enabled) }
                },
            )
            if (!settings.useDynamicColor) {
                Text("Palette Nexus", style = MaterialTheme.typography.titleSmall)
                NexusChoiceRow(
                    options = AccentPalette.entries,
                    selected = settings.palette,
                    label = { it.label },
                    onSelect = { palette ->
                        repository.updateAsync { it.copy(palette = palette) }
                    },
                )
            }
            NexusToggleRow(
                title = "Contrasto elevato",
                subtitle = "Testi e bordi più leggibili",
                checked = settings.highContrast,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(highContrast = enabled) }
                },
            )
        }

        SettingsSection("Sfondo", "Wallpaper e campo geometrico") {
            Text(
                if (settings.wallpaperUri == null) "Sfondo Nexus generato" else "Sfondo personalizzato",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (settings.wallpaperUri == null) {
                    "Nexus disegna un campo geometrico ${settings.backgroundStyle.label.lowercase()}."
                } else {
                    "Immagine scelta dall'utente, con overlay per mantenere leggibili icone e testi."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusChoiceRow(
                options = BackgroundStyle.entries,
                selected = settings.backgroundStyle,
                label = { it.label },
                onSelect = { style ->
                    repository.updateAsync { it.copy(backgroundStyle = style, wallpaperUri = null) }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusButton("Scegli sfondo", {
                    wallpaperError = null
                    pickWallpaper.launch(arrayOf("image/*"))
                }, leading = NexusGlyphId.PALETTE)
                if (settings.wallpaperUri != null) {
                    NexusButton("Rimuovi", {
                        repository.updateAsync { it.copy(wallpaperUri = null) }
                    }, filled = false)
                }
            }
            NexusToggleRow(
                title = "Sfondo animato",
                subtitle = "Movimento leggero del campo geometrico",
                checked = settings.animatedBackground,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(animatedBackground = enabled) }
                },
            )
            NexusToggleRow(
                title = "Blur dello sfondo",
                subtitle = "Sfocatura configurabile, disattivata di default per i dispositivi con 2 GB di RAM",
                checked = settings.blurEnabled,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(blurEnabled = enabled) }
                },
            )
            wallpaperError?.let {
                NexusNotice(it, tone = NexusNoticeTone.WARNING)
            }
        }

        SettingsSection("Movimento e tipografia", "Accessibilità visiva") {
            NexusSliderRow(
                title = "Dimensione caratteri Nexus",
                valueLabel = "${(settings.fontScale * 100).roundToInt()}%",
                value = settings.fontScale,
                range = 0.85f..1.6f,
                steps = 14,
                onValueChange = { value ->
                    repository.updateAsync { it.copy(fontScale = value) }
                },
            )
            NexusSliderRow(
                title = "Velocità animazioni",
                valueLabel = "${(settings.animationSpeed * 100).roundToInt()}%",
                value = settings.animationSpeed,
                range = 0.2f..1.5f,
                steps = 12,
                onValueChange = { value ->
                    repository.updateAsync { it.copy(animationSpeed = value) }
                },
            )
            NexusToggleRow(
                title = "Riduci movimento",
                subtitle = "Disattiva le animazioni non essenziali",
                checked = settings.reduceMotion,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(reduceMotion = enabled) }
                },
            )
            NexusSliderRow(
                title = "Dimensione icone",
                valueLabel = "${settings.iconSizeDp} dp",
                value = settings.iconSizeDp.toFloat(),
                range = 40f..80f,
                steps = 7,
                onValueChange = { value ->
                    repository.updateAsync { it.copy(iconSizeDp = value.roundToInt()) }
                },
            )
            NexusToggleRow(
                title = "Target tattili grandi",
                subtitle = "Aree di tocco più ampie in tutta l'interfaccia",
                checked = settings.largeTouchTargets,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(largeTouchTargets = enabled) }
                },
            )
        }

        NexusPanel {
            NexusSectionHeader("Anteprima", "Identità Nexus")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    NexusGlyph(
                        NexusGlyphId.DASHBOARD,
                        contentDescription = null,
                        size = 26.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Nexus Theme Studio", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Minimalismo geometrico, colori configurabili, animazioni equilibrate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeSettingsScreen(context: SettingsContext, onBack: () -> Unit) {
    val settings = context.settings
    val repository = context.settingsRepository
    var resetConfirm by remember { mutableStateOf(false) }
    var removePageConfirm by remember { mutableStateOf(false) }
    val itemCount = settings.homeItems.size
    val widgetCount = settings.homeItems.count { it.type == HomeItemType.WIDGET }
    val appCount = settings.homeItems.count { it.type == HomeItemType.APP }

    NexusDetailScreen(
        title = "Home e griglia",
        subtitle = "Layout",
        onBack = onBack,
        actions = {
            NexusButton("Apri Home", { context.navigator.openHome() }, filled = false)
        },
    ) {
        SettingsSection("Griglia", "Colonne e righe") {
            NexusSliderRow(
                title = "Colonne",
                valueLabel = "${settings.gridColumns}",
                value = settings.gridColumns.toFloat(),
                range = 3f..7f,
                steps = 3,
                onValueChange = { value ->
                    repository.updateAsync { it.copy(gridColumns = value.roundToInt()) }
                },
            )
            NexusSliderRow(
                title = "Righe di riferimento",
                valueLabel = "${settings.gridRows}",
                value = settings.gridRows.toFloat(),
                range = 3f..7f,
                steps = 3,
                onValueChange = { value ->
                    repository.updateAsync { it.copy(gridRows = value.roundToInt()) }
                },
            )
            GridPreview(columns = settings.gridColumns, rows = settings.gridRows)
            Text(
                "La griglia usa ${settings.gridColumns} colonne. Le righe definiscono la capienza usata dal layout automatico; la Home può scorrere verticalmente.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusToggleRow(
                title = "Etichette delle app",
                subtitle = "Mostra il nome sotto ogni icona",
                checked = settings.showAppLabels,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(showAppLabels = enabled) }
                },
            )
            NexusToggleRow(
                title = "Adaptive Color per le icone",
                subtitle = "Tinta monocromatica Nexus; le icone originali restano disponibili",
                checked = settings.adaptiveIcons,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(adaptiveIcons = enabled) }
                },
            )
        }

        SettingsSection("Dock", "Barra inferiore dinamica") {
            NexusSliderRow(
                title = "Numero di app nel dock",
                valueLabel = "${settings.dockSize}",
                value = settings.dockSize.toFloat(),
                range = 3f..6f,
                steps = 2,
                onValueChange = { value ->
                    repository.updateAsync { it.copy(dockSize = value.roundToInt()) }
                },
            )
            Text(
                "Le app scelte manualmente restano nel dock; gli spazi liberi vengono riempiti con le app più usate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusTag("${settings.dockApps.size} scelte")
                NexusTag("${settings.dockSize} slot")
            }
        }

        SettingsSection("Pagine", "Organizzazione") {
            Text(
                "${settings.homePages} pagine · $appCount icone Home · $widgetCount widget · $itemCount elementi totali",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusButton("Aggiungi pagina", {
                    repository.updateAsync {
                        it.copy(homePages = (it.homePages + 1).coerceAtMost(9))
                    }
                }, enabled = settings.homePages < 9, leading = NexusGlyphId.PLUS)
                NexusButton("Rimuovi ultima", {
                    removePageConfirm = true
                }, filled = false, enabled = settings.homePages > 1)
            }
            Text(
                "Lo scorrimento tra le pagine è ciclico. Rimuovendo l'ultima pagina, i suoi elementi vengono spostati nella pagina precedente.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusButton(
                "Personalizza dalla Home",
                { context.navigator.openHome() },
                leading = NexusGlyphId.EDIT,
                filled = false,
            )
        }

        SettingsSection("Notifiche sulle icone", "Badge") {
            Text(
                "Richiede l'accesso al servizio di ascolto notifiche. Nexus conserva solo il nome del pacchetto e il numero di notifiche attive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusChoiceRow(
                options = BadgeMode.entries,
                selected = settings.badgeMode,
                label = { it.label },
                onSelect = { mode ->
                    repository.updateAsync { it.copy(badgeMode = mode) }
                },
            )
            NexusSettingRow(
                title = "Autorizzazione badge",
                subtitle = "Configura l'accesso alle notifiche nelle impostazioni Android",
                glyph = NexusGlyphId.SHIELD,
                onClick = { context.navigator.openSettings(SettingsTargets.PRIVACY) },
            )
        }

        SettingsSection("Ripristino", "Layout automatico") {
            Text(
                "Ripristinando il layout, Nexus ricrea il widget Dashboard e posiziona le applicazioni più usate nelle prime pagine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusButton("Ripristina layout automatico", {
                resetConfirm = true
            }, filled = false, leading = NexusGlyphId.REFRESH)
        }
    }

    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text("Ripristinare la Home?") },
            text = {
                Text("Verranno rimossi icone e widget personalizzati dalla Home e ricreato il layout automatico. Note, calendario e impostazioni non verranno toccati.")
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.updateAsync {
                        it.copy(
                            homeItems = emptyList(),
                            dockApps = emptyList(),
                            homePages = 2,
                            autoLayoutApplied = false,
                        )
                    }
                    resetConfirm = false
                }) { Text("Ripristina") }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirm = false }) { Text("Annulla") }
            },
        )
    }

    if (removePageConfirm) {
        AlertDialog(
            onDismissRequest = { removePageConfirm = false },
            title = { Text("Rimuovere la pagina ${settings.homePages}?") },
            text = { Text("Gli elementi della pagina saranno spostati nella pagina precedente.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.updateAsync { current ->
                        if (current.homePages <= 1) return@updateAsync current
                        val removedPage = current.homePages - 1
                        val previous = removedPage - 1
                        val baseOrder = (current.homeItems.filter { it.page == previous }
                            .maxOfOrNull { it.order } ?: -1)
                        val moved = current.homeItems
                            .filter { it.page == removedPage }
                            .mapIndexed { index, item ->
                                item.copy(page = previous, order = baseOrder + 1 + index)
                            }
                        current.copy(
                            homeItems = current.homeItems.filterNot { it.page == removedPage } + moved,
                            homePages = removedPage,
                        )
                    }
                    removePageConfirm = false
                }) { Text("Rimuovi") }
            },
            dismissButton = {
                TextButton(onClick = { removePageConfirm = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun GridPreview(columns: Int, rows: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(columns) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DrawerSearchSettingsScreen(
    context: SettingsContext,
    onBack: () -> Unit,
    onOpenHiddenApps: () -> Unit,
) {
    val settings = context.settings
    val repository = context.settingsRepository
    NexusDetailScreen(
        title = "Drawer e ricerca",
        subtitle = "Applicazioni e web",
        onBack = onBack,
    ) {
        SettingsSection("Drawer", "Ordinamento e cartelle") {
            NexusChoiceRow(
                options = DrawerSort.entries,
                selected = settings.drawerSort,
                label = { it.label },
                onSelect = { sort -> repository.updateAsync { it.copy(drawerSort = sort) } },
            )
            NexusToggleRow(
                title = "Cartelle automatiche per categoria",
                subtitle = "Raggruppa le app usando la categoria dichiarata dal sistema",
                checked = settings.drawerCategoryFolders,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(drawerCategoryFolders = enabled) }
                },
            )
            NexusToggleRow(
                title = "Adaptive Color",
                subtitle = "Tinta le icone con il colore Nexus",
                checked = settings.adaptiveIcons,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(adaptiveIcons = enabled) }
                },
            )
            NexusSettingRow(
                title = "App nascoste",
                subtitle = "${settings.hiddenApps.size} applicazioni nascoste dal drawer",
                glyph = NexusGlyphId.APPS,
                onClick = onOpenHiddenApps,
            )
        }

        SettingsSection("Ricerca", "Suggerimenti configurabili") {
            Text(
                "Scegli quali categorie di suggerimenti mostrare nella ricerca globale.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SearchProvider.entries.forEach { provider ->
                NexusToggleRow(
                    title = provider.label,
                    subtitle = when (provider) {
                        SearchProvider.APPS -> "Applicazioni installate, con ordinamento per rilevanza e uso"
                        SearchProvider.WEB -> "Una scorciatoia per cercare sul motore scelto"
                        SearchProvider.TOOLS -> "Calcolatrice live, strumenti Nexus e scorciatoie impostazioni"
                    },
                    checked = provider in settings.searchProviders,
                    onCheckedChange = { enabled ->
                        repository.updateAsync { current ->
                            current.copy(
                                searchProviders = if (enabled) {
                                    current.searchProviders + provider
                                } else {
                                    current.searchProviders - provider
                                },
                            )
                        }
                    },
                )
            }
            if (settings.searchProviders.isEmpty()) {
                NexusNotice(
                    "Tutti i provider sono disattivati: la ricerca mostrerà solo il campo vuoto finché non ne riattivi almeno uno.",
                    tone = NexusNoticeTone.WARNING,
                )
            }
            Text("Motore web", style = MaterialTheme.typography.titleSmall)
            NexusChoiceRow(
                options = WebEngine.entries,
                selected = settings.webEngine,
                label = { it.label },
                onSelect = { engine ->
                    repository.updateAsync { it.copy(webEngine = engine) }
                },
            )
        }

        SettingsSection("Privacy della ricerca", "Trasparenza") {
            Text(
                "Le query restano sul dispositivo durante la ricerca locale. Vengono inviate al motore web soltanto quando apri un risultato Web o premi Cerca. Nexus non salva una cronologia delle ricerche.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun HiddenAppsScreen(
    settings: NexusSettings,
    settingsRepository: com.nexus.core.data.SettingsRepository,
    catalog: AppCatalogRepository,
    onBack: () -> Unit,
) {
    val apps by catalog.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        AppOrdering.alphabetical(
            apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) },
        )
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NexusTopBar(
            title = "App nascoste",
            subtitle = "Drawer Nexus",
            onBack = onBack,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Cerca un'app") },
            leadingIcon = {
                NexusGlyph(NexusGlyphId.SEARCH, contentDescription = null, size = 18.dp)
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        Text(
            "${settings.hiddenApps.size} app nascoste · ${filtered.size} visibili in elenco. Le app nascoste non compaiono nel drawer, ma restano installate.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            items(items = filtered, key = { it.key }) { app ->
                HiddenAppRow(
                    app = app,
                    hidden = app.key in settings.hiddenApps,
                    catalog = catalog,
                    onToggle = { hidden ->
                        settingsRepository.updateAsync { current ->
                            current.copy(
                                hiddenApps = if (hidden) current.hiddenApps + app.key
                                else current.hiddenApps - app.key,
                            )
                        }
                    },
                )
            }
            if (filtered.isEmpty()) {
                item {
                    NexusEmptyState(
                        title = "Nessuna app trovata",
                        message = "Le applicazioni installate sul dispositivo compariranno qui.",
                    )
                }
            }
        }
    }
}

@Composable
private fun HiddenAppRow(
    app: LauncherApp,
    hidden: Boolean,
    catalog: AppCatalogRepository,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val density = LocalDensity.current
        val sizePx = with(density) { 38.dp.roundToPx() }
        val icon by produceState<ImageBitmap?>(null, app.key) {
            value = catalog.icon(app.key, sizePx)?.asImageBitmap()
        }
        NexusAppIcon(bitmap = icon, label = app.label, sizeDp = 38, onClick = null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleSmall)
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Switch(checked = hidden, onCheckedChange = onToggle)
    }
}

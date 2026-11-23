package com.nexus.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.common.NexusFormatters
import com.nexus.core.data.NotificationBadges
import com.nexus.core.data.NexusDevice
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusChoiceRow
import com.nexus.core.designsystem.NexusDetailScreen
import com.nexus.core.designsystem.NexusDivider
import com.nexus.core.designsystem.NexusEmptyState
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusIconButton
import com.nexus.core.designsystem.NexusLogo
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusPanel
import com.nexus.core.designsystem.NexusSectionHeader
import com.nexus.core.designsystem.NexusSettingRow
import com.nexus.core.designsystem.NexusSliderRow
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.designsystem.NexusToggleRow
import com.nexus.core.designsystem.NexusTopBar
import com.nexus.core.model.BadgeMode
import com.nexus.core.model.NexusProject
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.ThemeMode
import com.nexus.core.model.UpdateChannel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@Composable
private fun rememberResumeTick(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return tick
}

@Composable
internal fun AccessibilitySettingsScreen(context: SettingsContext, onBack: () -> Unit) {
    val settings = context.settings
    val repository = context.settingsRepository
    val appContext = LocalContext.current
    NexusDetailScreen(
        title = "Accessibilità",
        subtitle = "Testo e interazione",
        onBack = onBack,
    ) {
        NexusNotice(
            "Le etichette accessibili sono presenti su icone, pulsanti e widget. TalkBack può leggere nomi, stati e conteggi delle notifiche.",
            tone = NexusNoticeTone.INFO,
        )
        SettingsSection("Testo", "Leggibilità") {
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
                title = "Contrasto elevato",
                subtitle = "Aumenta il contrasto di testi e bordi",
                checked = settings.highContrast,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(highContrast = enabled) }
                },
            )
            NexusToggleRow(
                title = "Target tattili grandi",
                subtitle = "Pulsanti e aree di tocco più ampie",
                checked = settings.largeTouchTargets,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(largeTouchTargets = enabled) }
                },
            )
            NexusToggleRow(
                title = "Riduci movimento",
                subtitle = "Meno animazioni e transizioni",
                checked = settings.reduceMotion,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(reduceMotion = enabled) }
                },
            )
        }
        SettingsSection("Anteprima", "Testo di esempio") {
            Text(
                "Nexus Launcher · Home · Applicazioni · Impostazioni",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Questa è un'anteprima della scala tipografica e del contrasto selezionati. Le modifiche si applicano immediatamente a tutta l'app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusButton("Apri impostazioni Android", {
                    runCatching {
                        appContext.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }, filled = false)
                NexusButton("Display", {
                    runCatching {
                        appContext.startActivity(
                            Intent(Settings.ACTION_DISPLAY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }, filled = false)
            }
        }
        SettingsSection("TalkBack", "Supporto") {
            Text(
                "Tutte le azioni principali hanno descrizioni testuali. Nella Home, le icone delle app includono il nome e il conteggio delle notifiche; i widget espongono le stesse informazioni dei testi visibili.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusNotice(
                "L'accessibilità completa dipende dal servizio TalkBack e dalle impostazioni di sistema del dispositivo.",
                tone = NexusNoticeTone.INFO,
            )
        }
    }
}

@Composable
internal fun PrivacySettingsScreen(
    context: SettingsContext,
    onOpenHiddenApps: () -> Unit,
    onBack: () -> Unit,
) {
    val settings = context.settings
    val repository = context.settingsRepository
    val appContext = LocalContext.current
    val apps by context.catalog.apps.collectAsStateWithLifecycle()
    val usage by context.usageRepository.usage.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val appsByKey = remember(apps) { apps.associateBy { it.key } }

    val notificationsGranted = remember(tick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val locationGranted = remember(tick) {
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val listenerEnabled = remember(tick) { NotificationBadges.isListenerEnabled(appContext) }
    val defaultLauncher = remember(tick, context.isDefaultLauncher) { context.isDefaultLauncher }
    val scope = rememberCoroutineScope()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> message = if (granted) "Notifiche autorizzate." else "Notifiche non autorizzate." }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        message = if (granted) {
            "Posizione approssimativa autorizzata: il meteo può usare la posizione."
        } else {
            "Posizione non autorizzata: il meteo userà la città scelta manualmente."
        }
    }
    val totalLaunches = usage.values.sumOf { it.launches }
    val topApps = remember(usage, appsByKey) {
        usage.values.sortedByDescending { it.launches }.take(8).mapNotNull { stat ->
            appsByKey[stat.key]?.let { it to stat }
        }
    }

    NexusDetailScreen(
        title = "Privacy e statistiche",
        subtitle = "Trasparenza",
        onBack = onBack,
    ) {
        NexusNotice(
            "Nexus non raccoglie dati nascosti e non li invia a server esterni. Meteo e controllo aggiornamenti usano la rete solo quando necessario; la ricerca web solo quando la scegli.",
            tone = NexusNoticeTone.INFO,
        )
        message?.let {
            NexusNotice(it, tone = NexusNoticeTone.SUCCESS)
        }
        SettingsSection("Statistiche d'uso", "Solo sul dispositivo") {
            NexusToggleRow(
                title = "Registra statistiche d'uso",
                subtitle = "Serve per ordinare le app più usate nel drawer e nella Home",
                checked = settings.usageStatsEnabled,
                onCheckedChange = { enabled ->
                    repository.updateAsync { it.copy(usageStatsEnabled = enabled) }
                },
            )
            Text(
                "$totalLaunches avvii registrati · ${usage.size} applicazioni · dati cifrati con Keystore",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (topApps.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                topApps.forEach { (app, stat) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${stat.launches} avvii",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    "Nessuna statistica registrata.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            NexusButton(
                "Cancella statistiche",
                { confirmClear = true },
                filled = false,
                enabled = usage.isNotEmpty(),
            )
            Text(
                "Se disattivi le statistiche, Nexus non registra nuovi avvii. I dati esistenti restano finché non li cancelli.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("Badge e notifiche", "Solo conteggi") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexusTag(if (listenerEnabled) "Accesso attivo" else "Accesso assente")
                Spacer(Modifier.width(8.dp))
                Text(
                    "Nexus legge solo pacchetto e numero di notifiche attive. Non legge titoli, testi o contenuti.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            NexusChoiceRow(
                options = BadgeMode.entries,
                selected = settings.badgeMode,
                label = { it.label },
                onSelect = { mode -> repository.updateAsync { it.copy(badgeMode = mode) } },
            )
            NexusSettingRow(
                title = "Accesso al servizio notifiche",
                subtitle = "Apri le impostazioni Android per autorizzare o revocare Nexus",
                glyph = NexusGlyphId.SHIELD,
                trailingText = if (listenerEnabled) "Concesso" else "Negato",
                onClick = { NotificationBadges.openSettings(appContext) },
            )
        }

        SettingsSection("Autorizzazioni", "Gestione guidata") {
            NexusSettingRow(
                title = "Notifiche",
                subtitle = "Avviso al termine dei timer. Facoltativa.",
                glyph = NexusGlyphId.TIMER,
                trailingText = if (notificationsGranted) "Concessa" else "Negata",
                onClick = {
                    if (notificationsGranted) {
                        runCatching {
                            appContext.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    } else if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            NexusDivider()
            NexusSettingRow(
                title = "Posizione approssimativa",
                subtitle = "Solo per il meteo locale. In alternativa puoi impostare una città.",
                glyph = NexusGlyphId.WEATHER,
                trailingText = if (locationGranted) "Concessa" else "Negata",
                onClick = {
                    if (locationGranted) {
                        runCatching {
                            appContext.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:${appContext.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    } else {
                        locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
            )
            NexusDivider()
            NexusSettingRow(
                title = "Launcher predefinito",
                subtitle = "Android concede il ruolo Home solo su tua conferma",
                glyph = NexusGlyphId.HOME,
                trailingText = if (defaultLauncher) "Nexus" else "Da scegliere",
                onClick = {
                    if (defaultLauncher) {
                        context.navigator.openDefaultLauncherSettings()
                    } else {
                        context.navigator.requestDefaultLauncher()
                    }
                },
            )
            NexusDivider()
            NexusSettingRow(
                title = "Internet",
                subtitle = "Usato solo per meteo, ricerca web e aggiornamenti",
                glyph = NexusGlyphId.WEATHER,
                trailingText = "Concessa",
                onClick = { message = "La connessione Internet è un'autorizzazione normale usata solo dalle funzioni di rete." },
            )
            Text(
                "Nexus non chiede permessi di accessibilità, amministrazione dispositivo, contatti, SMS o microfono.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("Controllo dei dati", "Sempre disponibile") {
            NexusSettingRow(
                title = "App nascoste",
                subtitle = "${settings.hiddenApps.size} app escluse dal drawer",
                glyph = NexusGlyphId.APPS,
                onClick = onOpenHiddenApps,
            )
            NexusDivider()
            NexusSettingRow(
                title = "Registri diagnostici",
                subtitle = "Locali, esportabili e cancellabili",
                glyph = NexusGlyphId.DIAGNOSTICS,
                onClick = { context.navigator.openSettings(SettingsTargets.DIAGNOSTICS) },
            )
            NexusDivider()
            NexusSettingRow(
                title = "Informativa privacy",
                subtitle = "Nexus non vende né condivide dati personali",
                glyph = NexusGlyphId.INFO,
                onClick = { context.navigator.openSettings(SettingsTargets.ABOUT) },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Cancellare le statistiche?") },
            text = { Text("Verranno eliminati conteggi e ultimo utilizzo di tutte le applicazioni. L'operazione è irreversibile.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        context.usageRepository.clear()
                        message = "Statistiche d'uso cancellate."
                        confirmClear = false
                    }
                }) { Text("Cancella") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
internal fun DiagnosticsSettingsScreen(context: SettingsContext, onBack: () -> Unit) {
    val appContext = LocalContext.current
    val entries by context.diagnostics.entries.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var exportReport by remember { mutableStateOf(true) }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = if (exportReport) {
            context.diagnostics.exportReport(uri)
        } else {
            context.diagnostics.exportLogs(uri)
        }
        message = result.fold(
            onSuccess = { "Esportati $it caratteri di diagnostica." },
            onFailure = { "Esportazione fallita: ${it.message}" },
        )
    }
    val crashes = remember(entries) { context.diagnostics.crashReports() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NexusTopBar(
            title = "Diagnostica",
            subtitle = "Solo locale",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NexusNotice(
                    "I registri restano nella memoria privata dell'app e non vengono inviati automaticamente. Puoi esportarli o cancellarli quando vuoi.",
                    tone = NexusNoticeTone.INFO,
                )
            }
            message?.let { text ->
                item { NexusNotice(text, tone = NexusNoticeTone.SUCCESS) }
            }
            item {
                NexusPanel {
                    NexusSectionHeader("Stato", "Salute di Nexus")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexusTag("${entries.size} eventi")
                        NexusTag("${crashes.size} crash")
                        NexusTag(if (context.buildInfo.isDebug) "Debug" else "Release")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        NexusDevice.summary(appContext),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexusButton("Esporta report", {
                            exportReport = true
                            createDocument.launch("nexus-diagnostics-${System.currentTimeMillis()}.txt")
                        }, leading = NexusGlyphId.DOWNLOAD)
                        NexusButton("Esporta log", {
                            exportReport = false
                            createDocument.launch("nexus-log-${System.currentTimeMillis()}.txt")
                        }, filled = false)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexusButton("Cancella registri", { confirmClear = true }, filled = false)
                        NexusButton("Ricrea configurazione", { confirmReset = true }, filled = false)
                    }
                    Text(
                        "La ricreazione della configurazione riporta tema, Home e automazioni ai valori iniziali. Note e calendario non vengono cancellati.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (crashes.isNotEmpty()) {
                item {
                    NexusPanel {
                        NexusSectionHeader("Crash recenti", "File locali")
                        Spacer(Modifier.height(6.dp))
                        crashes.take(3).forEach { file ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${file.length() / 1024} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item {
                NexusSectionHeader("Registro", "Ultimi ${entries.size} eventi")
            }
            if (entries.isEmpty()) {
                item {
                    NexusEmptyState(
                        title = "Nessun evento",
                        message = "Le attività di Nexus verranno registrate qui, localmente.",
                    )
                }
            } else {
                itemsIndexed(items = entries.asReversed(), key = { index, _ -> "log-$index" }) { _, entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            Modifier
                                .padding(top = 5.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when (entry.level) {
                                        com.nexus.core.data.DiagnosticsRepository.Level.ERROR -> MaterialTheme.colorScheme.error
                                        com.nexus.core.data.DiagnosticsRepository.Level.WARN -> com.nexus.core.designsystem.NexusColors.warning
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(
                                    entry.tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    NexusFormatters.time(entry.timestamp, context.settings.use24HourClock),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                entry.message,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Cancellare i registri?") },
            text = { Text("Log e report di crash locali verranno eliminati. La configurazione resta invariata.") },
            confirmButton = {
                TextButton(onClick = {
                    context.diagnostics.clear()
                    confirmClear = false
                    message = "Registri cancellati."
                }) { Text("Cancella") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Annulla") }
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Ricreare la configurazione?") },
            text = {
                Text("Tema, Home, automazioni e impostazioni torneranno ai valori iniziali. Note, eventi e statistiche restano disponibili finché non li cancelli dalle rispettive schermate.")
            },
            confirmButton = {
                TextButton(onClick = {
                    context.settingsRepository.updateAsync {
                        com.nexus.core.model.NexusSettings(onboardingCompleted = true)
                    }
                    confirmReset = false
                    message = "Configurazione ricreata."
                }) { Text("Ricrea") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
internal fun UpdatesSettingsScreen(context: SettingsContext, onBack: () -> Unit) {
    val settings = context.settings
    val repository = context.settingsRepository
    val appContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val available = settings.availableVersion
    val updateUrl = settings.availableVersionUrl.ifBlank { NexusProject.RELEASES_URL }

    fun openUrl(url: String) {
        runCatching {
            appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { error = "Nessuna app disponibile per aprire il link." }
    }

    NexusDetailScreen(
        title = "Aggiornamenti",
        subtitle = "Canale ${settings.updateChannel.label}",
        onBack = onBack,
        actions = {
            NexusIconButton(NexusGlyphId.REFRESH, "Controlla aggiornamenti", {
                if (!checking) {
                    checking = true
                    message = null
                    error = null
                    scope.launch {
                        context.updateRepository.check(settings.updateChannel)
                            .onSuccess { info ->
                                repository.update { current ->
                                    current.copy(
                                        lastUpdateCheckAt = System.currentTimeMillis(),
                                        availableVersion = info?.version.orEmpty(),
                                        availableVersionNotes = info?.notes.orEmpty().take(6_000),
                                        availableVersionUrl = info?.downloadUrl ?: info?.htmlUrl.orEmpty(),
                                    )
                                }
                                message = if (info == null) {
                                    "Nexus è aggiornato: nessuna versione più recente sul canale ${settings.updateChannel.label}."
                                } else {
                                    "Disponibile Nexus ${info.version}."
                                }
                            }
                            .onFailure { failure ->
                                error = "Controllo non riuscito: ${failure.message ?: "errore di rete"}"
                            }
                        checking = false
                    }
                }
            })
        },
    ) {
        NexusPanel {
            NexusSectionHeader("Versione installata", "Nexus ${context.buildInfo.versionName}")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusTag("Build ${context.buildInfo.versionCode}")
                NexusTag(if (context.buildInfo.isDebug) "Debug" else "Release")
                NexusTag(settings.updateChannel.label)
            }
            Spacer(Modifier.height(10.dp))
            Text("Canale di aggiornamento", style = MaterialTheme.typography.titleSmall)
            NexusChoiceRow(
                options = UpdateChannel.entries,
                selected = settings.updateChannel,
                label = { it.label },
                onSelect = { channel -> repository.updateAsync { it.copy(updateChannel = channel) } },
            )
            Text(
                if (settings.lastUpdateCheckAt > 0) {
                    "Ultimo controllo: ${NexusFormatters.relative(settings.lastUpdateCheckAt)}"
                } else {
                    "Nessun controllo effettuato finora."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (checking) {
                com.nexus.core.designsystem.NexusLoading("Controllo aggiornamenti…")
            } else {
                NexusButton(
                    "Controlla ora",
                    {
                        checking = true
                        message = null
                        error = null
                        scope.launch {
                            context.updateRepository.check(settings.updateChannel)
                                .onSuccess { info ->
                                    repository.update { current ->
                                        current.copy(
                                            lastUpdateCheckAt = System.currentTimeMillis(),
                                            availableVersion = info?.version.orEmpty(),
                                            availableVersionNotes = info?.notes.orEmpty().take(6_000),
                                            availableVersionUrl = info?.downloadUrl ?: info?.htmlUrl.orEmpty(),
                                        )
                                    }
                                    message = if (info == null) {
                                        "Nexus è aggiornato."
                                    } else {
                                        "Disponibile Nexus ${info.version}."
                                    }
                                }
                                .onFailure { failure ->
                                    error = "Controllo non riuscito: ${failure.message ?: "errore di rete"}"
                                }
                            checking = false
                        }
                    },
                    leading = NexusGlyphId.REFRESH,
                    enabled = !checking,
                )
            }
        }

        message?.let { text -> NexusNotice(text, tone = NexusNoticeTone.SUCCESS) }
        error?.let { text -> NexusNotice(text, tone = NexusNoticeTone.ERROR) }

        if (available.isNotBlank()) {
            NexusPanel {
                NexusSectionHeader("Disponibile", "Nexus $available")
                Spacer(Modifier.height(6.dp))
                if (settings.availableVersionNotes.isNotBlank()) {
                    Text(
                        settings.availableVersionNotes.take(6_000),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Le note di rilascio saranno visibili nella pagina GitHub della release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexusButton(
                        "Scarica aggiornamento",
                        { openUrl(updateUrl) },
                        leading = NexusGlyphId.DOWNLOAD,
                    )
                    NexusButton(
                        "Note release",
                        { openUrl(updateUrl) },
                        filled = false,
                    )
                }
                Text(
                    "Il download e l'installazione passano dal browser e dal programma di installazione Android. Nexus non installa APK silenziosamente e non dispone di privilegi di sistema.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            NexusNotice(
                "Nessun aggiornamento disponibile sul canale selezionato. Nexus non esegue installazioni automatiche.",
                tone = NexusNoticeTone.INFO,
            )
        }

        NexusSettingRow(
            title = "Pagina release su GitHub",
            subtitle = NexusProject.RELEASES_URL,
            glyph = NexusGlyphId.FORWARD,
            onClick = { openUrl(NexusProject.RELEASES_URL) },
        )
    }
}

@Composable
internal fun AboutSettingsScreen(context: SettingsContext, onBack: () -> Unit) {
    val appContext = LocalContext.current
    val settings = context.settings
    var confirmGuide by remember { mutableStateOf(false) }
    fun open(url: String) {
        runCatching {
            appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    NexusDetailScreen(
        title = "Informazioni",
        subtitle = "Nexus Launcher",
        onBack = onBack,
    ) {
        NexusPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexusLogo(Modifier.size(46.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Nexus Launcher", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Versione ${context.buildInfo.versionName} · build ${context.buildInfo.versionCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Launcher Android originale, geometrico e modulare. Home, drawer, widget, ricerca, strumenti, automazioni e backup funzionano sul dispositivo senza account cloud.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusTag("Kotlin")
                NexusTag("Jetpack Compose")
                NexusTag("Android 12+")
            }
        }

        SettingsSection("Launcher", "Ruolo di sistema") {
            NexusSettingRow(
                title = "Imposta Nexus come launcher predefinito",
                subtitle = if (context.isDefaultLauncher) "Nexus è attualmente la Home predefinita" else "Apri la richiesta di ruolo Home di Android",
                glyph = NexusGlyphId.HOME,
                trailingText = if (context.isDefaultLauncher) "Attivo" else "Imposta",
                onClick = {
                    if (context.isDefaultLauncher) {
                        context.navigator.openDefaultLauncherSettings()
                    } else {
                        context.navigator.requestDefaultLauncher()
                    }
                },
            )
            NexusDivider()
            NexusSettingRow(
                title = "Impostazioni launcher Android",
                subtitle = "Cambia launcher o torna a quello precedente",
                glyph = NexusGlyphId.SETTINGS,
                onClick = context.navigator.openDefaultLauncherSettings,
            )
        }

        SettingsSection("Progetto", "Repository e supporto") {
            NexusSettingRow(
                title = "Repository GitHub",
                subtitle = NexusProject.URL,
                glyph = NexusGlyphId.FORWARD,
                onClick = { open(NexusProject.URL) },
            )
            NexusDivider()
            NexusSettingRow(
                title = "Segnalazioni",
                subtitle = NexusProject.ISSUES_URL,
                glyph = NexusGlyphId.INFO,
                onClick = { open(NexusProject.ISSUES_URL) },
            )
            NexusDivider()
            NexusSettingRow(
                title = "Rivedi configurazione iniziale",
                subtitle = "Riapri la guida interattiva senza cancellare i dati",
                glyph = NexusGlyphId.EDIT,
                onClick = { confirmGuide = true },
            )
        }

        SettingsSection("Architettura", "Componenti") {
            Text(
                "core:model · core:common · core:data · core:designsystem · Home · Drawer · Ricerca · Impostazioni · Strumenti · Automazioni · Backup · app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Test unitari JVM sulla logica di calcolatrice, ranking, backup, trasferimento, meteo e automazioni; test UI strumentati inclusi nel progetto.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("Licenze", "Open source") {
            Text(
                "AndroidX · Jetpack Compose · Material 3 — Apache License 2.0\nKotlin · kotlinx.coroutines · kotlinx.serialization — Apache License 2.0\nMeteo: dati Open-Meteo (CC BY 4.0), nessuna chiave API.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("Privacy", "Informativa breve") {
            Text(
                "Nexus non crea profili, non usa analytics remoti e non trasmette elenchi di app, note o eventi. Le statistiche d'uso sono locali e disattivabili. I registri diagnostici sono locali e cancellabili. Le richieste di rete sono limitate a meteo, ricerca web scelta dall'utente e controllo aggiornamenti.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Nessun dato viene condiviso con terze parti senza un'azione esplicita dell'utente.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            "Nexus ${context.buildInfo.versionName} · schema ${settings.schemaVersion} · ${if (context.buildInfo.isDebug) "debug" else "release"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmGuide) {
        AlertDialog(
            onDismissRequest = { confirmGuide = false },
            title = { Text("Riaprire la configurazione iniziale?") },
            text = { Text("La guida ti mostrerà di nuovo tema, Home, launcher predefinito e autorizzazioni. I dati esistenti non vengono cancellati.") },
            confirmButton = {
                TextButton(onClick = {
                    context.settingsRepository.updateAsync { it.copy(onboardingCompleted = false) }
                    confirmGuide = false
                }) { Text("Riapri") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGuide = false }) { Text("Annulla") }
            },
        )
    }
}

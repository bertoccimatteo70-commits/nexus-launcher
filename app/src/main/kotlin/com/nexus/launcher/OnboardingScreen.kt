package com.nexus.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexus.core.data.SettingsRepository
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusChoiceRow
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusLogo
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusPanel
import com.nexus.core.designsystem.NexusSliderRow
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.designsystem.NexusToggleRow
import com.nexus.core.model.AccentPalette
import com.nexus.core.model.BackgroundStyle
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.ThemeMode
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(
    settings: NexusSettings,
    settingsRepository: SettingsRepository,
    isDefaultLauncher: Boolean,
    onRequestDefaultLauncher: () -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableIntStateOf(0) }
    val notificationsGranted = if (Build.VERSION.SDK_INT < 33) {
        true
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val locationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionMessage = if (granted) "Notifiche autorizzate." else "Notifiche non autorizzate: potrai abilitarle dalle impostazioni."
        refreshing++
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionMessage = if (granted) "Posizione approssimativa autorizzata." else "Posizione non autorizzata: potrai impostare una città nel meteo."
        refreshing++
    }

    fun finish() {
        settingsRepository.updateAsync { it.copy(onboardingCompleted = true) }
    }

    BackHandler(enabled = step > 0) { step -= 1 }

    fun update(transform: (NexusSettings) -> NexusSettings) {
        settingsRepository.updateAsync(transform)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexusLogo(Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("NEXUS LAUNCHER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Configurazione iniziale", style = MaterialTheme.typography.titleMedium)
            }
            NexusButton("Salta", { finish() }, filled = false)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        ),
                )
            }
        }
        Text(
            "Passo ${step + 1} di 4",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                0 -> WelcomeStep()
                1 -> ThemeStep(settings, ::update)
                2 -> HomeStep(settings, ::update)
                else -> SetupStep(
                    settings = settings,
                    isDefaultLauncher = isDefaultLauncher,
                    onRequestDefaultLauncher = onRequestDefaultLauncher,
                    notificationsGranted = notificationsGranted,
                    locationGranted = locationGranted,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestLocation = {
                        locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    onToggleBackup = { enabled -> update { it.copy(autoBackupEnabled = enabled) } },
                    onToggleUsageStats = { enabled -> update { it.copy(usageStatsEnabled = enabled) } },
                    permissionMessage = permissionMessage,
                    refreshKey = refreshing,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (step > 0) {
                NexusButton("Indietro", { step -= 1 }, filled = false, modifier = Modifier.weight(1f))
            }
            NexusButton(
                if (step == 3) "Inizia a usare Nexus" else "Avanti",
                { if (step == 3) finish() else step += 1 },
                modifier = Modifier.weight(if (step == 0) 1f else 1.2f),
                leading = if (step == 3) NexusGlyphId.CHECK else NexusGlyphId.FORWARD,
            )
        }
        Text(
            "Nexus non richiede account. Tutte le configurazioni restano sul dispositivo.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun WelcomeStep() {
    NexusPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexusLogo(Modifier.size(64.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Benvenuto in Nexus", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Un launcher geometrico, veloce e tuo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FeatureLine(NexusGlyphId.HOME, "Home e drawer reali", "Legge le applicazioni installate tramite PackageManager e LauncherApps.")
        FeatureLine(NexusGlyphId.SEARCH, "Ricerca globale", "App, web, strumenti e impostazioni in un solo campo.")
        FeatureLine(NexusGlyphId.DASHBOARD, "Widget e Dashboard", "Orologio, meteo con fallback offline, batteria, calendario e collegamenti.")
        FeatureLine(NexusGlyphId.BACKUP, "Backup locale", "Copia cifrata sul dispositivo, export con passphrase e trasferimento LAN.")
    }
    NexusNotice(
        "Nexus non simula funzioni: quello che vedi legge dati reali del sistema. Le funzioni che richiedono privilegi speciali sono spiegate e non vengono sostituite.",
        tone = NexusNoticeTone.INFO,
    )
}

@Composable
private fun ThemeStep(settings: NexusSettings, update: ((NexusSettings) -> NexusSettings) -> Unit) {
    NexusPanel {
        Text("Scegli il tema", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        NexusChoiceRow(
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { it.label },
            onSelect = { mode -> update { it.copy(themeMode = mode) } },
        )
        NexusToggleRow(
            title = "Colori dinamici",
            subtitle = "Usa la palette di Android 12+ quando disponibile",
            checked = settings.useDynamicColor,
            onCheckedChange = { enabled -> update { it.copy(useDynamicColor = enabled) } },
        )
        if (!settings.useDynamicColor) {
            Text("Palette Nexus", style = MaterialTheme.typography.titleSmall)
            NexusChoiceRow(
                options = AccentPalette.entries,
                selected = settings.palette,
                label = { it.label },
                onSelect = { palette -> update { it.copy(palette = palette) } },
            )
        }
        Text("Sfondo Nexus", style = MaterialTheme.typography.titleSmall)
        NexusChoiceRow(
            options = BackgroundStyle.entries,
            selected = settings.backgroundStyle,
            label = { it.label },
            onSelect = { style -> update { it.copy(backgroundStyle = style, wallpaperUri = null) } },
        )
        NexusToggleRow(
            title = "Sfondo animato",
            subtitle = "Movimento leggero, disattivabile",
            checked = settings.animatedBackground,
            onCheckedChange = { enabled -> update { it.copy(animatedBackground = enabled) } },
        )
    }
    ThemePreview(settings)
    Text(
        "Potrai cambiare tutto in qualsiasi momento da Impostazioni → Theme Studio.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ThemePreview(settings: NexusSettings) {
    NexusPanel {
        Text("ANTEPRIMA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                NexusGlyph(NexusGlyphId.HOME, contentDescription = null, size = 24.dp, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("12:30", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${settings.gridColumns} colonne · ${settings.dockSize} app nel dock",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { index ->
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (index % 2 == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun HomeStep(settings: NexusSettings, update: ((NexusSettings) -> NexusSettings) -> Unit) {
    NexusPanel {
        Text("Configura la Home", style = MaterialTheme.typography.headlineSmall)
        Text(
            "La griglia e il dock possono essere modificati anche dopo, dalla Home o dalle impostazioni.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NexusSliderRow(
            title = "Colonne",
            valueLabel = "${settings.gridColumns}",
            value = settings.gridColumns.toFloat(),
            range = 3f..7f,
            steps = 3,
            onValueChange = { value -> update { it.copy(gridColumns = value.roundToInt()) } },
        )
        NexusSliderRow(
            title = "App nel dock",
            valueLabel = "${settings.dockSize}",
            value = settings.dockSize.toFloat(),
            range = 3f..6f,
            steps = 2,
            onValueChange = { value -> update { it.copy(dockSize = value.roundToInt()) } },
        )
        NexusSliderRow(
            title = "Dimensione icone",
            valueLabel = "${settings.iconSizeDp} dp",
            value = settings.iconSizeDp.toFloat(),
            range = 40f..80f,
            steps = 7,
            onValueChange = { value -> update { it.copy(iconSizeDp = value.roundToInt()) } },
        )
        NexusToggleRow(
            title = "Etichette delle app",
            subtitle = "Nome sotto ogni icona",
            checked = settings.showAppLabels,
            onCheckedChange = { enabled -> update { it.copy(showAppLabels = enabled) } },
        )
        NexusToggleRow(
            title = "Adaptive Color",
            subtitle = "Colora le icone con l'accento Nexus",
            checked = settings.adaptiveIcons,
            onCheckedChange = { enabled -> update { it.copy(adaptiveIcons = enabled) } },
        )
    }
    NexusPanel {
        Text("ANTEPRIMA GRIGLIA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(settings.gridColumns) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                                RoundedCornerShape(9.dp),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
        }
    }
    NexusNotice(
        "Le pagine Home sono cicliche e si possono aggiungere o rimuovere. Puoi personalizzare icone, widget e dock direttamente dalla Home.",
        tone = NexusNoticeTone.INFO,
    )
}

@Composable
private fun SetupStep(
    settings: NexusSettings,
    isDefaultLauncher: Boolean,
    onRequestDefaultLauncher: () -> Unit,
    notificationsGranted: Boolean,
    locationGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestLocation: () -> Unit,
    onToggleBackup: (Boolean) -> Unit,
    onToggleUsageStats: (Boolean) -> Unit,
    permissionMessage: String?,
    refreshKey: Int,
) {
    NexusPanel {
        Text("Launcher predefinito", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexusTag(if (isDefaultLauncher) "Nexus è la Home" else "Non impostato")
            Spacer(Modifier.width(8.dp))
            if (!isDefaultLauncher) {
                NexusButton("Imposta Nexus", onRequestDefaultLauncher, leading = NexusGlyphId.HOME)
            }
        }
        Text(
            "Android chiede conferma. Se cambi idea puoi tornare al launcher precedente dalle impostazioni Android.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    NexusPanel {
        Text("Autorizzazioni spiegate", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        PermissionLine(
            title = "Notifiche",
            description = "Avvisi per i timer e aggiornamenti. Facoltative.",
            granted = notificationsGranted,
            actionLabel = "Autorizza",
            onAction = onRequestNotifications,
            refreshKey = refreshKey,
        )
        PermissionLine(
            title = "Posizione approssimativa",
            description = "Solo per il meteo locale; in alternativa puoi scegliere una città. Facoltativa.",
            granted = locationGranted,
            actionLabel = "Autorizza",
            onAction = onRequestLocation,
            refreshKey = refreshKey,
        )
        PermissionLine(
            title = "Accesso alle notifiche",
            description = "Per i badge sulle icone: Nexus legge solo pacchetto e numero, mai il contenuto. Puoi attivarlo dopo dalle impostazioni privacy.",
            granted = false,
            actionLabel = "Più tardi",
            onAction = null,
            refreshKey = refreshKey,
            neutral = true,
        )
        PermissionLine(
            title = "Internet",
            description = "Solo meteo, ricerca web scelta da te e controllo aggiornamenti. Nessun tracciamento.",
            granted = true,
            actionLabel = null,
            onAction = null,
            refreshKey = refreshKey,
        )
        if (permissionMessage != null) {
            Spacer(Modifier.height(6.dp))
            NexusNotice(permissionMessage, tone = NexusNoticeTone.INFO)
        }
    }
    NexusPanel {
        Text("Privacy e backup", style = MaterialTheme.typography.headlineSmall)
        NexusToggleRow(
            title = "Backup automatico locale",
            subtitle = "Una copia al giorno cifrata con Android Keystore",
            checked = settings.autoBackupEnabled,
            onCheckedChange = onToggleBackup,
        )
        NexusToggleRow(
            title = "Statistiche d'uso locali",
            subtitle = "Ordinano le app più usate. Disattivabili in ogni momento.",
            checked = settings.usageStatsEnabled,
            onCheckedChange = onToggleUsageStats,
        )
        Text(
            "Nexus non crea account, non usa analytics remoti e non invia elenchi di app o note a server. Nessuna autorizzazione non necessaria viene richiesta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionLine(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    refreshKey: Int,
    neutral: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (neutral) {
            NexusTag("Dopo")
        } else {
            NexusTag(if (granted) "Concessa" else "Facoltativa")
        }
        if (!granted && actionLabel != null && onAction != null) {
            Spacer(Modifier.width(6.dp))
            NexusButton(actionLabel, onAction, filled = false)
        }
        // refreshKey is read so permission changes recompose this line.
        if (refreshKey < 0) Text("")
    }
}

@Composable
private fun FeatureLine(glyph: NexusGlyphId, title: String, description: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        NexusGlyph(glyph, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

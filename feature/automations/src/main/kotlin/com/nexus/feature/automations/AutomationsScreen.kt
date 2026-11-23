package com.nexus.feature.automations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexus.core.common.AutomationRules
import com.nexus.core.data.SettingsRepository
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusDetailScreen
import com.nexus.core.designsystem.NexusDivider
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusPanel
import com.nexus.core.designsystem.NexusSectionHeader
import com.nexus.core.designsystem.NexusSettingRow
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.designsystem.NexusToggleRow
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.ThemeMode
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AutomationsScreen(
    settings: NexusSettings,
    settingsRepository: SettingsRepository,
    onOpenThemeStudio: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scheduler = remember(context) { AutomationScheduler(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    val localNow = remember(now) {
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
    val effective = remember(settings, localNow) {
        AutomationRules.effectiveAppearance(settings, localNow)
    }
    val night = effective.nightActive
    val nextTransition = remember(settings, now) {
        scheduler.nextTransition(settings)
    }
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    NexusDetailScreen(
        title = "Automazioni",
        subtitle = "Orari e modalità",
        onBack = onBack,
    ) {
        NexusNotice(
            "Le automazioni cambiano tema, contrasto e movimento in base all'orario. Nexus usa AlarmManager senza privilegi esatti: sotto Doze la transizione può essere posticipata, ma l'aspetto viene ricalcolato a ogni apertura.",
            tone = NexusNoticeTone.INFO,
        )
        message?.let { NexusNotice(it, tone = NexusNoticeTone.SUCCESS) }

        NexusPanel {
            NexusSectionHeader("Adesso", if (night) "Modalità notte" else "Modalità giorno")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusTag(if (night) "Notte" else "Giorno")
                NexusTag(
                    when (effective.themeMode) {
                        ThemeMode.DARK -> "Tema scuro"
                        ThemeMode.LIGHT -> "Tema chiaro"
                        ThemeMode.SYSTEM -> "Tema sistema"
                    },
                )
                NexusTag(if (settings.automationsEnabled) "Attive" else "Spente")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (settings.automationsEnabled) {
                    "Prossima transizione: ${
                        nextTransition?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(formatter)
                        } ?: "non pianificata"
                    }"
                } else {
                    "Automazioni disattivate: viene usato il tema scelto manualmente."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            NexusButton("Ricalcola pianificazione", {
                val scheduled = scheduler.schedule(settings)
                message = if (scheduled == null) {
                    if (settings.automationsEnabled) "Nessuna transizione da pianificare." else "Automazioni spente."
                } else {
                    "Pianificazione aggiornata."
                }
            }, filled = false, leading = com.nexus.core.designsystem.NexusGlyphId.REFRESH)
        }

        NexusSectionHeader("Regole", "Attiva o disattiva")
        NexusPanel {
            NexusToggleRow(
                title = "Automazioni orarie",
                subtitle = "Interruttore generale",
                checked = settings.automationsEnabled,
                onCheckedChange = { enabled ->
                    settingsRepository.updateAsync { it.copy(automationsEnabled = enabled) }
                },
            )
            NexusDivider()
            NexusToggleRow(
                title = "Tema automatico giorno/notte",
                subtitle = "Chiaro negli orari diurni, scuro di notte",
                checked = settings.autoThemeEnabled,
                onCheckedChange = { enabled ->
                    settingsRepository.updateAsync { it.copy(autoThemeEnabled = enabled) }
                },
            )
            NexusDivider()
            NexusToggleRow(
                title = "Forza tema scuro di notte",
                subtitle = "Indipendente dal tema manuale",
                checked = settings.nightModeEnabled,
                onCheckedChange = { enabled ->
                    settingsRepository.updateAsync { it.copy(nightModeEnabled = enabled) }
                },
            )
            NexusDivider()
            NexusToggleRow(
                title = "Notte calma",
                subtitle = "Riduce animazioni e movimento nella fascia notturna",
                checked = settings.nightCalmEnabled,
                onCheckedChange = { enabled ->
                    settingsRepository.updateAsync { it.copy(nightCalmEnabled = enabled) }
                },
            )
            NexusDivider()
            NexusToggleRow(
                title = "Contrasto notturno elevato",
                subtitle = "Testi più leggibili di notte",
                checked = settings.nightHighContrast,
                onCheckedChange = { enabled ->
                    settingsRepository.updateAsync { it.copy(nightHighContrast = enabled) }
                },
            )
        }

        NexusSectionHeader("Fascia oraria", "Ore intere")
        NexusPanel {
            HourAdjuster(
                label = "Inizio giorno",
                hour = settings.dayStartHour,
                onChange = { hour ->
                    settingsRepository.updateAsync { it.copy(dayStartHour = hour) }
                },
            )
            Spacer(Modifier.height(6.dp))
            HourAdjuster(
                label = "Inizio notte",
                hour = settings.nightStartHour,
                onChange = { hour ->
                    settingsRepository.updateAsync { it.copy(nightStartHour = hour) }
                },
            )
            if (settings.dayStartHour == settings.nightStartHour) {
                Spacer(Modifier.height(8.dp))
                NexusNotice(
                    "Inizio giorno e inizio notte coincidono: la modalità notte non verrà attivata.",
                    tone = NexusNoticeTone.WARNING,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "La modalità notte va da ${settings.nightStartHour}:00 a ${settings.dayStartHour}:00. Il cambio è calcolato nel fuso orario del dispositivo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusButton("Ripristina 07:00 / 20:00", {
                settingsRepository.updateAsync {
                    it.copy(dayStartHour = 7, nightStartHour = 20)
                }
                message = "Orari ripristinati."
            }, filled = false)
        }

        NexusPanel {
            NexusSectionHeader("Anteprima", "Aspetto efficace")
            Spacer(Modifier.height(8.dp))
            Text(
                "Tema: ${when (effective.themeMode) {
                    ThemeMode.DARK -> "Scuro"
                    ThemeMode.LIGHT -> "Chiaro"
                    ThemeMode.SYSTEM -> "Sistema"
                }}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Sfondo animato: ${if (effective.animateBackground) "sì" else "no"} · movimento ridotto: ${
                    if (effective.reduceMotion) "sì" else "no"
                } · contrasto elevato: ${if (effective.highContrast) "sì" else "no"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        NexusSettingRow(
            title = "Tema manuale e Theme Studio",
            subtitle = "Palette, sfondo e tipografia",
            glyph = com.nexus.core.designsystem.NexusGlyphId.PALETTE,
            onClick = onOpenThemeStudio,
        )
    }
}

@Composable
private fun HourAdjuster(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text("Ora intera, 0-23", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NexusButton("−", { onChange((hour + 23) % 24) }, filled = false)
        Spacer(Modifier.width(10.dp))
        Text(
            "%02d:00".format(java.util.Locale.ITALIAN, hour),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        NexusButton("+", { onChange((hour + 1) % 24) }, filled = false)
    }
}

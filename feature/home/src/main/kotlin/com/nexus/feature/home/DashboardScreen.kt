package com.nexus.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.common.NexusFormatters
import com.nexus.core.data.AppCatalogRepository
import com.nexus.core.data.CalendarRepository
import com.nexus.core.data.NotificationBadges
import com.nexus.core.data.UsageRepository
import com.nexus.core.data.WeatherRepository
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusDetailScreen
import com.nexus.core.designsystem.NexusDivider
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusIconButton
import com.nexus.core.designsystem.NexusLoading
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusPanel
import com.nexus.core.designsystem.NexusSectionHeader
import com.nexus.core.designsystem.NexusStat
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.designsystem.NexusColors
import com.nexus.core.model.NexusNavigator
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.ToolDestination

@Composable
fun DashboardScreen(
    settings: NexusSettings,
    catalog: AppCatalogRepository,
    usageRepository: UsageRepository,
    weatherRepository: WeatherRepository,
    calendarRepository: CalendarRepository,
    isDefaultLauncher: Boolean,
    navigator: NexusNavigator,
    onBack: () -> Unit,
) {
    val apps by catalog.apps.collectAsStateWithLifecycle()
    val usage by usageRepository.usage.collectAsStateWithLifecycle()
    val weather by weatherRepository.state.collectAsStateWithLifecycle()
    val events by calendarRepository.events.collectAsStateWithLifecycle()
    val battery = rememberBatteryInfo()
    val now = rememberMinuteNow()
    val context = LocalContext.current
    val badgesEnabled = NotificationBadges.isListenerEnabled(context)
    val upcoming = upcomingEvents(events, now, 3)
    val totalLaunches = usage.values.sumOf { it.launches }

    NexusDetailScreen(
        title = "Dashboard Nexus",
        subtitle = "Panoramica",
        onBack = onBack,
        actions = {
            NexusIconButton(NexusGlyphId.SETTINGS, "Impostazioni", {
                navigator.openSettings(SettingsTargets.ROOT)
            })
        },
    ) {
        if (settings.availableVersion.isNotBlank()) {
            NexusNotice(
                message = "Aggiornamento disponibile: ${settings.availableVersion}",
                tone = NexusNoticeTone.INFO,
                actionLabel = "Dettagli",
                onAction = { navigator.openSettings(SettingsTargets.UPDATES) },
            )
        }
        NexusPanel {
            NexusSectionHeader("Adesso", greeting(now))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    NexusFormatters.time(now, settings.use24HourClock),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    NexusFormatters.dateLong(now).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            NexusDivider()
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NexusStat("App installate", apps.size.toString(), accent = true)
                NexusStat("Pagine Home", settings.homePages.toString())
                NexusStat("Avvii registrati", totalLaunches.toString())
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (isDefaultLauncher) "Nexus è il launcher predefinito" else "Nexus non è ancora il launcher predefinito",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDefaultLauncher) NexusColors.success else NexusColors.warning,
            )
        }

        NexusPanel {
            NexusSectionHeader("Meteo", weather.city.ifBlank { "Posizione Nexus" })
            Spacer(Modifier.height(10.dp))
            val report = weather.report
            if (weather.loading && report == null) {
                NexusLoading("Aggiornamento…")
            } else if (report == null) {
                Text(
                    weather.error ?: "Nessun dato meteo disponibile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                NexusButton("Apri Meteo", { navigator.openTools(ToolDestination.WEATHER) }, filled = false)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        NexusFormatters.temperature(report.temperatureC),
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(report.kind.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Percepita ${NexusFormatters.temperature(report.feelsLikeC)} · Max ${NexusFormatters.temperature(report.highC)} · Min ${NexusFormatters.temperature(report.lowC)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexusTag(if (weather.offline) "Offline" else "Aggiornato")
                    Spacer(Modifier.width(8.dp))
                    Text(
                        NexusFormatters.relative(report.fetchedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    NexusButton("Dettagli", { navigator.openTools(ToolDestination.WEATHER) }, filled = false)
                }
                val weatherError = weather.error
                if (weatherError != null && !weather.offline) {
                    Spacer(Modifier.height(8.dp))
                    NexusNotice(weatherError, tone = NexusNoticeTone.WARNING)
                }
            }
        }

        NexusPanel {
            NexusSectionHeader("Batteria", if (battery.charging) "In carica" else "A batteria")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (battery.percent >= 0) "${battery.percent}%" else "—",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (battery.percent > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth(battery.percent / 100f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(
                                        if (battery.charging) NexusColors.success
                                        else MaterialTheme.colorScheme.primary,
                                    ),
                            )
                        }
                    }
                    if (battery.temperatureC != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            String.format(java.util.Locale.ITALIAN, "Temperatura %.1f °C", battery.temperatureC),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        NexusPanel {
            NexusSectionHeader("Calendario", if (upcoming.isEmpty()) "Nessun evento" else "Prossimi eventi")
            Spacer(Modifier.height(8.dp))
            if (upcoming.isEmpty()) {
                Text(
                    "Gli eventi Nexus sono salvati localmente. Creane uno dal calendario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                upcoming.forEach { event ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${NexusFormatters.dayLabel(event.startAt, now)} · ${NexusFormatters.time(event.startAt, settings.use24HourClock)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        NexusIconButton(NexusGlyphId.FORWARD, "Apri calendario", {
                            navigator.openTools(ToolDestination.CALENDAR)
                        })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            NexusButton(
                "Apri calendario",
                { navigator.openTools(ToolDestination.CALENDAR) },
                filled = false,
            )
        }

        NexusPanel {
            NexusSectionHeader("Stato sistema", "Privacy e collegamenti")
            Spacer(Modifier.height(8.dp))
            Text(
                "Pallini notifiche: ${if (badgesEnabled) "attivi" else "non autorizzati"} · Statistiche utilizzo: ${if (settings.usageStatsEnabled) "attive" else "disattivate"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            QuickLinksGrid(
                items = listOf(
                    QuickLinkItem(NexusGlyphId.SEARCH, "Cerca") { navigator.openSearch("") },
                    QuickLinkItem(NexusGlyphId.APPS, "App") { navigator.openDrawer() },
                    QuickLinkItem(NexusGlyphId.DASHBOARD, "Tools") { navigator.openTools(null) },
                    QuickLinkItem(NexusGlyphId.AUTOMATION, "Automazioni") {
                        navigator.openSettings(SettingsTargets.AUTOMATIONS)
                    },
                    QuickLinkItem(NexusGlyphId.BACKUP, "Backup") {
                        navigator.openSettings(SettingsTargets.BACKUP)
                    },
                    QuickLinkItem(NexusGlyphId.DIAGNOSTICS, "Diagnostica") {
                        navigator.openSettings(SettingsTargets.DIAGNOSTICS)
                    },
                    QuickLinkItem(NexusGlyphId.DOWNLOAD, "Aggiorna") {
                        navigator.openSettings(SettingsTargets.UPDATES)
                    },
                    QuickLinkItem(NexusGlyphId.SETTINGS, "Impostazioni") {
                        navigator.openSettings(SettingsTargets.ROOT)
                    },
                    QuickLinkItem(NexusGlyphId.SHIELD, "Privacy") {
                        navigator.openSettings(SettingsTargets.PRIVACY)
                    },
                ),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NexusGlyph(NexusGlyphId.SHIELD, contentDescription = null, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                "Tutti i dati restano su questo dispositivo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

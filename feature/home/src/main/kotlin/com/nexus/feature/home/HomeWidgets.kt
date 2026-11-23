package com.nexus.feature.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.core.common.NexusFormatters
import com.nexus.core.data.WeatherUiState
import com.nexus.core.designsystem.LocalNexusMetrics
import com.nexus.core.designsystem.NexusAppIcon
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusIconButton
import com.nexus.core.designsystem.NexusLoading
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.designsystem.NexusColors
import com.nexus.core.model.CalendarEvent
import com.nexus.core.model.HomeItem
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.ToolDestination
import com.nexus.core.model.UsageStat
import com.nexus.core.model.WidgetSpan
import com.nexus.core.model.WidgetType
import com.nexus.core.model.NexusNavigator
import com.nexus.core.model.LauncherApp
import androidx.compose.foundation.lazy.LazyRow
import com.nexus.core.designsystem.NexusAppIcon as NexusIcon

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WidgetFrame(
    span: WidgetSpan,
    onOpen: (() -> Unit)?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = if (onOpen != null || onEdit != null) {
        Modifier.combinedClickable(
            onClickLabel = "Apri",
            onLongClickLabel = "Modifica widget",
            onClick = { onOpen?.invoke() },
            onLongClick = onEdit,
        )
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(span.heightDp.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                RoundedCornerShape(26.dp),
            )
            .then(interaction)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun WidgetHeader(
    kicker: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            kicker.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun NexusWidgetContent(
    item: HomeItem,
    settings: NexusSettings,
    now: Long,
    battery: BatteryInfo,
    weather: WeatherUiState,
    events: List<CalendarEvent>,
    navigator: NexusNavigator,
    onRefreshWeather: () -> Unit,
    onEdit: () -> Unit,
) {
    val type = runCatching { WidgetType.valueOf(item.key) }.getOrDefault(WidgetType.DASHBOARD)
    when (type) {
        WidgetType.CLOCK -> ClockWidget(item, settings, now, onEdit)
        WidgetType.DASHBOARD -> DashboardWidget(item, settings, now, battery, weather, events, navigator, onEdit)
        WidgetType.WEATHER -> WeatherWidget(item, weather, navigator, onRefreshWeather, onEdit)
        WidgetType.BATTERY -> BatteryWidget(item, battery, onEdit)
        WidgetType.CALENDAR -> CalendarWidget(item, events, now, navigator, onEdit)
    }
}

@Composable
private fun ClockWidget(item: HomeItem, settings: NexusSettings, now: Long, onEdit: () -> Unit) {
    WidgetFrame(span = item.span, onOpen = null, onEdit = onEdit) {
        WidgetHeader("Nexus · orologio")
        Text(
            NexusFormatters.time(now, settings.use24HourClock),
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            NexusFormatters.dateLong(now).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.span != WidgetSpan.SMALL) {
            Spacer(Modifier.weight(1f))
            Text(
                "${greeting(now)}. Nexus è la tua Home.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DashboardWidget(
    item: HomeItem,
    settings: NexusSettings,
    now: Long,
    battery: BatteryInfo,
    weather: WeatherUiState,
    events: List<CalendarEvent>,
    navigator: NexusNavigator,
    onEdit: () -> Unit,
) {
    WidgetFrame(
        span = item.span,
        onOpen = { navigator.openDashboard() },
        onEdit = onEdit,
    ) {
        WidgetHeader("Nexus · dashboard") {
            NexusTag("Live")
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                NexusFormatters.time(now, settings.use24HourClock),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(
                    NexusFormatters.dateLong(now).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    greeting(now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric(
                label = "METEO",
                value = weather.report?.let { NexusFormatters.temperature(it.temperatureC) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            MiniMetric(
                label = "BATTERIA",
                value = if (battery.percent >= 0) "${battery.percent}%" else "—",
                modifier = Modifier.weight(1f),
            )
            MiniMetric(
                label = "PROSSIMO",
                value = events.firstOrNull()?.let { NexusFormatters.time(it.startAt, settings.use24HourClock) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        if (item.span != WidgetSpan.SMALL) {
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DashboardShortcut(NexusGlyphId.SEARCH, "Cerca") { navigator.openSearch("") }
                DashboardShortcut(NexusGlyphId.APPS, "App") { navigator.openDrawer() }
                DashboardShortcut(NexusGlyphId.CALCULATOR, "Tools") { navigator.openTools(null) }
                DashboardShortcut(NexusGlyphId.SETTINGS, "Setup") { navigator.openSettings(com.nexus.core.model.SettingsTargets.ROOT) }
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun DashboardShortcut(glyph: NexusGlyphId, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NexusGlyph(glyph, contentDescription = null, size = 14.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun WeatherWidget(
    item: HomeItem,
    weather: WeatherUiState,
    navigator: NexusNavigator,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
) {
    WidgetFrame(
        span = item.span,
        onOpen = { navigator.openTools(ToolDestination.WEATHER) },
        onEdit = onEdit,
    ) {
        WidgetHeader("Meteo Nexus") {
            NexusIconButton(NexusGlyphId.REFRESH, "Aggiorna meteo", onRefresh)
        }
        val report = weather.report
        if (report == null && weather.loading) {
            NexusLoading("Recupero meteo…")
        } else if (report == null) {
            Text(
                weather.error ?: "Meteo non ancora disponibile",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusButton("Apri Meteo", { navigator.openTools(ToolDestination.WEATHER) }, filled = false)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    NexusFormatters.temperature(report.temperatureC),
                    style = MaterialTheme.typography.displayMedium,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(report.kind.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Max ${NexusFormatters.temperature(report.highC)} · Min ${NexusFormatters.temperature(report.lowC)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${weather.city.ifBlank { report.city }} · ${NexusFormatters.relative(report.fetchedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val weatherError = weather.error
            if (weatherError != null) {
                Text(
                    if (weather.offline) "Offline: dati salvati" else weatherError,
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusColors.warning,
                )
            }
        }
    }
}

@Composable
private fun BatteryWidget(item: HomeItem, battery: BatteryInfo, onEdit: () -> Unit) {
    val context = LocalContext.current
    WidgetFrame(
        span = item.span,
        onOpen = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        },
        onEdit = onEdit,
    ) {
        WidgetHeader("Batteria") {
            NexusTag(if (battery.charging) "In carica" else "A batteria")
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (battery.percent >= 0) "${battery.percent}%" else "—",
                style = MaterialTheme.typography.displaySmall,
            )
            if (battery.temperatureC != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format(java.util.Locale.ITALIAN, "%.1f °C", battery.temperatureC),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
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
                            if (battery.charging) NexusColors.success else MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }
        if (item.span != WidgetSpan.SMALL) {
            Spacer(Modifier.weight(1f))
            Text(
                "Apri le impostazioni batteria di sistema",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarWidget(
    item: HomeItem,
    events: List<CalendarEvent>,
    now: Long,
    navigator: NexusNavigator,
    onEdit: () -> Unit,
) {
    WidgetFrame(
        span = item.span,
        onOpen = { navigator.openTools(ToolDestination.CALENDAR) },
        onEdit = onEdit,
    ) {
        WidgetHeader("Calendario Nexus") {
            NexusTag("${events.count { NexusFormatters.dayLabel(it.startAt, now) == "Oggi" }} oggi")
        }
        val upcoming = upcomingEvents(events, now, if (item.span == WidgetSpan.SMALL) 1 else 2)
        if (upcoming.isEmpty()) {
            Text(
                "Nessun evento in programma.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusButton(
                "Apri calendario",
                { navigator.openTools(ToolDestination.CALENDAR) },
                filled = false,
            )
        } else {
            upcoming.forEach { event ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { navigator.openTools(ToolDestination.CALENDAR) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        NexusFormatters.time(event.startAt),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            event.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            NexusFormatters.dayLabel(event.startAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

internal data class QuickLinkItem(
    val glyph: NexusGlyphId,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
internal fun QuickLinksGrid(
    items: List<QuickLinkItem>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .clickable(onClick = item.onClick)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NexusGlyph(
                            item.glyph,
                            contentDescription = null,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

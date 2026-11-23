package com.nexus.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexus.core.data.AppCatalogRepository
import com.nexus.core.model.CalendarEvent
import com.nexus.core.model.LauncherApp
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.UsageStat
import kotlinx.coroutines.delay

internal data class BatteryInfo(
    val percent: Int = -1,
    val charging: Boolean = false,
    val temperatureC: Float? = null,
)

@Composable
internal fun rememberBatteryInfo(): BatteryInfo {
    val context = LocalContext.current
    var info by remember { mutableStateOf(readBattery(context, null)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                info = readBattery(context, intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) info = readBattery(context, sticky)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return info
}

private fun readBattery(context: Context, intent: Intent?): BatteryInfo {
    val batteryIntent = intent
        ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return BatteryInfo()
    val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
    return BatteryInfo(
        percent = percent,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0,
        temperatureC = if (temperature == Int.MIN_VALUE) null else temperature / 10f,
    )
}

@Composable
internal fun rememberMinuteNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            now = current
            delay((60_000L - current % 60_000L).coerceAtLeast(200L))
        }
    }
    return now
}

@Composable
internal fun rememberAppIcon(
    catalog: AppCatalogRepository,
    key: String,
    sizeDp: Int,
): ImageBitmap? {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key, sizePx) {
        value = catalog.icon(key, sizePx)?.asImageBitmap()
    }
    return bitmap
}

internal fun effectiveDock(
    settings: NexusSettings,
    apps: List<LauncherApp>,
    usage: Map<String, UsageStat>,
): List<LauncherApp> {
    val byKey = apps.associateBy { it.key }
    val configured = settings.dockApps.mapNotNull { byKey[it] }.distinctBy { it.key }
    val fallback = apps
        .sortedWith(
            compareByDescending<LauncherApp> { usage[it.key]?.launches ?: 0L }
                .thenBy { it.label.lowercase() },
        )
        .filterNot { app -> configured.any { it.key == app.key } }
    return (configured + fallback).distinctBy { it.key }.take(settings.dockSize)
}

internal fun upcomingEvents(
    events: List<CalendarEvent>,
    now: Long,
    limit: Int = 2,
): List<CalendarEvent> = events
    .filter { it.startAt + it.durationMinutes * 60_000L >= now }
    .sortedBy { it.startAt }
    .take(limit)

internal fun greeting(now: Long): String {
    val hour = java.time.Instant.ofEpochMilli(now)
        .atZone(java.time.ZoneId.systemDefault())
        .hour
    return when (hour) {
        in 5..11 -> "Buongiorno"
        in 12..17 -> "Buon pomeriggio"
        else -> "Buonasera"
    }
}

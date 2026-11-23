package com.nexus.core.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexus.core.common.NexusJson
import com.nexus.core.model.AppBuildInfo
import com.nexus.core.model.NexusProject
import com.nexus.core.model.TimerSnapshot
import com.nexus.core.model.UpdateChannel
import com.nexus.core.model.UpdateInfo
import com.nexus.core.model.UsageStat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

object NexusBuildInfo {
    fun from(context: Context): AppBuildInfo {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return AppBuildInfo(
            versionName = info.versionName ?: "0.0.0",
            versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        )
    }
}

/* ------------------------------------------------------------------ */
/* Notification badges                                                 */
/* ------------------------------------------------------------------ */

/**
 * Counts active notifications per package. Notification content is never read or
 * stored: only package name and notification key are used, and only while the user
 * has explicitly granted notification listener access.
 */
object NotificationBadges {
    private val active = ConcurrentHashMap<String, MutableSet<String>>()
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    @Synchronized
    fun add(packageName: String, key: String) {
        active.getOrPut(packageName) { mutableSetOf() }.add(key)
        publish()
    }

    @Synchronized
    fun remove(packageName: String, key: String) {
        val keys = active[packageName] ?: return
        keys.remove(key)
        if (keys.isEmpty()) active.remove(packageName)
        publish()
    }

    @Synchronized
    fun replaceAll(entries: Map<String, Set<String>>) {
        active.clear()
        entries.forEach { (packageName, keys) ->
            if (keys.isNotEmpty()) active[packageName] = keys.toMutableSet()
        }
        publish()
    }

    @Synchronized
    fun clear() {
        active.clear()
        publish()
    }

    fun isListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openSettings(context: Context) {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun publish() {
        _counts.value = active.mapValues { it.value.size }.filterValues { it > 0 }
    }
}

class NexusNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        val active = activeNotifications ?: return
        NotificationBadges.replaceAll(
            active.groupBy { it.packageName }
                .mapValues { entry -> entry.value.map { it.key }.toSet() },
        )
    }

    override fun onListenerDisconnected() {
        NotificationBadges.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        NotificationBadges.add(notification.packageName, notification.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        NotificationBadges.remove(notification.packageName, notification.key)
    }
}

/* ------------------------------------------------------------------ */
/* Timer                                                               */
/* ------------------------------------------------------------------ */

class TimerRepository(context: Context) {
    private val file = File(context.filesDir, "timer_state.json")
    private val lock = Any()
    private val _snapshot = MutableStateFlow(readPersisted())
    val snapshot: StateFlow<TimerSnapshot?> = _snapshot.asStateFlow()

    fun start(durationSeconds: Int, label: String = "Timer Nexus"): TimerSnapshot {
        val safeSeconds = durationSeconds.coerceIn(1, 24 * 60 * 60)
        val snapshot = TimerSnapshot(
            targetAtEpochMillis = System.currentTimeMillis() + safeSeconds * 1000L,
            durationSeconds = safeSeconds,
            label = label.ifBlank { "Timer Nexus" },
        )
        synchronized(lock) {
            _snapshot.value = snapshot
            persist(snapshot)
        }
        return snapshot
    }

    fun cancel() {
        synchronized(lock) {
            _snapshot.value = null
            file.delete()
        }
    }

    fun refresh(): TimerSnapshot? {
        val current = _snapshot.value ?: return null
        if (current.targetAtEpochMillis <= System.currentTimeMillis()) {
            cancel()
            return null
        }
        return current
    }

    fun remainingMillis(now: Long = System.currentTimeMillis()): Long {
        val current = _snapshot.value ?: return 0L
        return (current.targetAtEpochMillis - now).coerceAtLeast(0L)
    }

    private fun readPersisted(): TimerSnapshot? = runCatching {
        if (!file.exists()) return null
        NexusJson.instance.decodeFromString<TimerSnapshot>(file.readText())
    }.getOrNull()

    private fun persist(snapshot: TimerSnapshot) {
        runCatching {
            atomicWrite(file, NexusJson.instance.encodeToString(snapshot).toByteArray(Charsets.UTF_8))
        }
    }
}

class TimerScheduler(private val context: Context) {
    fun schedule(snapshot: TimerSnapshot) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = timerPendingIntent()
        val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        runCatching {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(snapshot.targetAtEpochMillis, showIntent),
                pending,
            )
        }
    }

    fun cancel() {
        context.getSystemService(AlarmManager::class.java)?.cancel(timerPendingIntent())
    }

    private fun timerPendingIntent(): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
            .setAction(ACTION_TIMER_FIRED)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_TIMER_FIRED = "com.nexus.launcher.TIMER_FIRED"
        private const val REQUEST_CODE = 4101
    }
}

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val repository = TimerRepository(context)
        val finished = repository.snapshot.value
        repository.refresh()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        ensureTimerChannel(context, manager)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                1,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nexus_timer)
            .setContentTitle("Timer concluso")
            .setContentText(finished?.label ?: "Il timer è terminato")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        runCatching { manager.notify(TIMER_NOTIFICATION_ID, notification) }
    }

    private fun ensureTimerChannel(context: Context, manager: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TIMER_CHANNEL_ID,
                "Timer Nexus",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Avvisi al termine dei timer" }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TIMER_CHANNEL_ID = "nexus_timer"
        const val TIMER_NOTIFICATION_ID = 4102
    }
}

/* ------------------------------------------------------------------ */
/* Backup scheduling                                                   */
/* ------------------------------------------------------------------ */

class BackupScheduler(private val context: Context) {
    fun scheduleDaily() {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val trigger = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent())
        }
    }

    fun cancel() {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        4202,
        Intent(context, BackupAlarmReceiver::class.java).setAction(ACTION_BACKUP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_BACKUP = "com.nexus.launcher.BACKUP_TICK"
    }
}

class BackupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val buildInfo = NexusBuildInfo.from(appContext)
                val diagnostics = DiagnosticsRepository(appContext, buildInfo)
                val crypto = CryptoStore()
                val settings = SettingsRepository(appContext, diagnostics)
                val repository = BackupRepository(
                    context = appContext,
                    settings = settings,
                    notes = NotesRepository(appContext, crypto, diagnostics),
                    calendar = CalendarRepository(appContext, crypto, diagnostics),
                    usage = UsageRepository(appContext, crypto, settings, diagnostics),
                    crypto = crypto,
                    buildInfo = buildInfo,
                    diagnostics = diagnostics,
                )
                settings.awaitLoaded()
                if (settings.settings.value.autoBackupEnabled) repository.autoBackup()
            } finally {
                BackupScheduler(appContext).scheduleDaily()
                pending.finish()
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* App updates                                                         */
/* ------------------------------------------------------------------ */

@Serializable
private data class ReleaseAssetDto(
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val name: String = "",
)

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    val assets: List<ReleaseAssetDto> = emptyList(),
)

/**
 * Checks published GitHub releases for a newer version. There is no silent install:
 * the user is shown version, notes and a link opened through Android's browser /
 * package installer.
 */
class UpdateRepository(private val buildInfo: AppBuildInfo) {
    suspend fun check(channel: UpdateChannel): Result<UpdateInfo?> = kotlinx.coroutines.withContext(
        Dispatchers.IO,
    ) {
        runCatching {
            val raw = NexusHttp.get(NexusProject.RELEASES_API)
            val releases = NexusJson.instance.decodeFromString(
                ListSerializer(ReleaseDto.serializer()),
                raw,
            )
            val candidates = releases
                .filter { !it.draft }
                .filter { channel == UpdateChannel.BETA || !it.prerelease }
                .filter { it.tagName.isNotBlank() }
            val latest = candidates
                .sortedByDescending { com.nexus.core.common.NexusVersioning.parse(it.tagName) ?: com.nexus.core.common.SemanticVersion(0, 0, 0) }
                .firstOrNull { com.nexus.core.common.NexusVersioning.isNewer(it.tagName, buildInfo.versionName) }
                ?: return@runCatching null
            val download = latest.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            UpdateInfo(
                tagName = latest.tagName,
                version = latest.tagName.removePrefix("v"),
                title = latest.name.ifBlank { latest.tagName },
                notes = latest.body,
                htmlUrl = latest.htmlUrl.ifBlank { NexusProject.RELEASES_URL },
                downloadUrl = download?.downloadUrl,
                publishedAt = runCatching {
                    OffsetDateTime.parse(latest.publishedAt).toInstant().toEpochMilli()
                }.getOrDefault(0L),
                prerelease = latest.prerelease,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Small shared helpers                                                */
/* ------------------------------------------------------------------ */

object NexusStats {
    fun totalLaunches(usage: Map<String, UsageStat>): Long =
        usage.values.sumOf { it.launches }
}

package com.nexus.feature.automations

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexus.core.common.AutomationRules
import com.nexus.core.data.DiagnosticsRepository
import com.nexus.core.data.NexusBuildInfo
import com.nexus.core.data.SettingsRepository
import com.nexus.core.model.NexusSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Schedules the next appearance transition with AlarmManager. Alarms are inexact
 * ([AlarmManager.setAndAllowWhileIdle]) because a launcher has no exact-alarm privilege
 * and should not pretend otherwise: Android may delay the wake-up under Doze. The UI
 * also recomputes the effective appearance continuously while visible, so the change
 * is always correct as soon as Nexus is opened.
 */
class AutomationScheduler(private val context: Context) {
    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AutomationAlarmReceiver::class.java).setAction(ACTION_TRANSITION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun nextTransition(settings: NexusSettings, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        val epoch = AutomationRules.nextTransitionEpochMillis(now, settings)
        return epoch.takeIf { it != Long.MAX_VALUE }
    }

    fun schedule(settings: NexusSettings): Long? {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return null
        if (!settings.automationsEnabled) {
            manager.cancel(pendingIntent())
            return null
        }
        val next = nextTransition(settings) ?: return null
        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent())
        }
        return next
    }

    fun cancel() {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent())
    }

    companion object {
        const val ACTION_TRANSITION = "com.nexus.launcher.AUTOMATION_TRANSITION"
        private const val REQUEST_CODE = 4301
    }
}

class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val buildInfo = NexusBuildInfo.from(appContext)
                val diagnostics = DiagnosticsRepository(appContext, buildInfo)
                val settingsRepository = SettingsRepository(appContext, diagnostics)
                val settings = settingsRepository.awaitLoaded()
                AutomationScheduler(appContext).schedule(settings)
                diagnostics.info(
                    "AUTOMATION",
                    "Transizione oraria verificata; tema efficace ricalcolato all'apertura",
                )
            } finally {
                pending.finish()
            }
        }
    }
}

package com.nexus.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexus.core.data.BackupScheduler
import com.nexus.core.data.DiagnosticsRepository
import com.nexus.core.data.NexusBuildInfo
import com.nexus.core.data.SettingsRepository
import com.nexus.core.data.TimerRepository
import com.nexus.core.data.TimerScheduler
import com.nexus.feature.automations.AutomationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-arms the alarms Android drops on reboot or app update. This is required for
 * automations, the daily backup and a running timer to keep working.
 */
class BootReceiver : BroadcastReceiver() {
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
                val backupScheduler = BackupScheduler(appContext)
                if (settings.autoBackupEnabled) backupScheduler.scheduleDaily() else backupScheduler.cancel()
                TimerRepository(appContext).snapshot.value?.let { snapshot ->
                    if (snapshot.targetAtEpochMillis > System.currentTimeMillis()) {
                        TimerScheduler(appContext).schedule(snapshot)
                    }
                }
                diagnostics.info("BOOT", "Pianificazioni Nexus ripristinate")
            } finally {
                pending.finish()
            }
        }
    }
}

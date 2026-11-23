package com.nexus.launcher

import android.app.Application
import android.content.Context
import com.nexus.core.data.AppCatalogRepository
import com.nexus.core.data.BackupRepository
import com.nexus.core.data.CalendarRepository
import com.nexus.core.data.CryptoStore
import com.nexus.core.data.DiagnosticsRepository
import com.nexus.core.data.NotesRepository
import com.nexus.core.data.NexusBuildInfo
import com.nexus.core.data.SettingsRepository
import com.nexus.core.data.TimerRepository
import com.nexus.core.data.TimerScheduler
import com.nexus.core.data.UpdateRepository
import com.nexus.core.data.UsageRepository
import com.nexus.core.data.WeatherRepository

class NexusApplication : Application() {
    lateinit var container: NexusContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = NexusContainer(this)
        container.diagnostics.installCrashHandler()
        container.diagnostics.info(
            "APP",
            "Nexus ${container.buildInfo.versionName} (${container.buildInfo.versionCode}) avviato",
        )
    }
}

/** Manual dependency graph: every repository is a process-wide singleton. */
class NexusContainer(context: Context) {
    private val appContext = context.applicationContext
    val buildInfo = NexusBuildInfo.from(appContext)
    val diagnostics = DiagnosticsRepository(appContext, buildInfo)
    private val crypto = CryptoStore()
    val settings = SettingsRepository(appContext, diagnostics)
    val notes = NotesRepository(appContext, crypto, diagnostics)
    val calendar = CalendarRepository(appContext, crypto, diagnostics)
    val usage = UsageRepository(appContext, crypto, settings, diagnostics)
    val catalog = AppCatalogRepository(appContext)
    val weather = WeatherRepository(appContext, settings, diagnostics)
    val backup = BackupRepository(
        context = appContext,
        settings = settings,
        notes = notes,
        calendar = calendar,
        usage = usage,
        crypto = crypto,
        buildInfo = buildInfo,
        diagnostics = diagnostics,
    )
    val timer = TimerRepository(appContext)
    val timerScheduler = TimerScheduler(appContext)
    val updates = UpdateRepository(buildInfo)
}

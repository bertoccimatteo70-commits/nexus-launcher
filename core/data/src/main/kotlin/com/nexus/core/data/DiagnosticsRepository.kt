package com.nexus.core.data

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import com.nexus.core.model.AppBuildInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.ZonedDateTime

/**
 * Diagnostics are always local: one rotated log file plus crash reports under the
 * app's private storage. Nothing is uploaded. Users can inspect, export or clear them.
 */
class DiagnosticsRepository(
    private val context: Context,
    private val buildInfo: AppBuildInfo,
) {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
    )

    private val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
    private val logFile = File(directory, "nexus.log")
    private val crashDirectory = File(directory, "crashes").apply { mkdirs() }
    private val lock = Any()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Volatile
    private var crashHandlerInstalled = false

    init {
        _entries.value = readEntries().takeLast(300)
    }

    fun debug(tag: String, message: String) = log(Level.DEBUG, tag, message)

    fun info(tag: String, message: String) = log(Level.INFO, tag, message)

    fun warn(tag: String, message: String) = log(Level.WARN, tag, message)

    fun error(tag: String, message: String, failure: Throwable? = null) {
        log(Level.ERROR, tag, if (failure == null) message else "$message: ${failure.stackTraceToString()}")
    }

    fun log(level: Level, tag: String, message: String) {
        val entry = Entry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message.take(8_000).replace('\n', ' '),
        )
        synchronized(lock) {
            runCatching {
                rotateIfNeeded()
                logFile.appendText(format(entry) + "\n")
            }
        }
        _entries.update { (it + entry).takeLast(300) }
    }

    fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun crashReports(): List<File> =
        crashDirectory.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun reload() {
        _entries.value = readEntries().takeLast(300)
    }

    fun exportReport(uri: Uri): Result<Int> = runCatching {
        val text = buildString {
            appendLine("Nexus Launcher — report diagnostico locale")
            appendLine("App: ${buildInfo.versionName} (${buildInfo.versionCode}) debug=${buildInfo.isDebug}")
            appendLine(NexusDevice.summary(context))
            appendLine("Generato: ${ZonedDateTime.now()}")
            appendLine()
            appendLine("---- log (${readEntries().size} righe) ----")
            appendLine(readEntries().joinToString("\n") { format(it) })
            crashReports().take(3).forEach { file ->
                appendLine()
                appendLine("---- crash ${file.name} ----")
                appendLine(file.readText().take(20_000))
            }
        }
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("Impossibile aprire il file di destinazione")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        text.toByteArray(Charsets.UTF_8).size
    }

    fun exportLogs(uri: Uri): Result<Int> = runCatching {
        val text = readEntries().joinToString("\n") { format(it) }
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("Impossibile aprire il file di destinazione")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        text.length
    }

    fun clear() {
        synchronized(lock) {
            logFile.delete()
            crashDirectory.listFiles()?.forEach { it.delete() }
        }
        _entries.value = emptyList()
    }

    private fun rotateIfNeeded() {
        if (logFile.length() < MAX_LOG_BYTES) return
        val old = File(directory, "nexus.log.1")
        old.delete()
        logFile.renameTo(old)
    }

    private fun readEntries(): List<Entry> = runCatching {
        if (!logFile.exists()) emptyList()
        else logFile.readLines().mapNotNull(::parse).takeLast(MAX_MEMORY_ENTRIES)
    }.getOrDefault(emptyList())

    private fun format(entry: Entry): String =
        "${entry.timestamp}|${entry.level.name}|${entry.tag}|${entry.message}"

    private fun parse(line: String): Entry? {
        val parts = line.split('|', limit = 4)
        if (parts.size != 4) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        val level = runCatching { Level.valueOf(parts[1]) }.getOrNull() ?: return null
        return Entry(timestamp, level, parts[2], parts[3])
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val file = File(crashDirectory, "crash-${System.currentTimeMillis()}.txt")
        file.writeText(
            buildString {
                appendLine("Nexus Launcher crash report")
                appendLine("Time: ${ZonedDateTime.now()}")
                appendLine("App: ${buildInfo.versionName} (${buildInfo.versionCode})")
                appendLine(NexusDevice.summary(context))
                appendLine("Thread: ${thread.name}")
                appendLine()
                appendLine(throwable.stackTraceToString())
            },
        )
        log(Level.ERROR, "CRASH", "${throwable.javaClass.simpleName}: ${throwable.message}")
    }

    private companion object {
        const val MAX_LOG_BYTES = 512 * 1024L
        const val MAX_MEMORY_ENTRIES = 300
    }
}

object NexusDevice {
    fun summary(context: Context): String {
        val memory = runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            val info = ActivityManager.MemoryInfo()
            manager?.getMemoryInfo(info)
            val total = info.totalMem / (1024 * 1024)
            val lowRam = manager?.isLowRamDevice == true
            "RAM dispositivo: $total MB · lowRam=$lowRam"
        }.getOrDefault("")
        return buildString {
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine(memory)
        }.trim()
    }

    fun isLowRam(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
}

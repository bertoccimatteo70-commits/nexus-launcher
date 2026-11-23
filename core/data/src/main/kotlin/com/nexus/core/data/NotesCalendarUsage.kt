package com.nexus.core.data

import android.content.Context
import com.nexus.core.model.CalendarEvent
import com.nexus.core.model.Note
import com.nexus.core.model.UsageStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

class NotesRepository(
    context: Context,
    crypto: CryptoStore,
    private val diagnostics: DiagnosticsRepository,
) {
    private val store = SecureJsonStore<List<Note>>(
        directory = File(context.filesDir, "vault"),
        fileName = "notes.bin",
        alias = "nexus-notes",
        crypto = crypto,
        serializer = ListSerializer(Note.serializer()),
    )
    private val mutex = Mutex()
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    init {
        _notes.value = load()
    }

    fun note(id: String): Note? = _notes.value.firstOrNull { it.id == id }

    suspend fun upsert(note: Note) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = (_notes.value.filterNot { it.id == note.id } + note)
                .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
            _notes.value = next
            persist(next)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = _notes.value.filterNot { it.id == id }
            _notes.value = next
            persist(next)
        }
    }

    suspend fun replaceAll(notes: List<Note>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = notes.distinctBy { it.id }
                .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
            _notes.value = next
            persist(next)
        }
    }

    private fun load(): List<Note> = try {
        store.read().orEmpty()
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    } catch (failure: Exception) {
        diagnostics.error("NOTES", "Archivio note illeggibile, accantonato", failure)
        store.quarantine()
        emptyList()
    }

    private fun persist(notes: List<Note>) {
        runCatching { store.write(notes) }
            .onFailure { diagnostics.error("NOTES", "Salvataggio note fallito", it) }
    }
}

class CalendarRepository(
    context: Context,
    crypto: CryptoStore,
    private val diagnostics: DiagnosticsRepository,
) {
    private val store = SecureJsonStore<List<CalendarEvent>>(
        directory = File(context.filesDir, "vault"),
        fileName = "calendar.bin",
        alias = "nexus-calendar",
        crypto = crypto,
        serializer = ListSerializer(CalendarEvent.serializer()),
    )
    private val mutex = Mutex()
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    init {
        _events.value = load()
    }

    suspend fun upsert(event: CalendarEvent) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = (_events.value.filterNot { it.id == event.id } + event).sortedBy { it.startAt }
            _events.value = next
            persist(next)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = _events.value.filterNot { it.id == id }
            _events.value = next
            persist(next)
        }
    }

    suspend fun replaceAll(events: List<CalendarEvent>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = events.distinctBy { it.id }.sortedBy { it.startAt }
            _events.value = next
            persist(next)
        }
    }

    fun nextEvent(now: Long = System.currentTimeMillis()): CalendarEvent? =
        _events.value.firstOrNull { it.startAt + it.durationMinutes * 60_000L >= now }

    private fun load(): List<CalendarEvent> = try {
        store.read().orEmpty().sortedBy { it.startAt }
    } catch (failure: Exception) {
        diagnostics.error("CALENDAR", "Archivio calendario illeggibile, accantonato", failure)
        store.quarantine()
        emptyList()
    }

    private fun persist(events: List<CalendarEvent>) {
        runCatching { store.write(events) }
            .onFailure { diagnostics.error("CALENDAR", "Salvataggio calendario fallito", it) }
    }
}

class UsageRepository(
    context: Context,
    crypto: CryptoStore,
    private val settings: SettingsRepository,
    private val diagnostics: DiagnosticsRepository,
) {
    private val store = SecureJsonStore<Map<String, UsageStat>>(
        directory = File(context.filesDir, "vault"),
        fileName = "usage.bin",
        alias = "nexus-usage",
        crypto = crypto,
        serializer = MapSerializer(String.serializer(), UsageStat.serializer()),
    )
    private val mutex = Mutex()
    private val _usage = MutableStateFlow<Map<String, UsageStat>>(emptyMap())
    val usage: StateFlow<Map<String, UsageStat>> = _usage.asStateFlow()

    init {
        _usage.value = load()
    }

    suspend fun recordLaunch(appKey: String) = withContext(Dispatchers.IO) {
        if (!settings.settings.value.usageStatsEnabled) return@withContext
        mutex.withLock {
            val previous = _usage.value[appKey]
            val next = _usage.value + (
                appKey to UsageStat(
                    key = appKey,
                    launches = (previous?.launches ?: 0L) + 1L,
                    lastLaunchedAt = System.currentTimeMillis(),
                )
                )
            _usage.value = next
            persist(next)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _usage.value = emptyMap()
            persist(emptyMap())
        }
    }

    suspend fun replaceAll(stats: List<UsageStat>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = stats.associateBy { it.key }
            _usage.value = next
            persist(next)
        }
    }

    private fun load(): Map<String, UsageStat> = try {
        store.read().orEmpty()
    } catch (failure: Exception) {
        diagnostics.error("USAGE", "Statistiche illeggibili, azzerate", failure)
        store.quarantine()
        emptyMap()
    }

    private fun persist(stats: Map<String, UsageStat>) {
        runCatching { store.write(stats) }
            .onFailure { diagnostics.error("USAGE", "Salvataggio statistiche fallito", it) }
    }
}

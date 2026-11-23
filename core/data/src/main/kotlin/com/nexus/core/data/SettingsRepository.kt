package com.nexus.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexus.core.common.SettingsCodec
import com.nexus.core.model.NexusSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val Context.nexusSettingsDataStore: DataStore<Preferences> by preferencesDataStore("nexus_settings")

/**
 * Single source of truth for every setting. Updates go through a transactional
 * DataStore edit; a blob that cannot be decoded is reported, archived by diagnostics
 * and replaced with safe defaults rather than crashing the launcher.
 */
class SettingsRepository(
    private val context: Context,
    private val diagnostics: DiagnosticsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settings = MutableStateFlow(NexusSettings())
    val settings: StateFlow<NexusSettings> = _settings.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _corruption = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val corruption: SharedFlow<String> = _corruption.asSharedFlow()

    @Volatile
    private var repairing = false

    init {
        scope.launch {
            context.nexusSettingsDataStore.data
                .catch { failure ->
                    diagnostics.error("SETTINGS", "Errore DataStore", failure)
                    emit(emptyPreferences())
                }
                .collect { preferences ->
                    val raw = preferences[SETTINGS_KEY]
                    if (raw == null) {
                        _settings.value = NexusSettings()
                        _isLoaded.value = true
                        return@collect
                    }
                    SettingsCodec.decode(raw)
                        .onSuccess {
                            _settings.value = it
                            _isLoaded.value = true
                            repairing = false
                        }
                        .onFailure { failure ->
                            diagnostics.error("SETTINGS", "Configurazione danneggiata, ripristino default", failure)
                            _corruption.tryEmit(failure.message ?: "Configurazione danneggiata")
                            _settings.value = NexusSettings()
                            _isLoaded.value = true
                            if (!repairing) {
                                repairing = true
                                runCatching {
                                    context.nexusSettingsDataStore.edit { prefs ->
                                        prefs[SETTINGS_KEY] = SettingsCodec.encode(NexusSettings())
                                    }
                                }
                            }
                        }
                }
        }
    }

    suspend fun update(transform: (NexusSettings) -> NexusSettings) {
        context.nexusSettingsDataStore.edit { preferences ->
            val current = preferences[SETTINGS_KEY]
                ?.let { SettingsCodec.decode(it).getOrNull() }
                ?: _settings.value
            preferences[SETTINGS_KEY] = SettingsCodec.encode(transform(current).sanitized())
        }
    }

    fun updateAsync(transform: (NexusSettings) -> NexusSettings) {
        scope.launch {
            runCatching { update(transform) }
                .onFailure { diagnostics.error("SETTINGS", "Aggiornamento impostazioni fallito", it) }
        }
    }

    suspend fun replace(next: NexusSettings) {
        update { next }
    }

    suspend fun reset() {
        update { NexusSettings() }
    }

    /** Waits briefly for the first DataStore emission (used by background receivers). */
    suspend fun awaitLoaded(): NexusSettings {
        if (!_isLoaded.value) {
            withTimeoutOrNull(3_000) { isLoaded.first { it } }
        }
        return _settings.value
    }

    private companion object {
        val SETTINGS_KEY = stringPreferencesKey("settings_json")
    }
}

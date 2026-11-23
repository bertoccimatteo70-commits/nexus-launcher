package com.nexus.core.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.nexus.core.common.GeoPlace
import com.nexus.core.common.NexusJson
import com.nexus.core.common.WeatherApi
import com.nexus.core.model.WeatherForecast
import com.nexus.core.model.WeatherMode
import com.nexus.core.model.WeatherReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException

data class WeatherUiState(
    val loading: Boolean = false,
    val report: WeatherReport? = null,
    val forecast: WeatherForecast? = null,
    val city: String = "",
    val error: String? = null,
    val offline: Boolean = false,
)

@Serializable
private data class CachedWeather(
    val report: WeatherReport,
    val forecast: WeatherForecast? = null,
)

/**
 * Weather is the only feature that talks to the internet. It uses Open-Meteo (no API
 * key, no account) and always keeps the last successful reading on disk so the Home
 * keeps working when the device is offline or the provider is unreachable.
 */
class WeatherRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val diagnostics: DiagnosticsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()
    private val cacheFile = File(context.filesDir, "weather_cache.json")
    private val cityCache = mutableMapOf<String, GeoPlace>()
    private var refreshJob: Job? = null

    init {
        loadCache()
    }

    fun refresh(force: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch { runRefresh(force) }
    }

    suspend fun lookupCity(query: String): Result<List<GeoPlace>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching emptyList()
            if (!NexusHttp.isOnline(context)) error("Nessuna connessione disponibile")
            val raw = NexusHttp.get(WeatherApi.geocodingUrl(query))
            WeatherApi.parseGeocoding(raw).also { places ->
                places.forEach { cityCache[it.name] = it }
            }
        }
    }

    suspend fun useCity(place: GeoPlace) {
        settings.update {
            it.copy(weatherMode = WeatherMode.CITY, weatherCity = place.name)
        }
        cityCache[place.name] = place
        refresh(force = true)
    }

    suspend fun useAutomaticLocation() {
        settings.update { it.copy(weatherMode = WeatherMode.AUTO) }
        refresh(force = true)
    }

    private suspend fun runRefresh(force: Boolean) {
        val snapshot = settings.settings.value
        val cacheMinutes = snapshot.weatherCacheMinutes.coerceIn(5, 180)
        val cached = _state.value.report
        val cacheFresh = cached != null &&
            System.currentTimeMillis() - cached.fetchedAt < cacheMinutes * 60_000L
        if (!force && cacheFresh) {
            _state.update { it.copy(loading = false, offline = false, error = null) }
            return
        }

        _state.update { it.copy(loading = true, error = null) }
        val online = NexusHttp.isOnline(context)
        if (!online) {
            _state.update {
                it.copy(
                    loading = false,
                    offline = true,
                    error = if (it.report != null) {
                        "Sei offline: mostro l'ultimo meteo salvato"
                    } else {
                        "Sei offline: il meteo non è disponibile"
                    },
                )
            }
            return
        }

        try {
            val coordinates = resolveCoordinates(snapshot.weatherMode, snapshot.weatherCity)
                ?: throw IOException("Posizione non disponibile: imposta una città nelle impostazioni meteo")
            val cityLabel = snapshot.weatherCity.ifBlank { "Posizione attuale" }
            val payload = NexusHttp.get(
                WeatherApi.forecastUrl(coordinates.first, coordinates.second),
                userAgent = "NexusLauncher",
            )
            val parsed = WeatherApi.parseForecast(payload, cityLabel, System.currentTimeMillis())
                .getOrThrow()
            _state.value = WeatherUiState(
                loading = false,
                report = parsed.report,
                forecast = parsed.forecast,
                city = cityLabel,
                error = null,
                offline = false,
            )
            saveCache(parsed.report, parsed.forecast)
        } catch (failure: Exception) {
            diagnostics.warn("WEATHER", "Aggiornamento meteo fallito: ${failure.message}")
            _state.update {
                it.copy(
                    loading = false,
                    error = if (it.report != null) {
                        "Meteo non aggiornato: ${failure.message ?: "errore di rete"}"
                    } else {
                        "Meteo non disponibile: ${failure.message ?: "errore di rete"}"
                    },
                    offline = false,
                )
            }
        }
    }

    private suspend fun resolveCoordinates(mode: WeatherMode, city: String): Pair<Double, Double>? {
        if (mode == WeatherMode.AUTO) {
            currentLocation()?.let { return it }
        }
        if (city.isBlank()) return null
        cityCache[city]?.let { return it.latitude to it.longitude }
        val raw = NexusHttp.get(WeatherApi.geocodingUrl(city))
        val place = WeatherApi.parseGeocoding(raw).firstOrNull() ?: return null
        cityCache[city] = place
        return place.latitude to place.longitude
    }

    @Suppress("MissingPermission")
    private fun currentLocation(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val location = runCatching {
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }.getOrNull() ?: return null
        return location.latitude to location.longitude
    }

    private fun loadCache() {
        runCatching {
            if (!cacheFile.exists()) return
            val cached = NexusJson.instance.decodeFromString<CachedWeather>(cacheFile.readText())
            _state.value = WeatherUiState(
                loading = false,
                report = cached.report.copy(fromCache = true),
                forecast = cached.forecast,
                city = cached.report.city,
            )
        }.onFailure {
            diagnostics.warn("WEATHER", "Cache meteo illeggibile: ${it.message}")
        }
    }

    private fun saveCache(report: WeatherReport, forecast: WeatherForecast) {
        runCatching {
            atomicWrite(
                cacheFile,
                NexusJson.instance.encodeToString(CachedWeather(report, forecast))
                    .toByteArray(Charsets.UTF_8),
            )
        }.onFailure {
            diagnostics.warn("WEATHER", "Salvataggio cache meteo fallito: ${it.message}")
        }
    }
}

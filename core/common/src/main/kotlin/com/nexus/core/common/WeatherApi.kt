package com.nexus.core.common

import com.nexus.core.model.WeatherForecast
import com.nexus.core.model.WeatherForecastDay
import com.nexus.core.model.WeatherKind
import com.nexus.core.model.WeatherReport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.time.LocalDate

data class GeoPlace(
    val name: String,
    val admin: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
) {
    val displayName: String get() = listOf(name, admin, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

data class ParsedWeather(
    val report: WeatherReport,
    val forecast: WeatherForecast,
)

@Serializable
private data class CurrentWeatherDto(
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("apparent_temperature") val apparent: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
)

@Serializable
private data class DailyWeatherDto(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCodes: List<Int> = emptyList(),
    @SerialName("temperature_2m_max") val maximums: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val minimums: List<Double> = emptyList(),
)

@Serializable
private data class ForecastResponseDto(
    val current: CurrentWeatherDto? = null,
    val daily: DailyWeatherDto? = null,
)

@Serializable
private data class GeoResultDto(
    val name: String = "",
    @SerialName("admin1") val admin: String = "",
    val country: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

@Serializable
private data class GeocodingResponseDto(
    val results: List<GeoResultDto> = emptyList(),
)

/** Open-Meteo integration. No API key and no account are required. */
object WeatherApi {
    const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun forecastUrl(latitude: Double, longitude: Double): String =
        FORECAST_URL +
            "?latitude=${"%.4f".format(java.util.Locale.US, latitude)}" +
            "&longitude=${"%.4f".format(java.util.Locale.US, longitude)}" +
            "&current=temperature_2m,apparent_temperature,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=4"

    fun geocodingUrl(city: String): String =
        "$GEOCODING_URL?name=${URLEncoder.encode(city, "UTF-8")}&count=5&language=it&format=json"

    fun parseForecast(
        payload: String,
        city: String,
        nowEpochMillis: Long,
        fromCache: Boolean = false,
    ): Result<ParsedWeather> = runCatching {
        val response = json.decodeFromString(ForecastResponseDto.serializer(), payload)
        val current = response.current ?: error("Risposta meteo priva dei dati correnti")
        val temperature = current.temperature ?: error("Temperatura non disponibile")
        val code = current.weatherCode ?: -1
        val daily = response.daily
        val todayHigh = daily?.maximums?.firstOrNull() ?: temperature
        val todayLow = daily?.minimums?.firstOrNull() ?: temperature
        val report = WeatherReport(
            city = city,
            temperatureC = temperature,
            feelsLikeC = current.apparent ?: temperature,
            highC = todayHigh,
            lowC = todayLow,
            kind = kindFromWmo(code),
            fetchedAt = nowEpochMillis,
            fromCache = fromCache,
        )
        val days = buildList {
            if (daily != null) {
                val size = listOf(
                    daily.time.size,
                    daily.weatherCodes.size,
                    daily.maximums.size,
                    daily.minimums.size,
                ).min()
                for (index in 0 until size) {
                    val date = runCatching { LocalDate.parse(daily.time[index]) }.getOrNull() ?: continue
                    add(
                        WeatherForecastDay(
                            epochDay = date.toEpochDay(),
                            highC = daily.maximums[index],
                            lowC = daily.minimums[index],
                            kind = kindFromWmo(daily.weatherCodes[index]),
                        ),
                    )
                }
            }
        }
        ParsedWeather(
            report = report,
            forecast = WeatherForecast(city = city, days = days, fetchedAt = nowEpochMillis),
        )
    }

    fun parseGeocoding(payload: String): List<GeoPlace> = runCatching {
        json.decodeFromString(GeocodingResponseDto.serializer(), payload).results
            .filter { it.name.isNotBlank() }
            .map {
                GeoPlace(
                    name = it.name,
                    admin = it.admin,
                    country = it.country,
                    latitude = it.latitude,
                    longitude = it.longitude,
                )
            }
    }.getOrDefault(emptyList())

    fun kindFromWmo(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
        95, 96, 99 -> WeatherKind.THUNDER
        else -> WeatherKind.UNKNOWN
    }
}

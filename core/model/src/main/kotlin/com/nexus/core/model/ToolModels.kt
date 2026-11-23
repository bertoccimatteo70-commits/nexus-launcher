package com.nexus.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppCategory(val label: String) {
    GAME("Giochi"),
    SOCIAL("Social"),
    MEDIA("Media"),
    PRODUCTIVITY("Produttività"),
    SHOPPING("Shopping"),
    TRAVEL("Viaggi"),
    NEWS("Notizie"),
    TOOLS("Strumenti"),
    OTHER("Altre"),
}

/** A launchable activity discovered through [android.content.pm.LauncherApps]. */
data class LauncherApp(
    val key: String,
    val packageName: String,
    val activityName: String,
    val label: String,
    val category: AppCategory,
    val isSystem: Boolean,
    val userSerial: Long = 0L,
)

data class AppBuildInfo(
    val versionName: String,
    val versionCode: Long,
    val isDebug: Boolean,
)

/** A locally stored note. Persisted encrypted at rest by the data layer. */
@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
)

/** A Nexus calendar event (local, no system calendar permission required). */
@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val startAt: Long,
    val durationMinutes: Int,
    val location: String = "",
    val notes: String = "",
    val allDay: Boolean = false,
)

@Serializable
enum class WeatherKind(val label: String) {
    CLEAR("Sereno"),
    PARTLY_CLOUDY("Poco nuvoloso"),
    CLOUDY("Nuvoloso"),
    FOG("Nebbia"),
    DRIZZLE("Pioviggine"),
    RAIN("Pioggia"),
    SNOW("Neve"),
    THUNDER("Temporale"),
    UNKNOWN("Non disponibile"),
}

@Serializable
data class WeatherReport(
    val city: String,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val highC: Double,
    val lowC: Double,
    val kind: WeatherKind,
    val fetchedAt: Long,
    val fromCache: Boolean = false,
)

@Serializable
data class WeatherForecastDay(
    val epochDay: Long,
    val highC: Double,
    val lowC: Double,
    val kind: WeatherKind,
)

@Serializable
data class WeatherForecast(
    val city: String,
    val days: List<WeatherForecastDay>,
    val fetchedAt: Long,
)

@Serializable
data class UsageStat(
    val key: String,
    val launches: Long,
    val lastLaunchedAt: Long,
)

@Serializable
data class TimerSnapshot(
    val targetAtEpochMillis: Long,
    val durationSeconds: Int,
    val label: String,
    val pausedRemainingMillis: Long = 0L,
)

@Serializable
data class UpdateInfo(
    val tagName: String,
    val version: String,
    val title: String,
    val notes: String,
    val htmlUrl: String,
    val downloadUrl: String?,
    val publishedAt: Long,
    val prerelease: Boolean,
)

@Serializable
enum class ToolDestination(val label: String) {
    CALCULATOR("Calcolatrice"),
    NOTES("Note"),
    TIMER("Timer"),
    CALENDAR("Calendario"),
    WEATHER("Meteo"),
}

package com.nexus.core.common

import com.nexus.core.model.NexusSettings
import com.nexus.core.model.ThemeMode
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

data class EffectiveAppearance(
    val themeMode: ThemeMode,
    val animateBackground: Boolean,
    val reduceMotion: Boolean,
    val highContrast: Boolean,
    val nightActive: Boolean,
)

/**
 * Time based automations that only use APIs available to a normal launcher:
 * theme switching, night calm mode and contrast adjustments. The actual alarm
 * scheduling lives in the automations feature; this object stays pure and testable.
 */
object AutomationRules {
    fun isNight(time: LocalTime, nightStartHour: Int, dayStartHour: Int): Boolean {
        val nightStart = LocalTime.of(nightStartHour.coerceIn(0, 23), 0)
        val dayStart = LocalTime.of(dayStartHour.coerceIn(0, 23), 0)
        if (nightStart == dayStart) return false
        return if (nightStart < dayStart) {
            !time.isBefore(nightStart) && time.isBefore(dayStart)
        } else {
            !time.isBefore(nightStart) || time.isBefore(dayStart)
        }
    }

    fun effectiveAppearance(settings: NexusSettings, now: LocalDateTime): EffectiveAppearance {
        if (!settings.automationsEnabled) {
            return EffectiveAppearance(
                themeMode = settings.themeMode,
                animateBackground = settings.animatedBackground,
                reduceMotion = settings.reduceMotion,
                highContrast = settings.highContrast,
                nightActive = false,
            )
        }
        val night = isNight(now.toLocalTime(), settings.nightStartHour, settings.dayStartHour)
        val theme = when {
            night && settings.nightModeEnabled -> ThemeMode.DARK
            settings.autoThemeEnabled && night -> ThemeMode.DARK
            settings.autoThemeEnabled -> ThemeMode.LIGHT
            else -> settings.themeMode
        }
        val calm = night && settings.nightCalmEnabled
        return EffectiveAppearance(
            themeMode = theme,
            animateBackground = settings.animatedBackground && !calm,
            reduceMotion = settings.reduceMotion || calm,
            highContrast = settings.highContrast || (night && settings.nightHighContrast),
            nightActive = night,
        )
    }

    /** Epoch millis of the next hour at which the effective appearance can change. */
    fun nextTransitionEpochMillis(now: ZonedDateTime, settings: NexusSettings): Long {
        if (!settings.automationsEnabled) return Long.MAX_VALUE
        val candidates = mutableListOf<ZonedDateTime>()
        for (hour in listOf(settings.dayStartHour, settings.nightStartHour)) {
            for (dayOffset in 0L..1L) {
                val candidate = now.toLocalDate().plusDays(dayOffset)
                    .atTime(LocalTime.of(hour.coerceIn(0, 23), 0))
                    .atZone(now.zone)
                if (candidate.isAfter(now)) candidates += candidate
            }
        }
        return candidates.minOrNull()?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
    }
}

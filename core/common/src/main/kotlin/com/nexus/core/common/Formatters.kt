package com.nexus.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object NexusFormatters {
    private val locale = Locale.ITALIAN
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val time24 = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val time12 = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val dateLong = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val dateShort = DateTimeFormatter.ofPattern("d MMM", locale)
    private val dateWithYear = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val weekdayShort = DateTimeFormatter.ofPattern("EEE", locale)

    fun time(epochMillis: Long, use24Hour: Boolean = true): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(if (use24Hour) time24 else time12)

    fun clockNow(use24Hour: Boolean = true): String =
        LocalTime.now().format(if (use24Hour) time24 else time12)

    fun dateLong(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateLong)

    fun dateShort(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateShort)

    fun dateWithYear(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateWithYear)

    fun monthYear(year: Int, month: Int): String =
        LocalDate.of(year, month, 1).format(monthYear)

    fun weekdayShort(date: LocalDate): String = date.format(weekdayShort)

    fun dayLabel(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val target = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (target) {
            today -> "Oggi"
            today.plusDays(1) -> "Domani"
            today.minusDays(1) -> "Ieri"
            else -> target.format(dateShort)
        }
    }

    fun timer(remainingMillis: Long): String {
        val totalSeconds = remainingMillis.coerceAtLeast(0) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(locale, "%02d:%02d", minutes, seconds)
        }
    }

    fun durationHuman(seconds: Int): String {
        val minutes = seconds / 60
        val rest = seconds % 60
        return when {
            minutes == 0 -> "$rest s"
            rest == 0 -> "$minutes min"
            else -> "$minutes min $rest s"
        }
    }

    fun temperature(celsius: Double): String = String.format(locale, "%.0f°", celsius)

    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val delta = (now - epochMillis).coerceAtLeast(0)
        val minutes = delta / 60_000
        val hours = delta / 3_600_000
        val days = delta / 86_400_000
        return when {
            minutes < 1 -> "adesso"
            minutes < 60 -> "$minutes min fa"
            hours < 24 -> "$hours h fa"
            days < 7 -> "$days g fa"
            else -> dateShort(epochMillis)
        }
    }
}

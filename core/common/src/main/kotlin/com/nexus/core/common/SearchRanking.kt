package com.nexus.core.common

import com.nexus.core.model.AppCategory
import com.nexus.core.model.LauncherApp
import com.nexus.core.model.SearchHit
import com.nexus.core.model.SearchHitKind
import com.nexus.core.model.SearchProvider
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.ToolDestination
import com.nexus.core.model.UsageStat
import com.nexus.core.model.WebEngine
import java.net.URLEncoder
import java.text.Collator
import java.util.Locale
import kotlin.math.ln
import kotlin.math.min

/** Pure ranking logic for the Nexus global search, shared by UI and unit tests. */
object SearchRanking {
    const val MAX_RESULTS = 24

    private val collator: Collator = Collator.getInstance(Locale.ITALIAN)

    private data class ToolEntry(
        val destination: ToolDestination,
        val keywords: String,
    )

    private data class SettingsEntry(
        val title: String,
        val subtitle: String,
        val target: String,
        val keywords: String,
    )

    private val toolEntries = listOf(
        ToolEntry(ToolDestination.CALCULATOR, "calcolatrice calcolo calcola math espressione"),
        ToolEntry(ToolDestination.NOTES, "note appunti memo scrivi"),
        ToolEntry(ToolDestination.TIMER, "timer cronometro countdown sveglia minuti"),
        ToolEntry(ToolDestination.CALENDAR, "calendario eventi agenda appuntamenti"),
        ToolEntry(ToolDestination.WEATHER, "meteo temperatura previsioni pioggia"),
    )

    private val settingsEntries = listOf(
        SettingsEntry("Aspetto e Theme Studio", "Tema, colori, sfondo, animazioni", SettingsTargets.APPEARANCE, "tema tema scuro chiaro colori sfondo blur animazioni"),
        SettingsEntry("Home e griglia", "Righe, colonne, icone, dock, pagine", SettingsTargets.HOME, "home griglia colonne righe icone dock pagine layout"),
        SettingsEntry("Drawer e ricerca", "Ordinamento, cartelle, motore web", SettingsTargets.DRAWER_SEARCH, "drawer ricerca app cartelle ordinamento motore web"),
        SettingsEntry("Accessibilità", "Testo, contrasto, icone grandi", SettingsTargets.ACCESSIBILITY, "accessibilita testo contrasto icone grandi talkback"),
        SettingsEntry("Privacy e statistiche", "Dati locali, autorizzazioni, badge", SettingsTargets.PRIVACY, "privacy statistiche dati autorizzazioni badge notifiche"),
        SettingsEntry("Automazioni", "Tema automatico e modalità notte", SettingsTargets.AUTOMATIONS, "automazioni tema orario notte programmate regole"),
        SettingsEntry("Backup e trasferimento", "Backup locale e invio tra dispositivi", SettingsTargets.BACKUP, "backup ripristino trasferimento locale dispositivo"),
        SettingsEntry("Diagnostica", "Registri locali e recupero configurazione", SettingsTargets.DIAGNOSTICS, "diagnostica log crash registri errori"),
        SettingsEntry("Aggiornamenti", "Canale stable o beta", SettingsTargets.UPDATES, "aggiornamenti update versione stable beta"),
    )

    fun normalize(value: String): String =
        value.lowercase(Locale.ITALIAN).trim().replace(Regex("\\s+"), " ")

    /**
     * Scores [title] against [query]. Exact matches rank above prefix, word-prefix,
     * substring and finally a conservative subsequence match. Frequent apps receive
     * a small, bounded usage boost so the order stays predictable.
     */
    fun score(query: String, title: String, usageLaunches: Long = 0L): Int {
        val normalizedQuery = normalize(query)
        val normalizedTitle = normalize(title)
        if (normalizedQuery.isEmpty() || normalizedTitle.isEmpty()) return 0
        val tokens = normalizedQuery.split(' ').filter { it.isNotEmpty() }
        val titleWords = normalizedTitle.split(' ').filter { it.isNotEmpty() }

        var base = when {
            normalizedTitle == normalizedQuery -> 1000
            normalizedTitle.startsWith(normalizedQuery) -> 820
            titleWords.any { it.startsWith(normalizedQuery) } -> 640
            normalizedTitle.contains(normalizedQuery) -> 460
            else -> 0
        }

        if (base == 0) {
            var tokenScore = 0
            var matched = 0
            for (token in tokens) {
                val tokenValue = when {
                    titleWords.any { it.startsWith(token) } -> 220
                    normalizedTitle.contains(token) -> 140
                    else -> 0
                }
                if (tokenValue > 0) matched++
                tokenScore += tokenValue
            }
            if (tokens.size > 1 && matched == tokens.size) {
                base = tokenScore
            } else if (tokens.size == 1 && isSubsequence(tokens.first(), normalizedTitle)) {
                base = 180
            }
        }

        if (base == 0) return 0
        val boost = if (usageLaunches > 0) {
            min(60L, (ln(usageLaunches.toDouble() + 1.0) * 18).toLong()).toInt()
        } else {
            0
        }
        return base + boost
    }

    fun buildHits(
        query: String,
        apps: List<LauncherApp>,
        usage: Map<String, UsageStat>,
        providers: Set<SearchProvider>,
        webEngine: WebEngine,
        limit: Int = MAX_RESULTS,
    ): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val hits = mutableListOf<SearchHit>()

        if (SearchProvider.TOOLS in providers) {
            val calculation = Calculator.evaluate(trimmed)
            if (calculation is CalcResult.Value && trimmed.any { it.isDigit() } &&
                trimmed.any { it in "+-*/^%" }
            ) {
                hits += SearchHit(
                    id = "calc:${trimmed}",
                    title = "= ${calculation.display}",
                    subtitle = trimmed,
                    kind = SearchHitKind.CALCULATOR,
                    score = 1500,
                    tool = ToolDestination.CALCULATOR,
                )
            }

            for (entry in toolEntries) {
                val score = maxOf(
                    score(trimmed, entry.destination.label),
                    score(trimmed, entry.keywords),
                )
                if (score > 0) {
                    hits += SearchHit(
                        id = "tool:${entry.destination.name}",
                        title = entry.destination.label,
                        subtitle = "Strumento Nexus",
                        kind = SearchHitKind.TOOL,
                        score = score,
                        tool = entry.destination,
                    )
                }
            }

            for (entry in settingsEntries) {
                val score = maxOf(
                    score(trimmed, entry.title),
                    score(trimmed, entry.keywords),
                )
                if (score > 0) {
                    hits += SearchHit(
                        id = "settings:${entry.target}",
                        title = entry.title,
                        subtitle = entry.subtitle,
                        kind = SearchHitKind.SETTING,
                        score = score - 10,
                        settingsTarget = entry.target,
                    )
                }
            }
        }

        if (SearchProvider.APPS in providers) {
            for (app in apps) {
                val score = score(trimmed, app.label, usage[app.key]?.launches ?: 0L)
                if (score > 0) {
                    hits += SearchHit(
                        id = "app:${app.key}",
                        title = app.label,
                        subtitle = if (app.category == AppCategory.OTHER) "Applicazione" else "Applicazione · ${app.category.label}",
                        kind = SearchHitKind.APP,
                        score = score,
                        appKey = app.key,
                    )
                }
            }
        }

        if (SearchProvider.WEB in providers && trimmed.length >= 2) {
            val url = if (looksLikeUrl(trimmed)) {
                if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
            } else {
                webEngine.queryUrl.format(URLEncoder.encode(trimmed, "UTF-8"))
            }
            hits += SearchHit(
                id = "web:${trimmed.lowercase(Locale.ITALIAN)}",
                title = if (looksLikeUrl(trimmed)) "Apri $trimmed" else "Cerca «$trimmed»",
                subtitle = if (looksLikeUrl(trimmed)) "Sito web" else "Sul web con ${webEngine.label}",
                kind = SearchHitKind.WEB,
                score = 90,
                url = url,
            )
        }

        return hits
            .sortedWith(
                compareByDescending<SearchHit> { it.score }
                    .thenBy(collator) { it.title },
            )
            .distinctBy { it.id }
            .take(limit.coerceIn(1, MAX_RESULTS))
    }

    fun looksLikeUrl(value: String): Boolean =
        Regex("^(https?://)?[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(/\\S*)?$", RegexOption.IGNORE_CASE)
            .matches(value.trim())

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        if (needle.isEmpty()) return false
        var index = 0
        for (char in haystack) {
            if (index < needle.length && char == needle[index]) index++
            if (index == needle.length) return true
        }
        return index == needle.length
    }
}

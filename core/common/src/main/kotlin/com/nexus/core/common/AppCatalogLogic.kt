package com.nexus.core.common

import com.nexus.core.model.AppCategory
import com.nexus.core.model.LauncherApp
import com.nexus.core.model.UsageStat
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

object AppCategorizer {
    // Values mirror android.content.pm.ApplicationInfo.CATEGORY_* constants, duplicated
    // here so this pure module stays free of Android dependencies and is unit-testable.
    private const val PLATFORM_GAME = 0
    private const val PLATFORM_AUDIO = 1
    private const val PLATFORM_VIDEO = 2
    private const val PLATFORM_IMAGE = 3
    private const val PLATFORM_SOCIAL = 4
    private const val PLATFORM_NEWS = 5
    private const val PLATFORM_MAPS = 6
    private const val PLATFORM_PRODUCTIVITY = 7
    private const val PLATFORM_ACCESSIBILITY = 8

    private val rules: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.GAME to listOf("game", "gioco", "giochi", "play", "arcade", "puzzle"),
        AppCategory.SOCIAL to listOf("chat", "messag", "social", "whatsapp", "telegram", "instagram", "facebook", "tiktok", "threads", "signal", "discord", "mastodon"),
        AppCategory.MEDIA to listOf("youtube", "video", "music", "musica", "spotify", "netflix", "prime video", "disney", "twitch", "podcast", "camera", "foto", "photo", "gallery", "galleria", "player"),
        AppCategory.PRODUCTIVITY to listOf("mail", "gmail", "calendar", "calendario", "drive", "docs", "office", "note", "keep", "slack", "teams", "notion", "files", "file manager", "todo", "task"),
        AppCategory.SHOPPING to listOf("shop", "store", "amazon", "ebay", "aliexpress", "temu", "marketplace", "shopping"),
        AppCategory.TRAVEL to listOf("maps", "mappe", "navig", "uber", "lyft", "train", "treno", "trenitalia", "flight", "hotel", "booking", "airbnb", "travel"),
        AppCategory.NEWS to listOf("news", "notizie", "giornale", "repubblica", "corriere", "ansa", "feed", "reddit"),
        AppCategory.TOOLS to listOf("tool", "strument", "setting", "impostazion", "calculator", "calcolatrice", "clock", "orologio", "weather", "meteo", "compass", "bussola", "torch", "flashlight", "scanner"),
    )

    fun fromPlatformCategory(category: Int): AppCategory? = when (category) {
        PLATFORM_GAME -> AppCategory.GAME
        PLATFORM_AUDIO, PLATFORM_VIDEO, PLATFORM_IMAGE -> AppCategory.MEDIA
        PLATFORM_SOCIAL -> AppCategory.SOCIAL
        PLATFORM_NEWS -> AppCategory.NEWS
        PLATFORM_MAPS -> AppCategory.TRAVEL
        PLATFORM_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
        PLATFORM_ACCESSIBILITY -> AppCategory.TOOLS
        else -> null
    }

    fun categorize(packageName: String, label: String, platformCategory: Int? = null): AppCategory {
        fromPlatformCategory(platformCategory ?: -1)?.let { return it }
        val haystack = "${label.lowercase(Locale.ITALIAN)} ${packageName.lowercase(Locale.ITALIAN)}"
        for ((category, keywords) in rules) {
            if (keywords.any { haystack.contains(it) }) return category
        }
        return AppCategory.OTHER
    }
}

object AppOrdering {
    private val collator: Collator = Collator.getInstance(Locale.ITALIAN)

    fun alphabetical(apps: List<LauncherApp>): List<LauncherApp> =
        apps.sortedWith { first, second ->
            val byLabel = collator.compare(first.label, second.label)
            if (byLabel != 0) byLabel else first.key.compareTo(second.key)
        }

    fun mostUsed(apps: List<LauncherApp>, usage: Map<String, UsageStat>): List<LauncherApp> =
        apps.sortedWith(
            compareByDescending<LauncherApp> { usage[it.key]?.launches ?: 0L }
                .thenByDescending { usage[it.key]?.lastLaunchedAt ?: 0L }
                .thenBy(collator) { it.label },
        )

    fun visible(apps: List<LauncherApp>, hiddenKeys: Set<String>): List<LauncherApp> =
        apps.filterNot { it.key in hiddenKeys }

    fun sectionLetter(label: String): Char {
        val normalized = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .uppercase(Locale.ITALIAN)
        val first = normalized.firstOrNull() ?: '#'
        return if (first in 'A'..'Z') first else '#'
    }

    fun sections(apps: List<LauncherApp>): List<Pair<Char, List<LauncherApp>>> =
        alphabetical(apps)
            .groupBy { sectionLetter(it.label) }
            .toSortedMap(compareBy { if (it == '#') 'Z' + 1 else it })
            .map { (letter, list) -> letter to list }

    fun groupedByCategory(apps: List<LauncherApp>): List<Pair<AppCategory, List<LauncherApp>>> =
        AppCategory.entries
            .mapNotNull { category ->
                val list = alphabetical(apps.filter { it.category == category })
                if (list.isEmpty()) null else category to list
            }
}

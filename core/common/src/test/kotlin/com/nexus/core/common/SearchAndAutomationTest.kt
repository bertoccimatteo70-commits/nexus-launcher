package com.nexus.core.common

import com.nexus.core.model.AppCategory
import com.nexus.core.model.LauncherApp
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.SearchHitKind
import com.nexus.core.model.SearchProvider
import com.nexus.core.model.ThemeMode
import com.nexus.core.model.UsageStat
import com.nexus.core.model.WebEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class SearchAndAutomationTest {
    private val apps = listOf(
        LauncherApp("com.example.maps/com.example.maps.Main", "com.example.maps", "com.example.maps.Main", "Mappe", AppCategory.TRAVEL, false),
        LauncherApp("com.example.photo/com.example.photo.Main", "com.example.photo", "com.example.photo.Main", "Photo Editor", AppCategory.MEDIA, false),
        LauncherApp("com.example.photos/com.example.photos.Main", "com.example.photos", "com.example.photos.Main", "Photos", AppCategory.MEDIA, true),
    )

    @Test
    fun `exact prefix and usage affect ranking`() {
        val exact = SearchRanking.score("Photos", "Photos")
        val prefix = SearchRanking.score("pho", "Photos")
        val contains = SearchRanking.score("hot", "Photos")
        assertTrue(exact > prefix)
        assertTrue(prefix > contains)
        assertTrue(SearchRanking.score("photos", "Photos", 50) > SearchRanking.score("photos", "Photos"))
        assertEquals(0, SearchRanking.score("zzz", "Photos"))
    }

    @Test
    fun `build hits respects providers and orders app results`() {
        val hits = SearchRanking.buildHits(
            query = "pho",
            apps = apps,
            usage = mapOf("com.example.photos/com.example.photos.Main" to UsageStat("k", 10, 0)),
            providers = setOf(SearchProvider.APPS),
            webEngine = WebEngine.GOOGLE,
        )
        assertEquals(listOf("Photos", "Photo Editor"), hits.map { it.title })
        assertTrue(hits.all { it.kind == SearchHitKind.APP })
    }

    @Test
    fun `web provider can be disabled and urls encoded`() {
        val withWeb = SearchRanking.buildHits("caffè", emptyList(), emptyMap(), setOf(SearchProvider.WEB), WebEngine.DUCKDUCKGO)
        assertTrue(withWeb.single().url!!.startsWith("https://duckduckgo.com/?q=caff"))
        val withoutWeb = SearchRanking.buildHits("caffè", emptyList(), emptyMap(), setOf(SearchProvider.APPS), WebEngine.DUCKDUCKGO)
        assertTrue(withoutWeb.isEmpty())
        assertTrue(SearchRanking.looksLikeUrl("example.com"))
        assertFalse(SearchRanking.looksLikeUrl("parola"))
    }

    @Test
    fun `calculator and tools are surfaced by search`() {
        val calculation = SearchRanking.buildHits(
            "12*3",
            emptyList(),
            emptyMap(),
            setOf(SearchProvider.TOOLS),
            WebEngine.GOOGLE,
        )
        assertEquals(SearchHitKind.CALCULATOR, calculation.first().kind)
        assertEquals("= 36", calculation.first().title)

        val timerHits = SearchRanking.buildHits(
            "timer",
            emptyList(),
            emptyMap(),
            setOf(SearchProvider.TOOLS),
            WebEngine.GOOGLE,
        )
        assertTrue(timerHits.any { it.title == "Timer" })

        val calculatorTool = SearchRanking.buildHits(
            "calcolatrice",
            emptyList(),
            emptyMap(),
            setOf(SearchProvider.TOOLS),
            WebEngine.GOOGLE,
        )
        assertTrue(calculatorTool.any { it.title == "Calcolatrice" })
    }

    @Test
    fun `categories and sections use italian collation`() {
        assertEquals(AppCategory.SOCIAL, AppCategorizer.categorize("com.whatsapp", "WhatsApp"))
        assertEquals(AppCategory.GAME, AppCategorizer.categorize("com.x", "Qualcosa", 0))
        val sorted = AppOrdering.alphabetical(apps)
        assertEquals(listOf("Mappe", "Photo Editor", "Photos"), sorted.map { it.label })
        assertEquals('M', AppOrdering.sectionLetter("Mappe"))
        assertEquals('#', AppOrdering.sectionLetter("123 App"))
    }

    @Test
    fun `night window crossing midnight is detected`() {
        assertTrue(AutomationRules.isNight(LocalTime.of(23, 30), 20, 7))
        assertTrue(AutomationRules.isNight(LocalTime.of(6, 59), 20, 7))
        assertFalse(AutomationRules.isNight(LocalTime.of(12, 0), 20, 7))
        assertFalse(AutomationRules.isNight(LocalTime.of(20, 0), 20, 20))
        assertTrue(AutomationRules.isNight(LocalTime.of(10, 0), 22, 18))
    }

    @Test
    fun `automations override appearance only when enabled`() {
        val settings = NexusSettings(
            themeMode = ThemeMode.SYSTEM,
            automationsEnabled = true,
            autoThemeEnabled = true,
            nightModeEnabled = true,
            nightCalmEnabled = true,
        )
        val night = AutomationRules.effectiveAppearance(settings, LocalDateTime.of(2026, 11, 23, 23, 0))
        assertEquals(ThemeMode.DARK, night.themeMode)
        assertTrue(night.nightActive)
        assertFalse(night.animateBackground)
        assertTrue(night.reduceMotion)

        val day = AutomationRules.effectiveAppearance(settings, LocalDateTime.of(2026, 11, 23, 12, 0))
        assertEquals(ThemeMode.LIGHT, day.themeMode)
        assertTrue(day.animateBackground)

        val disabled = AutomationRules.effectiveAppearance(
            settings.copy(automationsEnabled = false, reduceMotion = false),
            LocalDateTime.of(2026, 11, 23, 23, 0),
        )
        assertEquals(ThemeMode.SYSTEM, disabled.themeMode)
        assertFalse(disabled.nightActive)
    }
}

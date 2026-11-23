package com.nexus.core.model

import kotlinx.serialization.Serializable

/** Schema version of the persisted [NexusSettings] document and of backup payloads. */
const val NEXUS_SCHEMA_VERSION: Int = 1

const val NEXUS_SCHEMA_VERSION_SUPPORTED_MAX: Int = NEXUS_SCHEMA_VERSION

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Chiaro"),
    DARK("Scuro"),
}

@Serializable
enum class AccentPalette(val label: String) {
    ION("Ion"),
    FLARE("Flare"),
    SOLAR("Solar"),
    ULTRAVIOLET("Ultraviolet"),
    MINT("Mint"),
}

@Serializable
enum class BackgroundStyle(val label: String) {
    VOID("Vuoto"),
    GRID("Griglia"),
    ORBIT("Orbita"),
    AURORA("Aurora"),
}

@Serializable
enum class BadgeMode(val label: String) {
    OFF("Disattivati"),
    DOT("Pallino"),
    COUNT("Contatore"),
}

@Serializable
enum class DrawerSort(val label: String) {
    ALPHABETICAL("Alfabetico"),
    MOST_USED("Più usate"),
}

@Serializable
enum class WeatherMode(val label: String) {
    AUTO("Posizione automatica"),
    CITY("Città manuale"),
}

@Serializable
enum class WebEngine(val label: String, val queryUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    WIKIPEDIA("Wikipedia", "https://it.wikipedia.org/w/index.php?search=%s"),
}

@Serializable
enum class SearchProvider(val label: String) {
    APPS("Applicazioni"),
    WEB("Web"),
    TOOLS("Strumenti Nexus"),
}

@Serializable
enum class UpdateChannel(val label: String) {
    STABLE("Stable"),
    BETA("Beta"),
}

@Serializable
enum class WidgetType(val label: String) {
    CLOCK("Orologio"),
    WEATHER("Meteo"),
    BATTERY("Batteria"),
    CALENDAR("Calendario"),
    DASHBOARD("Dashboard Nexus"),
}

@Serializable
enum class WidgetSpan(val label: String, val heightDp: Int) {
    SMALL("Compatto", 104),
    MEDIUM("Medio", 168),
    LARGE("Ampio", 236),
}

@Serializable
enum class HomeItemType {
    APP,
    WIDGET,
}

@Serializable
data class HomeItem(
    val id: String,
    val type: HomeItemType,
    /** App key (`package/activity`) for [HomeItemType.APP], [WidgetType] name for widgets. */
    val key: String,
    val page: Int = 0,
    val order: Int = 0,
    val span: WidgetSpan = WidgetSpan.MEDIUM,
)

@Serializable
enum class QuickAction(val label: String) {
    SEARCH("Cerca"),
    DRAWER("Applicazioni"),
    DASHBOARD("Dashboard"),
    CALCULATOR("Calcolatrice"),
    NOTES("Note"),
    TIMER("Timer"),
    CALENDAR("Calendario"),
    WEATHER("Meteo"),
    SETTINGS("Impostazioni"),
    BACKUP("Backup"),
    DIAGNOSTICS("Diagnostica"),
}

@Serializable
data class NexusSettings(
    val schemaVersion: Int = NEXUS_SCHEMA_VERSION,
    val onboardingCompleted: Boolean = false,

    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val palette: AccentPalette = AccentPalette.ION,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.ORBIT,
    val animatedBackground: Boolean = true,
    val wallpaperUri: String? = null,

    val blurEnabled: Boolean = false,
    val reduceMotion: Boolean = false,
    val animationSpeed: Float = 1f,
    val fontScale: Float = 1f,
    val highContrast: Boolean = false,
    val largeTouchTargets: Boolean = false,

    val gridColumns: Int = 5,
    val gridRows: Int = 5,
    val iconSizeDp: Int = 54,
    val showAppLabels: Boolean = true,
    val adaptiveIcons: Boolean = false,

    val dockSize: Int = 5,
    val dockApps: List<String> = emptyList(),
    val homePages: Int = 2,
    val homeItems: List<HomeItem> = emptyList(),
    val autoLayoutApplied: Boolean = false,

    val badgeMode: BadgeMode = BadgeMode.DOT,
    val drawerSort: DrawerSort = DrawerSort.ALPHABETICAL,
    val drawerCategoryFolders: Boolean = true,
    val hiddenApps: Set<String> = emptySet(),

    val searchProviders: Set<SearchProvider> =
        setOf(SearchProvider.APPS, SearchProvider.WEB, SearchProvider.TOOLS),
    val webEngine: WebEngine = WebEngine.GOOGLE,

    val weatherMode: WeatherMode = WeatherMode.AUTO,
    val weatherCity: String = "Milano",
    val weatherCacheMinutes: Int = 20,
    val use24HourClock: Boolean = true,

    val usageStatsEnabled: Boolean = true,
    val diagnosticsEnabled: Boolean = true,

    val automationsEnabled: Boolean = true,
    val autoThemeEnabled: Boolean = true,
    val dayStartHour: Int = 7,
    val nightStartHour: Int = 20,
    val nightModeEnabled: Boolean = true,
    val nightCalmEnabled: Boolean = true,
    val nightHighContrast: Boolean = false,

    val autoBackupEnabled: Boolean = true,
    val lastBackupAt: Long = 0L,
    val lastBackupStatus: String = "",

    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val lastUpdateCheckAt: Long = 0L,
    val availableVersion: String = "",
    val availableVersionNotes: String = "",
    val availableVersionUrl: String = "",
    val lastSeenVersion: String = "",
) {
    /**
     * Coerces every configurable value into its supported range. Used after decoding a
     * settings blob (including imported backups) so a hostile or damaged file cannot
     * produce an unusable launcher.
     */
    fun sanitized(): NexusSettings {
        val pages = homePages.coerceIn(1, 9)
        val dock = dockSize.coerceIn(3, 6)
        return copy(
            schemaVersion = NEXUS_SCHEMA_VERSION,
            gridColumns = gridColumns.coerceIn(3, 7),
            gridRows = gridRows.coerceIn(3, 7),
            iconSizeDp = iconSizeDp.coerceIn(40, 80),
            dockSize = dock,
            dockApps = dockApps.distinct().take(dock),
            fontScale = fontScale.coerceIn(0.85f, 1.6f),
            animationSpeed = animationSpeed.coerceIn(0f, 1.5f),
            homePages = pages,
            homeItems = homeItems
                .filter { it.key.isNotBlank() }
                .distinctBy { it.id }
                .map { it.copy(page = it.page.coerceIn(0, pages - 1)) },
            weatherCacheMinutes = weatherCacheMinutes.coerceIn(5, 180),
            dayStartHour = dayStartHour.coerceIn(0, 23),
            nightStartHour = nightStartHour.coerceIn(0, 23),
        )
    }

    fun withItem(item: HomeItem): NexusSettings =
        copy(homeItems = homeItems.filterNot { it.id == item.id } + item)

    fun withoutItem(itemId: String): NexusSettings =
        copy(homeItems = homeItems.filterNot { it.id == itemId })
}

/** Immutable reference to an installed activity, stored as `package/activity`. */
data class AppKey(val packageName: String, val activityName: String) {
    val flattened: String get() = "$packageName/$activityName"

    companion object {
        fun parse(raw: String): AppKey? {
            val index = raw.indexOf('/')
            if (index <= 0 || index == raw.lastIndex) return null
            val pkg = raw.substring(0, index)
            val activity = raw.substring(index + 1)
            if (pkg.isBlank() || activity.isBlank()) return null
            return AppKey(pkg, activity)
        }
    }
}

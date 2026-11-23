package com.nexus.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.core.data.AppCatalogRepository
import com.nexus.core.data.DiagnosticsRepository
import com.nexus.core.data.SettingsRepository
import com.nexus.core.data.UsageRepository
import com.nexus.core.data.UpdateRepository
import com.nexus.core.designsystem.NexusDetailScreen
import com.nexus.core.designsystem.NexusDivider
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusPanel
import com.nexus.core.designsystem.NexusSectionHeader
import com.nexus.core.designsystem.NexusSettingRow
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.model.AppBuildInfo
import com.nexus.core.model.NexusNavigator
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.SearchProvider
import com.nexus.core.model.SettingsTargets
import com.nexus.core.model.ThemeMode
import com.nexus.core.model.UpdateChannel

@Composable
fun SettingsFlow(
    settings: NexusSettings,
    settingsRepository: SettingsRepository,
    diagnostics: DiagnosticsRepository,
    catalog: AppCatalogRepository,
    usageRepository: UsageRepository,
    updateRepository: UpdateRepository,
    buildInfo: AppBuildInfo,
    isDefaultLauncher: Boolean,
    initialTarget: String,
    navigator: NexusNavigator,
    onBack: () -> Unit,
) {
    var target by rememberSaveable { mutableStateOf(initialTarget) }

    fun back() {
        when (target) {
            SettingsTargets.ROOT -> onBack()
            SettingsTargets.HIDDEN_APPS -> target = SettingsTargets.DRAWER_SEARCH
            else -> target = SettingsTargets.ROOT
        }
    }
    BackHandler { back() }

    val common = SettingsContext(
        settings = settings,
        settingsRepository = settingsRepository,
        diagnostics = diagnostics,
        catalog = catalog,
        usageRepository = usageRepository,
        updateRepository = updateRepository,
        buildInfo = buildInfo,
        navigator = navigator,
        isDefaultLauncher = isDefaultLauncher,
    )

    when (target) {
        SettingsTargets.APPEARANCE -> AppearanceSettingsScreen(common) { target = SettingsTargets.ROOT }
        SettingsTargets.HOME -> HomeSettingsScreen(common) { target = SettingsTargets.ROOT }
        SettingsTargets.DRAWER_SEARCH -> DrawerSearchSettingsScreen(
            context = common,
            onBack = { target = SettingsTargets.ROOT },
            onOpenHiddenApps = { target = SettingsTargets.HIDDEN_APPS },
        )
        SettingsTargets.HIDDEN_APPS -> HiddenAppsScreen(
            settings = settings,
            settingsRepository = settingsRepository,
            catalog = catalog,
            onBack = { target = SettingsTargets.DRAWER_SEARCH },
        )
        SettingsTargets.ACCESSIBILITY -> AccessibilitySettingsScreen(common) {
            target = SettingsTargets.ROOT
        }
        SettingsTargets.PRIVACY -> PrivacySettingsScreen(
            context = common,
            onOpenHiddenApps = { target = SettingsTargets.HIDDEN_APPS },
            onBack = { target = SettingsTargets.ROOT },
        )
        SettingsTargets.DIAGNOSTICS -> DiagnosticsSettingsScreen(common) {
            target = SettingsTargets.ROOT
        }
        SettingsTargets.UPDATES -> UpdatesSettingsScreen(common) { target = SettingsTargets.ROOT }
        SettingsTargets.ABOUT -> AboutSettingsScreen(common) { target = SettingsTargets.ROOT }
        else -> SettingsHomeScreen(
            context = common,
            onOpen = { target = it },
            onBack = onBack,
        )
    }
}

internal data class SettingsContext(
    val settings: NexusSettings,
    val settingsRepository: SettingsRepository,
    val diagnostics: DiagnosticsRepository,
    val catalog: AppCatalogRepository,
    val usageRepository: UsageRepository,
    val updateRepository: UpdateRepository,
    val buildInfo: AppBuildInfo,
    val navigator: NexusNavigator,
    val isDefaultLauncher: Boolean,
)

@Composable
internal fun SettingsSection(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NexusPanel(modifier = modifier) {
        NexusSectionHeader(kicker = kicker, title = title)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
internal fun SettingsRowGroup(content: @Composable ColumnScope.() -> Unit) {
    NexusPanel(padding = 14.dp) {
        content()
    }
}

@Composable
private fun SettingsHomeScreen(
    context: SettingsContext,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val settings = context.settings
    NexusDetailScreen(
        title = "Impostazioni",
        subtitle = "Nexus ${context.buildInfo.versionName}",
        onBack = onBack,
    ) {
        if (!context.isDefaultLauncher) {
            NexusNotice(
                "Nexus non è il launcher predefinito. Aprilo dalle impostazioni Android per usarlo come Home.",
                actionLabel = "Imposta",
                onAction = context.navigator.requestDefaultLauncher,
            )
        }
        SettingsSection("Stato", "Nexus su questo dispositivo") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusTag(if (context.isDefaultLauncher) "Launcher predefinito" else "Non predefinito")
                NexusTag(settings.updateChannel.label)
                NexusTag(if (settings.automationsEnabled) "Automazioni on" else "Automazioni off")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Tema ${themeLabel(settings)} · ${settings.palette.label} · ${settings.backgroundStyle.label} · griglia ${settings.gridColumns}×${settings.gridRows}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Configurazione salvata localmente. Nexus non richiede account.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("Personalizzazione", "Aspetto e comportamento") {
            NexusSettingRow(
                title = "Aspetto e Theme Studio",
                subtitle = "Tema, palette, sfondo, animazioni, tipografia",
                onClick = { onOpen(SettingsTargets.APPEARANCE) },
                glyph = com.nexus.core.designsystem.NexusGlyphId.PALETTE,
            )
            NexusDivider()
            NexusSettingRow(
                title = "Home e griglia",
                subtitle = "${settings.gridColumns} colonne · ${settings.homePages} pagine · dock ${settings.dockSize}",
                onClick = { onOpen(SettingsTargets.HOME) },
                glyph = com.nexus.core.designsystem.NexusGlyphId.GRID,
            )
            NexusDivider()
            NexusSettingRow(
                title = "Drawer e ricerca",
                subtitle = "${settings.drawerSort.label} · ${settings.searchProviders.size} provider attivi",
                onClick = { onOpen(SettingsTargets.DRAWER_SEARCH) },
                glyph = com.nexus.core.designsystem.NexusGlyphId.SEARCH,
            )
            NexusDivider()
            NexusSettingRow(
                title = "Accessibilità",
                subtitle = "Testo, contrasto, target tattili, TalkBack",
                onClick = { onOpen(SettingsTargets.ACCESSIBILITY) },
                glyph = com.nexus.core.designsystem.NexusGlyphId.ACCESSIBILITY,
            )
        }

        SettingsSection("Automazioni e dati", "Controllo") {
            NexusSettingRow(
                title = "Automazioni",
                subtitle = "Tema per orario e modalità notte",
                onClick = { context.navigator.openSettings(SettingsTargets.AUTOMATIONS) },
                glyph = com.nexus.core.designsystem.NexusGlyphId.AUTOMATION,
            )
            NexusDivider()
            NexusSettingRow(
                title = "Backup e trasferimento",
                subtitle = if (settings.autoBackupEnabled) "Backup automatico attivo" else "Backup automatico disattivato",
                onClick = { context.navigator.openSettings(SettingsTargets.BACKUP) },
                glyph = com.nexus.core.designsystem.NexusGlyphId.BACKUP,
            )
            NexusDivider()
            NexusSettingRow(
                title = "Privacy e statistiche",
                subtitle = if (settings.usageStatsEnabled) "Statistiche locali attive" else "Statistiche locali disattivate",
                onClick = { onOpen(SettingsTargets.PRIVACY) },
                glyph = com.nexus.core.designsystem.NexusGlyphId.SHIELD,
            )
            NexusDivider()
            NexusSettingRow(
                title = "Diagnostica",
                subtitle = "Registri locali, crash e recupero configurazione",
                onClick = { onOpen(SettingsTargets.DIAGNOSTICS) },
                glyph = com.nexus.core.designsystem.NexusGlyphId.DIAGNOSTICS,
            )
        }

        SettingsSection("Nexus", "Versione e supporto") {
            NexusSettingRow(
                title = "Aggiornamenti",
                subtitle = if (settings.availableVersion.isBlank()) {
                    "Canale ${settings.updateChannel.label}"
                } else {
                    "Disponibile Nexus ${settings.availableVersion}"
                },
                onClick = { onOpen(SettingsTargets.UPDATES) },
                glyph = com.nexus.core.designsystem.NexusGlyphId.DOWNLOAD,
            )
            NexusDivider()
            NexusSettingRow(
                title = "Informazioni",
                subtitle = "Versione ${context.buildInfo.versionName} · ${context.buildInfo.versionCode}",
                onClick = { onOpen(SettingsTargets.ABOUT) },
                glyph = com.nexus.core.designsystem.NexusGlyphId.INFO,
            )
        }

        Text(
            "Nexus ${context.buildInfo.versionName} · schema impostazioni ${settings.schemaVersion} · ${if (context.buildInfo.isDebug) "debug" else "release"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun themeLabel(settings: NexusSettings): String = when (settings.themeMode) {
    ThemeMode.SYSTEM -> "Sistema"
    ThemeMode.LIGHT -> "Chiaro"
    ThemeMode.DARK -> "Scuro"
}

internal fun searchProviderSummary(settings: NexusSettings): String {
    if (settings.searchProviders.isEmpty()) return "Nessun provider"
    return SearchProvider.entries.filter { it in settings.searchProviders }.joinToString(" · ") { it.label }
}

internal fun channelLabel(channel: UpdateChannel): String = channel.label

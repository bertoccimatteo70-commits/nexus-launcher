package com.nexus.core.model

import kotlinx.serialization.Serializable

enum class SearchHitKind {
    APP,
    TOOL,
    SETTING,
    WEB,
    CALCULATOR,
}

/** A ranked hit produced by the global Nexus search. */
data class SearchHit(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: SearchHitKind,
    val score: Int,
    val appKey: String? = null,
    val url: String? = null,
    val tool: ToolDestination? = null,
    val settingsTarget: String? = null,
)

/** Targets of the settings surface used by search results and quick links. */
object SettingsTargets {
    const val ROOT = "root"
    const val APPEARANCE = "appearance"
    const val HOME = "home"
    const val DRAWER_SEARCH = "drawer_search"
    const val HIDDEN_APPS = "hidden_apps"
    const val ACCESSIBILITY = "accessibility"
    const val PRIVACY = "privacy"
    const val AUTOMATIONS = "automations"
    const val BACKUP = "backup"
    const val DIAGNOSTICS = "diagnostics"
    const val UPDATES = "updates"
    const val ABOUT = "about"
}

@Serializable
data class BackupPayload(
    val schemaVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    val settings: NexusSettings,
    val notes: List<Note> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val usage: List<UsageStat> = emptyList(),
)

@Serializable
enum class BackupCryptMode {
    PASSPHRASE,
    DEVICE_KEY,
}

/**
 * Self-describing encrypted backup envelope. The whole payload is AES/GCM encrypted,
 * the plaintext SHA-256 is stored separately so the receiver can verify integrity
 * after decrypting (the GCM tag already authenticates the ciphertext).
 */
@Serializable
data class BackupEnvelope(
    val formatVersion: Int = 1,
    val createdAt: Long,
    val appVersion: String,
    val schemaVersion: Int,
    val mode: BackupCryptMode,
    val kdf: String,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val plaintextSha256: String,
)

data class BackupPreview(
    val createdAt: Long,
    val appVersion: String,
    val schemaVersion: Int,
    val noteCount: Int,
    val eventCount: Int,
    val homeItems: Int,
    val pages: Int,
    val compatible: Boolean,
    val warnings: List<String>,
)

sealed interface BackupResult {
    data class Success(val file: java.io.File, val bytes: Int) : BackupResult
    data class Failure(val reason: String, val cause: Throwable? = null) : BackupResult
}

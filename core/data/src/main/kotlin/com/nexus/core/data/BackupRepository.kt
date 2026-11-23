package com.nexus.core.data

import android.content.Context
import android.net.Uri
import com.nexus.core.common.BackupCodec
import com.nexus.core.common.NexusFormatters
import com.nexus.core.model.AppBuildInfo
import com.nexus.core.model.BackupCryptMode
import com.nexus.core.model.BackupEnvelope
import com.nexus.core.model.BackupPayload
import com.nexus.core.model.BackupPreview
import com.nexus.core.model.NEXUS_SCHEMA_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Creates and restores versioned backups. Automatic backups are encrypted with a
 * device-bound Keystore key; exported backups are protected by a user passphrase.
 * Every payload carries its schema version and is integrity checked before applying.
 */
class BackupRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val notes: NotesRepository,
    private val calendar: CalendarRepository,
    private val usage: UsageRepository,
    private val crypto: CryptoStore,
    private val buildInfo: AppBuildInfo,
    private val diagnostics: DiagnosticsRepository,
) {
    private val backupDirectory = File(context.filesDir, "backups").apply { mkdirs() }
    private val autoBackupAlias = "nexus-auto-backup"

    suspend fun createPayload(): BackupPayload {
        settings.awaitLoaded()
        return BackupPayload(
            schemaVersion = NEXUS_SCHEMA_VERSION,
            createdAt = System.currentTimeMillis(),
            appVersion = buildInfo.versionName,
            settings = settings.settings.value,
            notes = notes.notes.value,
            events = calendar.events.value,
            usage = usage.usage.value.values.toList(),
        )
    }

    suspend fun createPassphraseBackup(passphrase: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                BackupCodec.createPassphraseBackup(createPayload(), passphrase)
            }.onFailure { diagnostics.error("BACKUP", "Creazione backup cifrato fallita", it) }
        }

    suspend fun writeTo(uri: Uri, bytes: ByteArray): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("Impossibile aprire il file di destinazione")
            output.use { it.write(bytes) }
            diagnostics.info("BACKUP", "Backup esportato (${bytes.size} byte)")
            bytes.size
        }
    }

    suspend fun autoBackup(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = createPayload()
            val plaintext = BackupCodec.payloadJson(payload)
            val encrypted = crypto.encrypt(autoBackupAlias, plaintext)
            val envelope = BackupCodec.buildEnvelope(
                payload = payload,
                mode = BackupCryptMode.DEVICE_KEY,
                kdf = BackupCodec.DEVICE_KEY_KDF,
                salt = ByteArray(0),
                iv = encrypted.iv,
                ciphertext = encrypted.ciphertext,
            )
            val bytes = BackupCodec.encodeEnvelope(envelope)
            val file = File(backupDirectory, "nexus-auto-${payload.createdAt}.nxb")
            atomicWrite(file, bytes)
            pruneAutoBackups()
            settings.update {
                it.copy(
                    lastBackupAt = payload.createdAt,
                    lastBackupStatus = "OK · ${file.name}",
                )
            }
            diagnostics.info("BACKUP", "Backup automatico creato: ${file.name} (${bytes.size} byte)")
            file
        }.onFailure { failure ->
            diagnostics.error("BACKUP", "Backup automatico fallito", failure)
            runCatching {
                settings.update {
                    it.copy(lastBackupStatus = "Errore: ${failure.message ?: "sconosciuto"}")
                }
            }
        }
    }

    fun autoBackupFiles(): List<File> =
        backupDirectory.listFiles { file -> file.isFile && file.extension == "nxb" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun envelope(raw: ByteArray): Result<BackupEnvelope> = runCatching {
        val parsed = BackupCodec.parseEnvelope(raw).getOrThrow()
        BackupCodec.validateEnvelope(parsed).getOrThrow()
        parsed
    }

    fun decryptPassphrase(raw: ByteArray, passphrase: String): Result<BackupPayload> =
        BackupCodec.openPassphraseBackup(raw, passphrase)

    fun decryptAuto(file: File): Result<BackupPayload> = runCatching {
        BackupCodec.openDeviceKeyBackup(file.readBytes()) { iv, ciphertext ->
            crypto.decrypt(autoBackupAlias, iv, ciphertext)
        }.getOrThrow()
    }

    suspend fun apply(payload: BackupPayload): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val current = settings.settings.value
            val restored = payload.settings.sanitized().copy(
                lastBackupAt = current.lastBackupAt,
                lastBackupStatus = "Ripristino del ${NexusFormatters.dateWithYear(payload.createdAt)}",
            )
            settings.replace(restored)
            notes.replaceAll(payload.notes)
            calendar.replaceAll(payload.events)
            usage.replaceAll(payload.usage)
            diagnostics.info(
                "BACKUP",
                "Ripristino completato: ${payload.notes.size} note, ${payload.events.size} eventi",
            )
            BackupCodec.preview(payload)
        }.onFailure { diagnostics.error("BACKUP", "Ripristino fallito", it) }
    }

    suspend fun readUri(uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Impossibile leggere il file selezionato")
            input.use { it.readBytes() }
        }
    }

    suspend fun payloadBytes(): ByteArray = BackupCodec.payloadJson(createPayload())

    suspend fun applyPayloadBytes(bytes: ByteArray): Result<BackupPreview> = runCatching {
        val payload = BackupCodec.parsePayload(bytes.toString(Charsets.UTF_8)).getOrThrow()
        apply(payload).getOrThrow()
    }

    private fun pruneAutoBackups(keep: Int = 5) {
        autoBackupFiles().drop(keep).forEach { it.delete() }
    }
}

package com.nexus.core.common

import com.nexus.core.model.BackupCryptMode
import com.nexus.core.model.BackupEnvelope
import com.nexus.core.model.BackupPayload
import com.nexus.core.model.BackupPreview
import com.nexus.core.model.NEXUS_SCHEMA_VERSION
import com.nexus.core.model.NEXUS_SCHEMA_VERSION_SUPPORTED_MAX
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.Base64

object BackupCodec {
    const val FORMAT_VERSION = 1
    const val DEVICE_KEY_KDF = "ANDROID-KEYSTORE-AES-GCM"

    fun payloadJson(payload: BackupPayload): ByteArray =
        NexusJson.instance.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)

    fun encodePayload(payload: BackupPayload): String =
        NexusJson.instance.encodeToString(BackupPayload.serializer(), payload)

    fun parsePayload(raw: String): Result<BackupPayload> = runCatching {
        NexusJson.instance.decodeFromString(BackupPayload.serializer(), raw)
    }

    fun encodeEnvelope(envelope: BackupEnvelope): ByteArray =
        NexusJson.instance.encodeToString(BackupEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)

    fun parseEnvelope(raw: ByteArray): Result<BackupEnvelope> = runCatching {
        NexusJson.instance.decodeFromString(BackupEnvelope.serializer(), raw.toString(Charsets.UTF_8))
    }

    fun validateEnvelope(envelope: BackupEnvelope): Result<Unit> = runCatching {
        require(envelope.formatVersion == FORMAT_VERSION) {
            "Formato backup ${envelope.formatVersion} non supportato"
        }
        require(envelope.schemaVersion in 1..NEXUS_SCHEMA_VERSION_SUPPORTED_MAX) {
            "Backup creato con schema ${envelope.schemaVersion}, non compatibile con questa versione di Nexus"
        }
        require(envelope.plaintextSha256.length == 64) { "Hash del backup non valido" }
        val ciphertext = Base64.getDecoder().decode(envelope.ciphertextBase64)
        require(ciphertext.size >= 16) { "Contenuto del backup non valido" }
        Base64.getDecoder().decode(envelope.ivBase64)
        if (envelope.mode == BackupCryptMode.PASSPHRASE) {
            require(Base64.getDecoder().decode(envelope.saltBase64).size == 16) { "Salt del backup non valido" }
        }
    }

    fun buildEnvelope(
        payload: BackupPayload,
        mode: BackupCryptMode,
        kdf: String,
        salt: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): BackupEnvelope = BackupEnvelope(
        formatVersion = FORMAT_VERSION,
        createdAt = payload.createdAt,
        appVersion = payload.appVersion,
        schemaVersion = payload.schemaVersion,
        mode = mode,
        kdf = kdf,
        saltBase64 = Base64.getEncoder().encodeToString(salt),
        ivBase64 = Base64.getEncoder().encodeToString(iv),
        ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
        plaintextSha256 = BackupCrypto.sha256(payloadJson(payload)),
    )

    fun createPassphraseBackup(payload: BackupPayload, passphrase: String): ByteArray {
        require(passphrase.length >= 6) { "La passphrase deve contenere almeno 6 caratteri" }
        val encrypted = BackupCrypto.encrypt(
            plaintext = payloadJson(payload),
            passphrase = passphrase.toCharArray(),
        )
        val envelope = buildEnvelope(
            payload = payload,
            mode = BackupCryptMode.PASSPHRASE,
            kdf = BackupCrypto.KDF_DESCRIPTION,
            salt = encrypted.salt,
            iv = encrypted.iv,
            ciphertext = encrypted.ciphertext,
        )
        return encodeEnvelope(envelope)
    }

    fun openPassphraseBackup(raw: ByteArray, passphrase: String): Result<BackupPayload> = runCatching {
        val envelope = parseEnvelope(raw).getOrThrow()
        validateEnvelope(envelope).getOrThrow()
        require(envelope.mode == BackupCryptMode.PASSPHRASE) { "Il backup non usa una passphrase" }
        val plaintext = BackupCrypto.decrypt(
            BackupCrypto.Encrypted(
                salt = Base64.getDecoder().decode(envelope.saltBase64),
                iv = Base64.getDecoder().decode(envelope.ivBase64),
                ciphertext = Base64.getDecoder().decode(envelope.ciphertextBase64),
            ),
            passphrase.toCharArray(),
        )
        decodeVerifiedPayload(envelope, plaintext)
    }

    fun openDeviceKeyBackup(
        raw: ByteArray,
        decrypt: (iv: ByteArray, ciphertext: ByteArray) -> ByteArray,
    ): Result<BackupPayload> = runCatching {
        val envelope = parseEnvelope(raw).getOrThrow()
        validateEnvelope(envelope).getOrThrow()
        require(envelope.mode == BackupCryptMode.DEVICE_KEY) { "Il backup non è legato a questo dispositivo" }
        val plaintext = decrypt(
            Base64.getDecoder().decode(envelope.ivBase64),
            Base64.getDecoder().decode(envelope.ciphertextBase64),
        )
        decodeVerifiedPayload(envelope, plaintext)
    }

    fun decodeVerifiedPayload(envelope: BackupEnvelope, plaintext: ByteArray): BackupPayload {
        val digest = BackupCrypto.sha256(plaintext)
        require(BackupCrypto.constantTimeEquals(digest, envelope.plaintextSha256)) {
            "Verifica di integrità fallita: il backup è danneggiato"
        }
        val payload = parsePayload(plaintext.toString(Charsets.UTF_8)).getOrThrow()
        require(payload.schemaVersion in 1..NEXUS_SCHEMA_VERSION_SUPPORTED_MAX) {
            "Schema del backup non supportato"
        }
        return payload
    }

    fun preview(payload: BackupPayload): BackupPreview {
        val warnings = buildList {
            if (payload.schemaVersion != NEXUS_SCHEMA_VERSION) {
                add("Creato con schema ${payload.schemaVersion}: le impostazioni saranno adattate.")
            }
            if (payload.settings.homeItems.size > 200) {
                add("Il layout contiene molti elementi: verranno importati solo quelli validi.")
            }
        }
        return BackupPreview(
            createdAt = payload.createdAt,
            appVersion = payload.appVersion,
            schemaVersion = payload.schemaVersion,
            noteCount = payload.notes.size,
            eventCount = payload.events.size,
            homeItems = payload.settings.homeItems.size,
            pages = payload.settings.homePages,
            compatible = payload.schemaVersion in 1..NEXUS_SCHEMA_VERSION_SUPPORTED_MAX,
            warnings = warnings,
        )
    }

    fun importableSettings(payload: BackupPayload): com.nexus.core.model.NexusSettings =
        payload.settings.sanitized()
}

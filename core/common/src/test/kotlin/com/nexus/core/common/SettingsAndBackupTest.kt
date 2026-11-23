package com.nexus.core.common

import com.nexus.core.model.BackupCryptMode
import com.nexus.core.model.BackupPayload
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SettingsAndBackupTest {
    private val note = Note(
        id = "n1",
        title = "Test",
        body = "Contenuto locale",
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )

    private fun payload(settings: NexusSettings = NexusSettings()) = BackupPayload(
        schemaVersion = settings.schemaVersion,
        createdAt = 5_000L,
        appVersion = "1.0.0",
        settings = settings,
        notes = listOf(note),
    )

    @Test
    fun `settings roundtrip keeps values`() {
        val settings = NexusSettings(
            onboardingCompleted = true,
            gridColumns = 7,
            palette = com.nexus.core.model.AccentPalette.FLARE,
            homePages = 4,
        )
        val decoded = SettingsCodec.decode(SettingsCodec.encode(settings)).getOrThrow()
        assertEquals(7, decoded.gridColumns)
        assertEquals(com.nexus.core.model.AccentPalette.FLARE, decoded.palette)
        assertEquals(4, decoded.homePages)
        assertTrue(decoded.onboardingCompleted)
    }

    @Test
    fun `settings sanitize unsupported values`() {
        val decoded = SettingsCodec.decode(
            """{"gridColumns":99,"gridRows":1,"iconSizeDp":300,"fontScale":9,"homePages":0,"dockSize":9}""",
        ).getOrThrow()
        assertEquals(7, decoded.gridColumns)
        assertEquals(3, decoded.gridRows)
        assertEquals(80, decoded.iconSizeDp)
        assertEquals(1.6f, decoded.fontScale)
        assertEquals(1, decoded.homePages)
        assertEquals(6, decoded.dockSize)
    }

    @Test
    fun `unknown keys are ignored and newer schema rejected`() {
        assertTrue(SettingsCodec.decode("""{"futureOption":true}""").isSuccess)
        assertTrue(SettingsCodec.decode("""{"schemaVersion":99}""").isFailure)
        assertTrue(SettingsCodec.decode("not json").isFailure)
    }

    @Test
    fun `passphrase backup roundtrip and integrity`() {
        val settings = NexusSettings(autoLayoutApplied = true)
        val backup = payload(settings)
        val raw = BackupCodec.createPassphraseBackup(backup, "nexus-secret")

        val envelope = BackupCodec.parseEnvelope(raw).getOrThrow()
        assertEquals(BackupCryptMode.PASSPHRASE, envelope.mode)
        assertEquals(64, envelope.plaintextSha256.length)

        val opened = BackupCodec.openPassphraseBackup(raw, "nexus-secret").getOrThrow()
        assertEquals("Test", opened.notes.single().title)
        assertTrue(opened.settings.autoLayoutApplied)

        assertTrue(BackupCodec.openPassphraseBackup(raw, "wrong-pass").isFailure)
    }

    @Test
    fun `tampered backup fails verification`() {
        val raw = BackupCodec.createPassphraseBackup(payload(), "nexus-secret")
        val text = raw.toString(Charsets.UTF_8)
        val envelope = BackupCodec.parseEnvelope(raw).getOrThrow()
        val cipher = Base64.getDecoder().decode(envelope.ciphertextBase64)
        cipher[cipher.lastIndex] = (cipher.last().toInt() xor 0xFF).toByte()
        val tampered = envelope.copy(ciphertextBase64 = Base64.getEncoder().encodeToString(cipher))
        assertTrue(text.isNotEmpty())
        assertTrue(
            BackupCodec.openPassphraseBackup(BackupCodec.encodeEnvelope(tampered), "nexus-secret").isFailure,
        )
    }

    @Test
    fun `device key backups can be decrypted by injected key holder`() {
        val backup = payload()
        val plaintext = BackupCodec.payloadJson(backup)
        val ciphertext = ByteArray(32) { it.toByte() }
        val envelope = BackupCodec.buildEnvelope(
            payload = backup,
            mode = BackupCryptMode.DEVICE_KEY,
            kdf = BackupCodec.DEVICE_KEY_KDF,
            salt = ByteArray(0),
            iv = ByteArray(12) { 1 },
            ciphertext = ciphertext,
        )
        val raw = BackupCodec.encodeEnvelope(envelope)
        val opened = BackupCodec.openDeviceKeyBackup(raw) { _, _ -> plaintext }.getOrThrow()
        assertEquals(1, opened.notes.size)
        assertTrue(BackupCodec.openDeviceKeyBackup(raw) { _, _ -> plaintext.copyOf().also { it[0] = 0 } }.isFailure)
    }

    @Test
    fun `backup preview reports counts`() {
        val preview = BackupCodec.preview(payload())
        assertEquals(1, preview.noteCount)
        assertTrue(preview.compatible)
        assertEquals(0, preview.homeItems)
    }

    @Test
    fun `transfer over memory streams verifies payload`() {
        val original = "contenuto riservato del backup".toByteArray()
        val out = java.io.ByteArrayOutputStream()
        TransferProtocol.send(out, original, "482913", "1.0.0", 1)
        val received = TransferProtocol.receive(java.io.ByteArrayInputStream(out.toByteArray()), "482913")
        assertTrue(original.contentEquals(received.payload))
        assertEquals("1.0.0", received.appVersion)
        assertEquals(1, received.schemaVersion)
    }

    @Test
    fun `transfer rejects wrong code and tampered payload`() {
        val out = java.io.ByteArrayOutputStream()
        TransferProtocol.send(out, "payload".toByteArray(), "482913", "1.0.0", 1)
        val bytes = out.toByteArray()
        assertTrue(
            runCatching { TransferProtocol.receive(java.io.ByteArrayInputStream(bytes), "111111") }.isFailure,
        )
        val corrupted = bytes.copyOf()
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 0xFF).toByte()
        val failure = runCatching {
            TransferProtocol.receive(java.io.ByteArrayInputStream(corrupted), "482913")
        }.exceptionOrNull()
        assertTrue(failure is TransferException)
    }

    @Test
    fun `transfer rejects unsupported protocol header`() {
        val bad = java.io.ByteArrayOutputStream()
        bad.write("NEXUSXFER1".toByteArray())
        java.io.DataOutputStream(bad).writeInt(999_999)
        assertTrue(
            runCatching {
                TransferProtocol.receive(java.io.ByteArrayInputStream(bad.toByteArray()), "123456")
            }.isFailure,
        )
    }

    @Test
    fun `version comparison detects upgrades`() {
        assertTrue(NexusVersioning.isNewer("v1.1.0", "1.0.0"))
        assertFalse(NexusVersioning.isNewer("1.0.0", "1.0.0"))
        assertFalse(NexusVersioning.isNewer("v0.9.9", "1.0.0"))
        assertEquals(SemanticVersion(2, 3, 0), NexusVersioning.parse("v2.3"))
    }
}

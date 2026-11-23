package com.nexus.core.common

import com.nexus.core.model.NEXUS_SCHEMA_VERSION_SUPPORTED_MAX
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

class TransferException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class TransferPackage(
    val payload: ByteArray,
    val appVersion: String,
    val schemaVersion: Int,
)

@Serializable
internal data class TransferHandshake(
    val protocol: Int,
    val appVersion: String,
    val schemaVersion: Int,
    val saltBase64: String,
    val ivBase64: String,
    val cipherLength: Int,
    val plaintextSha256: String,
)

/**
 * Length-prefixed, AES-GCM protected transfer used by the "direct local transfer"
 * feature. Both devices share a short-lived six digit code displayed on the receiver.
 * The code is never sent on the wire: it is only used as the PBKDF2 passphrase, so a
 * wrong code fails the GCM authentication tag. The protocol is transport agnostic and
 * is covered by JVM loopback tests.
 */
object TransferProtocol {
    const val PROTOCOL_VERSION = 1
    const val MAX_PAYLOAD_BYTES: Int = 32 * 1024 * 1024
    private const val MAX_HEADER_BYTES = 8 * 1024
    private val magic = "NEXUSXFER1".toByteArray(Charsets.US_ASCII)
    private val codePattern = Regex("\\d{6}")

    fun send(
        outputStream: OutputStream,
        payload: ByteArray,
        code: String,
        appVersion: String,
        schemaVersion: Int,
    ) {
        require(codePattern.matches(code)) { "Il codice deve avere 6 cifre" }
        require(payload.isNotEmpty()) { "Payload vuoto" }
        require(payload.size <= MAX_PAYLOAD_BYTES) { "Payload troppo grande" }
        val encrypted = BackupCrypto.encrypt(payload, code.toCharArray())
        val handshake = TransferHandshake(
            protocol = PROTOCOL_VERSION,
            appVersion = appVersion,
            schemaVersion = schemaVersion,
            saltBase64 = Base64.getEncoder().encodeToString(encrypted.salt),
            ivBase64 = Base64.getEncoder().encodeToString(encrypted.iv),
            cipherLength = encrypted.ciphertext.size,
            plaintextSha256 = BackupCrypto.sha256(payload),
        )
        val header = NexusJson.instance
            .encodeToString(TransferHandshake.serializer(), handshake)
            .toByteArray(Charsets.UTF_8)
        require(header.size <= MAX_HEADER_BYTES) { "Header troppo grande" }
        val data = DataOutputStream(outputStream)
        data.write(magic)
        data.writeInt(header.size)
        data.write(header)
        data.write(encrypted.ciphertext)
        data.flush()
    }

    fun receive(inputStream: InputStream, expectedCode: String): TransferPackage {
        require(codePattern.matches(expectedCode)) { "Codice di ricezione non valido" }
        return try {
            val data = DataInputStream(inputStream)
            val receivedMagic = ByteArray(magic.size)
            data.readFully(receivedMagic)
            if (!receivedMagic.contentEquals(magic)) {
                throw TransferException("Dispositivo non compatibile con Nexus Transfer")
            }
            val headerSize = data.readInt()
            if (headerSize <= 0 || headerSize > MAX_HEADER_BYTES) {
                throw TransferException("Header di trasferimento non valido")
            }
            val headerBytes = ByteArray(headerSize)
            data.readFully(headerBytes)
            val handshake = runCatching {
                NexusJson.instance.decodeFromString(
                    TransferHandshake.serializer(),
                    headerBytes.toString(Charsets.UTF_8),
                )
            }.getOrElse { throw TransferException("Header di trasferimento illeggibile", it) }

            if (handshake.protocol != PROTOCOL_VERSION) {
                throw TransferException("Versione del protocollo non supportata")
            }
            if (handshake.schemaVersion !in 1..NEXUS_SCHEMA_VERSION_SUPPORTED_MAX) {
                throw TransferException("Backup creato con una versione di Nexus non compatibile")
            }
            if (handshake.cipherLength < 17 || handshake.cipherLength > MAX_PAYLOAD_BYTES + 64) {
                throw TransferException("Dimensione del trasferimento non valida")
            }
            val ciphertext = ByteArray(handshake.cipherLength)
            data.readFully(ciphertext)

            val salt = decodeBase64(handshake.saltBase64, "salt")
            val iv = decodeBase64(handshake.ivBase64, "iv")
            val plaintext = try {
                BackupCrypto.decrypt(
                    BackupCrypto.Encrypted(salt = salt, iv = iv, ciphertext = ciphertext),
                    expectedCode.toCharArray(),
                )
            } catch (failure: Exception) {
                throw TransferException("Codice errato o dati corrotti", failure)
            }
            val digest = BackupCrypto.sha256(plaintext)
            if (!BackupCrypto.constantTimeEquals(digest, handshake.plaintextSha256)) {
                throw TransferException("Verifica di integrità fallita")
            }
            TransferPackage(
                payload = plaintext,
                appVersion = handshake.appVersion,
                schemaVersion = handshake.schemaVersion,
            )
        } catch (failure: TransferException) {
            throw failure
        } catch (failure: Exception) {
            throw TransferException("Trasferimento interrotto o non valido", failure)
        }
    }

    fun generateCode(random: java.security.SecureRandom = java.security.SecureRandom()): String =
        String.format(java.util.Locale.US, "%06d", random.nextInt(1_000_000))

    private fun decodeBase64(value: String, label: String): ByteArray = runCatching {
        Base64.getDecoder().decode(value)
    }.getOrElse { throw TransferException("Campo $label non valido", it) }
}

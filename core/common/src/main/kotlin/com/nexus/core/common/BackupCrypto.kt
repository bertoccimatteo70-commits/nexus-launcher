package com.nexus.core.common

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    const val KDF_DESCRIPTION = "PBKDF2WithHmacSHA256/120000"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private val secureRandom = SecureRandom()

    data class Encrypted(
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val specification = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(specification).encoded, "AES")
        } finally {
            specification.clearPassword()
        }
    }

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): Encrypted {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(passphrase, salt),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return Encrypted(salt = salt, iv = iv, ciphertext = cipher.doFinal(plaintext))
    }

    fun decrypt(encrypted: Encrypted, passphrase: CharArray): ByteArray {
        require(encrypted.iv.size == IV_BYTES) { "Vettore di inizializzazione non valido" }
        require(encrypted.salt.isNotEmpty()) { "Salt mancante" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(passphrase, encrypted.salt),
            GCMParameterSpec(GCM_TAG_BITS, encrypted.iv),
        )
        return cipher.doFinal(encrypted.ciphertext)
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    fun constantTimeEquals(first: String, second: String): Boolean =
        MessageDigest.isEqual(first.toByteArray(Charsets.UTF_8), second.toByteArray(Charsets.UTF_8))
}

package com.nexus.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.nexus.core.common.NexusJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256/GCM keys generated and kept inside the Android Keystore. Keys never leave
 * the secure hardware/OS keystore and no password is stored by the app.
 */
class CryptoStore {
    data class CipherText(val iv: ByteArray, val ciphertext: ByteArray)

    @Synchronized
    private fun secretKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(alias: String, plaintext: ByteArray): CipherText {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(alias))
        return CipherText(iv = cipher.iv, ciphertext = cipher.doFinal(plaintext))
    }

    fun decrypt(alias: String, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(alias), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun delete(alias: String) {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}

/** Simple encrypted file container: `[version][12 byte IV][ciphertext]`. */
class EncryptedFileStore(
    private val directory: File,
    private val crypto: CryptoStore,
) {
    fun read(fileName: String, alias: String): ByteArray? {
        val file = File(directory, fileName)
        if (!file.exists()) return null
        val raw = file.readBytes()
        require(raw.size > IV_BYTES + 1) { "File cifrato corrotto: $fileName" }
        require(raw[0].toInt() == FORMAT_VERSION) { "Versione file cifrata non supportata" }
        val iv = raw.copyOfRange(1, IV_BYTES + 1)
        val ciphertext = raw.copyOfRange(IV_BYTES + 1, raw.size)
        return crypto.decrypt(alias, iv, ciphertext)
    }

    fun write(fileName: String, alias: String, plaintext: ByteArray) {
        if (!directory.exists() && !directory.mkdirs()) error("Impossibile creare la cartella dati")
        val encrypted = crypto.encrypt(alias, plaintext)
        val payload = ByteArray(1 + encrypted.iv.size + encrypted.ciphertext.size)
        payload[0] = FORMAT_VERSION.toByte()
        encrypted.iv.copyInto(payload, 1)
        encrypted.ciphertext.copyInto(payload, 1 + encrypted.iv.size)
        atomicWrite(File(directory, fileName), payload)
    }

    fun quarantine(fileName: String): File? {
        val source = File(directory, fileName)
        if (!source.exists()) return null
        val destination = File(directory, "$fileName.corrupt-${System.currentTimeMillis()}")
        return if (source.renameTo(destination)) destination else null
    }

    private companion object {
        const val IV_BYTES = 12
        const val FORMAT_VERSION = 1
    }
}

/** Typed JSON document persisted through [EncryptedFileStore]. */
class SecureJsonStore<T>(
    directory: File,
    private val fileName: String,
    private val alias: String,
    crypto: CryptoStore,
    private val serializer: KSerializer<T>,
) {
    private val files = EncryptedFileStore(directory, crypto)

    fun read(): T? = files.read(fileName, alias)?.let {
        NexusJson.instance.decodeFromString(serializer, it.toString(Charsets.UTF_8))
    }

    fun write(value: T) {
        files.write(
            fileName,
            alias,
            NexusJson.instance.encodeToString(serializer, value).toByteArray(Charsets.UTF_8),
        )
    }

    fun quarantine(): File? = files.quarantine(fileName)
}

/** Writes through a temporary file and fsync so a crash cannot leave a torn document. */
internal fun atomicWrite(target: File, bytes: ByteArray) {
    target.parentFile?.mkdirs()
    val temp = File(target.parentFile, "${target.name}.tmp")
    FileOutputStream(temp).use { output ->
        output.write(bytes)
        output.flush()
        output.fd.sync()
    }
    if (!temp.renameTo(target)) {
        target.delete()
        check(temp.renameTo(target)) { "Impossibile salvare ${target.name}" }
    }
}

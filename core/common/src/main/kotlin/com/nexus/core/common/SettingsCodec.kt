package com.nexus.core.common

import com.nexus.core.model.NEXUS_SCHEMA_VERSION_SUPPORTED_MAX
import com.nexus.core.model.NexusSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object NexusJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowTrailingComma = true
        classDiscriminator = "type"
    }
}

/**
 * Versioned settings document. Unknown keys are ignored so a newer minor version can
 * be read, while a schema newer than the running build is rejected instead of being
 * silently degraded. Everything is sanitized into supported ranges after decoding.
 */
object SettingsCodec {
    fun encode(settings: NexusSettings): String =
        NexusJson.instance.encodeToString(NexusSettings.serializer(), settings.sanitized())

    fun decode(raw: String): Result<NexusSettings> = runCatching {
        val element = NexusJson.instance.parseToJsonElement(raw)
        require(element is kotlinx.serialization.json.JsonObject) { "Configurazione non valida" }
        val version = element["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        require(isSupported(version)) {
            "Configurazione creata con una versione più recente (schema $version). Aggiorna Nexus."
        }
        val decoded = NexusJson.instance.decodeFromJsonElement(NexusSettings.serializer(), element.jsonObject)
        decoded.sanitized()
    }

    fun isSupported(schemaVersion: Int): Boolean =
        schemaVersion in 1..NEXUS_SCHEMA_VERSION_SUPPORTED_MAX
}

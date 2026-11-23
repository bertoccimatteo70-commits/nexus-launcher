package com.nexus.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal, dependency-free HTTP GET with explicit timeouts and connectivity check. */
object NexusHttp {
    const val USER_AGENT = "NexusLauncher/1.0 (Android)"

    fun get(url: String, userAgent: String = USER_AGENT): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val details = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText().take(200) }
                    .orEmpty()
                throw IOException("HTTP $code ${details.take(120)}".trim())
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection?.disconnect()
        }
    }

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

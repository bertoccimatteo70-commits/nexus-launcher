package com.nexus.core.common

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        val majorCompare = major.compareTo(other.major)
        if (majorCompare != 0) return majorCompare
        val minorCompare = minor.compareTo(other.minor)
        if (minorCompare != 0) return minorCompare
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"
}

object NexusVersioning {
    fun parse(value: String): SemanticVersion? {
        val cleaned = value.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        val parts = cleaned.split('.')
        if (parts.size < 2) return null
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        if (major < 0 || minor < 0 || patch < 0) return null
        return SemanticVersion(major, minor, patch)
    }

    fun isNewer(remoteTag: String, localVersion: String): Boolean {
        val remote = parse(remoteTag) ?: return false
        val local = parse(localVersion) ?: return false
        return remote > local
    }
}

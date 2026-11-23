package com.nexus.core.model

object NexusProject {
    const val OWNER = "bertoccimatteo70-commits"
    const val REPO = "nexus-launcher"
    const val URL = "https://github.com/$OWNER/$REPO"
    const val RELEASES_URL = "$URL/releases"
    const val ISSUES_URL = "$URL/issues"
    const val RELEASES_API = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10"
}

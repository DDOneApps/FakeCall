package com.upnp.fakeCall

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object RateLimited : UpdateCheckResult
    data object Unavailable : UpdateCheckResult
}

class UpdateChecker {
    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FakeCall-Android")
            }

            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            when (responseCode) {
                200 -> {
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name").orEmpty()
                    val htmlUrl = json.optString("html_url").ifBlank { LATEST_RELEASE_WEB }
                    if (tag.isBlank()) {
                        UpdateCheckResult.Unavailable
                    } else {
                        val updateAvailable = isRemoteNewer(tag, currentVersionName)
                        if (updateAvailable) {
                            UpdateCheckResult.UpdateAvailable(
                                release = ReleaseInfo(tagName = tag, htmlUrl = htmlUrl)
                            )
                        } else {
                            UpdateCheckResult.UpToDate
                        }
                    }
                }

                403 -> UpdateCheckResult.RateLimited
                else -> UpdateCheckResult.Unavailable
            }
        }.getOrElse {
            UpdateCheckResult.Unavailable
        }
    }

    private fun isRemoteNewer(remoteVersion: String, localVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteVersion)
        val localParts = parseVersionParts(localVersion)
        val maxSize = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until maxSize) {
            val remote = remoteParts.getOrElse(index) { 0 }
            val local = localParts.getOrElse(index) { 0 }
            if (remote > local) return true
            if (remote < local) return false
        }
        return false
    }

    private fun parseVersionParts(version: String): List<Int> {
        return version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull() ?: 0 }
            .ifEmpty { listOf(0) }
    }

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/DDOneApps/FakeCall/releases/latest"
        private const val LATEST_RELEASE_WEB = "https://github.com/DDOneApps/FakeCall/releases/latest"
    }
}

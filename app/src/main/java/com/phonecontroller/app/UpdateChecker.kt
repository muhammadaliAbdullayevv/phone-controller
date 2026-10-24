package com.phonecontroller.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String
)

object UpdateChecker {
    // Public repo, no auth needed to read releases.
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/muhammadaliAbdullayevv/phone-controller/releases/latest"

    fun currentVersionName(context: Context): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }

    /** Blocking network call -- run this off the main thread. Returns null on any failure or if already up to date. */
    fun checkForUpdate(context: Context): UpdateInfo? {
        val json = try {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } catch (e: Exception) {
            return null
        }

        val latestVersion = json.optString("tag_name").removePrefix("v")
        if (latestVersion.isBlank() || latestVersion == currentVersionName(context)) return null

        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk")) {
                val url = asset.optString("browser_download_url")
                if (url.isNotBlank()) {
                    return UpdateInfo(versionName = latestVersion, downloadUrl = url)
                }
            }
        }
        return null
    }
}

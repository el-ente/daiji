package io.github.elente.daiji.update

import io.github.elente.daiji.BuildConfig
import io.github.elente.daiji.data.DaijiDirs
import io.github.elente.daiji.net.Downloader
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A release on GitHub newer than the running build. */
data class Release(val version: String, val apkUrl: String)

/** Daiji's own updates come from the latest GitHub release, which CI publishes for every `v*` tag. */
object AppUpdater {
    private const val LATEST_URL = "https://api.github.com/repos/el-ente/daiji/releases/latest"
    private const val TIMEOUT_MS = 15_000

    /** The latest release when it is newer than this build, null when this build is current. Throws when offline. */
    fun newerRelease(): Release? {
        val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        val json = try {
            // No release published yet.
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode} for $LATEST_URL")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
        val version = json.getString("tag_name").removePrefix("v")
        if (compareVersions(version, BuildConfig.VERSION_NAME) <= 0) return null
        val assets = json.getJSONArray("assets")
        val apkUrl = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?.getString("browser_download_url")
            ?: throw IOException("Release $version has no APK")
        return Release(version, apkUrl)
    }

    fun apkFile(release: Release): File = File(DaijiDirs.downloads, "Daiji_${release.version}.apk")

    fun download(release: Release, onProgress: (Long, Long) -> Unit): File {
        val apk = apkFile(release)
        if (!apk.exists() && !Downloader.download(release.apkUrl, apk, onProgress)) throw IOException("APK not found: ${release.apkUrl}")
        return apk
    }

    /** Compares dotted versions like `0.10.1` number by number; missing parts count as zero. */
    internal fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
}

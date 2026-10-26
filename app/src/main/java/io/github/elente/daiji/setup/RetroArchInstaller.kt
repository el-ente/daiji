package io.github.elente.daiji.setup

import android.content.Context
import android.os.Build
import io.github.elente.daiji.data.DaijiDirs
import io.github.elente.daiji.net.Downloader
import java.io.File

object RetroArchInstaller {
    const val VERSION = "1.22.2"
    private const val BASE_URL = "https://buildbot.libretro.com/stable/$VERSION/android"

    val apkFile: File get() = File(DaijiDirs.downloads, "RetroArch_$VERSION.apk")

    fun download(onProgress: (Long, Long) -> Unit) {
        if (apkFile.exists()) return
        val url = "$BASE_URL/${apkName()}"
        if (!Downloader.download(url, apkFile, onProgress)) error("RetroArch $VERSION is not on the buildbot: $url")
    }

    fun install(context: Context) = ApkInstaller.install(context, apkFile)

    private fun apkName(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "RetroArch_aarch64.apk"
        "armeabi-v7a" -> "RetroArch_ra32.apk"
        else -> "RetroArch.apk"
    }
}

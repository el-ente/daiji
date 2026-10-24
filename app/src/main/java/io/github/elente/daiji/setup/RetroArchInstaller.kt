package io.github.elente.daiji.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.elente.daiji.data.DaijiDirs
import io.github.elente.daiji.net.Downloader
import java.io.File

object RetroArchInstaller {
    const val VERSION = "1.22.2"
    private const val BASE_URL = "https://buildbot.libretro.com/stable/$VERSION/android"
    private const val APK_MIME = "application/vnd.android.package-archive"

    val apkFile: File get() = File(DaijiDirs.downloads, "RetroArch_$VERSION.apk")

    fun download(onProgress: (Long, Long) -> Unit) {
        if (apkFile.exists()) return
        val url = "$BASE_URL/${apkName()}"
        if (!Downloader.download(url, apkFile, onProgress)) error("RetroArch $VERSION is not on the buildbot: $url")
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkFile)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private fun apkName(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "RetroArch_aarch64.apk"
        "armeabi-v7a" -> "RetroArch_ra32.apk"
        else -> "RetroArch.apk"
    }
}

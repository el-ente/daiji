package io.github.elente.daiji.setup

import android.os.Build
import io.github.elente.daiji.data.DaijiDirs
import io.github.elente.daiji.data.GameSystem
import io.github.elente.daiji.net.Downloader
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/** Fetches libretro cores from the buildbot into the shared folder RetroArch imports from. */
object CoreDownloader {
    private const val BASE_URL = "https://buildbot.libretro.com/nightly/android/latest"

    fun coreFileName(core: String): String = "${core}_libretro_android.so"

    fun coresFor(systems: List<GameSystem>): List<String> =
        systems.mapNotNull { it.retroArchCore }.distinct()

    fun isDownloaded(core: String): Boolean = File(DaijiDirs.cores, coreFileName(core)).exists()

    fun download(core: String) {
        val dest = File(DaijiDirs.cores, coreFileName(core))
        if (dest.exists()) return
        val zip = File(DaijiDirs.downloads, "${coreFileName(core)}.zip")
        val url = "$BASE_URL/${Build.SUPPORTED_ABIS.first()}/${zip.name}"
        if (!Downloader.download(url, zip)) throw IOException("No build of $core for this device: $url")
        try {
            unzipCore(zip, dest)
        } finally {
            zip.delete()
        }
    }

    private fun unzipCore(zip: File, dest: File) {
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            generateSequence { input.nextEntry }.firstOrNull { it.name.endsWith(".so") }
                ?: throw IOException("${zip.name} contains no core")
            dest.parentFile?.mkdirs()
            dest.outputStream().use { input.copyTo(it) }
        }
    }
}

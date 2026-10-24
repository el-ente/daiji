package io.github.elente.daiji.setup

import android.net.Uri
import io.github.elente.daiji.data.DaijiDirs
import io.github.elente.daiji.data.GameSystem
import io.github.elente.daiji.net.Downloader
import java.io.File

/** Fetches libretro-database `.rdb` files, which hold release year, genre, developer and players. */
object DatabaseDownloader {
    private const val BASE_URL = "https://raw.githubusercontent.com/libretro/libretro-database/master/rdb"

    fun databasesFor(systems: List<GameSystem>): List<String> =
        systems.mapNotNull { it.retroArchName }.distinct()

    fun isDownloaded(name: String): Boolean = file(name).exists()

    /** Returns false when libretro has no database under that name. */
    fun download(name: String): Boolean {
        val dest = file(name)
        if (dest.exists()) return true
        return Downloader.download("$BASE_URL/${Uri.encode(name)}.rdb", dest)
    }

    private fun file(name: String) = File(DaijiDirs.databases, "$name.rdb")
}

package io.github.elente.daiji.setup

import android.net.Uri
import io.github.elente.daiji.data.DaijiDirs
import io.github.elente.daiji.data.Game
import io.github.elente.daiji.data.RetroArchNames
import io.github.elente.daiji.data.Systems
import io.github.elente.daiji.net.Downloader
import java.io.File
import java.io.IOException

/** Downloads box art from libretro-thumbnails, named the way RetroArch expects. */
object ThumbnailScraper {
    private const val BASE_URL = "https://thumbnails.libretro.com"
    private const val MAX_CONSECUTIVE_FAILURES = 5

    fun missingCovers(games: List<Game>): List<Game> =
        games.filter { it.cover == null && Systems.byId(it.systemId)?.retroArchName != null }

    /** Returns how many covers were found. Gives up after repeated network failures. */
    fun scrape(games: List<Game>, onProgress: (done: Int, total: Int) -> Unit): Int {
        var found = 0
        var failures = 0
        games.forEachIndexed { index, game ->
            try {
                if (scrapeCover(game)) found++
                failures = 0
            } catch (e: IOException) {
                if (++failures >= MAX_CONSECUTIVE_FAILURES) throw e
            }
            onProgress(index + 1, games.size)
        }
        return found
    }

    private fun scrapeCover(game: Game): Boolean {
        val systemName = Systems.byId(game.systemId)?.retroArchName ?: return false
        val label = File(game.path).nameWithoutExtension
        val dest = File(DaijiDirs.thumbnails, "$systemName/Named_Boxarts/${RetroArchNames.thumbnail(label)}.png")
        if (dest.exists()) return true
        return candidates(label).any { Downloader.download(url(systemName, it), dest) }
    }

    private fun candidates(label: String): List<String> =
        listOfNotNull(label, RetroArchNames.shortName(label)).map(RetroArchNames::thumbnail)

    private fun url(systemName: String, fileName: String): String =
        "$BASE_URL/${Uri.encode(systemName)}/Named_Boxarts/${Uri.encode(fileName)}.png"
}

package io.github.elente.daiji.setup

import android.net.Uri
import io.github.elente.daiji.data.AcclaimedEntry
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

    /** A box art to fetch: the libretro-thumbnails system folder and the name to look it up by. */
    data class CoverRequest(val systemName: String, val label: String)

    fun missingCovers(games: List<Game>): List<CoverRequest> = games
        .filter { it.cover == null }
        .mapNotNull { game -> request(game.systemId, File(game.path).nameWithoutExtension) }

    /** Covers for acclaimed games the user doesn't own, so the Acclaimed view shows their box art too. */
    fun missingAcclaimedCovers(entries: List<AcclaimedEntry>): List<CoverRequest> = entries
        .mapNotNull { request(it.systemId, it.name) }
        .filterNot { DaijiDirs.boxArt(it.systemName, it.label).exists() }

    /** Returns how many covers were found. Gives up after repeated network failures. */
    fun scrape(requests: List<CoverRequest>, onProgress: (done: Int, total: Int) -> Unit): Int {
        var found = 0
        var failures = 0
        requests.forEachIndexed { index, request ->
            try {
                if (scrapeCover(request)) found++
                failures = 0
            } catch (e: IOException) {
                if (++failures >= MAX_CONSECUTIVE_FAILURES) throw e
            }
            onProgress(index + 1, requests.size)
        }
        return found
    }

    private fun request(systemId: String, label: String): CoverRequest? =
        Systems.byId(systemId)?.retroArchName?.let { CoverRequest(it, label) }

    private fun scrapeCover(request: CoverRequest): Boolean {
        val dest = DaijiDirs.boxArt(request.systemName, request.label)
        if (dest.exists()) return true
        return candidates(request.label).any { Downloader.download(url(request.systemName, it), dest) }
    }

    private fun candidates(label: String): List<String> =
        listOfNotNull(label, RetroArchNames.shortName(label)).map(RetroArchNames::thumbnail)

    private fun url(systemName: String, fileName: String): String =
        "$BASE_URL/${Uri.encode(systemName)}/Named_Boxarts/${Uri.encode(fileName)}.png"
}

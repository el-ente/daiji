package io.github.elente.daiji.data

import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class PlayStats(val runtimeSeconds: Long, val lastPlayed: Long?)

/** Reads RetroArch's `.lrtl` runtime logs, written per core and game under `playlists/logs`. */
object RetroArchPlayLog {
    private val logDirs: List<File>
        get() = listOf(DaijiDirs.playlists, File(Environment.getExternalStorageDirectory(), "RetroArch/playlists"))
            .map { File(it, "logs") }

    /** Keyed by ROM file name without extension, lowercase. Sessions from several cores add up. */
    fun read(): Map<String, PlayStats> {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val stats = HashMap<String, PlayStats>()
        logDirs.asSequence()
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension == "lrtl" }
            .forEach { file -> parse(file, format)?.let { stats.merge(file.nameWithoutExtension.lowercase(), it, ::combine) } }
        return stats
    }

    private fun combine(a: PlayStats, b: PlayStats) = PlayStats(
        runtimeSeconds = a.runtimeSeconds + b.runtimeSeconds,
        lastPlayed = maxOf(a.lastPlayed ?: 0, b.lastPlayed ?: 0).takeIf { it > 0 },
    )

    private fun parse(file: File, format: SimpleDateFormat): PlayStats? = runCatching {
        val log = JSONObject(file.readText())
        PlayStats(
            runtimeSeconds = seconds(log.optString("runtime")),
            lastPlayed = runCatching { format.parse(log.optString("last_played"))?.time }.getOrNull()?.takeIf { it > 0 },
        )
    }.getOrNull()

    /** "01:02:03" becomes 3723. */
    private fun seconds(clock: String): Long =
        clock.split(':').fold(0L) { total, part -> total * 60 + (part.toLongOrNull() ?: 0) }
}

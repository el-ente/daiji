package io.github.elente.daiji.data

import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * Scans a ROMs folder laid out as one subfolder per system, e.g. `ROMs/snes/Game.sfc`.
 * Plain file access is used on purpose: it is much faster than the Storage Access
 * Framework, and RetroArch needs real file paths anyway.
 */
object RomScanner {
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp")
    private val tagPattern = Regex("""\s*[(\[][^)\]]*[)\]]""")

    private val rootNames = listOf(
        "ROMs", "Roms", "roms", "Games", "games",
        "Emulation/roms", "Emulation/ROMs", "RetroArch/roms",
    )

    /** Internal storage first, then SD cards and USB drives. */
    fun volumes(): List<File> {
        val external = File("/storage").listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name != "emulated" && it.name != "self" }
            .sortedBy { it.name }
        return listOf(Environment.getExternalStorageDirectory()) + external
    }

    /** ROM folders that already exist on internal storage, SD cards or USB drives. */
    fun candidateRoots(): List<File> =
        volumes()
            .flatMap { volume -> rootNames.map { File(volume, it) } }
            .filter { it.isDirectory }
            .distinctBy { it.path.lowercase() }

    fun scan(root: File): List<Game> {
        val esdeMedia = File(Environment.getExternalStorageDirectory(), "ES-DE/downloaded_media")
        val retroArch = File(Environment.getExternalStorageDirectory(), "RetroArch")
        val result = ArrayList<Game>()
        val dirs = root.listFiles() ?: return emptyList()

        for (dir in dirs) {
            if (!dir.isDirectory) continue
            val system = Systems.byFolder(dir.name) ?: continue
            val files = dir.listFiles() ?: continue

            // Local media wins over ES-DE's downloaded media; RetroArch thumbnails are the last resort.
            val covers = indexImages(File(esdeMedia, "${dir.name}/covers")) +
                indexImages(File(dir, "media/covers"))
            val screenshots = indexImages(File(esdeMedia, "${dir.name}/screenshots")) +
                indexImages(File(dir, "media/screenshots"))
            val retroArchDir = system.retroArchName?.let { File(retroArch, "thumbnails/$it") }
            val daijiDir = system.retroArchName?.let { File(DaijiDirs.thumbnails, it) }
            val playlistLabels = system.retroArchName?.let { playlistLabels(File(retroArch, "playlists/$it.lpl")) }.orEmpty()
            val retroArchCovers = indexImages(retroArchDir?.resolve("Named_Boxarts")) +
                indexImages(daijiDir?.resolve("Named_Boxarts"))
            val retroArchScreenshots = indexImages(retroArchDir?.resolve("Named_Titles")) +
                indexImages(retroArchDir?.resolve("Named_Snaps"))

            // Multi-disc games: hide the discs an .m3u playlist already lists.
            val listedInM3u = HashSet<String>()
            for (file in files) {
                if (!file.extension.equals("m3u", ignoreCase = true)) continue
                runCatching {
                    file.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .forEach { listedInM3u += File(it).name.lowercase() }
                }
            }

            for (file in files) {
                if (!file.isFile || file.name.startsWith(".")) continue
                if (file.extension.lowercase() !in system.extensions) continue
                if (file.name.lowercase() in listedInM3u) continue
                val base = file.nameWithoutExtension
                val label = playlistLabels[file.name.lowercase()]
                val thumbnailNames = retroArchThumbnailNames(label, base)
                result += Game(
                    path = file.path,
                    title = cleanTitle(label ?: base),
                    systemId = system.id,
                    cover = covers[base] ?: thumbnailNames.firstNotNullOfOrNull { retroArchCovers[it] },
                    screenshot = screenshots[base] ?: thumbnailNames.firstNotNullOfOrNull { retroArchScreenshots[it] },
                )
            }
        }

        return result.sortedWith(
            compareBy<Game>({ Systems.order(it.systemId) }, { it.title.lowercase() })
        )
    }

    private fun indexImages(dir: File?): Map<String, String> =
        dir?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in imageExtensions }
            ?.associate { it.nameWithoutExtension to it.path }
            ?: emptyMap()

    /** ROM file name (lowercase) to playlist label, read from a RetroArch `.lpl` JSON playlist. */
    private fun playlistLabels(playlist: File): Map<String, String> = runCatching {
        val items = JSONObject(playlist.readText()).getJSONArray("items")
        buildMap {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val label = item.optString("label")
                if (label.isBlank()) continue
                put(File(item.optString("path").substringBefore('#')).name.lowercase(), label)
            }
        }
    }.getOrDefault(emptyMap())

    /**
     * Thumbnails are named after the playlist label, or after the ROM file when
     * RetroArch's "Use Filenames For Thumbnails" setting is on.
     */
    private fun retroArchThumbnailNames(label: String?, fileName: String): List<String> =
        listOfNotNull(label, fileName).distinct().map(RetroArchNames::thumbnail)

    /** "Chrono Trigger (USA) [!]" becomes "Chrono Trigger". */
    fun cleanTitle(name: String): String {
        val cleaned = name.replace(tagPattern, "").replace('_', ' ').trim()
        return cleaned.ifEmpty { name }
    }
}

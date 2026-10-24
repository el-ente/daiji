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

    private val retroArchDir: File get() = File(Environment.getExternalStorageDirectory(), "RetroArch")
    private val esdeMedia: File get() = File(Environment.getExternalStorageDirectory(), "ES-DE/downloaded_media")

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
        val dirs = root.listFiles() ?: return emptyList()
        return dirs.filter { it.isDirectory }
            .flatMap { dir -> Systems.byFolder(dir.name)?.let { scanSystem(dir, it) }.orEmpty() }
            .sortedWith(compareBy<Game>({ Systems.order(it.systemId) }, { it.title.lowercase() }))
    }

    private fun scanSystem(dir: File, system: GameSystem): List<Game> {
        val files = dir.listFiles() ?: return emptyList()
        val roms = romFiles(files, system)
        val media = SystemMedia(dir, system)
        val labels = system.retroArchName?.let { playlistLabels(File(retroArchDir, "playlists/$it.lpl")) }.orEmpty()
        val info = databaseInfo(system, roms)
        return roms.map { file ->
            val base = file.nameWithoutExtension
            val label = labels[file.name.lowercase()]
            val thumbnailNames = retroArchThumbnailNames(label, base)
            Game(
                path = file.path,
                title = cleanTitle(label ?: base),
                systemId = system.id,
                cover = media.cover(base, thumbnailNames),
                screenshot = media.screenshot(base, thumbnailNames),
                info = info[base.lowercase()] ?: GameInfo.NONE,
            )
        }
    }

    private fun romFiles(files: Array<File>, system: GameSystem): List<File> {
        val discs = discsListedInM3u(files)
        return files.filter {
            it.isFile && !it.name.startsWith(".") &&
                it.extension.lowercase() in system.extensions &&
                it.name.lowercase() !in discs
        }
    }

    /** Multi-disc games: the discs an .m3u playlist lists are hidden behind it. */
    private fun discsListedInM3u(files: Array<File>): Set<String> =
        files.filter { it.extension.equals("m3u", ignoreCase = true) }
            .flatMap { m3u -> runCatching { m3u.readLines() }.getOrDefault(emptyList()) }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { File(it).name.lowercase() }
            .toSet()

    /** Local media wins over ES-DE's downloaded media; RetroArch thumbnails are the last resort. */
    private class SystemMedia(dir: File, system: GameSystem) {
        private val retroArchThumbs = system.retroArchName?.let { File(retroArchDir, "thumbnails/$it") }
        private val daijiThumbs = system.retroArchName?.let { File(DaijiDirs.thumbnails, it) }
        private val covers = indexImages(File(esdeMedia, "${dir.name}/covers")) + indexImages(File(dir, "media/covers"))
        private val screenshots = indexImages(File(esdeMedia, "${dir.name}/screenshots")) + indexImages(File(dir, "media/screenshots"))
        private val retroArchCovers = indexImages(retroArchThumbs?.resolve("Named_Boxarts")) +
            indexImages(daijiThumbs?.resolve("Named_Boxarts"))
        private val retroArchScreenshots = indexImages(retroArchThumbs?.resolve("Named_Titles")) +
            indexImages(retroArchThumbs?.resolve("Named_Snaps"))

        fun cover(base: String, thumbnailNames: List<String>): String? =
            covers[base] ?: thumbnailNames.firstNotNullOfOrNull { retroArchCovers[it] }

        fun screenshot(base: String, thumbnailNames: List<String>): String? =
            screenshots[base] ?: thumbnailNames.firstNotNullOfOrNull { retroArchScreenshots[it] }
    }

    private fun indexImages(dir: File?): Map<String, String> =
        dir?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in imageExtensions }
            ?.associate { it.nameWithoutExtension to it.path }
            ?: emptyMap()

    /** Daiji's own database copy first, then the one a stock RetroArch install keeps. */
    private fun databaseInfo(system: GameSystem, roms: List<File>): Map<String, GameInfo> {
        val name = system.retroArchName ?: return emptyMap()
        val database = listOf(DaijiDirs.databases, File(retroArchDir, "database/rdb"))
            .map { File(it, "$name.rdb") }
            .firstOrNull { it.isFile } ?: return emptyMap()
        return RetroArchDatabase.lookup(database, roms.map { it.nameWithoutExtension.lowercase() }.toSet())
    }

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

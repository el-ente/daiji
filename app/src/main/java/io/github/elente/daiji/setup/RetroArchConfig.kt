package io.github.elente.daiji.setup

import io.github.elente.daiji.data.DaijiDirs
import io.github.elente.daiji.data.Game
import io.github.elente.daiji.data.GameSystem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Writes the `retroarch.cfg` and `.lpl` playlists RetroArch is launched with. */
object RetroArchConfig {
    private val entryPattern = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*"?(.*?)"?\s*$""")

    fun corePath(retroArchPackage: String, core: String): String =
        "/data/data/$retroArchPackage/cores/${CoreDownloader.coreFileName(core)}"

    fun isWritten(): Boolean = DaijiDirs.retroArchConfig.exists()

    /** Keeps whatever RetroArch saved in the file and only overrides Daiji's own keys. */
    fun writeConfig() {
        val file = DaijiDirs.retroArchConfig
        val entries = LinkedHashMap<String, String>()
        if (file.exists()) {
            file.readLines().forEach { line ->
                entryPattern.matchEntire(line)?.let { entries[it.groupValues[1]] = it.groupValues[2] }
            }
        }
        entries += managedEntries()
        file.parentFile?.mkdirs()
        file.writeText(entries.entries.joinToString("\n", postfix = "\n") { "${it.key} = \"${it.value}\"" })
    }

    private fun managedEntries(): Map<String, String> {
        val dirs = listOf(DaijiDirs.cores, DaijiDirs.thumbnails, DaijiDirs.playlists) +
            listOf("saves", "states", "system").map { File(DaijiDirs.retroArch, it) }
        dirs.forEach { it.mkdirs() }
        return mapOf(
            "core_assets_directory" to DaijiDirs.cores.path,
            "thumbnails_directory" to DaijiDirs.thumbnails.path,
            "playlist_directory" to DaijiDirs.playlists.path,
            "savefile_directory" to File(DaijiDirs.retroArch, "saves").path,
            "savestate_directory" to File(DaijiDirs.retroArch, "states").path,
            "system_directory" to File(DaijiDirs.retroArch, "system").path,
            "menu_driver" to "ozone",
            "video_fullscreen" to "true",
            "quit_press_twice" to "false",
        )
    }

    fun writePlaylists(games: List<Game>, systems: List<GameSystem>, retroArchPackage: String) {
        DaijiDirs.playlists.mkdirs()
        for (system in systems) {
            val core = system.retroArchCore ?: continue
            val name = system.retroArchName ?: continue
            val items = games.filter { it.systemId == system.id }
            if (items.isEmpty()) continue
            File(DaijiDirs.playlists, "$name.lpl").writeText(playlist(items, core, name, retroArchPackage).toString(2))
        }
    }

    private fun playlist(games: List<Game>, core: String, dbName: String, retroArchPackage: String): JSONObject {
        val items = JSONArray()
        for (game in games) {
            items.put(
                JSONObject()
                    .put("path", game.path)
                    .put("label", File(game.path).nameWithoutExtension)
                    .put("core_path", corePath(retroArchPackage, core))
                    .put("core_name", core)
                    .put("crc32", "00000000|crc")
                    .put("db_name", "$dbName.lpl"),
            )
        }
        return JSONObject()
            .put("version", "1.5")
            .put("default_core_path", corePath(retroArchPackage, core))
            .put("default_core_name", core)
            .put("label_display_mode", 0)
            .put("right_thumbnail_mode", 0)
            .put("left_thumbnail_mode", 0)
            .put("sort_mode", 0)
            .put("items", items)
    }
}

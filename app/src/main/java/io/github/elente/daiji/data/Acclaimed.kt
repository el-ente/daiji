package io.github.elente.daiji.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** A game from the bundled acclaimed list. Arcade entries carry their romset, which is what their files are named after. */
data class AcclaimedEntry(val systemId: String, val name: String, val set: String?)

/** Acclaimed games split by whether the library has them. Ones it lacks come as previews, shown by title and cover only. */
data class AcclaimedView(val owned: List<Game>, val notOwned: List<Game>) {
    companion object {
        val EMPTY = AcclaimedView(emptyList(), emptyList())
    }
}

/**
 * The bundled list of critically acclaimed games, matched to library files by title without region or revision tags.
 * Arcade systems share keys, since their files are all named after the same romsets, so a game counts wherever it was filed.
 */
class AcclaimedGames(private val entries: List<AcclaimedEntry>) {
    private val keys: Set<String> = entries.mapTo(HashSet(), ::key)

    fun isAcclaimed(game: Game): Boolean = key(game) in keys

    /** Reads the SD card for covers, so call it off the main thread. */
    fun view(library: Library): AcclaimedView = AcclaimedView(
        owned = library.games.filter(::isAcclaimed).sortedBy { it.title },
        notOwned = missing(library)
            .sortedWith(compareBy({ Systems.order(it.systemId) }, { it.name }))
            .map(::previewGame),
    )

    /** Acclaimed games the library lacks, for the systems it has games for. */
    fun missing(library: Library): List<AcclaimedEntry> {
        val owned = library.games.mapTo(HashSet(), ::key)
        return entries.filter { it.systemId in library.bySystem && key(it) !in owned }
    }

    private fun previewGame(entry: AcclaimedEntry): Game {
        val cover = Systems.byId(entry.systemId)?.retroArchName?.let { DaijiDirs.boxArt(it, entry.name) }
        return Game(
            path = "acclaimed:${entry.systemId}:${entry.name}",
            title = RetroArchNames.shortName(entry.name) ?: entry.name,
            systemId = entry.systemId,
            cover = cover?.takeIf { it.exists() }?.path,
            screenshot = null,
        )
    }

    companion object {
        private const val ASSET = "acclaimed.json"
        private const val ARCADE = "arcade"
        private val arcadeSystems = setOf("arcade", "neogeo", "mame")
        val EMPTY = AcclaimedGames(emptyList())

        fun load(context: Context): AcclaimedGames = try {
            AcclaimedGames(parse(context.assets.open(ASSET).bufferedReader().use { it.readText() }))
        } catch (e: Exception) {
            EMPTY
        }

        private fun parse(json: String): List<AcclaimedEntry> {
            val root = JSONObject(json)
            return root.keys().asSequence().flatMap { systemId ->
                val array = root.getJSONArray(systemId)
                List(array.length()) { i ->
                    val o = array.getJSONObject(i)
                    AcclaimedEntry(systemId, o.getString("name"), o.optString("set").ifEmpty { null })
                }
            }.toList()
        }

        private fun key(game: Game): String = key(game.systemId, File(game.path).nameWithoutExtension)

        private fun key(entry: AcclaimedEntry): String = key(entry.systemId, entry.set ?: entry.name)

        /** The list spells titles the way box art is named, so file names get the same character swaps. */
        private fun key(systemId: String, label: String): String {
            val group = if (systemId in arcadeSystems) ARCADE else systemId
            val title = RetroArchNames.thumbnail(RetroArchNames.shortName(label) ?: label)
            return "$group:${title.lowercase()}"
        }
    }
}

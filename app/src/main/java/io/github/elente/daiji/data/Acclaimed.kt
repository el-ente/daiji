package io.github.elente.daiji.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** A game from the bundled acclaimed list. Arcade entries carry their romset, which is what their files are named after. */
data class AcclaimedEntry(val systemId: String, val name: String, val set: String?)

/**
 * The bundled list of critically acclaimed games, matched to library files by title without region or revision tags.
 * Arcade systems share keys, since their files are all named after the same romsets, so a game counts wherever it was filed.
 */
class AcclaimedGames(private val entries: List<AcclaimedEntry>) {
    private val keys: Set<String> = entries.mapTo(HashSet(), ::key)

    fun isAcclaimed(game: Game): Boolean = key(game) in keys

    /** Acclaimed games the library lacks, for the systems it has games for. */
    fun missing(library: Library): List<AcclaimedEntry> {
        val owned = library.games.mapTo(HashSet(), ::key)
        return entries.filter { it.systemId in library.bySystem && key(it) !in owned }
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

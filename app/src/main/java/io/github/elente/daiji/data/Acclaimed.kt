package io.github.elente.daiji.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** A game from the bundled acclaimed list. Arcade entries carry their romset, which is what their files are named after. */
data class AcclaimedEntry(val systemId: String, val name: String, val set: String?)

/**
 * The bundled list of critically acclaimed games, matched to library files by title without region or revision tags.
 * Systems that share box art (Neo Geo and Arcade) share keys, so a game counts wherever its file was filed.
 */
class AcclaimedGames(entries: List<AcclaimedEntry>) {
    private val keys: Set<String> = entries.mapNotNullTo(HashSet()) { key(it.systemId, it.set ?: it.name) }

    fun isAcclaimed(game: Game): Boolean = key(game.systemId, File(game.path).nameWithoutExtension) in keys

    companion object {
        private const val ASSET = "acclaimed.json"
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

        private fun key(systemId: String, label: String): String? {
            val boxArtSet = Systems.byId(systemId)?.retroArchName ?: return null
            val title = RetroArchNames.shortName(label) ?: label
            return "$boxArtSet:${title.lowercase()}"
        }
    }
}

package io.github.elente.daiji.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Release details from the libretro database. Fields the database lacks stay null. */
data class GameInfo(
    val year: Int? = null,
    val players: Int? = null,
    val genre: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
) {
    companion object {
        val NONE = GameInfo()
    }
}

data class Game(
    val path: String,
    val title: String,
    val systemId: String,
    val cover: String?,
    val screenshot: String?,
    val info: GameInfo = GameInfo.NONE,
) {
    val extension: String get() = path.substringAfterLast('.', "").uppercase()
}

/** Games grouped for quick lookups by the UI. */
class Library(val games: List<Game>) {
    val bySystem: Map<String, List<Game>> = games.groupBy { it.systemId }
    val byPath: Map<String, Game> = games.associateBy { it.path }
    val systems: List<GameSystem> = Systems.all.filter { bySystem.containsKey(it.id) }

    companion object {
        val EMPTY = Library(emptyList())
    }
}

/** Caches the last scan on disk so the launcher shows games instantly on cold start. */
object LibraryStore {
    fun read(file: File): List<Game> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val array = JSONArray(file.readText())
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Game(
                    path = o.getString("p"),
                    title = o.getString("t"),
                    systemId = o.getString("s"),
                    cover = if (o.has("c")) o.getString("c") else null,
                    screenshot = if (o.has("i")) o.getString("i") else null,
                    info = readInfo(o),
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun readInfo(o: JSONObject) = GameInfo(
        year = if (o.has("y")) o.getInt("y") else null,
        players = if (o.has("u")) o.getInt("u") else null,
        genre = o.optString("g").ifEmpty { null },
        developer = o.optString("d").ifEmpty { null },
        publisher = o.optString("b").ifEmpty { null },
    )

    private fun writeInfo(o: JSONObject, info: GameInfo) {
        info.year?.let { o.put("y", it) }
        info.players?.let { o.put("u", it) }
        info.genre?.let { o.put("g", it) }
        info.developer?.let { o.put("d", it) }
        info.publisher?.let { o.put("b", it) }
    }

    fun write(file: File, games: List<Game>) {
        val array = JSONArray()
        for (g in games) {
            val o = JSONObject()
                .put("p", g.path)
                .put("t", g.title)
                .put("s", g.systemId)
            g.cover?.let { o.put("c", it) }
            g.screenshot?.let { o.put("i", it) }
            writeInfo(o, g.info)
            array.put(o)
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

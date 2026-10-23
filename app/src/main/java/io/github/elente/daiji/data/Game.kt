package io.github.elente.daiji.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Game(
    val path: String,
    val title: String,
    val systemId: String,
    val cover: String?,
    val screenshot: String?,
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
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
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

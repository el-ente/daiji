package io.github.elente.daiji.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class SettingsSnapshot(
    val romRoot: String?,
    /** Emulator the user picked per system id. Missing means automatic. */
    val chosenEmulators: Map<String, String>,
    /** Installed package per emulator id. */
    val installedEmulators: Map<String, String>,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("daiji", Context.MODE_PRIVATE)

    var romRoot: String?
        get() = sp.getString(KEY_ROM_ROOT, null)
        set(value) = sp.edit { putString(KEY_ROM_ROOT, value) }

    fun chosenEmulators(): Map<String, String> =
        Systems.all.mapNotNull { s -> sp.getString(KEY_EMU + s.id, null)?.let { s.id to it } }.toMap()

    fun setEmulator(systemId: String, emulatorId: String) =
        sp.edit { putString(KEY_EMU + systemId, emulatorId) }

    /** Game path to last-played time in milliseconds. */
    fun recents(): Map<String, Long> = try {
        val o = JSONObject(sp.getString(KEY_RECENTS, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getLong(it) }
    } catch (e: Exception) {
        emptyMap()
    }

    fun markPlayed(path: String) {
        val updated = (recents() + (path to System.currentTimeMillis()))
            .entries
            .sortedByDescending { it.value }
            .take(MAX_RECENTS)
        val o = JSONObject()
        updated.forEach { o.put(it.key, it.value) }
        sp.edit { putString(KEY_RECENTS, o.toString()) }
    }

    var coresImported: Boolean
        get() = sp.getBoolean(KEY_CORES_IMPORTED, false)
        set(value) = sp.edit { putBoolean(KEY_CORES_IMPORTED, value) }

    /** Setup is offered once on its own; afterwards it waits in Settings. */
    var setupOffered: Boolean
        get() = sp.getBoolean(KEY_SETUP_OFFERED, false)
        set(value) = sp.edit { putBoolean(KEY_SETUP_OFFERED, value) }

    /** Favourite game path to the time it was added. Favourites saved before the times get 0. */
    fun favourites(): Map<String, Long> {
        val json = sp.getString(KEY_FAVOURITES_TIMED, null)
            ?: return sp.getStringSet(KEY_FAVOURITES, null).orEmpty().associateWith { 0L }
        return try {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getLong(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun toggleFavourite(path: String) {
        val current = favourites()
        val updated = if (path in current) current - path else current + (path to System.currentTimeMillis())
        sp.edit {
            putString(KEY_FAVOURITES_TIMED, JSONObject(updated).toString())
            remove(KEY_FAVOURITES)
        }
    }

    /** Packages pinned to the Home dock, in order. Null until the user pins or unpins an app. */
    fun pinnedApps(): List<String>? {
        val json = sp.getString(KEY_PINNED_APPS, null) ?: return null
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun setPinnedApps(packages: List<String>) =
        sp.edit { putString(KEY_PINNED_APPS, JSONArray(packages).toString()) }

    companion object {
        private const val KEY_ROM_ROOT = "rom_root"
        private const val KEY_EMU = "emu_"
        private const val KEY_RECENTS = "recents"
        private const val KEY_FAVOURITES = "favourites"
        private const val KEY_FAVOURITES_TIMED = "favourites_timed"
        private const val KEY_PINNED_APPS = "pinned_apps"
        private const val KEY_CORES_IMPORTED = "cores_imported"
        private const val KEY_SETUP_OFFERED = "setup_offered"
        const val MAX_RECENTS = 40
    }
}

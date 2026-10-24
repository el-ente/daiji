package io.github.elente.daiji.data

import android.content.Context
import androidx.core.content.edit
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

    fun favourites(): Set<String> = sp.getStringSet(KEY_FAVOURITES, null).orEmpty().toSet()

    fun toggleFavourite(path: String) {
        val current = favourites()
        val updated = if (path in current) current - path else current + path
        sp.edit { putStringSet(KEY_FAVOURITES, updated) }
    }

    private companion object {
        const val KEY_ROM_ROOT = "rom_root"
        const val KEY_EMU = "emu_"
        const val KEY_RECENTS = "recents"
        const val KEY_FAVOURITES = "favourites"
        const val MAX_RECENTS = 40
    }
}

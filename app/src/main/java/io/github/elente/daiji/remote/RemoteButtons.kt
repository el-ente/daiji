package io.github.elente.daiji.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** What one remote button does: on a tap, and optionally something else when held. Targets are described in [RemoteActions]. */
data class ButtonMapping(val scancode: Int, val tap: String?, val hold: String?)

/** Remote buttons set in Settings, by scancode: the one number that tells apart buttons that share a keycode. */
object RemoteButtons {
    private const val PREFS = "remote_buttons"
    private const val TAP = "tap:"
    private const val HOLD = "hold:"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<ButtonMapping> {
        val values = prefs(context).all
        val scancodes = values.keys.mapNotNull { it.substringAfter(':').toIntOrNull() }.toSortedSet()
        return scancodes.map { code ->
            ButtonMapping(code, values[TAP + code] as? String, values[HOLD + code] as? String)
        }
    }

    fun set(context: Context, scancode: Int, hold: Boolean, target: String) =
        prefs(context).edit { putString(key(scancode, hold), target) }

    fun clear(context: Context, scancode: Int, hold: Boolean) =
        prefs(context).edit { remove(key(scancode, hold)) }

    fun remove(context: Context, scancode: Int) = prefs(context).edit {
        remove(key(scancode, hold = false))
        remove(key(scancode, hold = true))
    }

    private fun key(scancode: Int, hold: Boolean) = (if (hold) HOLD else TAP) + scancode
}

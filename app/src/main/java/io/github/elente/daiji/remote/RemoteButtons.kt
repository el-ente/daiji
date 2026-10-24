package io.github.elente.daiji.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * What one remote button does: on a tap, and optionally something else when held. Targets are described in [RemoteActions].
 * [label] is a name the user gave it, since a vendor button only reports a number.
 */
data class ButtonMapping(val scancode: Int, val tap: String?, val hold: String?, val label: String? = null) {
    val name: String get() = label ?: "Button $scancode"
}

/** Remote buttons set in Settings, by scancode: the one number that tells apart buttons that share a keycode. */
object RemoteButtons {
    private const val PREFS = "remote_buttons"
    private const val TAP = "tap:"
    private const val HOLD = "hold:"
    private const val LABEL = "label:"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<ButtonMapping> {
        val values = prefs(context).all
        val scancodes = values.keys.mapNotNull { it.substringAfter(':').toIntOrNull() }.toSortedSet()
        return scancodes.map { code ->
            ButtonMapping(code, values[TAP + code] as? String, values[HOLD + code] as? String, values[LABEL + code] as? String)
        }
    }

    fun set(context: Context, scancode: Int, hold: Boolean, target: String) =
        prefs(context).edit { putString(key(scancode, hold), target) }

    fun clear(context: Context, scancode: Int, hold: Boolean) =
        prefs(context).edit { remove(key(scancode, hold)) }

    fun rename(context: Context, scancode: Int, label: String) = prefs(context).edit {
        if (label.isBlank()) remove(LABEL + scancode) else putString(LABEL + scancode, label.trim())
    }

    fun remove(context: Context, scancode: Int) = prefs(context).edit {
        remove(key(scancode, hold = false))
        remove(key(scancode, hold = true))
        remove(LABEL + scancode)
    }

    private fun key(scancode: Int, hold: Boolean) = (if (hold) HOLD else TAP) + scancode
}

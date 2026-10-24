package io.github.elente.daiji.remote

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.edit

/**
 * Android drops an accessibility service from the enabled list when its app is updated, which silently turns the
 * remote buttons off. With WRITE_SECURE_SETTINGS (granted over ADB) Daiji puts it back, unless the user turned it off.
 */
object KeyServiceRepair {
    private const val PREFS = "key_service"
    private const val WANTED = "wanted"

    fun connected(context: Context) = prefs(context).edit { putBoolean(WANTED, true) }

    /** Called when Android unbinds the service: the user switched it off in Accessibility. */
    fun turnedOff(context: Context) = prefs(context).edit { putBoolean(WANTED, false) }

    fun repair(context: Context) {
        if (!prefs(context).getBoolean(WANTED, false)) return
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        val resolver = context.contentResolver
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val service = ComponentName(context, RemoteKeyService::class.java)
        val listed = enabled.split(':').mapNotNull(ComponentName::unflattenFromString)
        if (service in listed) return
        val updated = (enabled.split(':').filter { it.isNotBlank() } + service.flattenToString()).joinToString(":")
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

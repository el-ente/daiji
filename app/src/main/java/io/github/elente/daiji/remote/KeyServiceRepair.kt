package io.github.elente.daiji.remote

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.edit

/**
 * Keeps the remote buttons service working without the user opening Accessibility. With WRITE_SECURE_SETTINGS
 * (granted over ADB) Daiji fixes two ways Android breaks it, unless the user turned it off:
 * - An app update drops the service from the enabled list. Daiji puts it back.
 * - When Daiji's process dies (a crash, a force stop, an update), Android marks the service crashed but keeps it
 *   listed and filtering keys. Every key on the TV then waits about 500 ms for an answer that never comes, so presses
 *   arrive as long presses. Daiji turns the service off and on, which clears the crashed state.
 */
object KeyServiceRepair {
    private const val PREFS = "key_service"
    private const val WANTED = "wanted"
    private const val CONNECT_GRACE_MS = 2000L
    private const val RE_ENABLE_DELAY_MS = 1000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasRestarted = false
    private var isRestarting = false

    fun connected(context: Context) = prefs(context).edit { putBoolean(WANTED, true) }

    /** Called when Android unbinds the service: the user switched it off in Accessibility, unless Daiji did. */
    fun turnedOff(context: Context) {
        if (!isRestarting) prefs(context).edit { putBoolean(WANTED, false) }
    }

    /** Main thread only. */
    fun repair(context: Context) {
        if (!prefs(context).getBoolean(WANTED, false)) return
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        val service = ComponentName(context, RemoteKeyService::class.java)
        if (service in enabledServices(context)) restartIfNotConnected(context, service) else setEnabled(context, service, true)
    }

    /** Once per process, so a service that can't connect is not switched off and on at every resume. */
    private fun restartIfNotConnected(context: Context, service: ComponentName) {
        if (hasRestarted || RemoteKeyService.running != null) return
        hasRestarted = true
        mainHandler.postDelayed({
            if (RemoteKeyService.running != null) return@postDelayed
            isRestarting = true
            val withoutService = setEnabled(context, service, false)
            mainHandler.postDelayed({
                isRestarting = false
                // A change made by someone else while the service was off wins over turning it back on.
                if (enabledServices(context) == withoutService) setEnabled(context, service, true)
            }, RE_ENABLE_DELAY_MS)
        }, CONNECT_GRACE_MS)
    }

    /** Returns the list as written. */
    private fun setEnabled(context: Context, service: ComponentName, enabled: Boolean): List<ComponentName> {
        val others = enabledServices(context).filter { it != service }
        val updated = if (enabled) others + service else others
        val resolver = context.contentResolver
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated.joinToString(":") { it.flattenToString() })
        if (enabled) Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        return updated
    }

    private fun enabledServices(context: Context): List<ComponentName> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

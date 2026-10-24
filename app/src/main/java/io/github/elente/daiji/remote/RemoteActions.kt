package io.github.elente.daiji.remote

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.elente.daiji.data.AppsRepository

/** Something a remote button can do, with the name Settings shows for it. */
data class ButtonTarget(val id: String, val label: String)

/**
 * The things a remote button can do. A target is stored as a string: an action word ("closeall"), a TV input
 * ("input:<TvInputInfo id>") or an app's package name.
 */
object RemoteActions {
    private const val INPUT_PREFIX = "input:"
    private const val CLOSE_ALL_DELAY_MS = 800L
    private const val CHANNEL_ITEM_TYPE = "vnd.android.cursor.item/channel"

    private val actions = linkedMapOf(
        "closeall" to "Close all apps",
        "home" to "Home",
        "back" to "Back",
        "recents" to "Recent apps",
        "mute" to "Mute",
        "sleep" to "Sleep",
        "settings" to "Android settings",
        "search" to "Search games (on Daiji's Home)",
    )

    /** Actions first, then the TV's inputs, then every app by name. */
    fun targets(context: Context): List<ButtonTarget> =
        actions.map { ButtonTarget(it.key, it.value) } +
            inputs(context).map { ButtonTarget(INPUT_PREFIX + it.id, it.loadLabel(context).toString()) } +
            AppsRepository.load(context).map { ButtonTarget(it.packageName, it.label) }

    fun label(context: Context, target: String): String {
        actions[target]?.let { return it }
        inputId(target)?.let { id ->
            return inputs(context).firstOrNull { it.id == id }?.loadLabel(context)?.toString() ?: "TV input"
        }
        val pm = context.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(target, 0)).toString() }.getOrDefault(target)
    }

    fun run(service: AccessibilityService, target: String) {
        when (target) {
            "closeall" -> closeAllApps(service)
            "home" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "recents" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            // Locking the screen puts a TV in standby; Android has the action from 9.
            "sleep" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            }
            "mute" -> service.getSystemService(AudioManager::class.java)
                ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            "settings" -> start(service, Intent(Settings.ACTION_SETTINGS))
            "search" -> HomeShortcuts.search()
            else -> inputId(target)?.let { switchInput(service, it) } ?: openApp(service, target)
        }
    }

    /** External inputs only (HDMI, AV, component), as the TV lists them; the tuner is the TV app's own. */
    private fun inputs(context: Context): List<TvInputInfo> {
        val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return runCatching { manager.tvInputList }.getOrDefault(emptyList())
            .filter { it.type != TvInputInfo.TYPE_TUNER && !it.isHidden(context) }
    }

    private fun inputId(target: String): String? = target.removePrefix(INPUT_PREFIX).takeIf { target.startsWith(INPUT_PREFIX) }

    private fun switchInput(context: Context, inputId: String) {
        val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
        start(context, Intent(Intent.ACTION_VIEW).setDataAndType(uri, CHANNEL_ITEM_TYPE))
    }

    private fun openApp(context: Context, packageName: String) {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName) ?: return
        start(context, intent)
    }

    /**
     * Goes Home, then kills every app's background processes. The delay lets the app that was on screen reach the
     * background first: Android never kills a foreground process for another app.
     */
    private fun closeAllApps(service: AccessibilityService) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Handler(Looper.getMainLooper()).postDelayed({
            val manager = service.getSystemService(ActivityManager::class.java) ?: return@postDelayed
            AppsRepository.load(service)
                .filter { it.packageName != service.packageName }
                .forEach { manager.killBackgroundProcesses(it.packageName) }
        }, CLOSE_ALL_DELAY_MS)
    }

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
        } catch (e: SecurityException) {
        }
    }
}

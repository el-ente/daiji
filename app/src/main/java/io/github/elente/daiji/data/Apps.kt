package io.github.elente.daiji.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    /** True when the app has a TV (leanback) entry point. */
    val isTv: Boolean,
)

object AppsRepository {
    private const val DEFAULT_DOCK_SIZE = 4
    const val MAX_DOCK_SIZE = 6

    /** The Home dock: the pinned apps still installed, or the first TV apps until the user pins one. */
    fun dock(apps: List<AppEntry>, pinned: List<String>?): List<AppEntry> {
        if (pinned == null) return apps.filter { it.isTv }.take(DEFAULT_DOCK_SIZE)
        val byPackage = apps.associateBy { it.packageName }
        return pinned.mapNotNull { byPackage[it] }
    }

    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppEntry>()

        fun collect(category: String, isTv: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            for (info in pm.queryIntentActivities(intent, 0)) {
                val activity = info.activityInfo ?: continue
                if (activity.packageName == context.packageName) continue
                if (result.containsKey(activity.packageName)) continue
                result[activity.packageName] = AppEntry(
                    packageName = activity.packageName,
                    activityName = activity.name,
                    label = info.loadLabel(pm).toString(),
                    isTv = isTv,
                )
            }
        }

        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, isTv = true)
        collect(Intent.CATEGORY_LAUNCHER, isTv = false)
        homeScreens(context).forEach(result::remove)
        return result.values.sortedBy { it.label.lowercase() }
    }

    /**
     * Other home screens: a previous launcher, and the Settings app's fallback home. Opening one from Daiji only
     * shows a blank or duplicate screen, and Daiji's Settings pill already covers Android settings.
     */
    private fun homeScreens(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return context.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet()
    }

    /** Returns the TV banner when the app has one, else its icon. */
    fun loadArt(context: Context, app: AppEntry): Pair<Drawable?, Boolean> {
        val pm = context.packageManager
        val component = ComponentName(app.packageName, app.activityName)
        val banner = runCatching { pm.getActivityBanner(component) }.getOrNull()
            ?: runCatching { pm.getApplicationBanner(app.packageName) }.getOrNull()
        if (banner != null) return banner to true
        val icon = runCatching { pm.getActivityIcon(component) }.getOrNull()
        return icon to false
    }

    fun launch(context: Context, app: AppEntry): String? {
        val category = if (app.isTv) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "Couldn't open ${app.label}"
        } catch (e: SecurityException) {
            "Couldn't open ${app.label}"
        }
    }

    fun openInfo(context: Context, app: AppEntry) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

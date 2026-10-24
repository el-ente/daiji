package io.github.elente.daiji.launch

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.github.elente.daiji.data.Game
import io.github.elente.daiji.data.GameSystem
import io.github.elente.daiji.data.SettingsSnapshot
import java.io.File

data class Emulator(val id: String, val name: String, val packages: List<String>)

object Emulators {
    const val RETROARCH = "retroarch"
    const val PPSSPP = "ppsspp"
    const val DOLPHIN = "dolphin"
    const val DUCKSTATION = "duckstation"
    const val NETHERSX2 = "nethersx2"
    const val ASK = "ask"

    val all: List<Emulator> = listOf(
        Emulator(RETROARCH, "RetroArch", listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32")),
        Emulator(PPSSPP, "PPSSPP", listOf("org.ppsspp.ppssppgold", "org.ppsspp.ppsspp")),
        Emulator(DOLPHIN, "Dolphin", listOf("org.dolphinemu.dolphinemu")),
        Emulator(DUCKSTATION, "DuckStation", listOf("com.github.stenzek.duckstation")),
        Emulator(NETHERSX2, "NetherSX2", listOf("xyz.aethersx2.android")),
        Emulator(ASK, "Ask Android", emptyList()),
    )

    fun byId(id: String): Emulator? = all.firstOrNull { it.id == id }

    /** Emulators that can run a system, the preferred one first and "Ask Android" last. */
    fun optionsFor(system: GameSystem): List<Emulator> {
        val ids = buildList {
            if (system.retroArchCore != null) add(RETROARCH)
            addAll(system.standalone)
        }.sortedBy { if (it == system.preferredEmulator) 0 else 1 }
        return ids.mapNotNull { byId(it) } + byId(ASK)!!
    }

    /** The emulator a game will launch with: the user's pick, else the first installed option. */
    fun resolve(system: GameSystem, settings: SettingsSnapshot): Emulator {
        val options = optionsFor(system)
        settings.chosenEmulators[system.id]?.let { chosen ->
            options.firstOrNull { it.id == chosen }?.let { return it }
        }
        return options.firstOrNull { it.id in settings.installedEmulators } ?: options.first()
    }

    fun installed(context: Context): Map<String, String> {
        val pm = context.packageManager
        return all.mapNotNull { emu ->
            emu.packages.firstOrNull { isInstalled(pm, it) }?.let { emu.id to it }
        }.toMap()
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

object GameLauncher {
    private const val CLEAR = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

    /** Starts the game. Returns an error message, or null on success. */
    fun launch(
        context: Context,
        game: Game,
        system: GameSystem,
        emulator: Emulator,
        settings: SettingsSnapshot,
    ): String? {
        val file = File(game.path)
        if (!file.exists()) return "File not found: ${file.name}"

        return try {
            val intent = if (emulator.id == Emulators.ASK) {
                askAndroid(context, file)
            } else {
                val pkg = settings.installedEmulators[emulator.id]
                    ?: return "${emulator.name} is not installed"
                killIfRunning(context, pkg)
                when (emulator.id) {
                    Emulators.RETROARCH -> retroArch(context, pkg, file, system)
                        ?: return "RetroArch has no core set for ${system.name}"
                    Emulators.PPSSPP -> Intent(Intent.ACTION_VIEW)
                        .setComponent(ComponentName(pkg, "org.ppsspp.ppsspp.PpssppActivity"))
                        .withContent(context, file)
                    Emulators.DOLPHIN -> Intent(Intent.ACTION_MAIN)
                        .setComponent(ComponentName(pkg, "org.dolphinemu.dolphinemu.ui.main.MainActivity"))
                        .withContentExtra(context, file, "AutoStartFile")
                    Emulators.DUCKSTATION -> Intent(Intent.ACTION_MAIN)
                        .setComponent(ComponentName(pkg, "com.github.stenzek.duckstation.EmulationActivity"))
                        .withContentExtra(context, file, "bootPath")
                    Emulators.NETHERSX2 -> Intent(Intent.ACTION_MAIN)
                        .setComponent(ComponentName(pkg, "xyz.aethersx2.android.EmulationActivity"))
                        .withContentExtra(context, file, "bootPath")
                    else -> askAndroid(context, file)
                }
            }
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "Couldn't start ${emulator.name}"
        } catch (e: SecurityException) {
            "${emulator.name} refused the launch"
        } catch (e: IllegalArgumentException) {
            "Can't share ${file.name} with ${emulator.name}"
        }
    }

    /**
     * Emulator activities are single-instance: a running one swallows the new intent
     * and keeps showing the previous game. Only background processes can be killed,
     * which is exactly the case after the user pressed Home. Android 14+ ignores this
     * for other packages, so there the user must quit the emulator themselves.
     */
    private fun killIfRunning(context: Context, pkg: String) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        runCatching { activityManager.killBackgroundProcesses(pkg) }
    }

    /** Same extras RetroArch's own frontend and other launchers pass. */
    private fun retroArch(context: Context, pkg: String, file: File, system: GameSystem): Intent? {
        val core = system.retroArchCore ?: return null
        val sdcard = Environment.getExternalStorageDirectory().path
        val external = "$sdcard/Android/data/$pkg/files"
        val apk = runCatching { context.packageManager.getApplicationInfo(pkg, 0).sourceDir }.getOrNull()
        return Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture"))
            .putExtra("ROM", file.path)
            .putExtra("LIBRETRO", "/data/data/$pkg/cores/${core}_libretro_android.so")
            .putExtra("CONFIGFILE", "$external/retroarch.cfg")
            .putExtra("IME", Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
            .putExtra("DATADIR", "/data/data/$pkg")
            .putExtra("SDCARD", sdcard)
            .putExtra("EXTERNAL", external)
            .apply { if (apk != null) putExtra("APK", apk) }
            .addFlags(CLEAR or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY)
    }

    private fun askAndroid(context: Context, file: File): Intent {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(contentUri(context, file), mimeType(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(view, "Open ${file.name} with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun Intent.withContent(context: Context, file: File): Intent =
        setData(contentUri(context, file))
            .addFlags(CLEAR or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Passes the URI as a string extra and grants read access through ClipData. */
    private fun Intent.withContentExtra(context: Context, file: File, key: String): Intent {
        val uri = contentUri(context, file)
        clipData = ClipData.newRawUri(file.name, uri)
        return putExtra(key, uri.toString())
            .addFlags(CLEAR or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun contentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
}

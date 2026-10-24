package io.github.elente.daiji.data

import android.os.Environment
import java.io.File

/**
 * Everything Daiji shares with RetroArch lives under `/sdcard/Daiji`. RetroArch's own
 * `Android/data` folder is unreadable to other apps on Android 11+, so this is the
 * only place both can see.
 */
object DaijiDirs {
    val root: File get() = File(Environment.getExternalStorageDirectory(), "Daiji")
    val cores: File get() = File(root, "cores")
    val thumbnails: File get() = File(root, "thumbnails")
    val downloads: File get() = File(root, "downloads")
    val retroArch: File get() = File(root, "retroarch")
    val retroArchConfig: File get() = File(retroArch, "retroarch.cfg")
    val playlists: File get() = File(retroArch, "playlists")
    val databases: File get() = File(retroArch, "database/rdb")
}

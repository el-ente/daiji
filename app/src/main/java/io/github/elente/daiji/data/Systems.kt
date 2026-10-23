package io.github.elente.daiji.data

import io.github.elente.daiji.launch.Emulators

/** A game platform: which ROM folders map to it and how to launch its games. */
data class GameSystem(
    val id: String,
    val name: String,
    val shortName: String,
    val folders: Set<String>,
    val extensions: Set<String>,
    /** RetroArch core name without the `_libretro_android.so` suffix. */
    val retroArchCore: String?,
    /** Preferred emulator when the user hasn't picked one. */
    val preferredEmulator: String = Emulators.RETROARCH,
    /** Standalone emulators that can also run this system. */
    val standalone: List<String> = emptyList(),
)

object Systems {
    private val archives = setOf("zip", "7z")

    val all: List<GameSystem> = listOf(
        GameSystem("nes", "Nintendo Entertainment System", "NES", setOf("nes", "famicom", "fc"), setOf("nes", "fds", "unf", "unif") + archives, "fceumm"),
        GameSystem("snes", "Super Nintendo", "SNES", setOf("snes", "sfc", "superfamicom", "supernintendo"), setOf("sfc", "smc", "fig", "swc", "bs") + archives, "snes9x"),
        GameSystem("n64", "Nintendo 64", "N64", setOf("n64", "nintendo64"), setOf("n64", "z64", "v64") + archives, "mupen64plus_next"),
        GameSystem("gb", "Game Boy", "GB", setOf("gb", "gameboy"), setOf("gb") + archives, "gambatte"),
        GameSystem("gbc", "Game Boy Color", "GBC", setOf("gbc", "gameboycolor"), setOf("gbc", "gb") + archives, "gambatte"),
        GameSystem("gba", "Game Boy Advance", "GBA", setOf("gba", "gameboyadvance"), setOf("gba") + archives, "mgba"),
        GameSystem("nds", "Nintendo DS", "NDS", setOf("nds", "ds"), setOf("nds") + archives, "melonds"),
        GameSystem("gc", "GameCube", "GC", setOf("gc", "gamecube", "ngc"), setOf("iso", "gcm", "gcz", "rvz", "ciso"), null, Emulators.DOLPHIN, listOf(Emulators.DOLPHIN)),
        GameSystem("wii", "Wii", "Wii", setOf("wii"), setOf("iso", "wbfs", "rvz", "gcz", "wad"), null, Emulators.DOLPHIN, listOf(Emulators.DOLPHIN)),
        GameSystem("mastersystem", "Master System", "SMS", setOf("mastersystem", "sms"), setOf("sms") + archives, "genesis_plus_gx"),
        GameSystem("genesis", "Genesis / Mega Drive", "MD", setOf("genesis", "megadrive", "md"), setOf("md", "gen", "smd", "bin") + archives, "genesis_plus_gx"),
        GameSystem("segacd", "Sega CD", "SCD", setOf("segacd", "megacd"), setOf("cue", "chd", "iso", "m3u"), "genesis_plus_gx"),
        GameSystem("sega32x", "Sega 32X", "32X", setOf("sega32x", "32x"), setOf("32x", "bin") + archives, "picodrive"),
        GameSystem("gamegear", "Game Gear", "GG", setOf("gamegear", "gg"), setOf("gg") + archives, "genesis_plus_gx"),
        GameSystem("saturn", "Sega Saturn", "SAT", setOf("saturn"), setOf("cue", "chd", "iso", "m3u"), "yabasanshiro"),
        GameSystem("dreamcast", "Dreamcast", "DC", setOf("dreamcast", "dc"), setOf("gdi", "cdi", "chd", "m3u"), "flycast"),
        GameSystem("psx", "PlayStation", "PS1", setOf("psx", "ps1", "playstation"), setOf("cue", "chd", "pbp", "m3u", "iso", "img", "ecm"), "pcsx_rearmed", Emulators.RETROARCH, listOf(Emulators.DUCKSTATION)),
        GameSystem("ps2", "PlayStation 2", "PS2", setOf("ps2", "playstation2"), setOf("iso", "chd", "cso"), null, Emulators.NETHERSX2, listOf(Emulators.NETHERSX2)),
        GameSystem("psp", "PlayStation Portable", "PSP", setOf("psp"), setOf("iso", "cso", "pbp", "chd"), "ppsspp", Emulators.PPSSPP, listOf(Emulators.PPSSPP)),
        GameSystem("pcengine", "PC Engine / TurboGrafx-16", "PCE", setOf("pcengine", "pce", "tg16", "turbografx16"), setOf("pce", "sgx", "cue", "chd") + archives, "mednafen_pce_fast"),
        GameSystem("atari2600", "Atari 2600", "2600", setOf("atari2600", "a2600"), setOf("a26", "bin") + archives, "stella"),
        GameSystem("ngp", "Neo Geo Pocket", "NGP", setOf("ngp", "ngpc"), setOf("ngp", "ngc") + archives, "mednafen_ngp"),
        GameSystem("wonderswan", "WonderSwan", "WS", setOf("wonderswan", "wswan", "wsc", "wonderswancolor"), setOf("ws", "wsc") + archives, "mednafen_wswan"),
        GameSystem("neogeo", "Neo Geo", "NEO", setOf("neogeo"), archives, "fbneo"),
        GameSystem("arcade", "Arcade", "ARC", setOf("arcade", "fbneo", "fba"), archives, "fbneo"),
        GameSystem("mame", "MAME", "MAME", setOf("mame", "mame2003", "mame2003plus"), archives, "mame2003_plus"),
    )

    private val byId: Map<String, GameSystem> = all.associateBy { it.id }
    private val byFolder: Map<String, GameSystem> =
        all.flatMap { s -> s.folders.map { it to s } }.toMap()
    private val order: Map<String, Int> = all.withIndex().associate { it.value.id to it.index }

    fun byId(id: String): GameSystem? = byId[id]

    fun byFolder(folderName: String): GameSystem? =
        byFolder[folderName.lowercase().replace(" ", "").replace("-", "").replace("_", "")]

    fun order(id: String): Int = order[id] ?: Int.MAX_VALUE
}

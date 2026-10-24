package io.github.elente.daiji

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.elente.daiji.data.AppEntry
import io.github.elente.daiji.data.AppsRepository
import io.github.elente.daiji.data.Game
import io.github.elente.daiji.data.GameSystem
import io.github.elente.daiji.data.Library
import io.github.elente.daiji.data.Network
import io.github.elente.daiji.data.PlayStats
import io.github.elente.daiji.data.LibraryStore
import io.github.elente.daiji.data.Prefs
import io.github.elente.daiji.data.RetroArchPlayLog
import io.github.elente.daiji.data.RomScanner
import io.github.elente.daiji.data.SettingsSnapshot
import io.github.elente.daiji.data.Systems
import io.github.elente.daiji.launch.Emulator
import io.github.elente.daiji.launch.Emulators
import io.github.elente.daiji.launch.GameLauncher
import io.github.elente.daiji.setup.RetroArchSetup
import io.github.elente.daiji.setup.SetupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { HOME, ALL_GAMES, SETTINGS, SETUP }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val cacheFile = File(app.filesDir, "library.json")
    private val scanLock = Mutex()

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** The element each screen last had focused, so Back and Home land where the user left it. */
    val focusMemory = mutableMapOf<Screen, String>()

    /** The system pill selected on All games; null is All. */
    var allGamesFilter: String? = null

    private val _library = MutableStateFlow(Library.EMPTY)
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _recents = MutableStateFlow(prefs.recents())
    val recents: StateFlow<Map<String, Long>> = _recents.asStateFlow()

    /** Game path to seconds played in RetroArch. */
    private val _playtime = MutableStateFlow<Map<String, Long>>(emptyMap())
    val playtime: StateFlow<Map<String, Long>> = _playtime.asStateFlow()

    /** Favourite game path to the time it was added. */
    private val _favourites = MutableStateFlow(prefs.favourites())
    val favourites: StateFlow<Map<String, Long>> = _favourites.asStateFlow()

    private val _pinnedApps = MutableStateFlow(prefs.pinnedApps())
    val pinnedApps: StateFlow<List<String>?> = _pinnedApps.asStateFlow()

    private val _randomPick = MutableStateFlow<Game?>(null)
    val randomPick: StateFlow<Game?> = _randomPick.asStateFlow()

    private val _network = MutableStateFlow("")
    val network: StateFlow<String> = _network.asStateFlow()

    private val _settings = MutableStateFlow(SettingsSnapshot(prefs.romRoot, prefs.chosenEmulators(), emptyMap()))
    val settings: StateFlow<SettingsSnapshot> = _settings.asStateFlow()

    private val _hasStorageAccess = MutableStateFlow(checkStorageAccess())
    val hasStorageAccess: StateFlow<Boolean> = _hasStorageAccess.asStateFlow()

    private val retroArchSetup = RetroArchSetup(app, prefs)
    val setup: StateFlow<SetupState> = retroArchSetup.state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = LibraryStore.read(cacheFile)
            if (cached.isNotEmpty()) setLibrary(Library(cached))
            if (prefs.romRoot == null) {
                RomScanner.candidateRoots().firstOrNull()?.let { setRootInternal(it.path) }
            }
            scan()
        }
    }

    fun onResume() {
        val hadAccess = _hasStorageAccess.value
        _hasStorageAccess.value = checkStorageAccess()
        viewModelScope.launch(Dispatchers.IO) {
            refreshPlayStats()
            _network.value = Network.label(getApplication())
            _apps.value = AppsRepository.load(getApplication())
            _settings.value = _settings.value.copy(installedEmulators = Emulators.installed(getApplication()))
            if (!hadAccess && _hasStorageAccess.value) scan()
            refreshSetup()
        }
    }

    /** Where Back from Setup goes: Settings when opened from there, Home when Daiji offered it on its own. */
    private var setupReturn = Screen.SETTINGS

    fun openSetup() {
        prefs.setupOffered = true
        setupReturn = if (_screen.value == Screen.SETUP) setupReturn else _screen.value
        _screen.value = Screen.SETUP
    }

    fun closeSetup() {
        _screen.value = setupReturn
    }

    /** Installs RetroArch when missing; otherwise downloads cores, writes config and box art. */
    fun runSetup() {
        viewModelScope.launch(Dispatchers.IO) {
            val pkg = retroArchPackage()
            if (pkg == null) {
                retroArchSetup.installRetroArch()
            } else {
                retroArchSetup.provision(_library.value, pkg)
                scan()
            }
            refreshSetup()
        }
    }

    fun openRetroArch() {
        val pkg = retroArchPackage() ?: return toast("RetroArch is not installed")
        runCatching { retroArchSetup.openRetroArch(pkg) }.onFailure { toast("Cannot open RetroArch") }
    }

    fun markCoresImported() = retroArchSetup.markCoresImported()

    private fun retroArchPackage(): String? = _settings.value.installedEmulators[Emulators.RETROARCH]

    private fun refreshSetup() {
        retroArchSetup.refresh(retroArchPackage(), _library.value)
        if (shouldOfferSetup()) openSetup()
    }

    private fun shouldOfferSetup(): Boolean =
        !prefs.setupOffered && retroArchPackage() == null && _library.value.systems.any { it.retroArchCore != null }

    fun open(screen: Screen) {
        _screen.value = screen
    }

    fun goHome() {
        _screen.value = Screen.HOME
    }

    fun rescan() {
        viewModelScope.launch(Dispatchers.IO) { scan() }
    }

    private suspend fun scan() {
        if (scanLock.isLocked) return
        scanLock.withLock {
            val root = prefs.romRoot ?: return
            // Without storage access every folder looks empty; keep the cached library.
            if (!checkStorageAccess()) return
            _scanning.value = true
            try {
                val games = RomScanner.scan(File(root))
                setLibrary(Library(games))
                runCatching { LibraryStore.write(cacheFile, games) }
                refreshPlayStats()
                refreshSetup()
            } finally {
                _scanning.value = false
            }
        }
    }

    fun candidateRoots(): List<String> = RomScanner.candidateRoots().map { it.path }

    fun setRomRoot(path: String) {
        val trimmed = path.trim().trimEnd('/')
        if (trimmed.isEmpty()) return
        setRootInternal(trimmed)
        rescan()
    }

    private fun setRootInternal(path: String) {
        prefs.romRoot = path
        _settings.value = _settings.value.copy(romRoot = path)
    }

    fun emulatorFor(system: GameSystem): Emulator = Emulators.resolve(system, _settings.value)

    fun cycleEmulator(system: GameSystem) {
        val options = Emulators.optionsFor(system)
        val current = Emulators.resolve(system, _settings.value)
        val next = options[(options.indexOf(current) + 1) % options.size]
        prefs.setEmulator(system.id, next.id)
        _settings.value = _settings.value.copy(chosenEmulators = prefs.chosenEmulators())
    }

    fun launch(game: Game) {
        val system = Systems.byId(game.systemId) ?: return
        val settings = _settings.value
        val error = GameLauncher.launch(getApplication(), game, system, Emulators.resolve(system, settings), settings)
        if (error != null) {
            toast(error)
        } else {
            prefs.markPlayed(game.path)
            viewModelScope.launch(Dispatchers.IO) { refreshPlayStats() }
        }
    }

    /** RetroArch logs every session itself, including ones not started from Daiji. */
    private fun refreshPlayStats() {
        val logs = RetroArchPlayLog.read()
        val stats = _library.value.games
            .mapNotNull { game -> logs[File(game.path).nameWithoutExtension.lowercase()]?.let { game.path to it } }
            .toMap()
        _playtime.value = stats.mapValues { it.value.runtimeSeconds }.filterValues { it > 0 }
        _recents.value = mergeRecents(prefs.recents(), stats)
    }

    private fun mergeRecents(own: Map<String, Long>, stats: Map<String, PlayStats>): Map<String, Long> {
        val merged = own.toMutableMap()
        stats.forEach { (path, s) -> s.lastPlayed?.let { merged[path] = maxOf(merged[path] ?: 0, it) } }
        return merged.entries
            .sortedByDescending { it.value }
            .take(Prefs.MAX_RECENTS)
            .associate { it.key to it.value }
    }

    private fun setLibrary(library: Library) {
        _library.value = library
        if (_randomPick.value?.path !in library.byPath) rerollRandom()
    }

    fun toggleFavourite(game: Game) {
        val adding = game.path !in _favourites.value
        prefs.toggleFavourite(game.path)
        _favourites.value = prefs.favourites()
        toast(if (adding) "★ Added ${game.title} to favourites" else "Removed ${game.title} from favourites")
    }

    /** Picks another random game from the whole library, never the one already shown when there is another. */
    fun rerollRandom() {
        val games = _library.value.games
        val current = _randomPick.value?.path
        _randomPick.value = games.filter { it.path != current }.randomOrNull() ?: games.randomOrNull()
    }

    fun togglePin(app: AppEntry) {
        val dock = AppsRepository.dock(_apps.value, _pinnedApps.value).map { it.packageName }
        val updated = when {
            app.packageName in dock -> dock - app.packageName
            dock.size >= AppsRepository.MAX_DOCK_SIZE -> return toast("The dock holds ${AppsRepository.MAX_DOCK_SIZE} apps. Unpin one first.")
            else -> dock + app.packageName
        }
        prefs.setPinnedApps(updated)
        _pinnedApps.value = updated
    }

    fun launch(app: AppEntry) {
        AppsRepository.launch(getApplication(), app)?.let { toast(it) }
    }

    fun openAppInfo(app: AppEntry) = AppsRepository.openInfo(getApplication(), app)

    fun toast(message: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            getApplication<Application>().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
}

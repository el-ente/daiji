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
import io.github.elente.daiji.data.LibraryStore
import io.github.elente.daiji.data.Prefs
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

enum class Tab { GAMES, APPS, SETTINGS, SETUP }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val cacheFile = File(app.filesDir, "library.json")
    private val scanLock = Mutex()

    private val _tab = MutableStateFlow(Tab.GAMES)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _library = MutableStateFlow(Library.EMPTY)
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _recents = MutableStateFlow(prefs.recents())
    val recents: StateFlow<Map<String, Long>> = _recents.asStateFlow()

    private val _favourites = MutableStateFlow(prefs.favourites())
    val favourites: StateFlow<Set<String>> = _favourites.asStateFlow()

    private val _settings = MutableStateFlow(SettingsSnapshot(prefs.romRoot, prefs.chosenEmulators(), emptyMap()))
    val settings: StateFlow<SettingsSnapshot> = _settings.asStateFlow()

    private val _hasStorageAccess = MutableStateFlow(checkStorageAccess())
    val hasStorageAccess: StateFlow<Boolean> = _hasStorageAccess.asStateFlow()

    private val retroArchSetup = RetroArchSetup(app, prefs)
    val setup: StateFlow<SetupState> = retroArchSetup.state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = LibraryStore.read(cacheFile)
            if (cached.isNotEmpty()) _library.value = Library(cached)
            if (prefs.romRoot == null) {
                RomScanner.candidateRoots().firstOrNull()?.let { setRootInternal(it.path) }
            }
            scan()
        }
    }

    fun onResume() {
        val hadAccess = _hasStorageAccess.value
        _hasStorageAccess.value = checkStorageAccess()
        _recents.value = prefs.recents()
        viewModelScope.launch(Dispatchers.IO) {
            _apps.value = AppsRepository.load(getApplication())
            _settings.value = _settings.value.copy(installedEmulators = Emulators.installed(getApplication()))
            if (!hadAccess && _hasStorageAccess.value) scan()
            refreshSetup()
        }
    }

    fun openSetup() {
        prefs.setupOffered = true
        _tab.value = Tab.SETUP
    }

    fun closeSetup() {
        _tab.value = Tab.SETTINGS
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

    fun selectTab(tab: Tab) {
        _tab.value = tab
    }

    fun goHome() {
        _tab.value = Tab.GAMES
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
                _library.value = Library(games)
                runCatching { LibraryStore.write(cacheFile, games) }
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
            _recents.value = prefs.recents()
        }
    }

    fun toggleFavourite(game: Game) {
        prefs.toggleFavourite(game.path)
        _favourites.value = prefs.favourites()
    }

    /** A random game from the whole library, never the one already shown when there is another. */
    fun randomGame(excludingPath: String?): Game? {
        val games = _library.value.games
        return games.filter { it.path != excludingPath }.randomOrNull() ?: games.randomOrNull()
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

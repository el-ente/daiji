package io.github.elente.daiji

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.elente.daiji.data.AcclaimedGames
import io.github.elente.daiji.data.AcclaimedView
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
import io.github.elente.daiji.remote.KeyServiceRepair
import io.github.elente.daiji.setup.RetroArchSetup
import io.github.elente.daiji.setup.ThumbnailScraper
import io.github.elente.daiji.setup.SetupState
import io.github.elente.daiji.setup.ApkInstaller
import io.github.elente.daiji.update.AppUpdater
import io.github.elente.daiji.update.Release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** Where Settings' update row stands; the check runs only when the user asks for it. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val percent: Int) : UpdateState
    data object Failed : UpdateState
}

enum class Screen { HOME, SEARCH, ALL_GAMES, ALL_APPS, SETTINGS, GAMES_SETTINGS, REMOTE_BUTTONS, SETUP, GAME_DETAIL }

/** Share of random picks drawn from acclaimed games; the rest come from the whole library. */
private const val ACCLAIMED_PICK_SHARE = 0.75f

/** How many downloaded covers between refreshes of the Acclaimed view. */
private const val COVER_REFRESH_EVERY = 10

/** The thumbnail server answers slowly, so covers download side by side. */
private const val COVER_DOWNLOADS_AT_ONCE = 6

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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

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

    /** Path of the game the detail page shows; the page reads the game from the library so a rescan updates it. */
    var detailGamePath: String? = null
        private set

    private val _pinnedApps = MutableStateFlow(prefs.pinnedApps())
    val pinnedApps: StateFlow<List<String>?> = _pinnedApps.asStateFlow()

    /** Loaded on first use, which is the first library set on the IO thread. */
    val acclaimed: AcclaimedGames by lazy { AcclaimedGames.load(app) }

    private var acclaimedCoverJob: Job? = null
    private val _acclaimedView = MutableStateFlow(AcclaimedView.EMPTY)
    val acclaimedView: StateFlow<AcclaimedView> = _acclaimedView.asStateFlow()

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

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    /** OK on the update row: checks GitHub, or installs the release the last check found. */
    fun onUpdateRow() {
        when (val state = _update.value) {
            UpdateState.Checking, is UpdateState.Downloading -> Unit
            is UpdateState.Available -> installUpdate(state.release)
            else -> checkForUpdate()
        }
    }

    private fun checkForUpdate() {
        _update.value = UpdateState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            _update.value = try {
                AppUpdater.newerRelease()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: Exception) {
                UpdateState.Failed
            }
        }
    }

    private fun installUpdate(release: Release) {
        val app = getApplication<Application>()
        if (!ApkInstaller.canInstall(app)) {
            ApkInstaller.openUnknownSourcesSettings(app)
            return toast(string(R.string.allow_install))
        }
        _update.value = UpdateState.Downloading(release, 0)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apk = AppUpdater.download(release) { read, total ->
                    if (total > 0) _update.value = UpdateState.Downloading(release, (read * 100 / total).toInt())
                }
                withContext(Dispatchers.Main) { ApkInstaller.install(app, apk) }
                _update.value = UpdateState.Available(release)
            } catch (e: Exception) {
                AppUpdater.apkFile(release).delete()
                _update.value = UpdateState.Failed
            }
        }
    }

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
        KeyServiceRepair.repair(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            refreshPlayStats()
            _network.value = Network.label(getApplication())
            _apps.value = AppsRepository.load(getApplication())
            _settings.value = _settings.value.copy(installedEmulators = Emulators.installed(getApplication()))
            if (!hadAccess && _hasStorageAccess.value) scan()
            refreshSetup()
        }
    }

    fun openSetup() {
        prefs.setupOffered = true
        open(Screen.SETUP)
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
        val pkg = retroArchPackage() ?: return toast(string(R.string.retroarch_not_installed))
        runCatching { retroArchSetup.openRetroArch(pkg) }.onFailure { toast(string(R.string.cannot_open_retroarch)) }
    }

    fun markCoresImported() = retroArchSetup.markCoresImported()

    private fun retroArchPackage(): String? = _settings.value.installedEmulators[Emulators.RETROARCH]

    private fun refreshSetup() {
        retroArchSetup.refresh(retroArchPackage(), _library.value)
        if (shouldOfferSetup()) openSetup()
    }

    private fun shouldOfferSetup(): Boolean =
        !prefs.setupOffered && retroArchPackage() == null && _library.value.systems.any { it.retroArchCore != null }

    private val backStack = ArrayDeque<Screen>()

    fun open(screen: Screen) {
        if (screen == _screen.value) return
        backStack.addLast(_screen.value)
        _screen.value = screen
    }

    fun openDetail(game: Game) {
        detailGamePath = game.path
        open(Screen.GAME_DETAIL)
    }

    /** Back pops one screen; the launcher is the bottom of the stack, so it never leaves Home. */
    fun back() {
        _screen.value = backStack.removeLastOrNull() ?: Screen.HOME
    }

    fun goHome() {
        backStack.clear()
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

    fun cycleEmulator(system: GameSystem, step: Int = 1) {
        val options = Emulators.optionsFor(system)
        val current = Emulators.resolve(system, _settings.value)
        val next = options[Math.floorMod(options.indexOf(current) + step, options.size)]
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
        refreshAcclaimedView()
        if (_randomPick.value?.path !in library.byPath) rerollRandom()
        fetchAcclaimedCovers()
    }

    /**
     * Box art for acclaimed games not in the library, downloaded in the background so the Acclaimed view shows covers
     * without running setup. The view refreshes as covers arrive; one fetch at a time, files already there are skipped.
     */
    private fun fetchAcclaimedCovers() {
        if (acclaimedCoverJob?.isActive == true) return
        acclaimedCoverJob = viewModelScope.launch(Dispatchers.IO) {
            do {
                val library = _library.value
                downloadAcclaimedCovers(library)
            } while (_library.value !== library)
        }
    }

    private suspend fun downloadAcclaimedCovers(library: Library) = coroutineScope {
        val requests = ThumbnailScraper.missingAcclaimedCovers(acclaimed.missing(library))
        val done = AtomicInteger()
        requests.withIndex()
            .groupBy({ it.index % COVER_DOWNLOADS_AT_ONCE }, { it.value })
            .values
            .map { slice ->
                launch {
                    val job = coroutineContext.job
                    try {
                        ThumbnailScraper.scrape(slice) { _, _ ->
                            job.ensureActive()
                            if (done.incrementAndGet() % COVER_REFRESH_EVERY == 0) refreshAcclaimedView()
                        }
                    } catch (e: IOException) {
                        // Offline or the server is failing; the next launch tries again.
                    }
                }
            }
            .joinAll()
        refreshAcclaimedView()
    }

    private fun refreshAcclaimedView() {
        _acclaimedView.value = acclaimed.view(_library.value)
    }

    fun toggleFavourite(game: Game) {
        val adding = game.path !in _favourites.value
        prefs.toggleFavourite(game.path)
        _favourites.value = prefs.favourites()
        toast(string(if (adding) R.string.favourite_added else R.string.favourite_removed, game.title))
    }

    /**
     * Picks another random game, never the one already shown when there is another.
     * Most picks come from acclaimed games, so the suggestion is usually worth playing.
     */
    fun rerollRandom() {
        val games = _library.value.games
        val current = _randomPick.value?.path
        val others = games.filter { it.path != current }
        val acclaimedOthers = others.filter(acclaimed::isAcclaimed)
        val pool = if (acclaimedOthers.isNotEmpty() && Random.nextFloat() < ACCLAIMED_PICK_SHARE) acclaimedOthers else others
        _randomPick.value = pool.randomOrNull() ?: games.randomOrNull()
    }

    fun togglePin(app: AppEntry) {
        val home = AppsRepository.home(_apps.value, _pinnedApps.value).map { it.packageName }
        savePinnedApps(if (app.packageName in home) home - app.packageName else home + app.packageName)
    }

    fun setHomeOrder(apps: List<AppEntry>) = savePinnedApps(apps.map { it.packageName })

    private fun savePinnedApps(packages: List<String>) {
        prefs.setPinnedApps(packages)
        _pinnedApps.value = packages
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

    private fun string(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun checkStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            getApplication<Application>().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
}

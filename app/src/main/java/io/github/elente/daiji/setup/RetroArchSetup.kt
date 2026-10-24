package io.github.elente.daiji.setup

import android.content.Context
import io.github.elente.daiji.data.Library
import io.github.elente.daiji.data.Prefs
import io.github.elente.daiji.launch.GameLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Step(val title: String) {
    INSTALL("Install RetroArch"),
    CORES("Download cores"),
    CONFIG("Configure RetroArch"),
    THUMBNAILS("Download box art"),
    IMPORT("Import cores in RetroArch"),
}

enum class StepStatus { PENDING, RUNNING, DONE, FAILED }

data class SetupStep(val step: Step, val status: StepStatus = StepStatus.PENDING, val detail: String = "")

data class SetupState(
    val steps: List<SetupStep> = Step.entries.map { SetupStep(it) },
    val busy: Boolean = false,
) {
    operator fun get(step: Step): SetupStep = steps.first { it.step == step }
    val isInstalled: Boolean get() = this[Step.INSTALL].status == StepStatus.DONE
    val isProvisioned: Boolean get() = listOf(Step.CORES, Step.CONFIG).all { this[it].status == StepStatus.DONE }
    val isComplete: Boolean get() = isProvisioned && this[Step.IMPORT].status == StepStatus.DONE
}

/** Turns a bare RetroArch install into one that knows Daiji's games, cores and box art. */
class RetroArchSetup(private val context: Context, private val prefs: Prefs) {
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun refresh(retroArchPackage: String?, library: Library) {
        val cores = CoreDownloader.coresFor(library.systems)
        if (retroArchPackage != null) setStatus(Step.INSTALL, StepStatus.DONE, retroArchPackage)
        if (cores.isNotEmpty() && cores.all(CoreDownloader::isDownloaded)) setStatus(Step.CORES, StepStatus.DONE, "${cores.size} cores")
        if (RetroArchConfig.isWritten()) setStatus(Step.CONFIG, StepStatus.DONE)
        if (prefs.coresImported) setStatus(Step.IMPORT, StepStatus.DONE)
    }

    /** Fetches the APK and hands it to the system installer. Finishes once the user confirms there. */
    fun installRetroArch() = run(Step.INSTALL) {
        if (!RetroArchInstaller.canInstall(context)) {
            RetroArchInstaller.openUnknownSourcesSettings(context)
            error("Allow Daiji to install apps, then try again")
        }
        RetroArchInstaller.download { read, total -> setStatus(Step.INSTALL, StepStatus.RUNNING, progress(read, total)) }
        RetroArchInstaller.install(context)
        setStatus(Step.INSTALL, StepStatus.PENDING, "Confirm the install, then come back")
    }

    fun provision(library: Library, retroArchPackage: String) {
        downloadCores(library)
        writeConfig(library, retroArchPackage)
        scrapeThumbnails(library)
    }

    fun openRetroArch(retroArchPackage: String) {
        context.startActivity(GameLauncher.retroArchMenu(context, retroArchPackage))
    }

    fun markCoresImported() {
        prefs.coresImported = true
        setStatus(Step.IMPORT, StepStatus.DONE)
    }

    private fun downloadCores(library: Library) = run(Step.CORES) {
        val cores = CoreDownloader.coresFor(library.systems)
        cores.forEachIndexed { index, core ->
            setStatus(Step.CORES, StepStatus.RUNNING, "$core (${index + 1}/${cores.size})")
            CoreDownloader.download(core)
        }
        setStatus(Step.CORES, StepStatus.DONE, "${cores.size} cores")
    }

    private fun writeConfig(library: Library, retroArchPackage: String) = run(Step.CONFIG) {
        RetroArchConfig.writeConfig()
        RetroArchConfig.writePlaylists(library.games, library.systems, retroArchPackage)
        setStatus(Step.CONFIG, StepStatus.DONE)
    }

    private fun scrapeThumbnails(library: Library) = run(Step.THUMBNAILS) {
        val missing = ThumbnailScraper.missingCovers(library.games)
        val found = ThumbnailScraper.scrape(missing) { done, total ->
            setStatus(Step.THUMBNAILS, StepStatus.RUNNING, "$done/$total")
        }
        setStatus(Step.THUMBNAILS, StepStatus.DONE, "$found of ${missing.size} found")
    }

    private fun run(step: Step, block: () -> Unit) {
        _state.update { it.copy(busy = true) }
        setStatus(step, StepStatus.RUNNING)
        try {
            block()
        } catch (e: Exception) {
            setStatus(step, StepStatus.FAILED, e.message ?: e.javaClass.simpleName)
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    private fun setStatus(step: Step, status: StepStatus, detail: String = "") {
        _state.update { state ->
            state.copy(steps = state.steps.map { if (it.step == step) SetupStep(step, status, detail) else it })
        }
    }

    private fun progress(read: Long, total: Long): String {
        val readMb = read / 1_000_000
        return if (total > 0) "$readMb / ${total / 1_000_000} MB" else "$readMb MB"
    }
}

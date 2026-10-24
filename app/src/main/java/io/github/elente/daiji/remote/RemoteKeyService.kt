package io.github.elente.daiji.remote

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Runs what Settings > Remote buttons maps to each remote button, from any app. Buttons that aren't mapped pass
 * through untouched. Android only lets an accessibility service see keys pressed in other apps, so the user turns it on
 * once in Accessibility (or over ADB).
 */
class RemoteKeyService : AccessibilityService() {
    private var mappings: Map<Int, ButtonMapping> = emptyMap()

    /** Kept in a field: SharedPreferences holds its listeners weakly. */
    private val mappingsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> loadMappings() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        loadMappings()
        RemoteButtons.prefs(this).registerOnSharedPreferenceChangeListener(mappingsChanged)
    }

    override fun onDestroy() {
        RemoteButtons.prefs(this).unregisterOnSharedPreferenceChangeListener(mappingsChanged)
        super.onDestroy()
    }

    private fun loadMappings() {
        mappings = RemoteButtons.all(this).associateBy { it.scancode }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (ButtonLearning.capture(event)) return true
        val mapping = mappings[event.scanCode] ?: return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            val held = event.eventTime - event.downTime >= HOLD_MS
            val target = if (held && mapping.hold != null) mapping.hold else mapping.tap
            target?.let { RemoteActions.run(this, it) }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        /** Android's long-press timeout. */
        const val HOLD_MS = 500L
    }
}

/**
 * Hands the next remote button to Settings instead of acting on it. The service and the UI share the main thread, so a
 * plain field is enough.
 */
object ButtonLearning {
    private var listener: ((Int) -> Unit)? = null

    fun start(onButton: (Int) -> Unit) {
        listener = onButton
    }

    fun stop() {
        listener = null
    }

    /**
     * Navigation and volume keys pass through, so Back still cancels and the D-pad can't be remapped. Injected keys
     * carry no scancode and pass too.
     */
    fun capture(event: KeyEvent): Boolean {
        val onButton = listener ?: return false
        if (event.scanCode == 0 || event.keyCode in RESERVED_KEYS) return false
        if (event.action == KeyEvent.ACTION_UP) {
            listener = null
            onButton(event.scanCode)
        }
        return true
    }

    private val RESERVED_KEYS = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
    )
}

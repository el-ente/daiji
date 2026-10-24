package io.github.elente.daiji.remote

/**
 * Remote actions that only make sense while Home is on screen. Home binds itself while it is resumed and free of
 * dialogs; the rest of the time the button does nothing. The service and the UI share the main thread, so a plain
 * field is enough.
 */
object HomeShortcuts {
    private var onSearch: (() -> Unit)? = null

    fun bind(onSearch: () -> Unit) {
        this.onSearch = onSearch
    }

    fun unbind() {
        onSearch = null
    }

    fun search() = onSearch?.invoke()
}

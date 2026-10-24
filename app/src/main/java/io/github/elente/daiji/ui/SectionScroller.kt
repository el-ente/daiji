@file:OptIn(ExperimentalFoundationApi::class)

package io.github.elente.daiji.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Turns off a scroll container's own bring-into-view, for pages that SectionScroller scrolls instead. */
val NoPageScroll = object : BringIntoViewSpec {
    @Suppress("DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = spring()

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/**
 * Scrolls a page so the section holding focus starts at the top, whole: no row is left cut in half above it. The
 * [TOP] section scrolls the page back to its start, header included.
 */
class SectionScroller(private val scroll: ScrollState) {
    private val tops = HashMap<String, Int>()

    fun section(id: String): Modifier = Modifier.composed {
        val scope = rememberCoroutineScope()
        Modifier
            .onGloballyPositioned { tops[id] = it.positionInParent().y.roundToInt() }
            .onFocusChanged { if (it.hasFocus) scope.launch { scroll.animateScrollTo(target(id)) } }
    }

    private fun target(id: String): Int = if (id == TOP) 0 else tops[id] ?: scroll.value

    companion object {
        const val TOP = "top"
    }
}

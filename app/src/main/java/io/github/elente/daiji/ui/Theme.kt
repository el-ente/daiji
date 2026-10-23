package io.github.elente.daiji.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object DaijiColors {
    val Background = Color(0xFF121212)
    val Rail = Color(0xFF0A0A0A)
    val Surface = Color(0xFF262626)
    val SurfaceHigh = Color(0xFF3A3A3A)
    val Text = Color(0xFFF5F5F5)
    val Muted = Color(0xFFA3A3A3)
    val Accent = Color(0xFFF5A524)
    val AccentFocused = Color(0xFFFFC861)
    val OnAccent = Color(0xFF121212)

    /** Muted tones for games without artwork, picked by title. */
    val placeholders = listOf(
        Color(0xFF3A2F4F), Color(0xFF2F4A45), Color(0xFF4F3A2F), Color(0xFF2F3A4F),
        Color(0xFF4A2F3F), Color(0xFF3F4A2F), Color(0xFF2F2F4A), Color(0xFF4A4A2F),
    )

    fun placeholderFor(key: String): Color = placeholders[Math.floorMod(key.hashCode(), placeholders.size)]
}

@Composable
fun DaijiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DaijiColors.Accent,
            onPrimary = DaijiColors.OnAccent,
            background = DaijiColors.Background,
            onBackground = DaijiColors.Text,
            surface = DaijiColors.Surface,
            onSurface = DaijiColors.Text,
        ),
        content = content,
    )
}

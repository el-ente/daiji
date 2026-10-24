package io.github.elente.daiji.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object DaijiColors {
    val Background = Color(0xFF000000)
    val Ambient = Color(0xFF0B1626)
    val Glass = Color(0x1AFFFFFF)
    val GlassEdge = Color(0x73FFFFFF)
    val DockBand = Color(0x12FFFFFF)
    val FocusEdge = Color(0xF2FFFFFF)
    val FocusGlow = Color(0x80FFFFFF)
    val Label = Color(0xB3FFFFFF)
    val Marker = Color(0xB3000000)
    val Scrim = Color(0xD9000000)
    val Surface = Color(0xFF262626)
    val SurfaceHigh = Color(0xFF3A3A3A)
    val Text = Color(0xFFF5F5F5)
    val Muted = Color(0xFFA3A3A3)
    val Accent = Color(0xFFF5A524)
    val AccentFocused = Color(0xFFFFC861)
    val OnAccent = Color(0xFF121212)

    /** Muted tones for games without artwork, one per system so a grid reads as groups. */
    private val systemTones = mapOf(
        "nes" to Color(0xFF5A2A2A), "snes" to Color(0xFF3A2F5A), "n64" to Color(0xFF2F4A3A),
        "gb" to Color(0xFF3F4A2F), "gbc" to Color(0xFF2F4A4A), "gba" to Color(0xFF2F3A5A),
        "nds" to Color(0xFF4A4A4A), "mastersystem" to Color(0xFF2F3F5A), "genesis" to Color(0xFF22324A),
        "arcade" to Color(0xFF5A452A), "neogeo" to Color(0xFF4A3A22), "mame" to Color(0xFF4F3A2F),
        "psx" to Color(0xFF3A3A44), "psp" to Color(0xFF2A2A3A),
    )
    private val fallbackTone = Color(0xFF333340)

    fun systemTone(systemId: String): Color = systemTones[systemId] ?: fallbackTone
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

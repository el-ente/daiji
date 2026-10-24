package io.github.elente.daiji.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * A focusable card: scales up and shows a thin glass edge when the remote lands on it. The Menu key does what
 * holding OK does, for remotes that have one; holding OK keeps working on those that don't.
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    container: Color = DaijiColors.Surface,
    focusedContainer: Color = container,
    focusedScale: Float = 1.06f,
    showRing: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.menuKey(onLongClick),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = DaijiColors.Text,
            focusedContainerColor = focusedContainer,
            focusedContentColor = DaijiColors.Text,
            pressedContainerColor = focusedContainer,
            pressedContentColor = DaijiColors.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = if (showRing) {
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(2.dp, DaijiColors.GlassEdge), shape = shape),
            )
        } else {
            ClickableSurfaceDefaults.border()
        },
        content = content,
    )
}

private fun Modifier.menuKey(action: (() -> Unit)?): Modifier =
    if (action == null) {
        this
    } else {
        onPreviewKeyEvent { event ->
            if (event.key != Key.Menu) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp) action()
            true
        }
    }

/**
 * For a menu opened by holding OK: the release that ends the hold reaches the menu and would click its first option.
 * Select keys are dropped until one is pressed inside the menu; the hold's auto-repeats don't count as a press.
 */
fun Modifier.ignoreHeldSelect(): Modifier = composed {
    var pressedHere by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        if (event.key !in SelectKeys) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) pressedHere = true
        !pressedHere
    }
}

private val SelectKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** A frosted pill for the Home header. Android 11 has no live blur, so the frost is a translucent fill and a thin edge. */
@Composable
fun GlassPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = DaijiColors.Glass,
            contentColor = DaijiColors.Text,
            focusedContainerColor = DaijiColors.Text,
            focusedContentColor = DaijiColors.OnAccent,
            pressedContainerColor = DaijiColors.Text,
            pressedContentColor = DaijiColors.OnAccent,
        ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, DaijiColors.GlassEdge), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** A pill button. Primary is amber, secondary is gray; both turn bright when focused. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) DaijiColors.Accent else DaijiColors.Surface,
            contentColor = if (primary) DaijiColors.OnAccent else DaijiColors.Text,
            focusedContainerColor = if (primary) DaijiColors.AccentFocused else DaijiColors.Text,
            focusedContentColor = DaijiColors.OnAccent,
            pressedContainerColor = if (primary) DaijiColors.AccentFocused else DaijiColors.Text,
            pressedContentColor = DaijiColors.OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

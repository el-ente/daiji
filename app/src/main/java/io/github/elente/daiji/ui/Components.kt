package io.github.elente.daiji.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Glow
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
    focusedScale: Float = 1.08f,
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
                focusedBorder = Border(border = BorderStroke(3.dp, DaijiColors.FocusEdge), shape = shape),
            )
        } else {
            ClickableSurfaceDefaults.border()
        },
        glow = if (showRing) ClickableSurfaceDefaults.glow(focusedGlow = Glow(DaijiColors.FocusGlow, 14.dp)) else ClickableSurfaceDefaults.glow(),
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

/**
 * A glass pill holding a search box. The D-pad focuses it, OK opens the keyboard, Search on the keyboard closes it.
 * Up and Down always leave the pill, Left and Right only when there is no text for the cursor to move through,
 * because the text field would otherwise swallow them.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> { keyboard?.show(); true }
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionLeft -> query.isEmpty() && focusManager.moveFocus(FocusDirection.Left)
                    Key.DirectionRight -> query.isEmpty() && focusManager.moveFocus(FocusDirection.Right)
                    else -> false
                }
            }
            .clip(shape)
            .background(if (focused) DaijiColors.SurfaceHigh else DaijiColors.Glass)
            .border(1.dp, if (focused) DaijiColors.Text else DaijiColors.GlassEdge, shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        textStyle = TextStyle(color = DaijiColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(DaijiColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { field ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Glyphs.Search, contentDescription = null, tint = DaijiColors.Text, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(placeholder, color = DaijiColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    field()
                }
            }
        },
    )
}

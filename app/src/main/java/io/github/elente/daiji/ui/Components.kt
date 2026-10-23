package io.github.elente.daiji.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** A focusable card: scales up and shows an accent ring when the remote lands on it. */
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
        modifier = modifier,
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
                focusedBorder = Border(border = BorderStroke(3.dp, DaijiColors.Accent), shape = shape),
            )
        } else {
            ClickableSurfaceDefaults.border()
        },
        content = content,
    )
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

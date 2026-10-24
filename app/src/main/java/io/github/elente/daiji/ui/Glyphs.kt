package io.github.elente.daiji.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons missing from material-icons-core, drawn from the Material paths so the extended set isn't needed. */
object Glyphs {
    val Wifi: ImageVector = materialIcon(name = "Filled.Wifi") {
        materialPath {
            moveTo(1f, 9f)
            lineToRelative(2f, 2f)
            curveToRelative(4.97f, -4.97f, 13.03f, -4.97f, 18f, 0f)
            lineToRelative(2f, -2f)
            curveTo(16.93f, 2.93f, 7.08f, 2.93f, 1f, 9f)
            close()
            moveTo(9f, 17f)
            lineToRelative(3f, 3f)
            lineToRelative(3f, -3f)
            curveToRelative(-1.65f, -1.66f, -4.34f, -1.66f, -6f, 0f)
            close()
            moveTo(5f, 13f)
            lineToRelative(2f, 2f)
            curveToRelative(2.76f, -2.76f, 7.24f, -2.76f, 10f, 0f)
            lineToRelative(2f, -2f)
            curveTo(15.14f, 9.14f, 8.87f, 9.14f, 5f, 13f)
            close()
        }
    }
}

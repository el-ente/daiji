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

    val Search: ImageVector = materialIcon(name = "Filled.Search") {
        materialPath {
            moveTo(15.5f, 14f)
            horizontalLineToRelative(-0.79f)
            lineToRelative(-0.28f, -0.27f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
            reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
            curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
            lineToRelative(0.27f, 0.28f)
            verticalLineToRelative(0.79f)
            lineToRelative(5f, 4.99f)
            lineTo(20.49f, 19f)
            lineToRelative(-4.99f, -5f)
            close()
            moveTo(9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
            reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
            reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
            close()
        }
    }
}

package dev.hnm.workbench.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Premium dark brushed-metal theme for haptics authoring. */
object WorkbenchColors {
    // Base palette: very dark with brushed metal aesthetic
    val Background = Color(0xFF0F1013) // Deep black with slight warmth
    val Surface = Color(0xFF1A1D22) // Elevated surface
    val SurfaceVariant = Color(0xFF252A32) // Secondary surface
    val PanelLight = Color(0xFFF5F5F5) // Light control panels
    val PanelDark = Color(0xFF2D3139) // Dark control panels

    // Accents
    val Primary = Color(0xFFE74C3C) // Active red (from reference image)
    val Haptic = Color(0xFFFF8A5B) // Haptic events (warm orange)
    val HapticDim = Color(0xFF7A4632)
    val Accent = Color(0xFFE74C3C) // Recording/active indicator

    // UI Elements
    val Grid = Color(0xFF323743)
    val OnSurface = Color(0xFFE6E8EC) // High contrast text
    val Muted = Color(0xFF8B90A0)
    val Shadow = Color(0x4D000000) // For depth/shadows
}

@Composable
fun WorkbenchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = WorkbenchColors.Primary,
            background = WorkbenchColors.Background,
            surface = WorkbenchColors.Surface,
            surfaceVariant = WorkbenchColors.SurfaceVariant,
            onSurface = WorkbenchColors.OnSurface,
            onBackground = WorkbenchColors.OnSurface,
            onPrimary = Color(0xFF06243A),
        ),
        content = content,
    )
}

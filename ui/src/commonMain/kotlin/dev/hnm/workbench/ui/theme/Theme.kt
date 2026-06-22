package dev.hnm.workbench.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** A small dark theme tuned for a timeline/waveform editor. */
object WorkbenchColors {
    val Background = Color(0xFF14161B)
    val Surface = Color(0xFF1E2128)
    val SurfaceVariant = Color(0xFF272B34)
    val Primary = Color(0xFF5BC2FF) // audio / accent
    val Haptic = Color(0xFFFF8A5B) // haptic events
    val HapticDim = Color(0xFF7A4632)
    val Grid = Color(0xFF323743)
    val OnSurface = Color(0xFFE6E8EC)
    val Muted = Color(0xFF8B90A0)
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

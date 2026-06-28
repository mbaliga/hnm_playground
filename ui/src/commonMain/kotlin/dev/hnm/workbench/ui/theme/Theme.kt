package dev.hnm.workbench.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colors ported directly from the recorder HTML:
 * --screen:#0b0b0b  --ink:#efeeec  --ink-dim:#c8c7c4  --bar:#e9e9e6
 * --red:#e22c24  --housing:#e9e8e5  --housing-edge:#d6d5d1
 * --frame:#ecebE8  --dome-hi:#ffffff  --dome-lo:#dedcd8
 * --grille:#c3c2bf  --icon:#a9a8a5
 */
object WorkbenchColors {
    // Screen / dark area
    val Screen      = Color(0xFF0B0B0B)
    val Ink         = Color(0xFFEFEEEC)   // primary text on screen
    val InkDim      = Color(0xFFC8C7C4)   // secondary text on screen
    val Bar         = Color(0xFFE9E9E6)   // waveform bars
    val Red         = Color(0xFFE22C24)   // record dot, playhead, active

    // Housing / light controls area
    val Housing     = Color(0xFFE9E8E5)
    val HousingEdge = Color(0xFFD6D5D1)
    val Frame       = Color(0xFFECEBE8)
    val FrameEdge   = Color(0xFFD9D8D4)
    val DomeHi      = Color(0xFFFFFFFF)
    val DomeLo      = Color(0xFFDEDCD8)
    val Grille      = Color(0xFFC3C2BF)
    val Icon        = Color(0xFFA9A8A5)

    // Page background (the warm gray behind the device)
    val PageBg      = Color(0xFFCDCBC8)   // midpoint of the HTML radial-gradient

    // Semantic aliases used by existing components (mapped to new palette)
    val Background    = Screen
    val Surface       = Color(0xFF161616)   // slightly lifted from screen
    val SurfaceVariant = Color(0xFF1E1E1E)
    val PanelLight    = Housing
    val PanelDark     = Color(0xFF1A1A1A)
    val Primary       = Red
    val Haptic        = Color(0xFFFF8A5B)   // haptic events — warm orange
    val HapticDim     = Color(0xFF7A4632)
    val Accent        = Red
    val Grid          = Color(0xFF2A2A2A)
    val OnSurface     = Ink
    val Muted         = InkDim
    val Shadow        = Color(0x73000000)
}

@Composable
fun WorkbenchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary          = WorkbenchColors.Red,
            background       = WorkbenchColors.Screen,
            surface          = WorkbenchColors.Surface,
            surfaceVariant   = WorkbenchColors.SurfaceVariant,
            onSurface        = WorkbenchColors.Ink,
            onBackground     = WorkbenchColors.Ink,
            onPrimary        = Color.White,
        ),
        content = content,
    )
}

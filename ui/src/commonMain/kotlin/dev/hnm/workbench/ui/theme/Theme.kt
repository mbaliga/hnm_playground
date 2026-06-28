package dev.hnm.workbench.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colors ported directly from recorder2.html (the dark/refined revision):
 *   --screen:#141210   --ink:#dddbd6   --ink-dim:#5c5a56   --red:#e22c24
 * Device shell gradient: #1c1a18 → #111009 → #0a0908 (160deg), border #282420.
 * Battery: #0a0908 bg / #999 text / #252220 border.
 * Grille: #0a0908 bg, white .22 dots, #1c1a16 border.
 * Keypad slab: #050402; key cell: #161412; crater dome: dark→lighter vertical.
 * Glyphs: rec red, stop #58554f, icons #524f4a.
 */
object WorkbenchColors {
    // Screen / content
    val Screen   = Color(0xFF141210)   // dark grey-brown, NOT pitch black
    val Ink      = Color(0xFFDDDBD6)    // primary text
    val InkDim   = Color(0xFF5C5A56)    // secondary text (much dimmer than v1)
    val Red      = Color(0xFFE22C24)    // record dot, playhead, active accent

    // Waveform bars: rgba(210,207,200,.82)
    val Bar      = Color(0xFFD2CFC8)

    // Device shell
    val ShellTop = Color(0xFF1C1A18)
    val ShellMid = Color(0xFF111009)
    val ShellLo  = Color(0xFF0A0908)
    val ShellBorder = Color(0xFF282420)

    // Chin: battery + grille
    val BatteryBg     = Color(0xFF0A0908)
    val BatteryText   = Color(0xFF999999)
    val BatteryBorder = Color(0xFF252220)
    val GrilleBg      = Color(0xFF0A0908)
    val GrilleDot     = Color(0x38FFFFFF)   // rgba(255,255,255,.22)
    val GrilleBorder  = Color(0xFF1C1A16)

    // Keypad slab + cells
    val SlabBg   = Color(0xFF050402)
    val KeyCell  = Color(0xFF161412)
    val KeyDot   = Color(0x0AFFFFFF)        // rgba(255,255,255,.04)

    // Crater dome vertical gradient (top dark → bottom lighter)
    val Crater0  = Color(0xFF020201)
    val Crater18 = Color(0xFF0A0907)
    val Crater42 = Color(0xFF141210)
    val Crater68 = Color(0xFF1D1B17)
    val Crater88 = Color(0xFF262319)
    val Crater100 = Color(0xFF2D2A1F)

    // Glyphs / icons
    val GlyphStop = Color(0xFF58554F)
    val Icon      = Color(0xFF524F4A)
    val WifiOn    = Color(0xFF3A8DDB)

    // Semantic aliases used across existing components (mapped to the dark palette)
    val Background    = Screen
    val Surface       = Color(0xFF1A1815)   // slightly lifted card on screen
    val SurfaceVariant = Color(0xFF221F1B)
    val PanelLight    = KeyCell
    val PanelDark     = SlabBg
    val Primary       = Red
    val Haptic        = Color(0xFFC98A5B)   // haptic events — muted warm
    val HapticDim     = Color(0xFF5A4030)
    val Accent        = Red
    val Grid          = Color(0xFF26231F)
    val OnSurface     = Ink
    val Muted         = InkDim
    val Shadow        = Color(0xCC000000)
    val Housing       = SlabBg
    val HousingEdge   = ShellBorder
    val Frame         = ShellTop
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

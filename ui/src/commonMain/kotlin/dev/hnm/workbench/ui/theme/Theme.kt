package dev.hnm.workbench.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Compose [Color]s for every [HyleTokens.Color] value, so call sites never juggle raw ARGB Longs.
 * `Color(Long)` already reads 0xAARRGGBB directly, so this is a pure type wrapper.
 */
object HyleColors {
    val FieldVoid = Color(HyleTokens.Color.FieldVoid)
    val FieldNear = Color(HyleTokens.Color.FieldNear)
    val FieldRaised = Color(HyleTokens.Color.FieldRaised) // #121212 — the "never pure black" floor
    val FieldDeep = Color(HyleTokens.Color.FieldDeep)

    val InkPure = Color(HyleTokens.Color.InkPure)
    val InkFull = Color(HyleTokens.Color.InkFull)
    val InkDim = Color(HyleTokens.Color.InkDim)
    val InkFaint = Color(HyleTokens.Color.InkFaint)

    val AccentViolet = Color(HyleTokens.Color.AccentViolet)
    val AccentVioletBright = Color(HyleTokens.Color.AccentVioletBright)
    val AccentVioletDeep = Color(HyleTokens.Color.AccentVioletDeep)

    val ProvenanceNative = Color(HyleTokens.Color.ProvenanceNative) // radium — on-device
    val ProvenanceCloud = Color(HyleTokens.Color.ProvenanceCloud)  // cold cyan — from elsewhere

    val HairlineDefault = Color(HyleTokens.Color.HairlineDefault)
    val HairlineStrong = Color(HyleTokens.Color.HairlineStrong)

    val GlassPane = Color(HyleTokens.Color.GlassPane)

    val SignalDanger = Color(HyleTokens.Color.SignalDanger)
    val SignalWarning = Color(HyleTokens.Color.SignalWarning)
    val SignalSuccess = Color(HyleTokens.Color.SignalSuccess)

    val ControlSurface = Color(HyleTokens.Color.ControlSurface)
    val ControlSurfaceRaised = Color(HyleTokens.Color.ControlSurfaceRaised)
    val ControlSurfaceHigh = Color(HyleTokens.Color.ControlSurfaceHigh)
    val ControlGroove = Color(HyleTokens.Color.ControlGroove)
    val ControlEdge = Color(HyleTokens.Color.ControlEdge)
    val ControlRim = Color(HyleTokens.Color.ControlRim)
    val ControlRimSoft = Color(HyleTokens.Color.ControlRimSoft)
    val ControlIndicator = Color(HyleTokens.Color.ControlIndicator)
    val ControlScreen = Color(HyleTokens.Color.ControlScreen)       // = recorder2 Screen #141210
    val ControlScreenInk = Color(HyleTokens.Color.ControlScreenInk) // = recorder2 Ink #DDDBD6
    val ControlScreenDim = Color(HyleTokens.Color.ControlScreenDim) // = recorder2 InkDim #5C5A56
}

/**
 * The role map from the UX brief §3.5 — which token plays which job, so no composable ever reasons
 * about a raw hex value. **Red-as-state is retired product-wide**: the one loud color is violet;
 * playback/live/provenance states use the radium/cyan glow system instead of hue-as-alert.
 */
object HyleRoles {
    // Primary action — the one loud thing per screen (Play, Generate, Save).
    val PrimaryAction = HyleColors.AccentViolet
    val PrimaryActionHover = HyleColors.AccentVioletBright
    val PrimaryActionActive = HyleColors.AccentVioletDeep
    val OnPrimaryAction = Color(HyleTokens.Color.ActionOnPrimary)

    // Playback / "alive" state and assistant provenance both reuse the Provenance glow system
    // (see HyleProvenance.kt) rather than inventing a separate playback color.
    val PlaybackGlow = HyleColors.ProvenanceNative
    val AssistantOnDeviceGlow = HyleColors.ProvenanceNative
    val AssistantCloudGlow = HyleColors.ProvenanceCloud

    val SelectionOutline = HyleColors.AccentViolet
    val SelectionSurface = HyleColors.ControlSurfaceRaised

    // Destructive: luminance + icon + confirm step carry the meaning; hue is a supporting cue only.
    val Destructive = HyleColors.SignalDanger

    val Background = HyleColors.ControlScreen
    val Surface = HyleColors.ControlSurface
    val SurfaceVariant = HyleColors.ControlSurfaceRaised
    val OnSurface = HyleColors.ControlScreenInk
    val Muted = HyleColors.ControlScreenDim
    val Grid = HyleColors.HairlineDefault
    val GridStrong = HyleColors.HairlineStrong
}

/**
 * Legacy recorder2 palette + widget colors, kept as a **compatibility layer** so the ~20 existing
 * composables that reference `WorkbenchColors.*` keep compiling and immediately inherit the Hyle role
 * map (notably: [Red]/[Primary]/[Accent] now resolve to violet, not literal red — D5 "red-as-state is
 * banned" takes effect everywhere without a per-file edit). This object is scheduled for removal once
 * every call site is migrated to [HyleColors]/[HyleRoles] directly (tracked alongside the Phase 0
 * nav-graph restructuring, since that work already touches every panel file).
 */
object WorkbenchColors {
    // Screen / content — now literally HyleColors.ControlScreen family, not a separate palette.
    val Screen = HyleColors.ControlScreen
    val Ink = HyleColors.ControlScreenInk
    val InkDim = HyleColors.ControlScreenDim

    /** @deprecated Was literal red (#E22C24); now the Hyle violet accent. Prefer [HyleRoles.PrimaryAction]. */
    val Red = HyleRoles.PrimaryAction

    // Waveform bars: rgba(210,207,200,.82)
    val Bar = Color(0xFFD2CFC8)

    // Device shell
    val ShellTop = Color(0xFF1C1A18)
    val ShellMid = Color(0xFF111009)
    val ShellLo = Color(0xFF0A0908)
    val ShellBorder = Color(0xFF282420)

    // Chin: battery + grille
    val BatteryBg = Color(0xFF0A0908)
    val BatteryText = Color(0xFF999999)
    val BatteryBorder = Color(0xFF252220)
    val GrilleBg = Color(0xFF0A0908)
    val GrilleDot = Color(0x38FFFFFF)
    val GrilleBorder = Color(0xFF1C1A16)

    // Keypad slab + cells
    val SlabBg = Color(0xFF050402)
    val KeyCell = Color(0xFF161412)
    val KeyDot = Color(0x0AFFFFFF)

    // Crater dome vertical gradient (top dark → bottom lighter)
    val Crater0 = Color(0xFF020201)
    val Crater18 = Color(0xFF0A0907)
    val Crater42 = Color(0xFF141210)
    val Crater68 = Color(0xFF1D1B17)
    val Crater88 = Color(0xFF262319)
    val Crater100 = Color(0xFF2D2A1F)

    // Glyphs / icons
    val GlyphStop = Color(0xFF58554F)
    val Icon = Color(0xFF524F4A)
    val WifiOn = Color(0xFF3A8DDB)

    // Semantic aliases — now sourced from HyleRoles.
    val Background = HyleRoles.Background
    val Surface = HyleRoles.Surface
    val SurfaceVariant = HyleRoles.SurfaceVariant
    val PanelLight = KeyCell
    val PanelDark = SlabBg
    val Primary = HyleRoles.PrimaryAction
    val Haptic = Color(0xFFC98A5B)
    val HapticDim = Color(0xFF5A4030)
    val Accent = HyleRoles.PrimaryAction
    val Grid = HyleRoles.Grid
    val OnSurface = HyleRoles.OnSurface
    val Muted = HyleRoles.Muted
    val Shadow = Color(0xCC000000)
    val Housing = SlabBg
    val HousingEdge = ShellBorder
    val Frame = ShellTop
}

@Composable
fun WorkbenchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HyleRoles.PrimaryAction,
            background = HyleRoles.Background,
            surface = HyleRoles.Surface,
            surfaceVariant = HyleRoles.SurfaceVariant,
            onSurface = HyleRoles.OnSurface,
            onBackground = HyleRoles.OnSurface,
            onPrimary = HyleRoles.OnPrimaryAction,
            error = HyleRoles.Destructive,
        ),
        content = content,
    )
}

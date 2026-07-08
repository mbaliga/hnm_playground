package dev.hnm.workbench.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.library.ChromeSemantic
import dev.hnm.workbench.ui.components.DotGridSubstrate
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.nav.AppRoute
import dev.hnm.workbench.ui.nav.AppTab
import dev.hnm.workbench.ui.nav.MakeSourceKind
import dev.hnm.workbench.ui.screens.DeviceScreen
import dev.hnm.workbench.ui.screens.FeelScreen
import dev.hnm.workbench.ui.screens.MakeScreen
import dev.hnm.workbench.ui.screens.MakeSourceScreen
import dev.hnm.workbench.ui.theme.HyleColors
import dev.hnm.workbench.ui.theme.HyleRoles
import dev.hnm.workbench.ui.theme.HyleTokens

/**
 * The single-activity shell (UX brief §3.1/§3.2 D1/D2): three tabs — Feel (home), Make, Device — plus
 * the full-screen Editor route, entered from a tab and always returning to it. [WorkbenchApp] is what
 * the Editor route hosts, now with a real top bar (back arrow, rename, undo/redo — Phase 4); the
 * dense panel body underneath is otherwise unchanged.
 */
@Composable
fun AppShell(
    state: EditorState,
    onSelfTest: (() -> Unit)? = null,
    onCaptureDeviceReport: (() -> String)? = null,
    onReplayOnboarding: (() -> Unit)? = null,
    onReplaySplash: (() -> Unit)? = null,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var route by remember { mutableStateOf<AppRoute>(AppRoute.Tab(AppTab.FEEL)) }
    var returnTab by remember { mutableStateOf(AppTab.FEEL) }

    fun switchTab(tab: AppTab) {
        val current = route
        if (current is AppRoute.Tab && current.tab == tab) return
        state.chrome.play(ChromeSemantic.NAVIGATE)
        route = AppRoute.Tab(tab)
    }

    fun openEditor(fromTab: AppTab) {
        returnTab = fromTab
        route = AppRoute.Editor
    }

    fun closeEditor() {
        route = AppRoute.Tab(returnTab)
    }

    fun openMakeSource(kind: MakeSourceKind) {
        route = AppRoute.MakeSource(kind)
    }

    when (val r = route) {
        is AppRoute.Editor -> {
            // The Editor's own top bar (WorkbenchApp's EditorTopBar) supplies the back arrow via onBack.
            WorkbenchApp(state = state, onBack = ::closeEditor)
            // Platform back-gesture hook (Android predictable back / Esc on desktop) — still a no-op
            // marker; the top bar's back arrow is the primary path for now.
            BackHandlerHook(onBack = ::closeEditor)
        }
        is AppRoute.MakeSource -> {
            MakeSourceScreen(
                kind = r.kind,
                state = state,
                onBack = { route = AppRoute.Tab(AppTab.MAKE) },
                onOpenEditor = { openEditor(AppTab.MAKE) },
            )
        }
        is AppRoute.Tab -> {
            Scaffold(
                modifier = modifier,
                containerColor = HyleRoles.Background,
                bottomBar = {
                    TabBar(selected = r.tab, onSelect = ::switchTab)
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(HyleRoles.Background),
                ) {
                    DotGridSubstrate(modifier = Modifier.fillMaxSize())
                    // A quick cross-fade between tabs (Hyle's Duration.Calm) so switching reads as
                    // motion, not a hard cut — skipped entirely under reduced motion.
                    AnimatedContent(
                        targetState = r.tab,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(snap()) togetherWith fadeOut(snap())
                            } else {
                                fadeIn(tween(HyleTokens.Duration.Calm)) togetherWith
                                    fadeOut(tween(HyleTokens.Duration.Instant))
                            }
                        },
                        label = "tab-content",
                    ) { tab ->
                        // Desktop adaptive layout: these screens read as a phone-width list; on a wide
                        // window, cap and center that column instead of stretching text edge to edge.
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.fillMaxHeight().widthIn(max = DESKTOP_MAX_CONTENT_WIDTH)) {
                                when (tab) {
                                    AppTab.FEEL -> FeelScreen(
                                        state = state,
                                        modifier = Modifier.fillMaxSize(),
                                        onOpenEditor = { openEditor(AppTab.FEEL) },
                                        onSelfTest = onSelfTest,
                                        onJumpToMakeWithPrompt = { prompt ->
                                            state.lastPrompt = prompt
                                            switchTab(AppTab.MAKE)
                                        },
                                        contentPadding = padding,
                                    )
                                    AppTab.MAKE -> MakeScreen(
                                        state = state,
                                        onOpenEditor = { openEditor(AppTab.MAKE) },
                                        onOpenSource = ::openMakeSource,
                                        contentPadding = padding,
                                    )
                                    AppTab.DEVICE -> DeviceScreen(
                                        state = state,
                                        onCaptureDeviceReport = onCaptureDeviceReport,
                                        onReplayOnboarding = onReplayOnboarding,
                                        onReplaySplash = onReplaySplash,
                                        contentPadding = padding,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DESKTOP_MAX_CONTENT_WIDTH = 640.dp

@Composable
private fun TabBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(containerColor = HyleColors.ControlSurface) {
        NavigationBarItem(
            selected = selected == AppTab.FEEL,
            onClick = { onSelect(AppTab.FEEL) },
            icon = { Text("●", fontSize = 16.sp) },
            label = { Text("Feel") },
            colors = tabColors(),
        )
        NavigationBarItem(
            selected = selected == AppTab.MAKE,
            onClick = { onSelect(AppTab.MAKE) },
            icon = { Text("✦", fontSize = 16.sp) },
            label = { Text("Make") },
            colors = tabColors(),
        )
        NavigationBarItem(
            selected = selected == AppTab.DEVICE,
            onClick = { onSelect(AppTab.DEVICE) },
            icon = { Text("◐", fontSize = 16.sp) },
            label = { Text("Device") },
            colors = tabColors(),
        )
    }
}

@Composable
private fun tabColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = HyleRoles.OnPrimaryAction,
    selectedTextColor = HyleRoles.SelectionOutline,
    indicatorColor = HyleRoles.PrimaryAction,
    unselectedIconColor = HyleRoles.Muted,
    unselectedTextColor = HyleRoles.Muted,
)

/**
 * Placeholder for a real platform back-gesture hook (Android predictable back / Esc on desktop).
 * The Editor's top bar already has a working back arrow ([onBack] wired through); this hook is for
 * the OS-level gesture/key, not yet wired to any platform API — a no-op marker so the intent is
 * visible in the tree rather than silently absent.
 */
@Composable
private fun BackHandlerHook(onBack: () -> Unit) {
    // Intentionally inert for now — see KDoc above.
}

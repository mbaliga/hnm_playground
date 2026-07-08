package dev.hnm.workbench.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.library.ChromeSemantic
import dev.hnm.workbench.ui.components.DotGridSubstrate
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.nav.AppRoute
import dev.hnm.workbench.ui.nav.AppTab
import dev.hnm.workbench.ui.screens.DeviceScreen
import dev.hnm.workbench.ui.screens.FeelScreen
import dev.hnm.workbench.ui.screens.MakeScreen
import dev.hnm.workbench.ui.theme.HyleColors
import dev.hnm.workbench.ui.theme.HyleRoles

/**
 * The single-activity shell (UX brief §3.1/§3.2 D1/D2): three tabs — Feel (home), Make, Device — plus
 * the full-screen Editor route, entered from a tab and always returning to it. [WorkbenchApp] (the
 * existing panel-based editor, unchanged) is what the Editor route hosts; this shell doesn't touch its
 * internals — that redesign is Phase 4's job.
 */
@Composable
fun AppShell(
    state: EditorState,
    onOpenGallery: (() -> Unit)? = null,
    onSelfTest: (() -> Unit)? = null,
    onCaptureDeviceReport: (() -> String)? = null,
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

    when (val r = route) {
        is AppRoute.Editor -> {
            // Unchanged existing editor; Gallery button (if wired) still reaches the legacy activity.
            WorkbenchApp(state = state, onOpenGallery = onOpenGallery)
            // A minimal way back until Phase 4 gives the Editor its own top bar with a real back arrow:
            // the bottom nav simply isn't shown while editing, so route state itself is the back target.
            BackHandlerHook(onBack = ::closeEditor)
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
                    when (r.tab) {
                        AppTab.FEEL -> FeelScreen(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            onOpenEditor = { openEditor(AppTab.FEEL) },
                            onSelfTest = onSelfTest,
                            contentPadding = padding,
                        )
                        AppTab.MAKE -> MakeScreen(
                            state = state,
                            onOpenEditor = { openEditor(AppTab.MAKE) },
                            contentPadding = padding,
                        )
                        AppTab.DEVICE -> DeviceScreen(
                            state = state,
                            onCaptureDeviceReport = onCaptureDeviceReport,
                            contentPadding = padding,
                        )
                    }
                }
            }
        }
    }
}

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
 * Phase 4 gives the Editor a proper top bar with an explicit back arrow; until then this is a no-op
 * marker so the intent is visible in the tree rather than silently absent.
 */
@Composable
private fun BackHandlerHook(onBack: () -> Unit) {
    // Intentionally inert for now — see KDoc above.
}

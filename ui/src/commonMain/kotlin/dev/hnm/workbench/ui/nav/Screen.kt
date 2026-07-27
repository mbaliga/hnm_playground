package dev.hnm.workbench.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The three bottom-tab destinations that make up the shell's home. [glyph] is a plain-text stand-in
 * for an icon (the app has no icon-font/asset pipeline yet — every other visual in this UI is drawn or
 * set in Unicode too, see [dev.hnm.workbench.ui.components.GlyphRec]) so the tab bar stays legible
 * everywhere without adding an asset dependency.
 */
enum class Tab(val label: String, val glyph: String) {
    LIBRARY("Library", "▤"),
    MAKE("Make", "✦"),
    DEVICE("Device", "◧"),
}

/**
 * Full-screen routes pushed on top of the tab shell. A separate axis from [Tab] because the editor
 * covers the *whole* device screen — including the tab bar — rather than living inside one tab's
 * content area.
 */
sealed interface Screen {
    /**
     * The deep-edit surface: timeline, transport, inspector, envelope. Reachable from a library row's
     * chevron (tapping the row itself plays the pattern; the chevron opens it here — see
     * [dev.hnm.workbench.ui.components.LibraryListPanel]) or from "Editor" in the Make tab.
     * [patternName] is informational only — the pattern itself already lives in
     * [dev.hnm.workbench.ui.model.EditorState] by the time this route is pushed.
     */
    data class Editor(val patternName: String? = null) : Screen
}

/**
 * Modal sheets presentable from anywhere in the shell — over a tab or over the pushed [Screen.Editor]
 * alike. [SHIP], [LEARN] and [IMPORT] already have real panels to host
 * ([dev.hnm.workbench.ui.components.ExportPanel], [dev.hnm.workbench.ui.components.WalkthroughCard],
 * [dev.hnm.workbench.ui.components.ImportPanel] respectively); the rest render a minimal placeholder
 * body for now (see `nav/PlaceholderSheets.kt`) — but every route here is real and reachable today, not
 * a dead stub waiting on this enum entry to be added later.
 */
enum class Sheet(val title: String) {
    SETTINGS("Settings"),
    SHIP("Ship"),
    LEARN("Learn"),
    JSON("JSON"),
    IMPORT("Import"),
    COMBINE("Combine"),
    FEEDBACK("Feedback"),
}

/**
 * Hand-rolled navigation state for the workbench shell: one selected [Tab], an optional [Screen] pushed
 * full-screen over it, and an optional [Sheet] presented modally over everything (tab or screen alike).
 *
 * There's no deep back stack — [screen] is "one level deep" because that's all three tabs plus one
 * editor need today. If the app grows a second pushable route, widen [Screen] into a real stack rather
 * than bolting more nullable fields on here.
 */
class WorkbenchNavState(initialTab: Tab = Tab.MAKE) {
    var tab: Tab by mutableStateOf(initialTab)
        private set

    var screen: Screen? by mutableStateOf(null)
        private set

    var sheet: Sheet? by mutableStateOf(null)
        private set

    /** Switch tabs. Backs out of any pushed full-screen route — a tab switch is a fresh start. */
    fun selectTab(next: Tab) {
        tab = next
        screen = null
    }

    /** Push the full-screen editor. Doesn't touch [tab], so closing it lands back where you were. */
    fun openEditor(patternName: String? = null) {
        screen = Screen.Editor(patternName)
    }

    /** Pop the pushed [screen], returning to the tab shell. */
    fun closeScreen() {
        screen = null
    }

    /** Present [next] modally. Replaces whatever sheet (if any) was already open. */
    fun openSheet(next: Sheet) {
        sheet = next
    }

    /** Dismiss the active sheet, if any. */
    fun dismissSheet() {
        sheet = null
    }
}

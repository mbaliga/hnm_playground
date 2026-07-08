package dev.hnm.workbench.ui.nav

/**
 * The app's top-level destinations (UX brief §3.2 D2): three tabs plus one modal-feeling route. Hand-
 * rolled rather than a `navigation-compose` back stack — this app has exactly one non-tab destination
 * (Editor), entered from either Feel or Make and always returning to whichever tab it was entered from,
 * which a single "return to" field expresses more simply than a real back stack.
 */
enum class AppTab { FEEL, MAKE, DEVICE }

sealed interface AppRoute {
    /** One of the three bottom-nav tabs. */
    data class Tab(val tab: AppTab) : AppRoute

    /** Full-screen, entered with whatever pattern is already loaded into [EditorState][dev.hnm.workbench.ui.model.EditorState.pattern]. */
    data object Editor : AppRoute
}

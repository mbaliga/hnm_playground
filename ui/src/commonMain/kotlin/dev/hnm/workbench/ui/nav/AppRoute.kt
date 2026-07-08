package dev.hnm.workbench.ui.nav

/**
 * The app's top-level destinations (UX brief §3.2 D2, §6.2): three tabs plus two transient routes.
 * Hand-rolled rather than a `navigation-compose` back stack — every non-tab destination here always
 * returns to a single tab, which a "return to" field expresses more simply than a real back stack.
 */
enum class AppTab { FEEL, MAKE, DEVICE }

/** Make's five generative source mini-flows (UX brief §6.2). */
enum class MakeSourceKind(val title: String) {
    MATERIAL("Material"),
    TEXTURE("Texture"),
    MOTION("Motion"),
    RHYTHM("Rhythm"),
    BLEND("Blend"),
}

sealed interface AppRoute {
    /** One of the three bottom-nav tabs. */
    data class Tab(val tab: AppTab) : AppRoute

    /** Full-screen, entered with whatever pattern is already loaded into [EditorState][dev.hnm.workbench.ui.model.EditorState.pattern]. */
    data object Editor : AppRoute

    /** A Make source mini-flow (always entered from, and returns to, the Make tab). */
    data class MakeSource(val kind: MakeSourceKind) : AppRoute
}

package dev.hnm.workbench.ui.coachmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.settings.OnboardingProgress
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Coachmark engine for the beginner path: a small queue of one-at-a-time "point at this control and
 * explain it" callouts, each targeting a [CoachAnchorId] registered somewhere in the tree by
 * [CoachAnchor].
 *
 * The one rule this exists to enforce: **a coachmark only shows when its target is actually reachable
 * right now** — composed into the tree, on the visible tab/route, and not covered by a modal sheet.
 * [CoachAnchor] reports that liveness into a [CoachAnchorRegistry] as Compose composes/decomposes the
 * anchored element (tab switch, sheet opening over it, route pop, ...), so [CoachMarkEngine.active]
 * always reflects the *current* state of the UI, not an assumption made when the coachmark was enqueued.
 *
 * This directly avoids a real prototype bug class: a naive queue that just shows `specs.first()` (or
 * blocks until it's dismissed) starves forever the moment that first target isn't reachable — e.g. it
 * points at a Library-tab control while the user is sitting in the Editor. [CoachMarkEngine.active] instead
 * walks the queue in order and returns the first entry that is *both* not yet dismissed *and* live,
 * skipping over (not dismissing) anything unreachable — so a coach whose target the user hasn't reached
 * yet quietly steps aside and fires later, once they get there, instead of blocking every coach behind it.
 */

/** Identifies a specific UI element a coachmark can point at. */
enum class CoachAnchorId {
    /** The Library entry in the bottom tab bar — reachable whenever the tab shell is showing. */
    LIBRARY_TAB,

    /** The Inspector panel on the Editor route — reachable whenever the full-screen editor is pushed. */
    EDITOR_INSPECTOR,

    /** The "Ship" icon in the Editor's top bar — reachable whenever the full-screen editor is pushed. */
    EDITOR_SHIP,
}

/** How a coachmark clears out of the queue once it's showing. */
enum class ClearOn {
    /** Only clears when the user explicitly taps the coachmark itself ("Got it"). Blocks until tapped. */
    DISMISS,

    /** Clears automatically the moment its target is actually used (e.g. the button it points at is tapped). */
    TARGET_ACTION,
}

/** One entry in a [CoachMarkEngine]'s queue: what to say, about which anchor, cleared how. */
data class CoachMarkSpec(
    val id: CoachAnchorId,
    val title: String,
    val body: String,
    val clearOn: ClearOn = ClearOn.DISMISS,
)

/**
 * Tracks, for every [CoachAnchorId] currently registered by a [CoachAnchor] somewhere in the tree,
 * whether that element is presently reachable (composed, on-screen, unobstructed) — and its last-known
 * on-screen bounds, for positioning the callout near it.
 *
 * This is the "is it actually visible" check: presence here means Compose actually composed the anchor
 * *and* the caller told it nothing (like an open sheet) is covering it right now, not merely that the
 * anchor exists somewhere in the spec queue.
 */
class CoachAnchorRegistry {
    private var live: Map<CoachAnchorId, Rect?> by mutableStateOf(emptyMap())

    fun isLive(id: CoachAnchorId): Boolean = live.containsKey(id)

    fun boundsOf(id: CoachAnchorId): Rect? = live[id]

    /** Called by [CoachAnchor] whenever composition/reachability of [id] changes. */
    internal fun setPresent(id: CoachAnchorId, present: Boolean, bounds: Rect? = null) {
        live = if (present) {
            live + (id to (bounds ?: live[id]))
        } else {
            live - id
        }
    }
}

/**
 * Reads whether a modal sheet is currently covering everything below it ([dev.hnm.workbench.ui.nav.SheetHost]
 * draws its sheet on top of, not instead of, the tab shell / editor content — so those stay composed, and
 * an explicit signal like this one is exactly what's needed to tell a still-composed-but-covered anchor
 * apart from a genuinely visible one). Provided once near the root of [dev.hnm.workbench.ui.WorkbenchApp].
 */
val LocalCoachMarksBlocked = compositionLocalOf { false }

/**
 * Wraps a target composable so its liveness (and, best-effort, its on-screen bounds) is reported into
 * [registry] under [id] for as long as this call stays composed with [reachable] = true. When the caller
 * switches tabs/pops the route, Compose stops composing this wrapper entirely and [DisposableEffect]'s
 * `onDispose` clears the registration automatically — no manual bookkeeping, no stale "still enqueued"
 * state left behind.
 */
@Composable
fun CoachAnchor(
    id: CoachAnchorId,
    registry: CoachAnchorRegistry,
    reachable: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DisposableEffect(id, reachable) {
        registry.setPresent(id, reachable)
        onDispose { registry.setPresent(id, false) }
    }
    Box(
        modifier.onGloballyPositioned { coords ->
            if (reachable) registry.setPresent(id, true, coords.boundsInRoot())
        },
    ) {
        content()
    }
}

/**
 * One at a time, in order: the queue of coachmarks for the current session plus which ones have been
 * dismissed. [specs] is meant to be kept in sync with live app state (e.g. re-derived from
 * [dev.hnm.workbench.core.settings.OnboardingProgress] via [onboardingCoachMarkSpecs]) by whoever owns
 * this engine; the engine itself only cares about ordering, dismissal and reachability.
 */
class CoachMarkEngine(val registry: CoachAnchorRegistry = CoachAnchorRegistry()) {
    var specs: List<CoachMarkSpec> by mutableStateOf(emptyList())

    private var dismissedIds: Set<CoachAnchorId> by mutableStateOf(emptySet())

    /**
     * The coachmark that should be showing right now, or `null`. Walks [specs] in queue order and
     * returns the first one that is both not dismissed and [CoachAnchorRegistry.isLive] — critically,
     * an earlier, not-yet-reachable spec is *skipped*, not treated as blocking: it stays in [specs],
     * un-dismissed, so it becomes eligible again the moment its anchor reports live. That's what stops a
     * coach whose target is on another tab or under a sheet from starving the rest of the queue forever.
     */
    val active: CoachMarkSpec?
        get() = specs.firstOrNull { it.id !in dismissedIds && registry.isLive(it.id) }

    /** Explicit user dismissal ("Got it") — clears [id] for the rest of this session. */
    fun dismiss(id: CoachAnchorId) {
        dismissedIds = dismissedIds + id
    }

    /** Called when [id]'s target itself was used; only actually clears it if it opted into [ClearOn.TARGET_ACTION]. */
    fun clearByAction(id: CoachAnchorId) {
        if (specs.firstOrNull { it.id == id }?.clearOn == ClearOn.TARGET_ACTION) {
            dismissedIds = dismissedIds + id
        }
    }

    fun isDismissed(id: CoachAnchorId): Boolean = id in dismissedIds

    /** Clears every dismissal — mainly for tests / a future "replay onboarding" affordance. */
    fun resetDismissed() {
        dismissedIds = emptySet()
    }
}

/**
 * The concrete beginner-path queue: one coachmark per not-yet-completed rung of the onboarding ladder
 * that this build knows how to point at (see [CoachAnchorId] — today that's "feel one" on the Library
 * tab, and "change one" / "ship one" on the Editor route). A pure function of [progress] so it's trivial
 * to unit test and re-derive on every recomposition without any effects.
 */
fun onboardingCoachMarkSpecs(progress: OnboardingProgress): List<CoachMarkSpec> = buildList {
    if (!progress.hasFeltOne) {
        add(
            CoachMarkSpec(
                id = CoachAnchorId.LIBRARY_TAB,
                title = "Feel one",
                body = "Tap Library, then tap any saved pattern — it plays right away.",
            ),
        )
    }
    if (!progress.hasChangedOne) {
        add(
            CoachMarkSpec(
                id = CoachAnchorId.EDITOR_INSPECTOR,
                title = "Change one",
                body = "Select an event in the timeline, then drag a slider here — softer, sharper, longer.",
            ),
        )
    }
    if (!progress.hasShippedOne) {
        add(
            CoachMarkSpec(
                id = CoachAnchorId.EDITOR_SHIP,
                title = "Ship one",
                body = "Open Ship to see the export code for what you built.",
                clearOn = ClearOn.TARGET_ACTION,
            ),
        )
    }
}

/**
 * Renders [engine]'s active coachmark (if any) as a small non-blocking callout near its target's last
 * reported bounds — unlike [dev.hnm.workbench.ui.nav.SheetHost]'s modal sheets, this never scrims or
 * blocks the rest of the screen, so the target it's pointing at stays fully tappable underneath it.
 */
@Composable
fun CoachMarkOverlay(engine: CoachMarkEngine, modifier: Modifier = Modifier) {
    val spec = engine.active ?: return
    val bounds = engine.registry.boundsOf(spec.id)
    val density = LocalDensity.current

    Box(modifier.fillMaxSize()) {
        if (bounds != null) {
            val (x, y) = with(density) {
                bounds.left.toDp().coerceAtLeast(8.dp) to (bounds.bottom + 8f).toDp()
            }
            CoachMarkBubble(
                spec = spec,
                onDismiss = { engine.dismiss(spec.id) },
                modifier = Modifier.align(Alignment.TopStart).offset(x = x, y = y),
            )
        } else {
            CoachMarkBubble(
                spec = spec,
                onDismiss = { engine.dismiss(spec.id) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
            )
        }
    }
}

@Composable
private fun CoachMarkBubble(spec: CoachMarkSpec, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(WorkbenchColors.Screen)
            .border(1.dp, WorkbenchColors.Red, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(spec.title, color = WorkbenchColors.Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(spec.body, color = WorkbenchColors.Ink, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Got it ✕",
            color = WorkbenchColors.Red,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss),
        )
    }
}

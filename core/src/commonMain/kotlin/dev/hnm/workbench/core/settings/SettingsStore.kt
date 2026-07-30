package dev.hnm.workbench.core.settings

/**
 * App-level configuration and onboarding progress — distinct from *pattern* data
 * ([dev.hnm.workbench.core.library.PatternLibrary] / [dev.hnm.workbench.core.library.LibraryRepository]).
 * This is where "does this user want the cloud AI fallback" and "how far along the beginner ladder is
 * this person" live.
 *
 * [cloudAssistantEnabled] is the flag [dev.hnm.workbench.core.design.HybridPatternGenerator]'s `cloud`
 * path is gated behind, via [dev.hnm.workbench.core.design.OptInPatternGenerator] — the seam
 * [dev.hnm.workbench.ui.model.EditorState] actually wires the assistant through. It defaults to `false`
 * and is opt-in only: nothing in `core` ever flips it on by itself, no [SettingsStore] implementation
 * should default it to `true`, and the Settings sheet's toggle
 * ([dev.hnm.workbench.ui.components.SettingsPanel]) is the only UI in the app that can turn it on.
 */
interface SettingsStore {

    /** A read-only snapshot of everything this store holds, for bulk display/comparison. */
    fun snapshot(): WorkbenchSettings

    /** Replace the whole settings snapshot in one call — e.g. after loading from disk in a later stage. */
    fun restore(settings: WorkbenchSettings)

    // --- cloud assistant opt-in --------------------------------------------------

    /** Opt-in only. Defaults to `false`; never flipped on by anything in `core` itself. */
    val cloudAssistantEnabled: Boolean

    fun setCloudAssistantEnabled(enabled: Boolean)

    // --- onboarding / beginner ladder --------------------------------------------

    val onboarding: OnboardingProgress

    fun markFeltOne()
    fun markMadeOne()
    fun markChangedOne()
    fun markShippedOne()
    fun resetOnboarding()
}

/** Everything a [SettingsStore] holds, as one immutable value — the shape [SettingsStore.snapshot]/[SettingsStore.restore] move. */
data class WorkbenchSettings(
    val cloudAssistantEnabled: Boolean = false,
    val onboarding: OnboardingProgress = OnboardingProgress(),
)

/**
 * The beginner ladder tracked by the first-run walkthrough
 * (`dev.hnm.workbench.ui.components.WalkthroughCard`'s "Describe it / Feel it / Refine it / Export"
 * steps): has this person *felt* a pattern played back, *made* one from a prompt, *changed* one with an
 * edit, and *shipped* one via export? Each flag is independently settable — a user can jump straight to
 * "Export" from the walkthrough and only ever set [hasShippedOne] — so [tier] reports the furthest rung
 * reached regardless of the order the flags were set in, for a later onboarding-ladder UI to render as a
 * single progress indicator.
 */
data class OnboardingProgress(
    val hasFeltOne: Boolean = false,
    val hasMadeOne: Boolean = false,
    val hasChangedOne: Boolean = false,
    val hasShippedOne: Boolean = false,
) {
    val tier: OnboardingTier
        get() = when {
            hasShippedOne -> OnboardingTier.SHIPPED_ONE
            hasChangedOne -> OnboardingTier.CHANGED_ONE
            hasMadeOne -> OnboardingTier.MADE_ONE
            hasFeltOne -> OnboardingTier.FELT_ONE
            else -> OnboardingTier.NEW
        }
}

/** Ordinal order doubles as ladder rank: later entries are strictly further along than earlier ones. */
enum class OnboardingTier { NEW, FELT_ONE, MADE_ONE, CHANGED_ONE, SHIPPED_ONE }

/**
 * In-memory default implementation. No persistence yet — a disk/DataStore-backed [SettingsStore] is a
 * drop-in later stage; nothing above this line assumes in-memory storage.
 */
class InMemorySettingsStore(initial: WorkbenchSettings = WorkbenchSettings()) : SettingsStore {

    private var state: WorkbenchSettings = initial

    override fun snapshot(): WorkbenchSettings = state

    override fun restore(settings: WorkbenchSettings) {
        state = settings
    }

    override val cloudAssistantEnabled: Boolean get() = state.cloudAssistantEnabled

    override fun setCloudAssistantEnabled(enabled: Boolean) {
        state = state.copy(cloudAssistantEnabled = enabled)
    }

    override val onboarding: OnboardingProgress get() = state.onboarding

    override fun markFeltOne() = updateOnboarding { it.copy(hasFeltOne = true) }
    override fun markMadeOne() = updateOnboarding { it.copy(hasMadeOne = true) }
    override fun markChangedOne() = updateOnboarding { it.copy(hasChangedOne = true) }
    override fun markShippedOne() = updateOnboarding { it.copy(hasShippedOne = true) }

    override fun resetOnboarding() {
        state = state.copy(onboarding = OnboardingProgress())
    }

    private inline fun updateOnboarding(transform: (OnboardingProgress) -> OnboardingProgress) {
        state = state.copy(onboarding = transform(state.onboarding))
    }
}

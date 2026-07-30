package dev.hnm.workbench.core.library

import dev.hnm.workbench.core.ir.HapticAudioPattern

/**
 * Role-appropriate starting patterns for a Clackpad hand-off session
 * (`docs/CLACKPAD_CONTRACT.md` §1: "Pre-seeds the editor with a role-appropriate starting pattern...
 * instead of the default `BuiltInPatterns.CONFIRM`"). The mapping is a plain editorial judgment call
 * about how each Clackpad feedback role should *feel* as a first draft, not a technical requirement —
 * every value is an existing [BuiltInPatterns] entry, so this file adds no new pattern content.
 */
object RoleSeedPatterns {

    /**
     * @param role one of Clackpad's five feedback roles (`key`/`del`/`action`/`suggest`/`swipe`). An
     *   unrecognized value never throws — it falls back to [BuiltInPatterns.CONFIRM], the same default
     *   the editor opens with outside of any hand-off.
     */
    fun seedFor(role: String): HapticAudioPattern = when (role) {
        "key" -> BuiltInPatterns.TAP           // ordinary keypress: light, unobtrusive
        "del" -> BuiltInPatterns.SNAP          // delete: a firmer, more decisive spring-back
        "action" -> BuiltInPatterns.CONFIRM    // return/send/commit: the canonical "confirmed"
        "suggest" -> BuiltInPatterns.SELECTION // autocomplete/suggestion accept: a soft, gentle tick
        "swipe" -> BuiltInPatterns.SWIPE       // swipe-typing: directional buzz
        else -> BuiltInPatterns.CONFIRM
    }
}

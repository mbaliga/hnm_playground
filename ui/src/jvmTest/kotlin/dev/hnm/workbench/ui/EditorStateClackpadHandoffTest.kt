package dev.hnm.workbench.ui

import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.library.RoleSeedPatterns
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.model.ExportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [EditorState.beginHandoff] / [EditorState.exportClackpadJson] are the Android-independent half of the
 * Clackpad hand-off (`docs/CLACKPAD_CONTRACT.md` §1–§2) — [dev.hnm.workbench.android.WorkbenchActivity]
 * (untestable here, no Android SDK) is a thin wrapper over exactly this.
 */
class EditorStateClackpadHandoffTest {

    @Test
    fun freshEditorStateHasNoHandoffRole() {
        assertNull(EditorState().handoffRole)
    }

    @Test
    fun beginHandoffSeedsTheRoleAppropriatePatternAndMarksTheRole() {
        val state = EditorState()

        state.beginHandoff("del")

        assertEquals("del", state.handoffRole)
        assertEquals(RoleSeedPatterns.seedFor("del").name, state.pattern.name)
        assertEquals(BuiltInPatterns.SNAP.name, state.pattern.name)
    }

    @Test
    fun beginHandoffSwitchesExportKindToClackpad() {
        val state = EditorState()
        assertTrue(state.exportKind != ExportKind.CLACKPAD)

        state.beginHandoff("action")

        assertEquals(ExportKind.CLACKPAD, state.exportKind)
    }

    @Test
    fun exportClackpadJsonRoundTripsRoleAndSourceProvenance() {
        val state = EditorState()
        state.beginHandoff("swipe")

        val json = state.exportClackpadJson("swipe", sourceVersion = "0.14.0", sourceBuild = "15")

        assertTrue(json.contains("\"role\": \"swipe\"") || json.contains("\"role\":\"swipe\""))
        assertTrue(json.contains("0.14.0"))
        assertTrue(json.contains("\"clackpad-haptic\""))
    }

    @Test
    fun exportTextInClackpadModeProducesTheSameShapeAsExportClackpadJson() {
        val state = EditorState()
        state.beginHandoff("key")

        // exportText() (the in-editor preview) must stay in the clackpad-haptic shape once a hand-off is
        // active — it just can't know the real host app version yet, so only the format is asserted here.
        assertTrue(state.exportText().contains("\"format\": \"clackpad-haptic\"") ||
            state.exportText().contains("\"format\":\"clackpad-haptic\""))
    }
}

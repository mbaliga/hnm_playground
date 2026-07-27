package dev.hnm.workbench.core

import dev.hnm.workbench.core.export.ClackpadExporter
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.library.RoleSeedPatterns
import kotlin.test.Test
import kotlin.test.assertEquals

class RoleSeedPatternsTest {

    @Test
    fun seedsEveryClackpadRoleWithAnExistingBuiltIn() {
        assertEquals(BuiltInPatterns.TAP, RoleSeedPatterns.seedFor("key"))
        assertEquals(BuiltInPatterns.SNAP, RoleSeedPatterns.seedFor("del"))
        assertEquals(BuiltInPatterns.CONFIRM, RoleSeedPatterns.seedFor("action"))
        assertEquals(BuiltInPatterns.SELECTION, RoleSeedPatterns.seedFor("suggest"))
        assertEquals(BuiltInPatterns.SWIPE, RoleSeedPatterns.seedFor("swipe"))
    }

    @Test
    fun coversEveryRoleClackpadExporterActuallyAccepts() {
        // RoleSeedPatterns and ClackpadExporter must agree on the vocabulary, or a hand-off could seed a
        // pattern for a role the exporter would then reject.
        for (role in ClackpadExporter.VALID_ROLES) {
            RoleSeedPatterns.seedFor(role) // must not throw
        }
    }

    @Test
    fun fallsBackToConfirmForAnUnrecognizedRole() {
        assertEquals(BuiltInPatterns.CONFIRM, RoleSeedPatterns.seedFor("return"))
        assertEquals(BuiltInPatterns.CONFIRM, RoleSeedPatterns.seedFor(""))
    }
}

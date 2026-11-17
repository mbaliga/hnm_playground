package dev.hnm.workbench.ui

import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.ui.components.hostCapabilitiesText
import dev.hnm.workbench.ui.model.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostCapabilitiesTest {
    @Test
    fun defaultsSayNothing() {
        val state = EditorState()
        assertNull(state.hostCapabilities)
        assertFalse(state.hasIo)
    }

    @Test
    fun noneHostIsHonestAboutAudioOnly() {
        assertEquals("This host: audio only, no haptic actuator here.", hostCapabilitiesText(HapticCapabilities.NONE))
    }

    @Test
    fun actuatorHostNamesTheActuator() {
        assertTrue(hostCapabilitiesText(HapticCapabilities.LRA_FULL).contains("LRA"))
    }

    @Test
    fun ioControlsRoundTripThroughTheSeam() {
        val saved = mutableListOf<Pair<String, String>>()
        val copied = mutableListOf<String>()
        val state = EditorState()
        state.io = object : PlatformIo {
            override fun openText(onResult: (String?) -> Unit) = onResult(null)
            override fun saveText(suggestedName: String, text: String) { saved += suggestedName to text }
            override fun copyText(text: String) { copied += text }
        }
        assertTrue(state.hasIo)
        state.saveToHost()
        state.copyToHost()
        assertEquals(1, saved.size)
        assertTrue(saved[0].first.endsWith(".${state.exportKind.extension}"))
        assertEquals(state.exportText(), saved[0].second)
        assertEquals(listOf(state.exportText()), copied)
        state.openFromHost()
        assertEquals("Nothing opened.", state.importMessage)
    }

    @Test
    fun noneIoDoesNothing() {
        val state = EditorState()
        state.saveToHost()
        state.copyToHost()
        assertFalse(state.hasIo)
    }
}

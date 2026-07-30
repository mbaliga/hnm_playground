package dev.hnm.workbench.ui

import dev.hnm.workbench.core.design.GenerationResult
import dev.hnm.workbench.core.design.OptInPatternGenerator
import dev.hnm.workbench.core.design.PatternGenerator
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.ui.model.EditorState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end coverage (independent of Compose rendering) for the product's hard constraint: the cloud
 * assistant path defaults OFF and only ever runs when [EditorState.setCloudAssistantEnabled] (the
 * Settings sheet's toggle) has explicitly turned it on — see `SettingsPanel.kt` and
 * `core/design/PatternGenerator.kt`'s `OptInPatternGenerator`.
 */
class CloudAssistantSettingsTest {

    private fun spyCloud(invoked: () -> Unit): PatternGenerator = object : PatternGenerator {
        override suspend fun generate(prompt: String, current: HapticAudioPattern?): GenerationResult {
            invoked()
            return GenerationResult(BuiltInPatterns.TAP, "from cloud", "cloud")
        }
    }

    @Test
    fun freshEditorStateHasCloudAssistantOff() {
        val state = EditorState()
        assertFalse(state.cloudAssistantEnabled)
        assertFalse(state.settings.cloudAssistantEnabled)
    }

    @Test
    fun togglingCloudAssistantOnIsInstantAndMirroredOnSettings() {
        val state = EditorState()

        state.setCloudAssistantEnabled(true)

        assertTrue(state.cloudAssistantEnabled, "the Compose-observed mirror must flip immediately")
        assertTrue(state.settings.cloudAssistantEnabled, "the underlying SettingsStore must be the source of truth")
    }

    @Test
    fun togglingCloudAssistantOffAgainIsInstant() {
        val state = EditorState()
        state.setCloudAssistantEnabled(true)

        state.setCloudAssistantEnabled(false)

        assertFalse(state.cloudAssistantEnabled)
        assertFalse(state.settings.cloudAssistantEnabled)
    }

    @Test
    fun generateNeverInvokesAWiredCloudGeneratorWhileTheSettingIsOff() = kotlinx.coroutines.test.runTest {
        var cloudCalled = false
        val state = EditorState()
        state.generator = OptInPatternGenerator(settings = state.settings, cloud = spyCloud { cloudCalled = true })

        state.generate("urgent alert")

        assertFalse(cloudCalled, "cloud must never be invoked — full stop — while cloudAssistantEnabled is false")
        assertTrue(state.onboarding.hasMadeOne, "on-device generation must still have succeeded")
    }

    @Test
    fun generateUsesTheWiredCloudGeneratorOnceOptedIn() = kotlinx.coroutines.test.runTest {
        var cloudCalled = false
        val state = EditorState()
        state.generator = OptInPatternGenerator(settings = state.settings, cloud = spyCloud { cloudCalled = true })
        state.setCloudAssistantEnabled(true)

        state.generate("urgent alert")

        assertTrue(cloudCalled, "cloud must be invoked once explicitly opted in via the Settings toggle")
    }

    @Test
    fun defaultGeneratorStaysOnDeviceEvenWhenOptedInBecauseNoCloudIsWired() = kotlinx.coroutines.test.runTest {
        // The default EditorState() generator has no cloud backend wired at all — opting in must not
        // crash or otherwise misbehave; it simply has nothing to opt into.
        val state = EditorState()
        state.setCloudAssistantEnabled(true)

        state.generate("urgent alert")

        assertTrue(state.onboarding.hasMadeOne)
    }
}

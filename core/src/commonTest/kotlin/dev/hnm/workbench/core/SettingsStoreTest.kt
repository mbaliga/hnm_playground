package dev.hnm.workbench.core

import dev.hnm.workbench.core.settings.InMemorySettingsStore
import dev.hnm.workbench.core.settings.OnboardingProgress
import dev.hnm.workbench.core.settings.OnboardingTier
import dev.hnm.workbench.core.settings.WorkbenchSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStoreTest {

    @Test
    fun cloudAssistantDefaultsToFalse() {
        val store = InMemorySettingsStore()
        assertFalse(store.cloudAssistantEnabled, "the cloud AI fallback must stay opt-in only")
        assertFalse(store.snapshot().cloudAssistantEnabled)
        assertFalse(WorkbenchSettings().cloudAssistantEnabled)
    }

    @Test
    fun cloudAssistantIsExplicitlySettable() {
        val store = InMemorySettingsStore()
        store.setCloudAssistantEnabled(true)
        assertTrue(store.cloudAssistantEnabled)
        assertTrue(store.snapshot().cloudAssistantEnabled)

        store.setCloudAssistantEnabled(false)
        assertFalse(store.cloudAssistantEnabled)
    }

    @Test
    fun onboardingDefaultsToNewTier() {
        val store = InMemorySettingsStore()
        assertEquals(OnboardingTier.NEW, store.onboarding.tier)
        assertFalse(store.onboarding.hasFeltOne)
        assertFalse(store.onboarding.hasMadeOne)
        assertFalse(store.onboarding.hasChangedOne)
        assertFalse(store.onboarding.hasShippedOne)
    }

    @Test
    fun markingStepsAdvancesTheLadder() {
        val store = InMemorySettingsStore()

        store.markFeltOne()
        assertEquals(OnboardingTier.FELT_ONE, store.onboarding.tier)

        store.markMadeOne()
        assertEquals(OnboardingTier.MADE_ONE, store.onboarding.tier)

        store.markChangedOne()
        assertEquals(OnboardingTier.CHANGED_ONE, store.onboarding.tier)

        store.markShippedOne()
        assertEquals(OnboardingTier.SHIPPED_ONE, store.onboarding.tier)
        assertTrue(store.onboarding.hasFeltOne)
        assertTrue(store.onboarding.hasMadeOne)
        assertTrue(store.onboarding.hasChangedOne)
        assertTrue(store.onboarding.hasShippedOne)
    }

    @Test
    fun tierReflectsFurthestRungRegardlessOfOrder() {
        // A user can jump straight to "Export" from the walkthrough without feeling/making/changing
        // one first — tier must report the furthest rung reached, not the order flags were set in.
        val store = InMemorySettingsStore()
        store.markShippedOne()
        assertEquals(OnboardingTier.SHIPPED_ONE, store.onboarding.tier)
        assertFalse(store.onboarding.hasFeltOne)
        assertFalse(store.onboarding.hasMadeOne)
        assertFalse(store.onboarding.hasChangedOne)
    }

    @Test
    fun markingAStepTwiceStaysIdempotent() {
        val store = InMemorySettingsStore()
        store.markFeltOne()
        store.markFeltOne()
        assertEquals(OnboardingTier.FELT_ONE, store.onboarding.tier)
    }

    @Test
    fun resetOnboardingClearsAllFlags() {
        val store = InMemorySettingsStore()
        store.markFeltOne()
        store.markShippedOne()
        store.resetOnboarding()
        assertEquals(OnboardingTier.NEW, store.onboarding.tier)
        assertEquals(OnboardingProgress(), store.onboarding)
    }

    @Test
    fun resetOnboardingLeavesCloudAssistantFlagAlone() {
        val store = InMemorySettingsStore()
        store.setCloudAssistantEnabled(true)
        store.markFeltOne()
        store.resetOnboarding()
        assertTrue(store.cloudAssistantEnabled, "resetting onboarding must not touch unrelated settings")
    }

    @Test
    fun snapshotAndRestoreRoundTrip() {
        val store = InMemorySettingsStore()
        store.setCloudAssistantEnabled(true)
        store.markFeltOne()
        store.markMadeOne()
        val snap = store.snapshot()

        val fresh = InMemorySettingsStore()
        fresh.restore(snap)
        assertEquals(snap, fresh.snapshot())
        assertEquals(OnboardingTier.MADE_ONE, fresh.onboarding.tier)
        assertTrue(fresh.cloudAssistantEnabled)
    }

    @Test
    fun initialSettingsCanBeSuppliedAtConstruction() {
        val store = InMemorySettingsStore(
            initial = WorkbenchSettings(cloudAssistantEnabled = true, onboarding = OnboardingProgress(hasFeltOne = true)),
        )
        assertTrue(store.cloudAssistantEnabled)
        assertEquals(OnboardingTier.FELT_ONE, store.onboarding.tier)
    }
}

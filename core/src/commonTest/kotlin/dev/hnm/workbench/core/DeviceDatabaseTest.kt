package dev.hnm.workbench.core

import dev.hnm.workbench.core.device.DeviceDatabase
import dev.hnm.workbench.core.device.DeviceProfile
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.ActuatorType
import dev.hnm.workbench.core.playback.PlayOneShot
import dev.hnm.workbench.core.playback.PlayPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceDatabaseTest {

    private val renderer = DefaultPatternRenderer()

    @Test
    fun seedHasReferenceTiers() {
        val db = DeviceDatabase.seeded()
        assertTrue(db.size >= 6)
        assertNotNull(db.get("google-pixel-8"))
        assertNotNull(db.get("android-budget-erm"))
        assertNotNull(db.get("apple-iphone-15"))
    }

    @Test
    fun pixelProfileMapsToPrimitiveCapableCaps() {
        val pixel = DeviceDatabase.seeded().get("google-pixel-8")!!
        val caps = pixel.toCapabilities()
        assertEquals(ActuatorType.LRA, caps.actuatorType)
        assertTrue(caps.hasAmplitudeControl)
        assertTrue(PrimitiveType.TICK in caps.supportedPrimitives)
        // A real device profile drives the existing scheduler: CONFIRM -> primitives.
        val cmds = renderer.scheduleHaptics(BuiltInPatterns.CONFIRM, caps)
        assertTrue(cmds.all { it is PlayPrimitive })
    }

    @Test
    fun ermProfileForcesOnOffDegrade() {
        val erm = DeviceDatabase.seeded().get("android-budget-erm")!!
        val caps = erm.toCapabilities()
        assertEquals(ActuatorType.ERM, caps.actuatorType)
        val cmds = renderer.scheduleHaptics(BuiltInPatterns.CONFIRM, caps)
        assertTrue(cmds.all { it is PlayOneShot })
    }

    @Test
    fun roundTripsThroughJson() {
        val db = DeviceDatabase.seeded()
        val restored = DeviceDatabase.fromJson(db.toJson())
        assertEquals(db.size, restored.size)
        val a = db.get("samsung-galaxy-s23")!!
        val b = restored.get("samsung-galaxy-s23")!!
        assertEquals(a, b)
    }

    @Test
    fun upsertAddsProbedDevice() {
        val db = DeviceDatabase.seeded()
        val before = db.size
        db.upsert(
            DeviceProfile(
                id = "my-phone",
                manufacturer = "Test",
                model = "T1",
                platform = DeviceProfile.Platform.ANDROID,
                actuatorType = ActuatorType.LRA,
                hasAmplitudeControl = true,
                source = DeviceProfile.Source.PROBED,
            ),
        )
        assertEquals(before + 1, db.size)
        assertEquals(DeviceProfile.Source.PROBED, db.get("my-phone")!!.source)
    }
}

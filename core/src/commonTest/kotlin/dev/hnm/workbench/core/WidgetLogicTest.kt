package dev.hnm.workbench.core

import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.library.FeelKind
import dev.hnm.workbench.core.library.FeelTestCatalog
import dev.hnm.workbench.core.playback.ActuatorType
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.widget.EffectPads
import dev.hnm.workbench.core.widget.HapticTier
import dev.hnm.workbench.core.widget.LibraryGlances
import dev.hnm.workbench.core.widget.PatternOfTheDay
import dev.hnm.workbench.core.widget.PatternSketch
import dev.hnm.workbench.core.widget.PhoneGlances
import dev.hnm.workbench.core.widget.RecentFeel
import dev.hnm.workbench.core.widget.RecentlyFelt
import dev.hnm.workbench.core.widget.RenderPath
import dev.hnm.workbench.core.widget.RenderPaths
import dev.hnm.workbench.core.widget.SizeBucket
import dev.hnm.workbench.core.widget.WidgetTemplate
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetLogicTest {

    // --- sizes ---------------------------------------------------------------

    @Test
    fun bucketIsTheLargestLayoutThatFits() {
        val card = WidgetTemplate.CARD
        assertEquals(SizeBucket.COLLAPSED, card.bucketFor(100f, 40f))
        assertEquals(SizeBucket.SMALL, card.bucketFor(110f, 40f))
        assertEquals(SizeBucket.SMALL, card.bucketFor(240f, 300f), "too narrow for medium stays small")
        assertEquals(SizeBucket.MEDIUM, card.bucketFor(250f, 110f))
        assertEquals(SizeBucket.MEDIUM, card.bucketFor(400f, 179f))
        assertEquals(SizeBucket.LARGE, card.bucketFor(250f, 180f))
    }

    @Test
    fun listAndPadsCollapseBelowTwoByTwo() {
        // A 4×1 strip is wide but too short for a row of 48 dp touch targets under a header.
        assertEquals(SizeBucket.COLLAPSED, WidgetTemplate.LIST.bucketFor(320f, 60f))
        assertEquals(SizeBucket.COLLAPSED, WidgetTemplate.PADS.bucketFor(320f, 60f))
        assertEquals(SizeBucket.SMALL, WidgetTemplate.LIST.bucketFor(110f, 110f))
        assertEquals(SizeBucket.MEDIUM, WidgetTemplate.LIST.bucketFor(180f, 180f))
        assertEquals(SizeBucket.LARGE, WidgetTemplate.LIST.bucketFor(180f, 276f))
    }

    @Test
    fun layoutsGrowMonotonically() {
        for (t in WidgetTemplate.entries) {
            val sizes = SizeBucket.entries.map { t.sizeOf(it) }
            sizes.zipWithNext().forEach { (a, b) ->
                assertTrue(a.width <= b.width && a.height <= b.height, "$t: $a should fit inside $b")
            }
            // Each layout's own size picks that layout.
            for (bucket in SizeBucket.entries.drop(1)) {
                val s = t.sizeOf(bucket)
                assertEquals(bucket, t.bucketFor(s.width.toFloat(), s.height.toFloat()), "$t $bucket")
            }
        }
        assertEquals(listOf(0, 1, 3, 5), SizeBucket.entries.map { WidgetTemplate.listRows(it) })
    }

    @Test
    fun standardCellSizesLandOnTheIntendedLayouts() {
        fun cells(n: Int) = 70f * n - 30f
        assertEquals(SizeBucket.SMALL, WidgetTemplate.PADS.bucketFor(cells(2), cells(2)))
        assertEquals(SizeBucket.MEDIUM, WidgetTemplate.PADS.bucketFor(cells(4), cells(2)))
        assertEquals(SizeBucket.LARGE, WidgetTemplate.PADS.bucketFor(cells(4), cells(3)))
        assertEquals(SizeBucket.MEDIUM, WidgetTemplate.LIST.bucketFor(cells(3), cells(3)))
        assertEquals(SizeBucket.SMALL, WidgetTemplate.CARD.bucketFor(cells(2), cells(1)))
        assertEquals(SizeBucket.MEDIUM, WidgetTemplate.CARD.bucketFor(cells(4), cells(2)))
        assertEquals(SizeBucket.LARGE, WidgetTemplate.CARD.bucketFor(cells(4), cells(3)))
        assertEquals(SizeBucket.MEDIUM, WidgetTemplate.STAT.bucketFor(cells(3), cells(2)))
        assertEquals(SizeBucket.LARGE, WidgetTemplate.IMAGE.bucketFor(cells(4), cells(3)))
    }

    @Test
    fun listAndPadLayoutsHoldFortyEightDpTargets() {
        // Lists: 8 dp padding top and bottom, an 18 dp header and a 2 dp gap, then 48 dp rows.
        for (bucket in listOf(SizeBucket.SMALL, SizeBucket.MEDIUM, SizeBucket.LARGE)) {
            val needed = 8 + 8 + 18 + 2 + 48 * WidgetTemplate.listRows(bucket)
            assertTrue(WidgetTemplate.LIST.sizeOf(bucket).height >= needed, "list $bucket needs $needed dp")
        }
        // Pads: 5 dp padding top and bottom, 4 dp between rows, an 18 dp header on the large layout.
        assertTrue(WidgetTemplate.PADS.small.height >= 5 + 5 + 4 + 2 * 48)
        assertTrue(WidgetTemplate.PADS.medium.height >= 5 + 5 + 4 + 2 * 48)
        assertTrue(WidgetTemplate.PADS.large.height >= 5 + 5 + 18 + 2 * 4 + 3 * 48)
    }

    // --- effect pads -----------------------------------------------------------

    @Test
    fun padsShowTwoFourOrSixBuiltIns() {
        assertEquals(listOf(0, 2, 4, 6), SizeBucket.entries.map { EffectPads.pads(it).size })
        assertEquals(listOf("tap", "heartbeat"), EffectPads.pads(SizeBucket.SMALL).map { it.id })
        assertTrue(EffectPads.pads(SizeBucket.LARGE).all { it.kind == FeelKind.REFERENCE })
    }

    @Test
    fun padsSkipIdsTheCatalogueLacks() {
        val pads = EffectPads.pads(SizeBucket.LARGE) { id -> if (id == "heartbeat") null else FeelTestCatalog.find(id) }
        assertEquals(5, pads.size)
        assertTrue(pads.none { it.id == "heartbeat" })
    }

    // --- pattern of the day ------------------------------------------------------

    @Test
    fun patternOfTheDayIsStableForADay() {
        val day = 20_396L // 2025-11-04
        assertEquals(PatternOfTheDay.pick(day), PatternOfTheDay.pick(day))
        assertNotNull(PatternOfTheDay.pick(day))
    }

    @Test
    fun patternOfTheDayCyclesThroughEveryEligibleEntryOnce() {
        val cycle = PatternOfTheDay.cycle()
        val eligible = FeelTestCatalog.entries.filter { it.kind in PatternOfTheDay.KINDS }
        assertEquals(42, eligible.size)
        val start = 20_000L
        val picks = (0 until cycle.size).map { PatternOfTheDay.pick(start + it)!!.id }
        assertEquals(eligible.map { it.id }.toSet(), picks.toSet(), "one full cycle covers every eligible entry")
        assertEquals(picks.size, picks.toSet().size, "no repeats within a cycle")
        assertEquals(PatternOfTheDay.pick(start), PatternOfTheDay.pick(start + cycle.size))
    }

    @Test
    fun patternOfTheDayHandlesDaysBeforeTheEpochAndAnEmptyCatalogue() {
        assertNotNull(PatternOfTheDay.pick(-1L))
        assertNull(PatternOfTheDay.pick(5L, emptyList()))
        val onlyVariations = FeelTestCatalog.entries.filter { it.kind == FeelKind.VARIATION }
        assertNull(PatternOfTheDay.pick(5L, onlyVariations), "variations are never the pattern of the day")
    }

    // --- recently felt -------------------------------------------------------------

    @Test
    fun recordingMovesToTheFrontDedupesAndCaps() {
        var list = emptyList<RecentFeel>()
        listOf("tap", "confirm", "snap", "heartbeat", "success", "error").forEachIndexed { i, id ->
            list = RecentlyFelt.record(list, id, nowMillis = 1_000L * (i + 1))
        }
        assertEquals(listOf("error", "success", "heartbeat", "snap", "confirm"), list.map { it.id })
        list = RecentlyFelt.record(list, "snap", nowMillis = 9_000L)
        assertEquals(listOf("snap", "error", "success", "heartbeat", "confirm"), list.map { it.id })
        assertEquals(9_000L, list.first().feltAtEpochMillis)
        assertFailsWith<IllegalArgumentException> { RecentlyFelt.record(list, "Not an id", 1L) }
    }

    @Test
    fun storeTextRoundTrips() {
        val list = listOf(RecentFeel("tap", 1_700_000_000_000L), RecentFeel("material-wood", 5L))
        assertEquals(list, RecentlyFelt.decode(RecentlyFelt.encode(list)))
        assertEquals(emptyList(), RecentlyFelt.decode(""))
    }

    @Test
    fun decodeSkipsAnythingItDoesNotRecognise() {
        val text = listOf(
            "tap\t100",
            "../etc/passwd\t5",
            "Confirm\t6",
            "snap\t-1",
            "heartbeat",
            "success\tnot-a-number",
            "tap\t200",
            "material-wood\t300\textra",
            "  confirm\t400  ",
        ).joinToString("\n")
        assertEquals(listOf(RecentFeel("tap", 100), RecentFeel("confirm", 400)), RecentlyFelt.decode(text))
    }

    @Test
    fun resolveDropsIdsThisVersionDoesNotKnow() {
        val list = listOf(RecentFeel("removed-in-a-later-version", 1L), RecentFeel("tap", 2L))
        val resolved = RecentlyFelt.resolve(list, FeelTestCatalog::find)
        assertEquals(listOf("tap"), resolved.map { it.second.id })
    }

    // --- sketch ----------------------------------------------------------------------

    @Test
    fun tapSketchIsOneCrispImpulseAtTheStart() {
        val sketch = PatternSketch.of(BuiltInPatterns.TAP, columns = 8)
        assertEquals(1, sketch.eventCount)
        assertEquals(0.75, sketch.peakIntensity, 1e-9)
        assertEquals(0.85, sketch.sharpnessMin!!, 1e-9)
        assertEquals(0.75, sketch.intensity.first(), 1e-9)
        assertEquals(0.85, sketch.sharpness.first()!!, 1e-9)
        assertEquals(0.1, sketch.durationSeconds, 1e-9, "a lone tap is drawn in a 100 ms window")
        assertEquals("▇▇▁▁▁▁▁▁", sketch.sparkline())
    }

    @Test
    fun sketchCarriesEveryEventsSharpnessRange() {
        val sketch = PatternSketch.of(BuiltInPatterns.CONFIRM)
        assertEquals(2, sketch.eventCount)
        assertEquals(0.5, sketch.sharpnessMin!!, 1e-9)
        assertEquals(0.9, sketch.sharpnessMax!!, 1e-9)
        assertEquals(1.0, sketch.peakIntensity, 1e-9)
        // Silent columns between the two clicks carry no sharpness.
        sketch.intensity.zip(sketch.sharpness).forEach { (i, s) -> assertEquals(i <= 0.001, s == null) }
    }

    @Test
    fun heartbeatSparklineShowsTwoPulsesWithSilenceBetween() {
        val line = PatternSketch.of(BuiltInPatterns.HEARTBEAT, columns = 12).sparkline()
        assertTrue(line.first() != '▁', line)
        assertTrue('▁' in line, line)
        assertEquals(2, Regex("[▂▃▄▅▆▇█]+").findAll(line).count(), line)
    }

    @Test
    fun continuousSketchFollowsItsEnvelope() {
        val swell = HapticAudioPattern(
            name = "swell",
            tracks = listOf(
                HapticTrack(
                    id = "h",
                    events = listOf(
                        Continuous(0.0, 1.0, intensity = 1.0, sharpness = 0.3, envelope = Envelope(attack = 0.5, sustain = 1.0)),
                    ),
                ),
            ),
        )
        val sketch = PatternSketch.of(swell, columns = 10)
        assertTrue(sketch.intensity[0] < sketch.intensity[2], "attack rises")
        assertTrue(sketch.intensity[2] < sketch.intensity[5], "attack keeps rising until the sustain")
        assertEquals(0.3, sketch.sharpness[5]!!, 1e-9)
    }

    @Test
    fun sweptPrimitivesRiseAndTexturesMove() {
        val toggleOn = PatternSketch.of(BuiltInPatterns.TOGGLE_ON, columns = 24)
        assertTrue(toggleOn.intensity[1] < toggleOn.intensity[6], "the slow rise rises")
        val rough = PatternSketch.of(FeelTestCatalog.find("texture-worley-rough")!!.pattern, columns = 24)
        assertTrue(rough.intensity.distinct().size > 4, "a texture's intensity curve shows in the sketch")
    }

    @Test
    fun mutedAndEmptyPatternsAreSilent() {
        val muted = HapticAudioPattern(
            name = "muted",
            tracks = listOf(HapticTrack(id = "h", muted = true, events = listOf(Transient(0.0, 1.0, 1.0)))),
        )
        val sketch = PatternSketch.of(muted, columns = 6)
        assertTrue(sketch.isSilent)
        assertEquals("▁▁▁▁▁▁", sketch.sparkline())
        assertTrue(sketch.sharpness.all { it == null })
        assertTrue(PatternSketch.of(HapticAudioPattern(name = "empty", tracks = emptyList())).isSilent)
    }

    @Test
    fun everyCatalogueEntrySketchesWithinRange() {
        for (entry in FeelTestCatalog.entries) {
            val sketch = PatternSketch.of(entry.pattern, columns = 32)
            assertTrue(!sketch.isSilent, "${entry.id} sketches as silent")
            assertTrue(sketch.intensity.all { it in 0.0..1.0 }, entry.id)
            assertTrue(sketch.sharpness.all { it == null || it in 0.0..1.0 }, entry.id)
            assertTrue(sketch.durationSeconds > 0.0, entry.id)
        }
    }

    // --- glances ------------------------------------------------------------------------

    private val renderer = DefaultPatternRenderer()

    private fun pathOf(pattern: HapticAudioPattern, caps: HapticCapabilities) =
        RenderPaths.of(pattern, renderer.scheduleHaptics(pattern, caps))

    @Test
    fun renderPathMatchesThePlayersLadder() {
        assertEquals(RenderPath.PRIMITIVES, pathOf(BuiltInPatterns.TAP, HapticCapabilities.LRA_FULL))
        assertEquals(RenderPath.WAVEFORM, pathOf(BuiltInPatterns.SUCCESS, HapticCapabilities.LRA_FULL))
        assertEquals(RenderPath.PREDEFINED, pathOf(BuiltInPatterns.TAP, HapticCapabilities.ERM_BASIC))
        assertEquals(RenderPath.PREDEFINED, pathOf(BuiltInPatterns.SPIN_LOCK, HapticCapabilities.ERM_BASIC))
        assertEquals(RenderPath.WAVEFORM, pathOf(BuiltInPatterns.NOTIFICATION, HapticCapabilities.ERM_BASIC))
        assertEquals(RenderPath.NOTHING, pathOf(BuiltInPatterns.TAP, HapticCapabilities.NONE))
    }

    @Test
    fun libraryGlanceCountsAddUp() {
        val entries = FeelTestCatalog.entries
        val glance = LibraryGlances.of(entries, HapticCapabilities.LRA_FULL)
        assertEquals(57, glance.total)
        assertEquals(glance.total, glance.byKind.sumOf { it.second })
        assertEquals(glance.total, glance.byPath.sumOf { it.second })
        assertEquals(FeelKind.REFERENCE to 15, glance.byKind.first())
        assertTrue(glance.byPath.none { it.first == RenderPath.NOTHING })
        assertEquals(glance.byPath.map { it.first }, glance.byPath.map { it.first }.sortedBy { it.ordinal })

        val none = LibraryGlances.of(entries, HapticCapabilities.NONE)
        assertEquals(listOf(RenderPath.NOTHING to 57), none.byPath)
    }

    @Test
    fun phoneGlanceUsesThePlayersHonestTiers() {
        assertEquals(HapticTier.PRIMITIVES, PhoneGlances.tierOf(HapticCapabilities.LRA_FULL))
        assertEquals(HapticTier.ON_OFF, PhoneGlances.tierOf(HapticCapabilities.ERM_BASIC))
        assertEquals(HapticTier.NONE, PhoneGlances.tierOf(HapticCapabilities.NONE))
        val amplitudeOnly = HapticCapabilities(true, true, emptySet(), false, ActuatorType.LRA)
        assertEquals(HapticTier.AMPLITUDE, PhoneGlances.tierOf(amplitudeOnly))

        val partial = HapticCapabilities(true, true, setOf(PrimitiveType.CLICK, PrimitiveType.TICK), false, ActuatorType.LRA)
        val glance = PhoneGlances.of(partial, listOf("CLICK"))
        assertEquals(8, glance.primitivesTotal)
        assertEquals(8, glance.primitiveRows.size)
        assertEquals(2, glance.primitiveRows.count { it.second })

        val absent = PhoneGlances.of(HapticCapabilities.NONE.copy(supportedPrimitives = setOf(PrimitiveType.CLICK)), listOf("CLICK"))
        assertTrue(absent.supportedPrimitives.isEmpty() && absent.supportedEffects.isEmpty() && !absent.hasAmplitudeControl)
    }
}

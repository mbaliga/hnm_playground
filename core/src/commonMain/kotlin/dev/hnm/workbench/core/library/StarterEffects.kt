package dev.hnm.workbench.core.library

import dev.hnm.workbench.core.ir.*

/** Original, editable HAfx interpretations. References establish use cases, not vendor waveforms. */
object StarterEffects {
    const val ID = "hafx.preset.id"
    const val DESCRIPTION = "hafx.preset.description"
    const val NOTE = "hafx.reference.note"
    private const val IPHONE = "https://support.apple.com/en-gb/102438"
    private const val MAC = "https://support.apple.com/en-us/102442"
    private const val ANDROID = "https://developer.android.com/develop/ui/views/haptics/haptics-principles"

    private fun preset(id: String, name: String, description: String, events: List<HapticEvent>,
        sourceTitle: String, sourceUrl: String, quote: String = "", note: String,
        secondaryTitle: String = "", secondaryUrl: String = "", audio: List<OscEvent> = emptyList()): HapticAudioPattern = HapticAudioPattern(
        name = name,
        tracks = listOf(HapticTrack("starter", events = events)) +
            if (audio.isEmpty()) emptyList() else listOf(AudioTrack("starter-sound", name = "Paired sound", events = audio)),
        metadata = mapOf(
            ID to id, DESCRIPTION to description, NOTE to note,
            "hafx.reference.title" to sourceTitle, "hafx.reference.url" to sourceUrl,
            "hafx.reference.quote" to quote,
            "hafx.reference.secondary.title" to secondaryTitle,
            "hafx.reference.secondary.url" to secondaryUrl,
            "hafx.reference.checked" to "2026-10-18",
            "hafx.design.modalities" to if (audio.isEmpty()) "haptics" else "audio+haptics",
            "hafx.design.author" to "HAfx original synthesis and timing",
        ),
    )

    val UNLOCK = preset("unlock-confirmation", "Unlock confirmation",
        "Two taps paired with a short rising, percussive tone.",
        listOf(Primitive(0.0, PrimitiveType.LOW_TICK, .45), Primitive(.10, PrimitiveType.CLICK, .80)),
        "Apple Support · Unlock your iPhone with Apple Watch", IPHONE,
        quote = "your watch gives haptic feedback and shows an alert",
        note = "HAfx interpretation of the cross-device unlock confirmation. The source documents the interaction, not its waveform or timing. Sound, haptics and their alignment are original HAfx design, not an Apple recording. This effect does not unlock another device.",
        secondaryTitle = "Apple Support · Unlock your Mac with Apple Watch", secondaryUrl = MAC,
        audio = listOf(
            OscEvent(0.0,.020,Waveform.SINE,650.0,.070,envelope=Envelope(.001,.010,.20,.018)),
            OscEvent(.10,.028,Waveform.SINE,980.0,.140,envelope=Envelope(.0015,.012,.20,.036)),
            OscEvent(.10,.015,Waveform.SINE,1960.0,.025,envelope=Envelope(.001,.007,.10,.020)),
        ))

    val LOCK = preset("lock-latch", "Lock latch",
        "A firm close and a smaller latch, paired with a descending click.",
        listOf(Primitive(0.0, PrimitiveType.CLICK, .75), Primitive(.075, PrimitiveType.LOW_TICK, .30)),
        "Apple Support · Lock iPhone action after an unlock", IPHONE,
        quote = "tap the Lock iPhone button",
        note = "Original HAfx sound-and-haptic companion to Unlock confirmation. Apple documents a Lock iPhone action; this source does not specify a lock haptic or sound. This is a sensation preview, not a security action.",
        audio = listOf(
            OscEvent(0.0,.022,Waveform.SINE,620.0,.130,envelope=Envelope(.001,.010,.18,.020)),
            OscEvent(0.0,.012,Waveform.SINE,1240.0,.020,envelope=Envelope(.001,.006,.10,.014)),
            OscEvent(.075,.018,Waveform.SINE,390.0,.045,envelope=Envelope(.001,.008,.15,.022)),
        ))

    val DETENT = preset("soft-detent", "Soft detent",
        "A restrained tick for a single step through a list, dial or timeline.",
        listOf(Primitive(0.0, PrimitiveType.LOW_TICK, .30)),
        "Android Developers · Haptics design principles", ANDROID,
        quote = "Favor rich and clear haptics over buzzy haptics.",
        note = "Original HAfx tuning of a device-native primitive, informed by Android's guidance on subtle feedback for frequent interactions. Not a recording of a specific device.")

    val BOUNDARY = preset("boundary-bump", "Boundary bump",
        "One rounded bump to mark the end of a range.",
        listOf(Primitive(0.0, PrimitiveType.THUD, .55)),
        "Android Developers · HapticFeedbackConstants", "https://developer.android.com/reference/android/view/HapticFeedbackConstants#REJECT",
        quote = "A haptic effect to signal the rejection or failure of a user interaction.",
        note = "Original HAfx range-boundary cue. Android's GESTURE_THRESHOLD_DEACTIVATE and REJECT describe related interaction meanings; this is an editable design, not a platform constant replay.")

    val SEARCH = preset("search-match", "Search match",
        "Two quiet, spaced ticks to mark a match while scrubbing text.",
        listOf(Primitive(0.0, PrimitiveType.LOW_TICK, .42), Primitive(.16, PrimitiveType.LOW_TICK, .28)),
        "Android Developers · Haptics design principles", ANDROID,
        quote = "well crafted haptics provide valuable sensory feedback",
        note = "Original HAfx search cue inspired by event confirmation and restrained frequent feedback. Its effect on reading or search performance has not been established.")

    val COMPLETE = preset("completion-bloom", "Completion bloom",
        "A small lift that lands, then gently fades into a final tick.",
        listOf(Primitive(0.0, PrimitiveType.QUICK_RISE, .55), Primitive(.18, PrimitiveType.TICK, .70), Primitive(.28, PrimitiveType.LOW_TICK, .25)),
        "Apple Developer · WKHapticType.success", "https://developer.apple.com/documentation/watchkit/wkhaptictype/success",
        quote = "Indicates the successful completion of a task or the answering of a question.",
        note = "Original HAfx completion pattern based on the documented meaning of success. It is not Apple's success haptic or an extracted Taptic Engine waveform.")

    val ALL = listOf(UNLOCK, LOCK, DETENT, BOUNDARY, SEARCH, COMPLETE)
}

data class EffectSource(val title: String, val url: String, val quote: String = "")

data class EffectAttribution(val description: String, val note: String, val sources: List<EffectSource>) {
    companion object {
        fun from(pattern: HapticAudioPattern): EffectAttribution? {
            val m = pattern.metadata
            val description = m[StarterEffects.DESCRIPTION] ?: return null
            fun source(prefix: String): EffectSource? {
                val title = m["$prefix.title"]?.takeIf { it.isNotBlank() } ?: return null
                val url = m["$prefix.url"]?.takeIf { it.startsWith("https://") && it.none(Char::isWhitespace) } ?: return null
                return EffectSource(title, url, m["$prefix.quote"].orEmpty())
            }
            val sources = listOfNotNull(source("hafx.reference"), source("hafx.reference.secondary"))
            return EffectAttribution(description, m[StarterEffects.NOTE].orEmpty(), sources)
        }
    }
}

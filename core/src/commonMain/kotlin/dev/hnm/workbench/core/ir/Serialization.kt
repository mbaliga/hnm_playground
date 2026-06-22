package dev.hnm.workbench.core.ir

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The native save/load format. AHAP / Kotlin-code / WAV are *exports* (see the `export` package);
 * this JSON is the source of truth that round-trips losslessly.
 *
 * The sealed hierarchies (`Track`, `HapticEvent`, `AudioEvent`) are registered here so polymorphic
 * JSON uses the `"type"` class discriminator declared via `@SerialName`. Getting this in early means
 * save/load never has to be retrofitted.
 */
@OptIn(ExperimentalSerializationApi::class)
object PatternSerialization {

    val module: SerializersModule = SerializersModule {
        polymorphic(Track::class) {
            subclass(HapticTrack::class)
            subclass(AudioTrack::class)
        }
        polymorphic(HapticEvent::class) {
            subclass(Transient::class)
            subclass(Continuous::class)
            subclass(Primitive::class)
        }
        polymorphic(AudioEvent::class) {
            subclass(OscEvent::class)
            subclass(SampleEvent::class)
        }
    }

    /** Pretty JSON for the on-disk library format. */
    val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        classDiscriminator = "type"
        encodeDefaults = false
        serializersModule = module
    }

    /** Compact JSON for embedding / wire use. */
    val compact: Json = Json {
        prettyPrint = false
        classDiscriminator = "type"
        encodeDefaults = false
        serializersModule = module
    }

    fun encode(pattern: HapticAudioPattern, pretty: Boolean = true): String =
        (if (pretty) json else compact).encodeToString(HapticAudioPattern.serializer(), pattern)

    fun decode(text: String): HapticAudioPattern =
        json.decodeFromString(HapticAudioPattern.serializer(), text)
}

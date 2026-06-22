package dev.hnm.workbench.core.export

import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Exports the haptic tracks of a pattern to Apple's AHAP JSON (§5c). The IR was modelled on Core
 * Haptics so this mapping is direct. Primitives have no AHAP equivalent, so they are decomposed into
 * their transient/continuous form on export (surfaced via [decomposedPrimitiveCount] for the UI note).
 */
object AhapExporter {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    /** Number of IR primitives that had to be decomposed (no native AHAP equivalent). */
    fun decomposedPrimitiveCount(pattern: HapticAudioPattern): Int =
        pattern.tracks.filterIsInstance<HapticTrack>().sumOf { t -> t.events.count { it is Primitive } }

    fun export(pattern: HapticAudioPattern): String {
        val patternArray = buildJsonArray {
            for (track in pattern.tracks.filterIsInstance<HapticTrack>()) {
                if (track.muted) continue
                for (event in track.events) {
                    when (event) {
                        is Transient -> add(transientEvent(event.time, event.intensity, event.sharpness))
                        is Continuous -> add(continuousEvent(event))
                        is Primitive -> decomposePrimitive(event).forEach { add(it) }
                    }
                }
                for (curve in track.curves) {
                    parameterCurve(curve)?.let { add(it) }
                }
            }
        }

        val root = buildJsonObject {
            put("Version", 1.0)
            if (pattern.metadata.isNotEmpty() || pattern.name.isNotEmpty()) {
                putJsonObject("Metadata") { put("Project", pattern.name) }
            }
            put("Pattern", patternArray)
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)
    }

    private fun transientEvent(time: Double, intensity: Double, sharpness: Double) = buildJsonObject {
        putJsonObject("Event") {
            put("Time", time)
            put("EventType", "HapticTransient")
            putJsonArray("EventParameters") {
                addParam("HapticIntensity", intensity)
                addParam("HapticSharpness", sharpness)
            }
        }
    }

    private fun continuousEvent(e: Continuous) = buildJsonObject {
        putJsonObject("Event") {
            put("Time", e.time)
            put("EventType", "HapticContinuous")
            put("EventDuration", e.duration)
            putJsonArray("EventParameters") {
                addParam("HapticIntensity", e.intensity)
                addParam("HapticSharpness", e.sharpness)
            }
        }
    }

    /** Primitives have no AHAP equivalent -> decompose into transient/continuous form. */
    private fun decomposePrimitive(p: Primitive): List<kotlinx.serialization.json.JsonObject> = when (p.type) {
        PrimitiveType.QUICK_RISE, PrimitiveType.SLOW_RISE, PrimitiveType.QUICK_FALL, PrimitiveType.SPIN -> {
            val dur = if (p.type == PrimitiveType.SLOW_RISE) 0.30 else 0.15
            val sharp = primitiveSharpness(p.type)
            listOf(
                buildJsonObject {
                    putJsonObject("Event") {
                        put("Time", p.time)
                        put("EventType", "HapticContinuous")
                        put("EventDuration", dur)
                        putJsonArray("EventParameters") {
                            addParam("HapticIntensity", p.scale)
                            addParam("HapticSharpness", sharp)
                        }
                    }
                },
            )
        }
        else -> listOf(transientEvent(p.time, p.scale, primitiveSharpness(p.type)))
    }

    private fun primitiveSharpness(type: PrimitiveType): Double = when (type) {
        PrimitiveType.TICK -> 0.95
        PrimitiveType.LOW_TICK -> 0.6
        PrimitiveType.CLICK -> 0.5
        PrimitiveType.THUD -> 0.15
        else -> 0.5
    }

    private fun parameterCurve(curve: ParameterCurve): kotlinx.serialization.json.JsonObject? {
        val controlId = when (curve.parameter) {
            CurveParam.HAPTIC_INTENSITY -> "HapticIntensityControl"
            CurveParam.HAPTIC_SHARPNESS -> "HapticSharpnessControl"
            else -> return null // audio curves have no AHAP representation
        }
        if (curve.points.isEmpty()) return null
        return buildJsonObject {
            putJsonObject("ParameterCurve") {
                put("ParameterID", controlId)
                put("Time", curve.points.first().time)
                putJsonArray("ParameterCurveControlPoints") {
                    for (pt in curve.points) {
                        addJsonObject {
                            put("Time", pt.time)
                            put("ParameterValue", pt.value)
                        }
                    }
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addParam(id: String, value: Double) {
        addJsonObject {
            put("ParameterID", id)
            put("ParameterValue", value)
        }
    }
}

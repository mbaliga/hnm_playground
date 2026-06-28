package dev.hnm.workbench.core.export

import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.ControlPoint
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Interpolation
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Imports Apple AHAP JSON into the IR — the inverse of [AhapExporter]. This is the "borrow from the
 * ecosystem" path: there is a large body of existing `.ahap` content (game assets, sample packs, web
 * designers like Captain AHAP), and AHAP's schema is publicly documented, so ingesting it gives users a
 * head start instead of authoring from scratch.
 *
 * Mapping (Core Haptics → IR), per Apple's AHAP reference:
 *  - `HapticTransient`  → [Transient] (HapticIntensity → intensity, HapticSharpness → sharpness)
 *  - `HapticContinuous` → [Continuous] (+ EventDuration)
 *  - `ParameterCurve`(HapticIntensityControl/HapticSharpnessControl) → [ParameterCurve]
 *  - Audio events (`AudioCustom`/`AudioContinuous`) are ignored — this importer covers the haptic track.
 * Unknown keys are tolerated; missing intensity/sharpness default to 1.0/0.5.
 */
object AhapImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    class AhapParseException(message: String) : IllegalArgumentException(message)

    /** Parse AHAP text into a pattern. [name] names the resulting pattern (AHAP Metadata.Project wins if present). */
    fun import(text: String, name: String = "Imported AHAP"): HapticAudioPattern {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (t: Throwable) {
            throw AhapParseException("Not valid AHAP JSON: ${t.message}")
        }

        val patternArray = root["Pattern"]?.jsonArray
            ?: throw AhapParseException("AHAP has no top-level \"Pattern\" array")

        val events = mutableListOf<HapticEvent>()
        val curves = mutableListOf<ParameterCurve>()

        for (element in patternArray) {
            val obj = element as? JsonObject ?: continue
            obj["Event"]?.jsonObject?.let { parseEvent(it)?.let(events::add) }
            obj["ParameterCurve"]?.jsonObject?.let { parseCurve(it)?.let(curves::add) }
        }

        val projectName = root["Metadata"]?.jsonObject?.get("Project")?.jsonPrimitive?.contentOrNull() ?: name

        return HapticAudioPattern(
            name = projectName,
            tracks = listOf(
                HapticTrack(
                    id = "h1",
                    events = events.sortedBy { it.time },
                    curves = curves,
                ),
            ),
            metadata = mapOf("imported" to "ahap"),
        )
    }

    /** Best-effort: returns null on malformed input instead of throwing (for batch import). */
    fun importOrNull(text: String, name: String = "Imported AHAP"): HapticAudioPattern? =
        runCatching { import(text, name) }.getOrNull()

    private fun parseEvent(event: JsonObject): HapticEvent? {
        val type = event["EventType"]?.jsonPrimitive?.contentOrNull() ?: return null
        val time = event["Time"]?.jsonPrimitive?.floatOrNull?.toDouble() ?: 0.0
        val params = paramMap(event["EventParameters"]?.jsonArray)
        val intensity = (params["HapticIntensity"] ?: 1.0).coerceIn(0.0, 1.0)
        val sharpness = (params["HapticSharpness"] ?: 0.5).coerceIn(0.0, 1.0)
        return when (type) {
            "HapticTransient" -> Transient(time = time, intensity = intensity, sharpness = sharpness)
            "HapticContinuous" -> {
                val duration = event["EventDuration"]?.jsonPrimitive?.floatOrNull?.toDouble() ?: 0.1
                Continuous(time = time, duration = duration, intensity = intensity, sharpness = sharpness)
            }
            else -> null // AudioCustom / AudioContinuous etc. — not a haptic event
        }
    }

    private fun parseCurve(curve: JsonObject): ParameterCurve? {
        val parameter = when (curve["ParameterID"]?.jsonPrimitive?.contentOrNull()) {
            "HapticIntensityControl" -> CurveParam.HAPTIC_INTENSITY
            "HapticSharpnessControl" -> CurveParam.HAPTIC_SHARPNESS
            else -> return null
        }
        val base = curve["Time"]?.jsonPrimitive?.floatOrNull?.toDouble() ?: 0.0
        val points = curve["ParameterCurveControlPoints"]?.jsonArray.orEmptyArray().mapNotNull { cp ->
            val o = cp as? JsonObject ?: return@mapNotNull null
            val t = o["Time"]?.jsonPrimitive?.floatOrNull?.toDouble() ?: return@mapNotNull null
            val v = o["ParameterValue"]?.jsonPrimitive?.floatOrNull?.toDouble() ?: return@mapNotNull null
            // AHAP control-point times are relative to the curve's Time; IR stores absolute.
            ControlPoint(time = base + t, value = v)
        }
        if (points.isEmpty()) return null
        return ParameterCurve(parameter = parameter, points = points, interpolation = Interpolation.LINEAR)
    }

    private fun paramMap(array: JsonArray?): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        array.orEmptyArray().forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val id = o["ParameterID"]?.jsonPrimitive?.contentOrNull() ?: return@forEach
            val value = o["ParameterValue"]?.jsonPrimitive?.floatOrNull?.toDouble() ?: return@forEach
            out[id] = value
        }
        return out
    }

    private fun JsonArray?.orEmptyArray(): JsonArray = this ?: JsonArray(emptyList())

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (isString) content else content.ifEmpty { null }
}

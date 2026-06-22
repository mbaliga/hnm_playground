package dev.hnm.workbench.desktop

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.export.AhapExporter
import dev.hnm.workbench.core.export.KotlinVibrationEffectExporter
import dev.hnm.workbench.core.export.WavExporter
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.PatternSerialization
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.HapticCapabilities
import java.io.File

/**
 * A tiny driver that exercises the `core` seam end-to-end without any device:
 *   - prints the native JSON, AHAP and Kotlin VibrationEffect exports for "Confirm"
 *   - writes audio + haptic-waveform WAVs to disk
 *   - optionally plays the audio if an output device exists (`--play`)
 *
 * Usage: `./gradlew :desktopApp:run` or `:desktopApp:run --args="--play"`.
 */
fun main(args: Array<String>) {
    val play = "--play" in args
    val outDir = File(args.firstOrNull { !it.startsWith("--") } ?: "build/workbench-out").apply { mkdirs() }
    val pattern: HapticAudioPattern = BuiltInPatterns.CONFIRM
    val renderer = DefaultPatternRenderer()

    println("=== Haptics + Audio Workbench (desktop driver) ===")
    println("Pattern: \"${pattern.name}\"  tracks=${pattern.tracks.size}\n")

    println("--- Native JSON (save format) ---")
    println(PatternSerialization.encode(pattern))

    println("\n--- Kotlin VibrationEffect (LRA target) ---")
    println(KotlinVibrationEffectExporter.export(pattern, HapticCapabilities.LRA_FULL))

    println("\n--- AHAP ---")
    println(AhapExporter.export(pattern))

    println("\n--- Discrete haptic schedule (LRA / ERM) ---")
    println("LRA: " + renderer.scheduleHaptics(pattern, HapticCapabilities.LRA_FULL))
    println("ERM: " + renderer.scheduleHaptics(pattern, HapticCapabilities.ERM_BASIC))

    val sr = WavExporter.DEFAULT_SAMPLE_RATE
    val audioWav = File(outDir, "confirm-audio.wav").apply { writeBytes(WavExporter.exportAudio(pattern, sr)) }
    val hapticWav = File(outDir, "confirm-haptic.wav").apply { writeBytes(WavExporter.exportHapticWaveform(pattern, sr)) }
    println("\nWrote ${audioWav.path} (${audioWav.length()} bytes) and ${hapticWav.path} (${hapticWav.length()} bytes)")

    if (play) {
        try {
            val backend = JvmAudioBackend()
            backend.start(renderer.renderAudio(pattern, sr), sr)
            Thread.sleep(500)
            backend.stop()
            println("Played audio via JvmAudioBackend.")
        } catch (t: Throwable) {
            println("No audio output available (${t.message}); WAV files were still written.")
        }
    }
}

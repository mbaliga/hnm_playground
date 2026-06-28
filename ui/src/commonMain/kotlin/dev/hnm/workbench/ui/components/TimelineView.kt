package dev.hnm.workbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.SampleEvent
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.playback.readAll
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors
import kotlin.math.abs

/**
 * Timeline rendered with the exact visual language of the HTML recorder:
 *  • Dark screen background (#0b0b0b), CRT scanline overlay
 *  • White mirrored bars (--bar: #e9e9e6) for the haptic waveform
 *  • Dotted "future" baseline after the playhead
 *  • Red playhead line + dot cap
 *  • Audio events in the lower half with a subtler blue-gray
 * Tap an event to select it.
 */
@Composable
fun TimelineView(state: EditorState, modifier: Modifier = Modifier) {
    val pattern = state.pattern
    val duration = state.durationSeconds
    val waveform = remember(pattern) {
        DefaultPatternRenderer().renderHapticWaveform(pattern, WAVE_SR).readAll()
    }

    Column(modifier) {
        Text(
            "Timeline",
            color = WorkbenchColors.InkDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        // Inner screen container — matches .screen (26px radius, #141210)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(WorkbenchColors.Screen),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pattern, duration) {
                        detectTapGestures { offset ->
                            val w = size.width.toFloat()
                            if (w <= 0f) return@detectTapGestures
                            val tappedTime = offset.x / w * duration
                            val threshold = duration * 0.08
                            if (offset.y < size.height / 2f) {
                                val nearest = state.hapticEvents.withIndex()
                                    .minByOrNull { abs(it.value.time - tappedTime) }
                                if (nearest != null && abs(nearest.value.time - tappedTime) < threshold)
                                    state.select(nearest.index)
                            } else {
                                val nearest = state.audioEvents.withIndex()
                                    .minByOrNull { abs(it.value.time - tappedTime) }
                                if (nearest != null && abs(nearest.value.time - tappedTime) < threshold)
                                    state.selectAudio(nearest.index)
                            }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height

                // ---- constants from recorder2.html ----
                val slot = 4.3f.dp.toPx()
                val barW = 2.2f.dp.toPx()
                val topPad = 6f.dp.toPx()           // TOP_PAD: bars begin here
                val maxBarH = h * 0.50f             // tallest bar = 50% of canvas
                val audioTop = h * 0.62f            // audio bars hang from lower band

                // ---- haptic waveform: top-anchored bars hanging down ----
                if (waveform.isNotEmpty()) {
                    val cols = (w / slot).toInt().coerceAtLeast(1)
                    val per = (waveform.size / cols).coerceAtLeast(1)
                    for (c in 0 until cols) {
                        var peak = 0f
                        val start = c * per
                        for (i in start until minOf(start + per, waveform.size)) {
                            val a = abs(waveform[i])
                            if (a > peak) peak = a
                        }
                        val x = c * slot + (slot - barW) / 2
                        val barH = maxOf(2f, peak * maxBarH)
                        drawRect(
                            color = WorkbenchColors.Bar.copy(alpha = 0.82f),
                            topLeft = Offset(x, topPad),
                            size = Size(barW, barH),
                        )
                    }
                }

                // Playhead at pattern end + dotted future-baseline (no live cursor yet)
                val playX = (w - 2f).coerceAtLeast(0f)
                drawDottedBaseline(playX + 2f, w - 4f, topPad)
                drawPlayhead(playX, topPad, maxBarH)

                // ---- haptic event markers (along the top-anchored band) ----
                val markerMid = topPad + maxBarH * 0.5f
                state.hapticEvents.forEachIndexed { i, event ->
                    val x = (event.time / duration * w).toFloat()
                    val selected = i == state.selectedEventIndex
                    val color = if (selected) WorkbenchColors.Ink else WorkbenchColors.Haptic
                    when (event) {
                        is Transient -> {
                            val r = (4 + event.intensity * 8).toFloat()
                            drawCircle(color, radius = r, center = Offset(x, markerMid))
                        }
                        is Primitive -> {
                            val barH = (maxBarH * 0.6f * event.scale).toFloat()
                            drawRect(color, Offset(x - 3f, markerMid - barH / 2f), Size(6f, barH))
                        }
                        is Continuous -> {
                            val wEv = (event.duration / duration * w).toFloat()
                            val barH = (maxBarH * 0.6f * event.intensity).toFloat()
                            drawRect(
                                color.copy(alpha = 0.6f),
                                Offset(x, markerMid - barH / 2f),
                                Size(wEv, barH),
                            )
                        }
                    }
                }

                // ---- audio events: hang from the lower band ----
                pattern.tracks.filterIsInstance<AudioTrack>().forEach { track ->
                    track.events.forEachIndexed { audioIdx, ev ->
                        val x = (ev.time / duration * w).toFloat()
                        val dur = when (ev) { is OscEvent -> ev.duration; is SampleEvent -> 0.05 }
                        val wEv = (dur / duration * w).toFloat().coerceAtLeast(3f)
                        val gain = if (ev is OscEvent) ev.gain else 1.0
                        val barH = ((h - audioTop) * 0.85f * gain).toFloat()
                        val audioSelected = audioIdx == state.selectedAudioEventIndex
                        drawRect(
                            if (audioSelected) WorkbenchColors.Ink.copy(alpha = 0.85f)
                            else Color(0xFF6E8AA8).copy(alpha = 0.5f),
                            Offset(x, audioTop),
                            Size(wEv, barH),
                        )
                    }
                }

                // ---- grid: faint time ticks ----
                val step = niceStep(duration)
                var t = 0.0
                while (t <= duration) {
                    val x = (t / duration * w).toFloat()
                    drawLine(
                        WorkbenchColors.Grid.copy(alpha = 0.4f),
                        Offset(x, 0f), Offset(x, h),
                        strokeWidth = 1f,
                    )
                    t += step
                }
            }

            // CRT scanline + sheen overlay on top of everything
            ScanlineOverlay()
        }

        Text(
            "haptics ▲   audio ▼     ${formatSeconds(duration)} total",
            color = WorkbenchColors.InkDim,
            fontSize = 11.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

private const val WAVE_SR = 4000

private fun niceStep(duration: Double): Double = when {
    duration <= 0.25 -> 0.05
    duration <= 1.0  -> 0.1
    duration <= 4.0  -> 0.5
    else             -> 1.0
}

private fun formatSeconds(s: Double): String {
    val ms = (s * 1000).toInt()
    return "$ms ms"
}

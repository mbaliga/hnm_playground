package dev.hnm.workbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.SampleEvent
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.playback.readAll
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Which visual language [TimelineView] renders the same amplitude-over-time data with. [WAVEFORM] is
 * the original recorder2-style mirrored-bar rendering (untouched below); [DOT_MATRIX] renders the same
 * per-column peak samples as a classic LED dot-matrix / VU-meter grid instead. Both modes consume the
 * exact same `waveform` samples — only the renderer differs.
 */
enum class TimelineRenderMode { WAVEFORM, DOT_MATRIX }

/**
 * Timeline rendered with the exact visual language of the HTML recorder:
 *  • Dark screen background (#0b0b0b), CRT scanline overlay
 *  • White mirrored bars (--bar: #e9e9e6) for the haptic waveform
 *  • Dotted "future" baseline after the playhead
 *  • Red playhead line + dot cap
 *  • Audio events in the lower half with a subtler blue-gray
 * Tap an event to select it. A small WAVE/LED toggle in the header switches to [TimelineRenderMode.DOT_MATRIX]
 * — an LED dot-matrix rendering of the same haptic amplitude data — without touching the waveform path.
 * [initialMode] seeds which renders first (defaults to the original waveform, so every existing caller
 * is pixel-for-pixel unaffected); mainly useful for tests that want to exercise the dot-matrix path
 * without simulating a tap on the toggle.
 */
@Composable
fun TimelineView(
    state: EditorState,
    modifier: Modifier = Modifier,
    initialMode: TimelineRenderMode = TimelineRenderMode.WAVEFORM,
) {
    val pattern = state.pattern
    val duration = state.durationSeconds
    val waveform = remember(pattern) {
        DefaultPatternRenderer().renderHapticWaveform(pattern, WAVE_SR).readAll()
    }
    var mode by remember { mutableStateOf(initialMode) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Timeline", color = WorkbenchColors.InkDim, fontSize = 12.sp)
            TimelineModeToggle(mode) { mode = it }
        }

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

                if (mode == TimelineRenderMode.WAVEFORM) {
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
                } else {
                    drawDotMatrixTimeline(waveform, duration)
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

/** The header's WAVE/LED mode switch — plain clickable text, matching this UI's drawn/Unicode chrome. */
@Composable
private fun TimelineModeToggle(mode: TimelineRenderMode, onModeChange: (TimelineRenderMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimelineModeLabel("WAVE", mode == TimelineRenderMode.WAVEFORM) { onModeChange(TimelineRenderMode.WAVEFORM) }
        TimelineModeLabel("LED", mode == TimelineRenderMode.DOT_MATRIX) { onModeChange(TimelineRenderMode.DOT_MATRIX) }
    }
}

@Composable
private fun TimelineModeLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) WorkbenchColors.Red else WorkbenchColors.InkDim,
        fontSize = 10.sp,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * [TimelineRenderMode.DOT_MATRIX]'s full-timeline draw: the same per-column peak-amplitude sampling the
 * waveform bars above use, rendered as an LED dot-matrix grid instead, plus the same playhead/baseline
 * and time-tick grid. Deliberately independent of the `WAVEFORM` branch above — it recomputes its own
 * layout constants rather than sharing them, so nothing here can perturb that path.
 */
private fun DrawScope.drawDotMatrixTimeline(waveform: FloatArray, duration: Double, rows: Int = 14) {
    val w = size.width
    val h = size.height
    val topPad = 6f.dp.toPx()
    val maxBarH = h * 0.50f

    drawLedDotMatrix(waveform, rows = rows, top = topPad, bandHeight = maxBarH)

    val playX = (w - 2f).coerceAtLeast(0f)
    drawDottedBaseline(playX + 2f, w - 4f, topPad)
    drawPlayhead(playX, topPad, maxBarH)

    val step = niceStep(duration)
    var t = 0.0
    while (t <= duration) {
        val x = (t / duration * w).toFloat()
        drawLine(WorkbenchColors.Grid.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        t += step
    }
}

/**
 * Draws [waveform] — the same amplitude-over-time samples the mirrored-bar waveform renderer consumes —
 * as a classic LED dot-matrix / VU-meter grid: each time column becomes a symmetric column of discrete
 * lit/unlit dots mirrored around the band's midline, up to [rows] deep on each side. Shared by the
 * full [TimelineView] LED mode and [TimelineDotMatrixThumbnail] so the two never drift apart on what
 * "the LED rendering" of a given amplitude buffer looks like.
 */
internal fun DrawScope.drawLedDotMatrix(
    waveform: FloatArray,
    rows: Int,
    top: Float = 0f,
    bandHeight: Float = size.height,
    colSpacingDp: Float = 5.5f,
    dotRadiusDp: Float = 1.5f,
) {
    if (waveform.isEmpty() || rows <= 0) return
    val colSpacing = colSpacingDp.dp.toPx()
    val dotR = dotRadiusDp.dp.toPx()
    val w = size.width
    val cols = (w / colSpacing).toInt().coerceAtLeast(1)
    val per = (waveform.size / cols).coerceAtLeast(1)
    val midY = top + bandHeight / 2f
    val rowSpacing = (bandHeight / 2f) / rows

    for (c in 0 until cols) {
        var peak = 0f
        val start = c * per
        for (i in start until minOf(start + per, waveform.size)) {
            val a = abs(waveform[i])
            if (a > peak) peak = a
        }
        val lit = (peak * rows).roundToInt().coerceIn(0, rows)
        val x = c * colSpacing + colSpacing / 2f
        for (r in 0 until rows) {
            val color = ledDotColor(r, rows, lit = r < lit)
            drawCircle(color, radius = dotR, center = Offset(x, midY - (r + 0.5f) * rowSpacing))
            drawCircle(color, radius = dotR, center = Offset(x, midY + (r + 0.5f) * rowSpacing))
        }
    }
}

/** Unlit = dim grid gray; lit ramps from the app's warm haptic amber at the base up to red at the peak. */
private fun ledDotColor(row: Int, rows: Int, lit: Boolean): Color {
    if (!lit) return WorkbenchColors.Grid.copy(alpha = 0.55f)
    val t = row.toFloat() / rows.coerceAtLeast(1)
    return lerp(WorkbenchColors.Haptic, WorkbenchColors.Red, t)
}

/**
 * A small, self-contained LED dot-matrix rendering of [pattern]'s haptic amplitude — the same
 * amplitude-over-time data [TimelineView]'s LED mode renders, at a size that fits [FeelCard]'s art
 * panel. Independent of [EditorState] so it can render any library pattern, not just the one currently
 * open in the editor; renders a fresh [DefaultPatternRenderer] waveform for whatever [pattern] it's given.
 */
@Composable
fun TimelineDotMatrixThumbnail(pattern: HapticAudioPattern, modifier: Modifier = Modifier, rows: Int = 8) {
    val waveform = remember(pattern) {
        DefaultPatternRenderer().renderHapticWaveform(pattern, THUMBNAIL_WAVE_SR).readAll()
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(WorkbenchColors.Screen),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (waveform.isNotEmpty()) drawLedDotMatrix(waveform, rows = rows, colSpacingDp = 4f, dotRadiusDp = 1.1f)
        }
    }
}

private const val WAVE_SR = 4000
private const val THUMBNAIL_WAVE_SR = 2000

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

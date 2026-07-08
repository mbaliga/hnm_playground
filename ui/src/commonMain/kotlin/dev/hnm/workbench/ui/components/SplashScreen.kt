package dev.hnm.workbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.design.SplashGeometry
import dev.hnm.workbench.core.design.SplashScene
import dev.hnm.workbench.core.design.SplashVisual
import dev.hnm.workbench.ui.theme.HyleColors
import dev.hnm.workbench.ui.theme.HyleRoles
import kotlin.math.abs
import kotlin.math.min

/**
 * The procedural splash, rendered with Compose. Visual, sound and haptics all derive from one
 * [SplashScene], so they're coincident. [onStart] fires the pattern (haptics+audio) once; [onFinished]
 * dismisses after the scene's duration. Pass [fixedTimeSec] to draw a single deterministic frame (tests/
 * previews) instead of animating. [reducedMotion] draws a static mark (no animated geometry) while still
 * running the haptic/audio sting — the platform host wires this from the system's reduce-motion signal.
 */
@Composable
fun SplashScreen(
    scene: SplashScene,
    onStart: () -> Unit = {},
    onFinished: () -> Unit = {},
    fixedTimeSec: Double? = null,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tail = 0.5
    val total = scene.durationSeconds + tail
    var elapsed by remember { mutableStateOf(fixedTimeSec ?: 0.0) }

    if (fixedTimeSec == null) {
        LaunchedEffect(scene) {
            onStart()
            val start = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                elapsed = (now - start) / 1e9
                if (elapsed >= total) break
            }
            onFinished()
        }
    }

    val master = SplashGeometry.masterAlpha(elapsed, scene.durationSeconds)
    // Palette position (UX brief §4.1): violet <-> cyan by paletteMix, blooming toward radium on a beat.
    val baseAccent = lerp(HyleRoles.PrimaryAction, HyleColors.ProvenanceCloud, scene.paletteMix.toFloat())
    val radiumBoost = scene.beats.maxOf { b ->
        val age = elapsed - b
        if (age in 0.0..0.2) (1.0 - age / 0.2).toFloat() else 0f
    }.coerceIn(0f, 1f)
    val accent = lerp(baseAccent, HyleColors.ProvenanceNative, radiumBoost * 0.6f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HyleRoles.Background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (fixedTimeSec == null) { elapsed = total; onFinished() } },
        contentAlignment = Alignment.Center,
    ) {
        if (reducedMotion) {
            // No animated geometry — a still mark. The haptic/audio sting (fired by onStart above) is
            // unaffected; only the *drawn* motion is suppressed.
            Text(
                "HAPTICS WORKBENCH",
                color = HyleRoles.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            return@Box
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.44f
            val scale = min(w, h) * 0.40f
            when (scene.visual) {
                SplashVisual.RIPPLE -> {
                    for (ring in SplashGeometry.ripple(elapsed, scene.beats)) {
                        drawCircle(
                            color = accent.copy(alpha = ring.alpha * master),
                            radius = ring.radius * scale,
                            center = Offset(cx, cy),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                    drawCircle(accent.copy(alpha = master), 6.dp.toPx(), Offset(cx, cy))
                }
                SplashVisual.BLOOM -> {
                    val bars = 40
                    val heights = SplashGeometry.bloom(elapsed, scene.durationSeconds, scene.beats, bars, scene.seed)
                    val band = w * 0.7f
                    val left = cx - band / 2f
                    val slot = band / bars
                    val maxH = h * 0.22f
                    val mid = (bars - 1) / 2.0
                    for (i in 0 until bars) {
                        val centerish = (1f - (abs(i - mid) / mid).toFloat()).coerceIn(0f, 1f)
                        val color = lerp(HyleColors.InkPure, accent, centerish * 0.6f)
                            .copy(alpha = master * 0.85f)
                        val bh = heights[i] * maxH
                        val x = left + i * slot
                        drawRect(color, topLeft = Offset(x, cy - bh), size = Size(slot * 0.55f, bh * 2))
                    }
                }
                SplashVisual.SWEEP -> {
                    val left = w * 0.12f
                    val span = w * 0.76f
                    for ((x, bright) in SplashGeometry.sweepTicks(elapsed, scene.durationSeconds, scene.beats)) {
                        val px = left + x * span
                        drawRect(
                            HyleColors.InkPure.copy(alpha = bright * master),
                            topLeft = Offset(px - 1.dp.toPx(), cy - 10.dp.toPx()),
                            size = Size(2.dp.toPx(), 20.dp.toPx()),
                        )
                    }
                    val p = SplashGeometry.sweepProgress(elapsed, scene.durationSeconds)
                    val px = left + p * span
                    drawLine(
                        accent.copy(alpha = master),
                        Offset(px, cy - 26.dp.toPx()), Offset(px, cy + 22.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawCircle(accent.copy(alpha = master), 5.dp.toPx(), Offset(px, cy - 26.dp.toPx()))
                }
                SplashVisual.SPARK -> {
                    drawCircle(accent.copy(alpha = master), 7.dp.toPx(), Offset(cx, cy))
                    for (s in SplashGeometry.sparks(elapsed, scene.beats, scene.seed)) {
                        drawCircle(
                            lerp(accent, HyleColors.InkPure, 0.4f).copy(alpha = s.alpha * master),
                            radius = s.size * scale,
                            center = Offset(cx + s.x * scale, cy + s.y * scale),
                        )
                    }
                }
                SplashVisual.LATTICE -> {
                    val gridSize = 7
                    val span = min(w, h) * 0.6f
                    val cellSize = span / gridSize
                    val left = cx - span / 2f
                    val top = cy - span / 2f
                    for (cell in SplashGeometry.lattice(elapsed, scene.beats)) {
                        val x = left + cell.col * cellSize + cellSize / 2f
                        val y = top + cell.row * cellSize + cellSize / 2f
                        drawCircle(
                            accent.copy(alpha = cell.alpha * master),
                            radius = cellSize * 0.28f,
                            center = Offset(x, y),
                        )
                    }
                }
            }
            // Scanlines.
            var y = 0f
            while (y < h) {
                drawRect(Color.White.copy(alpha = 0.018f * master), topLeft = Offset(0f, y), size = Size(w, 1f))
                y += 3f
            }
        }
        Text(
            "HAPTICS WORKBENCH",
            color = HyleRoles.OnSurface.copy(alpha = master * 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )
    }
}

private fun lerp(a: Color, b: Color, f: Float): Color {
    val ff = f.coerceIn(0f, 1f)
    return Color(
        red = a.red * (1 - ff) + b.red * ff,
        green = a.green * (1 - ff) + b.green * ff,
        blue = a.blue * (1 - ff) + b.blue * ff,
    )
}

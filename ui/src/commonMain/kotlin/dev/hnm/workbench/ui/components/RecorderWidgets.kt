package dev.hnm.workbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * A single keypad cell with a recessed hemispherical CRATER (recorder2.html .key + .dome).
 *
 * The crater is concave — light from directly above hits the bottom inner wall (bright) and
 * the top inner wall is in shadow (dark). Reproduced with:
 *   • a vertical gradient dark-top → lighter-bottom (the CSS .dome linear-gradient)
 *   • a radial vignette darkening the rim (inset 0 0 60px black)
 *   • a top-down inner shadow band (inset 0 22px black) drawn over the upper half
 * The cell itself is a flat slice of the slab (#161412 + faint dot texture); no card rounding —
 * the parent slab clips everything to one radius and 2dp gaps show as hairline seams.
 */
@Composable
fun KeypadCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    domeFraction: Float = 0.80f,        // .key.big .dome { width:80% }
    glow: Color = Color.Transparent,    // red when live / blue when wifi on
    glyph: @Composable () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(WorkbenchColors.KeyCell)
            .drawBehind {
                // faint two-grid dot texture (background-size 5px & 2.5px)
                drawDotTexture(WorkbenchColors.KeyDot)
            }
            .clickable(interactionSource = interaction, indication = null) { onClick() },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize(domeFraction)
                .aspectRatio(1f)
                .clip(CircleShape)
                .drawBehind { drawCrater(pressed, glow) },
        ) {
            glyph()
        }
    }
}

/** Draw the concave crater into a circular bounds. */
private fun DrawScope.drawCrater(pressed: Boolean, glow: Color) {
    val c = WorkbenchColors
    // Base vertical gradient — dark top → lighter bottom (pressed = even darker)
    val stops = if (pressed) {
        arrayOf(
            0.00f to Color(0xFF010100),
            0.18f to Color(0xFF060504),
            0.42f to Color(0xFF0E0C09),
            0.68f to Color(0xFF15130F),
            1.00f to Color(0xFF1C1A15),
        )
    } else {
        arrayOf(
            0.00f to c.Crater0,
            0.18f to c.Crater18,
            0.42f to c.Crater42,
            0.68f to c.Crater68,
            0.88f to c.Crater88,
            1.00f to c.Crater100,
        )
    }
    drawCircle(
        brush = Brush.verticalGradient(colorStops = stops),
    )
    // Rim vignette (inset 0 0 60px rgba(0,0,0,.28))
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.55f to Color.Transparent,
                1.00f to Color(0x66000000),
            ),
        ),
    )
    // Upper inner-wall shadow (inset 0 22px 38px rgba(0,0,0,.96)) — darken the top band
    drawCircle(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xF0000000),
                0.34f to Color(0x40000000),
                0.55f to Color.Transparent,
            ),
        ),
    )
    // Bottom inner-wall highlight (inset 0 -14px 26px rgba(255,255,255,.07))
    drawCircle(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.72f to Color.Transparent,
                1.00f to Color(0x14FFFFFF),
            ),
        ),
    )
    // Top hairline highlight (0 .5px 0 rgba(255,255,255,.09))
    drawCircle(
        color = Color(0x17FFFFFF),
        style = Stroke(width = 1f),
    )
    // Active glow ring (0 0 0 2.5px red/blue)
    if (glow != Color.Transparent && !pressed) {
        drawCircle(
            color = glow,
            radius = size.minDimension / 2f - 1.25f.dp.toPx(),
            style = Stroke(width = 2.5f.dp.toPx()),
        )
    }
}

/** Two offset dot grids: background-size 5px & 2.5px, white .04 — the subtle cell texture. */
private fun DrawScope.drawDotTexture(dot: Color) {
    val s1 = 5f.dp.toPx()
    val s2 = 2.5f.dp.toPx()
    val r = 0.5f.dp.toPx()
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            drawCircle(dot, radius = r, center = Offset(x, y))
            x += s1
        }
        y += s1
    }
    y = s2
    while (y < size.height) {
        var x = s2
        while (x < size.width) {
            drawCircle(dot, radius = r, center = Offset(x, y))
            x += s2
        }
        y += s2
    }
}

/** Red record glyph: solid circle, 21% of dome. */
@Composable
fun GlyphRec(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize(0.21f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(WorkbenchColors.Red),
    )
}

/** Stop glyph: rounded square #58554f, 24% of dome. */
@Composable
fun GlyphStop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize(0.24f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(WorkbenchColors.GlyphStop),
    )
}

/**
 * Speaker grille (recorder2.html .grille):
 *   background:#0a0908, white .22 dots at 6.5px spacing, 1px border #1c1a16, 8px radius.
 */
@Composable
fun SpeakerGrille(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(WorkbenchColors.GrilleBg)
            .border(1.dp, WorkbenchColors.GrilleBorder, RoundedCornerShape(8.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val spacing = 6.5f.dp.toPx()
            val dotR = 0.7f.dp.toPx()
            var y = spacing / 2
            while (y < size.height) {
                var x = spacing / 2
                while (x < size.width) {
                    drawCircle(WorkbenchColors.GrilleDot, radius = dotR, center = Offset(x, y))
                    x += spacing
                }
                y += spacing
            }
        }
    }
}

/** Battery badge (recorder2.html .battery): dark pill #0a0908, #999 text, #252220 border. */
@Composable
fun BatteryBadge(percent: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(WorkbenchColors.BatteryBg)
            .border(1.dp, WorkbenchColors.BatteryBorder, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "▮ $percent%",
            color = WorkbenchColors.BatteryText,
            fontSize = 14.sp,
            letterSpacing = 0.02.sp,
        )
    }
}

/** CRT scanline overlay (recorder2.html .screen::before): 1px line every 3px + top sheen 0→15%. */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x0AFFFFFF), Color.Transparent),
                endY = size.height * 0.15f,
            ),
        )
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.White.copy(alpha = 0.018f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += 3f
        }
    }
}

/** Red recording dot + glow (recorder2.html .recdot). */
@Composable
fun RecordingDot(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (active) WorkbenchColors.Red else Color(0xFF2E2C28))
            .drawBehind {
                if (active) {
                    drawCircle(
                        color = WorkbenchColors.Red.copy(alpha = 0.7f),
                        radius = size.minDimension,
                        style = Stroke(width = size.minDimension * 0.4f),
                    )
                }
            },
    )
}

// ---------- waveform draw helpers (recorder2.html canvas, top-anchored) ----------

/** Horizontal dotted guide just below bar-start level (recorder2 drawDottedBaseline). */
fun DrawScope.drawDottedBaseline(x0: Float, x1: Float, topPad: Float) {
    val y = topPad + 4f
    val spacing = 6.5f.dp.toPx()
    var x = x0
    while (x < x1) {
        drawRect(
            color = Color(0xFF96938C).copy(alpha = 0.38f),
            topLeft = Offset(x, y - 1.2f),
            size = Size(2f, 2.4f),
        )
        x += spacing
    }
}

/** Red playhead: dot near the top + line hanging down (recorder2 drawPlayhead). */
fun DrawScope.drawPlayhead(x: Float, topPad: Float, maxBarH: Float) {
    val dotY = topPad - 1f
    val botY = topPad + maxBarH * 1.08f
    drawLine(
        color = WorkbenchColors.Red,
        start = Offset(x + 0.5f, dotY + 9f),
        end = Offset(x + 0.5f, botY),
        strokeWidth = 1.5f,
    )
    drawCircle(
        color = WorkbenchColors.Red,
        radius = 4.2f,
        center = Offset(x + 0.5f, dotY + 5f),
    )
}

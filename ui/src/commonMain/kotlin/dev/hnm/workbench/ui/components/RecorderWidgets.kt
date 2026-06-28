package dev.hnm.workbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Pressed-dome button matching the HTML recorder:
 *   .key → light housing background, 18 px radius, subtle drop-shadow
 *   .dome → radial-gradient circle (dome-hi at 38%/32% → dome-lo at edge)
 *           + dish ring pseudo-element
 *   :active → dome depresses 1 px, outer glow removed
 */
@Composable
fun DomeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Transparent,
    content: @Composable () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFEFEEEB), Color(0xFFE4E3DF)),
                )
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(0.dp),
    ) {
        // Dome circle — radial gradient from dome-hi to dome-lo, exactly as in HTML
        val domeTranslate = if (pressed) 1.dp else 0.dp
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize(0.54f)
                .aspectRatio(1f)
                .padding(top = domeTranslate)
                .clip(CircleShape)
                .background(
                    // radial-gradient(circle at 38% 32%, dome-hi → #f4f3f0 → dome-lo)
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to WorkbenchColors.DomeHi,
                            0.42f to Color(0xFFF4F3F0),
                            1.00f to WorkbenchColors.DomeLo,
                        ),
                        center = Offset(0.38f * 100, 0.32f * 100),
                        radius = 100f,
                    )
                )
                .drawBehind {
                    // dish ring under dome (::after in HTML)
                    drawCircle(
                        color = Color(0x0A000000),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                // Shadow: 0 5px 9px -3px rgba(0,0,0,.30), 0 2px 3px rgba(0,0,0,.18)
                .shadow(if (pressed) 2.dp else 6.dp, CircleShape),
        ) {
            // glow ring when active (e.g. recording or wifi on)
            if (glowColor != Color.Transparent && !pressed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .drawBehind {
                            drawCircle(
                                color = glowColor.copy(alpha = 0.5f),
                                radius = size.minDimension / 2f + 2.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                )
            }
            content()
        }
    }
}

/**
 * Speaker grille: dot grid from the HTML.
 *   background-image: radial-gradient(var(--grille) 1px, transparent 1.25px)
 *   background-size: 6.5px 6.5px
 */
@Composable
fun SpeakerGrille(
    modifier: Modifier = Modifier,
    dotColor: Color = WorkbenchColors.Grille,
) {
    Canvas(modifier = modifier) {
        val spacing = 6.5.dp.toPx()
        val dotR = 0.7.dp.toPx()
        var y = spacing / 2
        while (y < size.height) {
            var x = spacing / 2
            while (x < size.width) {
                drawCircle(dotColor, radius = dotR, center = Offset(x, y))
                x += spacing
            }
            y += spacing
        }
    }
}

/**
 * CRT scanline overlay from the HTML:
 *   repeating-linear-gradient(180deg, rgba(255,255,255,.028) 0 1px, rgba(0,0,0,0) 1px 3px)
 * plus a top sheen: rgba(255,255,255,.05) → 0 at 18%
 */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Top sheen gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x0DFFFFFF), Color.Transparent),
                endY = size.height * 0.18f,
            ),
        )
        // Scanlines: 1px bright every 3px
        val lineAlpha = (0.028f * 255).toInt()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color(0xFF_FFFFFF).copy(alpha = 0.028f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += 3f
        }
    }
}

/** Red recording dot with its glow: box-shadow: 0 0 6px rgba(226,44,36,.7) */
@Composable
fun RecordingDot(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (active) WorkbenchColors.Red else Color(0xFF5A5A58))
            .drawBehind {
                if (active) {
                    drawCircle(
                        color = WorkbenchColors.Red.copy(alpha = 0.7f),
                        radius = size.minDimension,
                        style = Stroke(width = size.minDimension * 0.4f),
                    )
                }
            }
    )
}

/** Dotted baseline (future-time area after playhead), as drawn in the HTML. */
fun DrawScope.drawDottedBaseline(x0: Float, x1: Float, midY: Float) {
    val spacing = 6.5.dp.toPx()
    val dotW = 2f
    val dotH = 3.2f
    var x = x0
    while (x < x1) {
        drawRect(
            color = Color(0xFF_B4B4B0).copy(alpha = 0.55f),
            topLeft = Offset(x, midY - dotH / 2),
            size = androidx.compose.ui.geometry.Size(dotW, dotH),
        )
        x += spacing
    }
}

/** Red playhead line + dot cap, matching the HTML canvas code exactly. */
fun DrawScope.drawPlayhead(x: Float, midY: Float, maxHalf: Float) {
    val top = midY - maxHalf * 1.32f
    val bot = midY + maxHalf * 1.12f
    drawLine(
        color = WorkbenchColors.Red,
        start = Offset(x + 0.5f, top),
        end = Offset(x + 0.5f, bot),
        strokeWidth = 2f,
    )
    drawCircle(
        color = WorkbenchColors.Red,
        radius = 4.6f,
        center = Offset(x + 0.5f, top),
    )
}

/** Battery badge: black pill, white text/icon — from the HTML .battery rule. */
@Composable
fun BatteryBadge(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFF0E0E0E))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = "$percent%",
            color = Color.White,
            fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.02f, androidx.compose.ui.unit.TextUnitType.Em),
        )
    }
}

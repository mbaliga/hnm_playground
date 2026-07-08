package dev.hnm.workbench.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hnm.workbench.ui.theme.Finish
import dev.hnm.workbench.ui.theme.HyleColors
import dev.hnm.workbench.ui.theme.HyleTokens
import dev.hnm.workbench.ui.theme.Provenance
import dev.hnm.workbench.ui.theme.Pulse

/**
 * The dot-grid "substrate" — a quiet, generative-feeling background that reads as material rather than
 * chrome. An original Compose Multiplatform Canvas implementation (Hyle's own dot-grid "room" exists
 * only as an AGSL `RuntimeShader` demo requiring Android API 33+, so it cannot run here); this is
 * informed by that visual idea, not a literal port.
 */
@Composable
fun DotGridSubstrate(
    modifier: Modifier = Modifier,
    dotColor: Color = HyleColors.HairlineDefault,
    spacing: Dp = 18.dp,
    dotRadius: Dp = 1.dp,
) {
    Canvas(modifier = modifier) {
        val spacingPx = spacing.toPx()
        val radiusPx = dotRadius.toPx()
        var y = spacingPx / 2
        while (y < size.height) {
            var x = spacingPx / 2
            while (x < size.width) {
                drawCircle(dotColor, radius = radiusPx, center = Offset(x, y))
                x += spacingPx
            }
            y += spacingPx
        }
    }
}

/**
 * A frosted "glass" surface: `HyleColors.GlassPane` (the vendored token — a translucent dark fill, not
 * a real-time backdrop blur; Hyle's own token model implements "glass" the same way, so this matches the
 * source system rather than approximating it) over a hairline border, [HyleTokens.Dimension.RadiusLg]
 * rounded corners by default.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(HyleTokens.Dimension.RadiusLg.dp),
    borderColor: Color = HyleColors.HairlineDefault,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(HyleColors.GlassPane, shape)
            .border(HyleTokens.Dimension.SizeBorderThin.dp, borderColor, shape),
    ) {
        content()
    }
}

/**
 * The animated half of [Finish.Radiant]: "heartbeat, not weather" — alpha breathes between
 * [Pulse.minAlphaPct] and [Pulse.maxAlphaPct] over [Pulse.periodMs], linearly, reversing (never an
 * aperiodic flicker). [Finish.Reflective] draws nothing — ordinary chrome stays inert until touched.
 */
fun Modifier.provenanceGlow(
    finish: Finish,
    shape: Shape = RectangleShape,
): Modifier = when (finish) {
    Finish.Reflective -> this
    is Finish.Radiant -> this.then(RadiantGlowElement(finish, shape))
}

/** Convenience: glow using a [Provenance]'s own [Provenance.finish] (already [Finish.Radiant]). */
fun Modifier.provenanceGlow(provenance: Provenance, shape: Shape = RectangleShape): Modifier =
    provenanceGlow(provenance.finish, shape)

// A composed-modifier factory (rather than a raw Modifier.Node) so this stays simple across the
// Compose Multiplatform version pinned here; the animation itself is unremarkable infra so hand-rolling
// a Node isn't warranted.
private fun RadiantGlowElement(finish: Finish.Radiant, shape: Shape): Modifier =
    Modifier.composed {
        val transition = rememberInfiniteTransition(label = "provenanceGlow")
        val alphaPct by transition.animateFloat(
            initialValue = finish.pulse.minAlphaPct.toFloat(),
            targetValue = finish.pulse.maxAlphaPct.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = finish.pulse.periodMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "provenanceGlowAlpha",
        )
        val tint = Color(finish.tint)
        drawBehind {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = alphaPct / 100f), tint.copy(alpha = 0f)),
                    radius = size.maxDimension * 0.75f,
                ),
                size = size,
            )
        }
    }

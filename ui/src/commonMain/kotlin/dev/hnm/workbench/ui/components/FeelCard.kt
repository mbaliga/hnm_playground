package dev.hnm.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * A portrait "trading card" for one pattern — name + star up top, an LED dot-matrix thumbnail of its
 * haptic amplitude as the art panel ([TimelineDotMatrixThumbnail], see TimelineView.kt), intensity/
 * sharpness stat bars, and — when its events carry one — a line of flavor text from the new
 * [dev.hnm.workbench.core.ir.HapticEvent.voice] field. Used for the Starred/Recent rail on the Library
 * tab's home block ([LibraryListPanel]): a quick, glanceable "what does this feel like" preview, distinct
 * from the dense text rows in the row list below it.
 */
@Composable
fun FeelCard(
    pattern: HapticAudioPattern,
    favorite: Boolean,
    onTap: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = remember(pattern) { FeelStats.of(pattern) }
    val flavor = remember(pattern) { flavorTextOf(pattern) }

    Column(
        modifier
            .width(136.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WorkbenchColors.Surface)
            .border(1.dp, WorkbenchColors.Grid, RoundedCornerShape(14.dp))
            .clickable(onClick = onTap)
            .padding(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pattern.name,
                color = WorkbenchColors.Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (favorite) "★" else "☆",
                color = if (favorite) WorkbenchColors.Red else WorkbenchColors.InkDim,
                fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = onToggleFavorite).padding(start = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        TimelineDotMatrixThumbnail(
            pattern = pattern,
            rows = 8,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
        Spacer(Modifier.height(8.dp))
        FeelStatBar("INT", stats.intensity)
        Spacer(Modifier.height(4.dp))
        FeelStatBar("SHP", stats.sharpness)
        if (flavor != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "“$flavor”",
                color = WorkbenchColors.InkDim,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One labeled meter bar, e.g. `INT ▓▓▓▓▓▓▓░░░` — the trading-card "stats" row. */
@Composable
private fun FeelStatBar(label: String, value: Double, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = WorkbenchColors.Muted, fontSize = 8.sp, modifier = Modifier.width(24.dp))
        Box(
            Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(WorkbenchColors.Grid),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.toFloat().coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(WorkbenchColors.Red),
            )
        }
    }
}

/** Average intensity/sharpness across a pattern's haptic events — the trading-card "stats." */
internal data class FeelStats(val intensity: Double, val sharpness: Double) {
    companion object {
        fun of(pattern: HapticAudioPattern): FeelStats {
            val events = pattern.tracks.filterIsInstance<HapticTrack>().flatMap { it.events }
            if (events.isEmpty()) return FeelStats(intensity = 0.0, sharpness = 0.0)
            val intensities = events.map {
                when (it) {
                    is Transient -> it.intensity
                    is Continuous -> it.intensity
                    is Primitive -> it.scale
                }
            }
            val sharpnesses = events.map {
                when (it) {
                    is Transient -> it.sharpness
                    is Continuous -> it.sharpness
                    // Primitives carry no sharpness of their own — same nominal perceptual mapping
                    // DefaultPatternRenderer.primitiveToTransient uses to synthesize one.
                    is Primitive -> nominalPrimitiveSharpness(it.type)
                }
            }
            return FeelStats(
                intensity = intensities.average().coerceIn(0.0, 1.0),
                sharpness = sharpnesses.average().coerceIn(0.0, 1.0),
            )
        }
    }
}

private fun nominalPrimitiveSharpness(type: PrimitiveType): Double = when (type) {
    PrimitiveType.TICK -> 0.95
    PrimitiveType.LOW_TICK -> 0.6
    PrimitiveType.CLICK -> 0.5
    PrimitiveType.THUD -> 0.15
    PrimitiveType.SPIN -> 0.4
    PrimitiveType.QUICK_RISE -> 0.7
    PrimitiveType.SLOW_RISE -> 0.5
    PrimitiveType.QUICK_FALL -> 0.6
}

/** First authored `voice` found on the pattern's haptic events, if any — optional card flavor text. */
private fun flavorTextOf(pattern: HapticAudioPattern): String? =
    pattern.tracks.filterIsInstance<HapticTrack>()
        .flatMap { it.events }
        .firstNotNullOfOrNull { it.voice }

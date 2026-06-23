package dev.hnm.workbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.design.MotionPrimitive
import dev.hnm.workbench.core.design.MotionPrimitives
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Stage 1 authoring surface: the named motion-primitive vocabulary. Each tile sparklines the
 * primitive's motion curve (the same curve that becomes the haptic envelope) and, on tap, loads it
 * into the editor so the timeline/inspector/export all reflect it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MotionPalette(state: EditorState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Motion primitives", color = WorkbenchColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (primitive in MotionPrimitive.entries) {
                MotionTile(primitive, onClick = { state.load(MotionPrimitives.toPattern(primitive)) })
            }
        }
        Text(
            "One curve, two modalities: the sparkline is both the on-screen motion and the haptic envelope.",
            color = WorkbenchColors.Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun MotionTile(primitive: MotionPrimitive, onClick: () -> Unit) {
    val samples = remember(primitive) {
        val c = MotionPrimitives.curve(primitive)
        // Downsample to ~64 points for the sparkline.
        val n = 64
        DoubleArray(n) { i -> c.valueAt(c.durationSeconds * i / (n - 1)) }
    }
    Column(
        Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(WorkbenchColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(primitive.displayName, color = WorkbenchColors.OnSurface, fontSize = 12.sp)
        Canvas(Modifier.fillMaxWidth().height(28.dp).padding(top = 4.dp)) {
            val w = size.width
            val mid = size.height / 2f
            var peak = 0.0
            for (s in samples) if (kotlin.math.abs(s) > peak) peak = kotlin.math.abs(s)
            val norm = if (peak > 1e-9) (size.height * 0.45f / peak).toFloat() else 0f
            var prev: Offset? = null
            for (i in samples.indices) {
                val x = w * i / (samples.size - 1)
                val y = mid - (samples[i] * norm)
                val pt = Offset(x.toFloat(), y.toFloat())
                prev?.let { drawLine(WorkbenchColors.Haptic, it, pt, strokeWidth = 1.5f) }
                prev = pt
            }
        }
    }
}

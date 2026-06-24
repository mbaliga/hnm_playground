package dev.hnm.workbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.design.TextureField
import dev.hnm.workbench.core.design.TextureFieldType
import dev.hnm.workbench.core.design.TextureFields
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Stage 2 authoring surface: the procedural texture-field palette. Each tile sparklines the field
 * sampled at the current roughness, and tapping loads the scrub pattern into the editor.
 *
 * The roughness slider is shared across all tiles so the designer can hear "smooth vs coarse" by
 * moving one control and comparing the sparklines side-by-side — the same workflow as in
 * Interhaptics Haptic Composer's Texture perception.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TexturePalette(state: EditorState, modifier: Modifier = Modifier) {
    var roughness by remember { mutableStateOf(0.5f) }
    var velocity by remember { mutableStateOf(1.0f) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Texture fields", color = WorkbenchColors.Muted, fontSize = 12.sp)
        }

        // Roughness and velocity sliders share a row.
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("roughness", color = WorkbenchColors.Muted, fontSize = 10.sp)
            Slider(
                value = roughness,
                onValueChange = { roughness = it },
                modifier = Modifier.weight(1f),
            )
            Text("${(roughness * 100).toInt()}%", color = WorkbenchColors.OnSurface, fontSize = 10.sp, modifier = Modifier.width(30.dp))
            Text("velocity", color = WorkbenchColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp))
            Slider(
                value = velocity,
                onValueChange = { velocity = it },
                valueRange = 0.2f..3.0f,
                modifier = Modifier.weight(1f),
            )
            Text("${(velocity * 10).toInt() / 10.0}×", color = WorkbenchColors.OnSurface, fontSize = 10.sp, modifier = Modifier.width(30.dp))
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (type in TextureFieldType.entries) {
                val field = TextureField(type = type, roughness = roughness.toDouble())
                TextureTile(
                    field = field,
                    velocity = velocity.toDouble(),
                    onClick = { state.load(TextureFields.toPattern(field, duration = 0.5, velocity = velocity.toDouble())) },
                )
            }
        }

        Text(
            "Finger velocity → temporal frequency: the same field scraped fast or slow feels finer or coarser.",
            color = WorkbenchColors.Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun TextureTile(field: TextureField, velocity: Double, onClick: () -> Unit) {
    val samples = remember(field, velocity) {
        val c = TextureFields.curve(field, duration = 0.5, velocity = velocity)
        val n = 64
        DoubleArray(n) { i -> c.valueAt(0.5 * i / (n - 1)) }
    }
    Column(
        Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(WorkbenchColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(field.type.displayName, color = WorkbenchColors.OnSurface, fontSize = 12.sp)
        Canvas(Modifier.fillMaxWidth().height(28.dp).padding(top = 4.dp)) {
            val w = size.width
            val h = size.height
            // Draw filled area (field value = intensity; bottom of canvas = 0).
            for (i in 0 until samples.size - 1) {
                val x0 = w * i / (samples.size - 1)
                val x1 = w * (i + 1) / (samples.size - 1)
                val y0 = h * (1f - samples[i]).toFloat()
                val y1 = h * (1f - samples[i + 1]).toFloat()
                // Filled bar under the curve segment.
                drawRect(
                    color = WorkbenchColors.Haptic.copy(alpha = 0.25f),
                    topLeft = Offset(x0.toFloat(), minOf(y0, y1)),
                    size = Size(x1.toFloat() - x0.toFloat(), h - minOf(y0, y1)),
                )
                drawLine(WorkbenchColors.Haptic, Offset(x0.toFloat(), y0), Offset(x1.toFloat(), y1), strokeWidth = 1.5f)
            }
        }
    }
}

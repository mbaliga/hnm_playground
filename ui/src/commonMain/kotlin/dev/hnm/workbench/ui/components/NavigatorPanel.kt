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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.design.MotionPrimitive
import dev.hnm.workbench.core.design.MotionPrimitives
import dev.hnm.workbench.core.design.ParameterNavigator
import dev.hnm.workbench.core.design.TextureField
import dev.hnm.workbench.core.design.TextureFieldType
import dev.hnm.workbench.core.design.TextureFields
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Stage 3 authoring surface: the parameter navigator. Pick two endpoints and it fills a perceptually
 * graded family between them (the deterministic, on-device version of "give me 5 variations of this").
 * Each tile sparklines a member; tap to load it into the editor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigatorPanel(state: EditorState, modifier: Modifier = Modifier) {
    val count = 5
    val springPrimitives = remember { MotionPrimitive.entries.filter { MotionPrimitives.springParamsFor(it) != null } }

    Column(modifier) {
        Text("Parameter navigator", color = WorkbenchColors.Muted, fontSize = 12.sp)
        Text(
            "Interpolate between two authored feels to fill a graded family.",
            color = WorkbenchColors.Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // --- texture morph: smooth -> rough within one field type ---
        var texType by remember { mutableStateOf(TextureFieldType.PERLIN) }
        Text("Texture · smooth → rough", color = WorkbenchColors.OnSurface, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        ChipRow(
            options = TextureFieldType.entries,
            selected = texType,
            label = { it.displayName },
            onSelect = { texType = it },
        )
        val texFields = remember(texType) {
            ParameterNavigator.textureFamily(
                TextureField(texType, roughness = 0.05),
                TextureField(texType, roughness = 0.95),
                count,
            )
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (field in texFields) {
                val samples = remember(field) { sparkOf(TextureFields.curve(field, 0.5, 1.0).let { c -> DoubleArray(48) { c.valueAt(0.5 * it / 47) } }) }
                SparkTile(
                    label = "${(field.roughness * 100).toInt()}%",
                    samples = samples,
                    onClick = { state.load(TextureFields.toPattern(field)) },
                )
            }
        }

        // --- motion morph: A -> B in spring space ---
        var motionA by remember { mutableStateOf(MotionPrimitive.STIR) }
        var motionB by remember { mutableStateOf(MotionPrimitive.SETTLE) }
        Text("Motion · A → B", color = WorkbenchColors.OnSurface, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipRow(springPrimitives, motionA, { it.displayName }, { motionA = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipRow(springPrimitives, motionB, { it.displayName }, { motionB = it })
        }
        val motionPatterns = remember(motionA, motionB) { ParameterNavigator.motionFamilyPatterns(motionA, motionB, count) }
        val motionParams = remember(motionA, motionB) {
            ParameterNavigator.springFamily(
                MotionPrimitives.springParamsFor(motionA)!!,
                MotionPrimitives.springParamsFor(motionB)!!,
                count,
            )
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            motionParams.forEachIndexed { i, p ->
                val samples = remember(p) { sparkOf(MotionPrimitives.springCurve(p).let { c -> DoubleArray(48) { c.valueAt(c.durationSeconds * it / 47) } }) }
                SparkTile(
                    label = "${i + 1}",
                    samples = samples,
                    onClick = { state.load(motionPatterns[i]) },
                )
            }
        }
    }
}

/** Normalize a raw sample array to peak ±1 for drawing (keeps sign so springs swing both ways). */
private fun sparkOf(raw: DoubleArray): DoubleArray {
    var peak = 0.0
    for (s in raw) if (kotlin.math.abs(s) > peak) peak = kotlin.math.abs(s)
    if (peak <= 1e-9) return raw
    return DoubleArray(raw.size) { raw[it] / peak }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (option in options) {
            val isSelected = option == selected
            Text(
                label(option),
                color = if (isSelected) WorkbenchColors.Background else WorkbenchColors.OnSurface,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) WorkbenchColors.Primary else WorkbenchColors.SurfaceVariant)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun SparkTile(label: String, samples: DoubleArray, onClick: () -> Unit) {
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(WorkbenchColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Text(label, color = WorkbenchColors.Muted, fontSize = 9.sp)
        Canvas(Modifier.fillMaxWidth().height(22.dp).padding(top = 2.dp)) {
            val w = size.width
            val mid = size.height / 2f
            val scale = size.height * 0.45f
            var prev: Offset? = null
            for (i in samples.indices) {
                val x = w * i / (samples.size - 1)
                val y = mid - (samples[i] * scale)
                val pt = Offset(x.toFloat(), y.toFloat())
                prev?.let { drawLine(WorkbenchColors.Haptic, it, pt, strokeWidth = 1.5f) }
                prev = pt
            }
        }
    }
}

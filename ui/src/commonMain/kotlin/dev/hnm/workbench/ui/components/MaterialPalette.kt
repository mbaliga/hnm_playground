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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.design.Material
import dev.hnm.workbench.core.design.MaterialPreset
import dev.hnm.workbench.core.design.ModalSynth
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors
import kotlin.math.exp

/**
 * Stage 4 authoring surface: material handles driving one modal model. Each preset tile shows the
 * material's pitch and ring-down; the sliders author a custom material whose strike — sound *and*
 * haptics from the same modes — loads into the editor on tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialPalette(state: EditorState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Materials (modal synth)", color = WorkbenchColors.Muted, fontSize = 12.sp)
        Text(
            "One model, both channels: a strike's sound and its felt ring-down come from the same modes.",
            color = WorkbenchColors.Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (preset in MaterialPreset.entries) {
                MaterialTile(preset.displayName, preset.material, onClick = { state.load(ModalSynth.toPattern(preset.material)) })
            }
        }

        // --- custom material authoring ---
        var stiffness by remember { mutableStateOf(0.6f) }
        var density by remember { mutableStateOf(0.5f) }
        var damping by remember { mutableStateOf(0.3f) }
        var brightness by remember { mutableStateOf(0.6f) }
        val custom = Material("Custom", stiffness.toDouble(), density.toDouble(), damping.toDouble(), brightness.toDouble())

        Text("Custom material", color = WorkbenchColors.OnSurface, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        MaterialSlider("stiffness", stiffness) { stiffness = it }
        MaterialSlider("density", density) { density = it }
        MaterialSlider("damping", damping) { damping = it }
        MaterialSlider("brightness", brightness) { brightness = it }
        Row(Modifier.padding(top = 4.dp)) {
            MaterialTile("Custom", custom, onClick = { state.load(ModalSynth.toPattern(custom)) })
        }
    }
}

@Composable
private fun MaterialSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = WorkbenchColors.Muted, fontSize = 10.sp, modifier = Modifier.width(64.dp))
        Slider(value = value, onValueChange = onChange, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MaterialTile(name: String, material: Material, onClick: () -> Unit) {
    val f0 = remember(material) { ModalSynth.fundamentalHz(material) }
    val ring = remember(material) { ModalSynth.ringSeconds(material) }
    Column(
        Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(WorkbenchColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(name, color = WorkbenchColors.OnSurface, fontSize = 12.sp)
        Text("${f0.toInt()} Hz · ${ring.fmt()} s", color = WorkbenchColors.Muted, fontSize = 9.sp)
        // Ring-down over a fixed 1.2 s window, so longer rings visibly extend further right.
        Canvas(Modifier.fillMaxWidth().height(24.dp).padding(top = 4.dp)) {
            val w = size.width
            val h = size.height
            val window = 1.2
            val tau = (ring * 0.4).coerceAtLeast(0.01)
            var prev: Offset? = null
            val n = 48
            for (i in 0 until n) {
                val t = window * i / (n - 1)
                val v = exp(-t / tau)
                val x = w * i / (n - 1)
                val y = h - (v * h * 0.92).toFloat()
                val pt = Offset(x.toFloat(), y.toFloat())
                prev?.let { drawLine(WorkbenchColors.Haptic, it, pt, strokeWidth = 1.5f) }
                prev = pt
            }
        }
    }
}

/** Two-decimal format without java.lang.String.format (keeps commonMain KMP-clean). */
private fun Double.fmt(): String {
    val scaled = (this * 100).toInt()
    val whole = scaled / 100
    val frac = (scaled % 100).let { if (it < 10) "0$it" else "$it" }
    return "$whole.$frac"
}

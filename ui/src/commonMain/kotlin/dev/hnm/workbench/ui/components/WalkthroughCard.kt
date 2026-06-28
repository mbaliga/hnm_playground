package dev.hnm.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.theme.WorkbenchColors

private data class Step(val n: String, val title: String, val body: String)

private val STEPS = listOf(
    Step("1", "Describe it", "Type what you want in the Assistant — \"urgent alert\", \"metal tap\", \"gentle tick\" — and tap Generate. It builds a haptic + sound pattern and explains what it made."),
    Step("2", "Feel it", "Tap ▶ Play to feel it on the actuator (and hear it). On desktop there's no actuator, so Play is disabled."),
    Step("3", "Refine it", "Ask for edits in plain words — \"softer\", \"sharper\", \"longer\", \"slower\" — or drag the sliders in the Inspector on the right."),
    Step("4", "Or design by hand", "Below the Assistant are the building blocks: motion primitives, texture fields, materials, a parameter navigator, the pattern library, and a rhythm-capture pad. Tap any tile to load it."),
    Step("5", "Export", "The Export panel shows live Android VibrationEffect code, Apple AHAP, or the raw JSON for whatever you've built — copy it straight into an app."),
)

/**
 * A first-run walkthrough that explains the whole tool in five steps, plus a one-line note on the
 * perceptual idea behind it. Collapsible and dismissable so it stays out of the way once you know the
 * ropes; a "?" affordance in the header brings it back.
 */
@Composable
fun WalkthroughCard(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WorkbenchColors.Surface)
            .padding(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "How this works",
                color = WorkbenchColors.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(if (expanded) "Hide ▲" else "Show ▼", color = WorkbenchColors.Red, fontSize = 12.sp)
        }

        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Design haptics + sound together by describing a feeling — not by drawing waveforms. " +
                    "A visual/material handle controls felt vibration far more reliably than a word does.",
                color = WorkbenchColors.InkDim,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            STEPS.forEach { step ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    StepBadge(step.n)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(step.title, color = WorkbenchColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(step.body, color = WorkbenchColors.InkDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBadge(n: String) {
    Column(
        Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(WorkbenchColors.Red)
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(n, color = WorkbenchColors.Background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

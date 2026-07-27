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
import dev.hnm.workbench.core.settings.OnboardingProgress
import dev.hnm.workbench.core.settings.OnboardingTier
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

private data class Step(val n: String, val title: String, val body: String)

private val STEPS = listOf(
    Step("1", "Describe it", "Type what you want in the Assistant — \"urgent alert\", \"metal tap\", \"gentle tick\" — and tap Generate. It builds a haptic + sound pattern and explains what it made."),
    Step("2", "Feel it", "Tap ▶ Play to feel it on the actuator (and hear it). On desktop there's no actuator, so Play is disabled."),
    Step("3", "Refine it", "Ask for edits in plain words — \"softer\", \"sharper\", \"longer\", \"slower\" — or drag the sliders in the Inspector on the right."),
    Step("4", "Or design by hand", "Below the Assistant are the building blocks: motion primitives, texture fields, materials, a parameter navigator, the pattern library, and a rhythm-capture pad. Tap any tile to load it."),
    Step("5", "Export", "The Export panel shows live Android VibrationEffect code, Apple AHAP, or the raw JSON for whatever you've built — copy it straight into an app."),
)

/** One rung of the beginner ladder, in order — mirrors [OnboardingProgress]'s four flags. */
private data class ChecklistItem(
    val label: String,
    val hint: String,
    val done: (OnboardingProgress) -> Boolean,
)

private val CHECKLIST = listOf(
    ChecklistItem("Feel one", "Library — tap any saved pattern to play it.") { it.hasFeltOne },
    ChecklistItem("Make one", "Make — describe a feel to the Assistant and tap Generate.") { it.hasMadeOne },
    ChecklistItem("Change one", "Editor — select an event, then nudge a slider in the Inspector.") { it.hasChangedOne },
    ChecklistItem("Ship one", "Editor ⇱ Ship — see the export code for what you built.") { it.hasShippedOne },
)

/**
 * The Learn sheet's content: a starter checklist driven by [EditorState.onboarding] (see
 * [dev.hnm.workbench.core.settings.SettingsStore]'s beginner-ladder fields), a small progression-ladder
 * indicator, and — unchanged from before — the collapsible five-step "how this works" explanation.
 * Coachmarks pointing at the Library tab and the Editor route (`coachmarks/CoachMark.kt`) chase the same
 * four rungs this checklist tracks, so ticking one off here and having its coachmark disappear elsewhere
 * are two views of the same underlying progress, not two separate trackers.
 */
@Composable
fun WalkthroughCard(state: EditorState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        StarterChecklist(state.onboarding)
        Spacer(Modifier.height(14.dp))
        WalkthroughSteps()
    }
}

/** The checklist + ladder alone, for standalone rendering/testing without pulling in the five-step body. */
@Composable
fun StarterChecklist(progress: OnboardingProgress, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WorkbenchColors.Surface)
            .padding(14.dp),
    ) {
        Text("Your first steps", color = WorkbenchColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            "Four small things, in any order — do them once and this card stays out of your way.",
            color = WorkbenchColors.InkDim,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        OnboardingLadder(progress)
        Spacer(Modifier.height(10.dp))
        CHECKLIST.forEach { item -> ChecklistRow(item, item.done(progress)) }
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItem, done: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(
            if (done) "✓" else "○",
            color = if (done) WorkbenchColors.Red else WorkbenchColors.InkDim,
            fontSize = 13.sp,
            modifier = Modifier.width(20.dp),
        )
        Column {
            Text(
                item.label,
                color = if (done) WorkbenchColors.InkDim else WorkbenchColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (!done) {
                Text(item.hint, color = WorkbenchColors.InkDim, fontSize = 11.sp)
            }
        }
    }
}

/**
 * A small segmented progress indicator across the four ladder rungs, one segment per [CHECKLIST] item —
 * filled from that item's own flag, same source of truth the rows below it use, so the bar and the rows
 * never disagree even though [OnboardingProgress.tier] (shown as the caption underneath) reports only the
 * furthest rung reached and can outrun the individual flags (see its kdoc: a user can jump straight to
 * "shipped" without the earlier three ever being set).
 */
@Composable
private fun OnboardingLadder(progress: OnboardingProgress, modifier: Modifier = Modifier) {
    val tier = progress.tier
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CHECKLIST.forEach { item ->
                val reached = item.done(progress)
                Row(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (reached) WorkbenchColors.Red else WorkbenchColors.Grid),
                ) {}
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (tier == OnboardingTier.NEW) "Just getting started" else "Furthest so far: ${tier.name.lowercase().replace('_', ' ')}",
            color = WorkbenchColors.Muted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun WalkthroughSteps(modifier: Modifier = Modifier) {
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

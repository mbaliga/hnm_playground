package dev.hnm.workbench.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Placeholder bodies for the [Sheet] routes that exist and are reachable today but don't have a
 * dedicated panel yet ([Sheet.JSON], [Sheet.COMBINE]). [Sheet.SETTINGS] and [Sheet.FEEDBACK] have since
 * moved to real panels — [dev.hnm.workbench.ui.components.SettingsPanel] and
 * [dev.hnm.workbench.ui.components.FeedbackSheet], wired in `SheetHost.kt` — but [PlaceholderBody] and
 * [SheetLinkRow] stay here as the shared look the remaining placeholders (and the real panels' own
 * "jump to another sheet" rows) both use. Each placeholder keeps the same dark device chrome as the rest
 * of the app, so swapping in real content later is a drop-in — the route, and every button that opens
 * it, already works.
 */

@Composable
internal fun PlaceholderBody(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(message, color = WorkbenchColors.InkDim, fontSize = 12.sp)
    }
}

/** A single tappable "go to another sheet" row, styled to match a typical Settings list. */
@Composable
internal fun SheetLinkRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = WorkbenchColors.OnSurface, fontSize = 13.sp)
        Text("›", color = WorkbenchColors.Muted, fontSize = 16.sp)
    }
}

@Composable
fun JsonSheetBody(nav: WorkbenchNavState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        PlaceholderBody(
            "A focused raw-JSON viewer (copy, share, diff) is coming. The full export picker — JSON, " +
                "Kotlin VibrationEffect or AHAP, plus the scheduled command preview — already lives in Ship.",
        )
        HorizontalDivider(color = WorkbenchColors.Grid)
        SheetLinkRow("Open Ship") { nav.openSheet(Sheet.SHIP) }
    }
}

@Composable
fun CombineSheetBody(modifier: Modifier = Modifier) {
    PlaceholderBody(
        "Blending two library patterns end-to-end, or layering one on top of another, is coming. Today, " +
            "the Navigator in Make already fills a graded family between two motions or textures — a " +
            "close cousin of this.",
        modifier,
    )
}


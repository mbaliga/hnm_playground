package dev.hnm.workbench.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.ExportPanel
import dev.hnm.workbench.ui.components.FeedbackSheet
import dev.hnm.workbench.ui.components.ImportPanel
import dev.hnm.workbench.ui.components.SettingsPanel
import dev.hnm.workbench.ui.components.WalkthroughCard
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Renders whatever [nav] hosts — the tab shell or the pushed editor, via [content] — and, on top of
 * that, [nav]'s active [Sheet] (if any) as a bottom-anchored card over a scrim.
 *
 * Deliberately not `ModalBottomSheet`: that API's animated [androidx.compose.material3.SheetState]
 * doesn't settle inside a single headless-render-test frame (see `PreviewRenderTest`), and every sheet
 * this app needs today just has to *exist* and *render* correctly, not spring into place. A plain
 * scrim + card gets both without the animation risk.
 */
@Composable
fun SheetHost(
    nav: WorkbenchNavState,
    state: EditorState,
    modifier: Modifier = Modifier,
    onShipToClackpad: ((role: String) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        content()

        val sheet = nav.sheet
        if (sheet != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { nav.dismissSheet() },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(WorkbenchColors.Screen)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(sheet.title, color = WorkbenchColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Close",
                        color = WorkbenchColors.Red,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { nav.dismissSheet() },
                    )
                }
                Box(Modifier.padding(top = 12.dp)) {
                    SheetContent(sheet, state, nav, onShipToClackpad)
                }
            }
        }
    }
}

/** Dispatches to the panel (real or placeholder) for each [Sheet] route. See [Sheet]'s kdoc. */
@Composable
private fun SheetContent(
    sheet: Sheet,
    state: EditorState,
    nav: WorkbenchNavState,
    onShipToClackpad: ((role: String) -> Unit)?,
) {
    when (sheet) {
        Sheet.LEARN -> WalkthroughCard(state)
        Sheet.IMPORT -> ImportPanel(state)
        Sheet.SHIP -> Column {
            SheetLinkRow("View raw JSON only") { nav.openSheet(Sheet.JSON) }
            ExportPanel(state, onShip = onShipToClackpad)
            ShipConfirmRow(state)
        }
        Sheet.SETTINGS -> Column {
            SettingsPanel(state)
            HorizontalDivider(color = WorkbenchColors.Grid, modifier = Modifier.padding(vertical = 12.dp))
            SheetLinkRow("How this works") { nav.openSheet(Sheet.LEARN) }
            HorizontalDivider(color = WorkbenchColors.Grid)
            SheetLinkRow("Send feedback") { nav.openSheet(Sheet.FEEDBACK) }
        }
        Sheet.JSON -> JsonSheetBody(nav)
        Sheet.COMBINE -> CombineSheetBody()
        Sheet.FEEDBACK -> FeedbackSheet(state)
    }
}

/**
 * Closes the loop on the beginner ladder's "ship one" rung: there's no cross-platform clipboard/file-save
 * API wired into `ui/commonMain` yet (same gap [dev.hnm.workbench.ui.components.LibraryOptionsMenu]'s
 * "Export WAV…" stub calls out), so rather than silently faking a copy/save this is an honest, explicit
 * "yes, I used this" confirmation — same self-reported shape as ticking off a paper checklist. Sending a
 * pattern to Clackpad (`LibraryOptionsMenu`'s "Send to Clackpad") also marks this, since that one *is* a
 * real, working export.
 */
@Composable
private fun ShipConfirmRow(state: EditorState, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.onboarding.hasShippedOne) {
            Text("✓ Shipped — you've exported a pattern out of the workbench.", color = WorkbenchColors.InkDim, fontSize = 11.sp)
        } else {
            Text("Copied this into an app, or sent it to Clackpad?", color = WorkbenchColors.InkDim, fontSize = 11.sp)
            Text(
                "Mark as shipped ✓",
                color = WorkbenchColors.Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { state.markShipped() },
            )
        }
    }
}

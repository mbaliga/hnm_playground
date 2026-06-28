package dev.hnm.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Import existing Apple AHAP content into the editor — the "borrow from the ecosystem" path. AHAP is a
 * publicly-documented open JSON format with a large body of existing assets (game effects, sample packs,
 * web designers like Captain AHAP), so pasting one gives a head start over authoring from scratch.
 */
@Composable
fun ImportPanel(state: EditorState, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⇲ Import AHAP", color = WorkbenchColors.Red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("paste an Apple .ahap to bring it in", color = WorkbenchColors.Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp, max = 160.dp),
            placeholder = { Text("{ \"Version\": 1, \"Pattern\": [ … ] }", fontSize = 11.sp, color = WorkbenchColors.Muted) },
            textStyle = TextStyle(color = WorkbenchColors.OnSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = WorkbenchColors.Surface,
                unfocusedContainerColor = WorkbenchColors.Surface,
                focusedIndicatorColor = WorkbenchColors.Red,
                unfocusedIndicatorColor = WorkbenchColors.Grid,
                cursorColor = WorkbenchColors.Red,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { state.importAhap(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Red),
            ) {
                Text("Import", fontSize = 13.sp)
            }
            state.importMessage?.let { msg ->
                Text(msg, color = WorkbenchColors.Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(WorkbenchColors.SurfaceVariant)
                .padding(8.dp),
        ) {
            Text(
                "AHAP is open & documented — a continuous event becomes a sustained haptic, a transient a tap; " +
                    "intensity & sharpness carry over. Audio events are skipped.",
                color = WorkbenchColors.Muted,
                fontSize = 10.sp,
            )
        }
    }
}

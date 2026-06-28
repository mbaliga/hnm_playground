package dev.hnm.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors
import kotlinx.coroutines.launch

private val EXAMPLES = listOf(
    "urgent alert", "gentle tick", "metal tap", "rough texture",
    "heartbeat", "soft long buzz", "three quick taps", "settle",
)

private val EDIT_EXAMPLES = listOf("make it softer", "sharper", "longer", "more intense", "slower")

/**
 * The AI assistant: describe a feel in plain words and it synthesizes (or edits) the pattern, then
 * explains what it did. This is the front door of the tool — you don't need to know the IR to author
 * something good. Powered by [EditorState.generator] (on-device by default; cloud when wired).
 */
@Composable
fun AssistantPanel(state: EditorState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(state.lastPrompt) }

    fun run(prompt: String) {
        text = prompt
        scope.launch { state.generate(prompt) }
    }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦ Assistant", color = WorkbenchColors.Red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("describe what you want to feel", color = WorkbenchColors.InkDim, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("e.g. urgent alert, metal tap, make it softer", fontSize = 12.sp, color = WorkbenchColors.Muted) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = WorkbenchColors.OnSurface, fontSize = 13.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = WorkbenchColors.Surface,
                    unfocusedContainerColor = WorkbenchColors.Surface,
                    focusedIndicatorColor = WorkbenchColors.Red,
                    unfocusedIndicatorColor = WorkbenchColors.Grid,
                    cursorColor = WorkbenchColors.Red,
                ),
            )
            Button(
                onClick = { run(text) },
                enabled = !state.isGenerating && text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Red),
                modifier = Modifier.height(52.dp),
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(Modifier.height(16.dp), color = WorkbenchColors.Background, strokeWidth = 2.dp)
                } else {
                    Text("Generate", fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Try:", color = WorkbenchColors.InkDim, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(EXAMPLES) { ex -> ExampleChip(ex, accent = false) { run(ex) } }
        }
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(EDIT_EXAMPLES) { ex -> ExampleChip(ex, accent = true) { run(ex) } }
        }

        // The assistant's explanation — this is the running "why" that makes the tool teachable.
        state.assistantMessage?.let { msg ->
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(WorkbenchColors.SurfaceVariant)
                    .padding(10.dp),
            ) {
                Text(msg, color = WorkbenchColors.OnSurface, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ExampleChip(label: String, accent: Boolean, onClick: () -> Unit) {
    val border = if (accent) WorkbenchColors.Haptic else WorkbenchColors.Primary
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(WorkbenchColors.Surface)
            .clickable { onClick() }
            .heightIn(min = 28.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = border, fontSize = 11.sp)
    }
}

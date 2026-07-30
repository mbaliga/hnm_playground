package dev.hnm.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/** What kind of feedback this is — only shapes the subject line of the composed message. */
enum class FeedbackCategory(val label: String) {
    BUG("Bug"),
    IDEA("Idea"),
    QUESTION("Question"),
    OTHER("Other"),
}

/**
 * The Feedback sheet's content (`Sheet.FEEDBACK`, wired in `nav/SheetHost.kt`): compose a note, see
 * exactly what it will say, then hand it off yourself. There is no submit button that calls out
 * anywhere — no backend, and this sheet never makes a network request of its own. Every action here
 * ends the same way: text lands on the clipboard (or, if [onShare] is wired by a host, on the platform
 * share sheet) for *you* to paste into an email, an issue, or a chat — the same "honest, explicit
 * hand-off" shape as the Ship sheet's own confirm-you-used-it row.
 *
 * [onShare] is an optional host-injected capability, mirroring [EditorState.player] and
 * [EditorState.generator]: absent a host wiring a real platform share intent, the sheet still works
 * fully via the two clipboard-based hand-offs below, which need no host support at all.
 */
@Composable
fun FeedbackSheet(
    state: EditorState,
    modifier: Modifier = Modifier,
    onShare: ((subject: String, body: String) -> Unit)? = null,
) {
    var category by remember { mutableStateOf(FeedbackCategory.IDEA) }
    var body by remember { mutableStateOf("") }
    var handoffMessage by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    val subject = "Haptics + Audio Workbench feedback: ${category.label}"
    val message = buildFeedbackMessage(category, body, state)
    val mailtoLink = "mailto:?subject=${percentEncode(subject)}&body=${percentEncode(message)}"

    Column(modifier.fillMaxWidth()) {
        Text(
            "Composed on this device only — nothing is sent anywhere until you hand it off yourself below.",
            color = WorkbenchColors.InkDim,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FeedbackCategory.entries.forEach { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = c; handoffMessage = null },
                    label = { Text(c.label, fontSize = 11.sp) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = body,
            onValueChange = { body = it; handoffMessage = null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            placeholder = { Text("What happened, or what would help?", fontSize = 12.sp, color = WorkbenchColors.Muted) },
            textStyle = TextStyle(color = WorkbenchColors.OnSurface, fontSize = 13.sp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = WorkbenchColors.Surface,
                unfocusedContainerColor = WorkbenchColors.Surface,
                focusedIndicatorColor = WorkbenchColors.Red,
                unfocusedIndicatorColor = WorkbenchColors.Grid,
                cursorColor = WorkbenchColors.Red,
            ),
        )

        Spacer(Modifier.height(12.dp))
        Text("Preview — exactly what gets copied", color = WorkbenchColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(WorkbenchColors.SurfaceVariant)
                .padding(10.dp),
        ) {
            Text(message, color = WorkbenchColors.OnSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(message))
                handoffMessage = "Copied — paste it into an email, issue, or chat."
            }) {
                Text("Copy text", fontSize = 12.sp)
            }

            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(mailtoLink))
                handoffMessage = "Copied a mailto: link — paste it into your browser's address bar to open your mail app, pre-filled."
            }) {
                Text("Copy mailto link", fontSize = 12.sp)
            }

            if (onShare != null) {
                Button(
                    onClick = {
                        onShare(subject, message)
                        handoffMessage = "Handed off to the share sheet."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Red),
                ) {
                    Text("Share…", fontSize = 12.sp)
                }
            }
        }

        handoffMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = WorkbenchColors.Red, fontSize = 11.sp)
        }
    }
}

/** The exact text every hand-off path sends — composed once so Copy/mailto/Share can never diverge. */
private fun buildFeedbackMessage(category: FeedbackCategory, body: String, state: EditorState): String = buildString {
    append(body.ifBlank { "(nothing written yet)" })
    append("\n\n— context —\n")
    append("Category: ${category.label}\n")
    append("Pattern: ${state.pattern.name}\n")
    append("Target: ${state.capabilities.actuatorType}")
}

/**
 * Minimal RFC 3986 percent-encoder for the `mailto:` link. Hand-rolled rather than `java.net.URLEncoder`
 * because `ui/commonMain` stays free of `java.*` so the same source compiles for every Compose target
 * this module ships (see the rest of `ui/commonMain` — no `java.*` import appears anywhere in it).
 */
private fun percentEncode(s: String): String = buildString {
    for (byte in s.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val ch = code.toChar()
        if (code < 128 && (ch.isLetterOrDigit() || ch in "-_.~")) {
            append(ch)
        } else {
            append('%')
            append(code.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

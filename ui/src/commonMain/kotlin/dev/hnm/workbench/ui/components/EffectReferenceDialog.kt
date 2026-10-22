package dev.hnm.workbench.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.library.EffectAttribution

@Composable
fun EffectReferenceDialog(pattern: HapticAudioPattern, onClose: () -> Unit) {
    val info = EffectAttribution.from(pattern) ?: return
    val uri = LocalUriHandler.current
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(pattern.name) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(info.description)
                Text(info.note)
                Text("Design references", style = MaterialTheme.typography.titleSmall)
                info.sources.forEach { source ->
                    Text(source.title, style = MaterialTheme.typography.labelLarge)
                    if (source.quote.isNotBlank()) Text("“${source.quote}”")
                    TextButton(onClick = { runCatching { uri.openUri(source.url) }.onFailure { error = "Could not open a browser. You can copy the address below." } }) { Text("Open source") }
                    SelectionContainer { Text(source.url, style = MaterialTheme.typography.bodySmall) }
                }
                Text("References describe the inspiration. Your edits can change the timing and feel; hardware also affects playback.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

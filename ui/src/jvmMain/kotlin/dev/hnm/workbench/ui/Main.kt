package dev.hnm.workbench.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.hnm.workbench.ui.model.EditorState
import org.jetbrains.skia.Image

/** Desktop entrypoint: `./gradlew :ui:run`. Hosts the shared [WorkbenchApp] in a window. */
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1180.dp, 820.dp))
    // Light or dark app icon to match the OS appearance (the Android launcher icon does the same via
    // values-night). Where the OS theme can't be read, this falls back to the light icon.
    val darkOs = isSystemInDarkTheme()
    val icon = remember(darkOs) { AppIcon.painter(dark = darkOs) }
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "HAfx Studio",
        icon = icon,
    ) {
        val state = remember { EditorState() }
        // A different procedural splash motif each launch (seed varies with wall-clock).
        val seed = remember { (System.currentTimeMillis() / 500L).toInt() }
        WorkbenchWithSplash(state, seed = seed)
    }
}

/** The HAfx Studio app icon: 512 px renders of docs/brand/hafx-studio-icon-{light,dark}.svg. */
internal object AppIcon {
    fun painter(dark: Boolean): Painter {
        val path = if (dark) "/icons/hafx-studio-icon-dark.png" else "/icons/hafx-studio-icon-light.png"
        val bytes = checkNotNull(javaClass.getResourceAsStream(path)) { "missing app icon $path" }
            .use { it.readBytes() }
        return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
    }
}

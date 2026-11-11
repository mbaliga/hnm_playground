package dev.hnm.workbench.ui

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

/** The desktop window icon resources are on the classpath and decode (light + dark). */
class AppIconTest {

    @Test
    fun bothIconVariantsLoad() {
        for (dark in listOf(false, true)) {
            assertEquals(Size(512f, 512f), AppIcon.painter(dark).intrinsicSize, "dark=$dark")
        }
    }
}

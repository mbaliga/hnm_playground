package dev.hnm.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hnm.workbench.ui.theme.TextureShaders
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * A surface component with material texture effects.
 * Combines procedural shaders with optional image textures for premium feel.
 */
@Composable
fun TexturedSurface(
    modifier: Modifier = Modifier,
    baseColor: Color = WorkbenchColors.Surface,
    texture: TextureType = TextureType.BRUSHED_METAL,
    shadowElevation: Dp = 4.dp,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .background(baseColor)
    ) {
        // Apply procedural texture overlay based on type
        when (texture) {
            TextureType.BRUSHED_METAL -> {
                // Horizontal line pattern with subtle highlights
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x0CFFFFFF),
                                    Color(0x00FFFFFF)
                                )
                            )
                        )
                )
            }

            TextureType.CARBON_FIBER -> {
                // Weave pattern overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    Color(0x0DFFFFFF),
                                    Color(0x00FFFFFF),
                                    Color(0x05000000)
                                )
                            )
                        )
                )
            }

            TextureType.MATTE_FABRIC -> {
                // Soft fabric finish
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x02FFFFFF),
                                    Color(0x00FFFFFF)
                                )
                            )
                        )
                )
            }

            TextureType.LEATHER -> {
                // Premium leather-like finish
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    Color(0x08FFFFFF),
                                    Color(0x00FFFFFF)
                                )
                            )
                        )
                )
            }

            TextureType.NONE -> {} // No texture overlay
        }

        // Content layer
        Box(modifier = Modifier.matchParentSize()) {
            content()
        }
    }
}

enum class TextureType {
    BRUSHED_METAL,
    CARBON_FIBER,
    MATTE_FABRIC,
    LEATHER,
    NONE,
}

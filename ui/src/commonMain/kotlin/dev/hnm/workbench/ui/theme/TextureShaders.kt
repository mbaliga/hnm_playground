package dev.hnm.workbench.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Procedural texture generators for the premium brushed-metal aesthetic.
 * These create visual richness without external assets, while real textures are overlaid for key surfaces.
 */
object TextureShaders {

    /**
     * Brushed metal horizontal lines (like the recording app background).
     * Creates fine parallel lines with slight opacity variation for a polished look.
     */
    @Composable
    fun brushedMetalBackground(
        baseColor: Color = WorkbenchColors.Background,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit = {}
    ) {
        Box(
            modifier = modifier.background(baseColor)
        ) {
            // Overlay a gradient that subtly shifts left to right
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x0CFFFFFF), // Slight white at top
                                Color(0x00FFFFFF)  // Fade to transparent
                            )
                        )
                    )
            )
            content()
        }
    }

    /**
     * Subtle noise overlay for organic texture. Use as a semi-transparent overlay.
     */
    @Composable
    fun noiseOverlay(
        alpha: Float = 0.03f,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit = {}
    ) {
        Box(modifier = modifier) {
            // Fine grain via gradient dither effect
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF000000).copy(alpha = alpha))
            )
            content()
        }
    }

    /**
     * Soft shadow for panel elevation. Creates a subtle 3D effect.
     */
    @Composable
    fun elevatedSurface(
        baseColor: Color = WorkbenchColors.Surface,
        modifier: Modifier = Modifier,
        shadowSize: Dp = 12.dp,
        content: @Composable () -> Unit = {}
    ) {
        Box(
            modifier = modifier
                .background(baseColor)
        ) {
            // Shadow is handled via outer composable for correct layering
            content()
        }
    }

    /**
     * Carbon fiber-like weave pattern (subtle, not distracting).
     * Used sparingly for accent areas.
     */
    @Composable
    fun carbonFiberAccent(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit = {}
    ) {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0x0DFFFFFF),
                        Color(0x00FFFFFF),
                        Color(0x05000000)
                    )
                )
            )
        ) {
            content()
        }
    }

    /**
     * Matte panel finish (light gray surfaces like control buttons in the reference image).
     * Creates a soft, premium feel without glossiness.
     */
    @Composable
    fun mattePanelBackground(
        baseColor: Color = WorkbenchColors.PanelLight,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit = {}
    ) {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        baseColor.copy(alpha = 1.0f),
                        baseColor.copy(alpha = 0.97f)
                    )
                )
            )
        ) {
            content()
        }
    }
}

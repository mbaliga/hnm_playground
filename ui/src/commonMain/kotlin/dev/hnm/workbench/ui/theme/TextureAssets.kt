package dev.hnm.workbench.ui.theme

/**
 * Royalty-free texture sources for the premium dark UI aesthetic.
 * All sources use CC0 or equivalent free-to-use licenses.
 *
 * SETUP INSTRUCTIONS:
 * 1. Download textures from the provided sources
 * 2. Place in: ui/src/commonMain/resources/drawable-mdpi/ (Android)
 *              or ui/src/commonMain/resources/textures/ (JVM)
 * 3. Use TextureAssets.load() to reference them
 *
 * Recommended Free Texture Sources:
 * - Brushed Metal: CG Bookcase (cgbookcase.com/textures)
 *   └─ Search for "Brushed Metal Tiles" or "Brushed Iron"
 *   └─ Download: 2K resolution, any variant
 * - Carbon Fiber: Share Textures (sharetextures.com) or Unsplash
 *   └─ Seamless, PBR-ready variants preferred
 * - Soft Fabric: AI Textured (aitextured.com) - Cotton/Linen matte
 *   └─ 1K-2K PNG, matte finish variants
 * - Black Leather: CC0 Textures (cc0-textures.com/c/leather)
 *   └─ 4K available, multiple black variants
 */
object TextureAssets {
    // Texture reference paths (update to actual file locations after download)
    object Paths {
        // Background base materials
        const val BRUSHED_METAL_DARK = "drawable/texture_brushed_metal_dark"
        const val CARBON_FIBER = "drawable/texture_carbon_fiber"

        // Surface materials
        const val FABRIC_MATTE = "drawable/texture_fabric_matte"
        const val LEATHER_BLACK = "drawable/texture_leather_black"

        // Accent materials
        const val WOVEN_PATTERN = "drawable/texture_woven_pattern"
    }

    /**
     * Material texture specifications for the UI aesthetic.
     * Each material defines its recommended opacity, blur, and blend mode.
     */
    data class TextureSpec(
        val name: String,
        val path: String,
        val opacity: Float = 0.7f,
        val blurRadius: Int = 0,
        val description: String,
    )

    val BRUSHED_METAL = TextureSpec(
        name = "Brushed Metal Dark",
        path = Paths.BRUSHED_METAL_DARK,
        opacity = 0.85f,
        blurRadius = 8,
        description = "Dark aluminum/brushed metal for main background. Source: CG Bookcase",
    )

    val CARBON_FIBER = TextureSpec(
        name = "Carbon Fiber",
        path = Paths.CARBON_FIBER,
        opacity = 0.4f,
        blurRadius = 2,
        description = "Weave pattern for accent depth. Source: Share Textures / Unsplash",
    )

    val FABRIC_MATTE = TextureSpec(
        name = "Matte Fabric",
        path = Paths.FABRIC_MATTE,
        opacity = 0.5f,
        blurRadius = 0,
        description = "Cotton/linen fabric for soft UI panels. Source: AI Textured",
    )

    val LEATHER_BLACK = TextureSpec(
        name = "Black Leather",
        path = Paths.LEATHER_BLACK,
        opacity = 0.6f,
        blurRadius = 4,
        description = "Premium leather accent material. Source: CC0 Textures",
    )
}

/**
 * Download guide and integration steps.
 *
 * Step 1: Download Textures
 * - Brushed Metal: Visit cgbookcase.com/textures, search "Brushed Metal Tiles"
 *   Download the 2K version (free CC0)
 * - Carbon Fiber: Share Textures (sharetextures.com) or Unsplash search
 * - Fabric: aitextured.com, search "Cotton Matte" or "Linen Matte"
 * - Leather: cc0-textures.com/c/leather, download "Black Leather"
 *
 * Step 2: Add to Project
 * - Place in: ui/src/commonMain/resources/drawable/
 * - Rename files to match TextureAssets.Paths constants
 * - Example: texture_brushed_metal_dark.png
 *
 * Step 3: Use in Composables
 * - Create composables that load from resources using Image()
 * - Apply opacity and blur using Modifier.alpha() and .graphicsLayer()
 * - Layer multiple textures for depth effect
 *
 * Step 4: Test
 * - Run: ./gradlew :ui:run (desktop) or install APK (Android)
 * - Verify textures load and render correctly
 */
class TextureIntegrationGuide

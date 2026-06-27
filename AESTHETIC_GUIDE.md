# Premium Dark Brushed-Metal Aesthetic Implementation

This document describes the new visual aesthetic applied to the Haptics + Audio Workbench, inspired by premium recording interfaces and professional audio tools.

## Design Philosophy

**Visual Inspiration:** Premium voice recording app (iOS example)
- Deep dark backgrounds with brushed metal texture
- Red accent color for active/recording states
- Clean, minimal interface with soft shadows
- High contrast for readability
- Professional, sophisticated feel

## Color Palette

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Background | Deep Black | `#0F1013` | Main background, very dark with warmth |
| Surface | Elevated Dark | `#1A1D22` | Panel backgrounds, elevated from base |
| Variant Surface | Secondary Dark | `#252A32` | Tertiary backgrounds |
| Light Panels | Off-White | `#F5F5F5` | Control panels, buttons |
| Accent | Premium Red | `#E74C3C` | Active states, play buttons, recording indicator |
| Text | High Contrast | `#E6E8EC` | Primary text on dark |
| Muted Text | Gray | `#8B90A0` | Secondary text, metadata |
| Grid | Dark Gray | `#323743` | Dividers, grid lines |
| Shadow | Black Transparent | `#4D000000` | Depth and elevation |

## Texture Layers

The aesthetic uses a **layered texture approach**:

1. **Base Layer:** Solid color background
2. **Procedural Layer:** Mathematically generated gradients simulating brushed metal, fabric, etc.
3. **Real Texture Layer** (Optional): Actual material photographs for maximum fidelity

### Procedural Textures

Implemented in `TextureShaders.kt`, these work immediately without external assets:

- **Brushed Metal:** Horizontal line pattern with subtle light highlights
- **Carbon Fiber:** Weave pattern with diagonal flow
- **Matte Fabric:** Soft vertical gradient for cloth-like appearance
- **Leather:** Radial gradient for premium leather finish
- **Noise Overlay:** Fine grain for organic feel

## Real Material Textures (Optional Enhancement)

For maximum premium feel, integrate actual material photographs. All sources are royalty-free (CC0).

### Installation Steps

#### 1. Download Textures

**Brushed Metal (Background)**
- Source: CG Bookcase (https://www.cgbookcase.com/textures)
- Search: "Brushed Metal Tiles" or "Brushed Iron"
- Download: 2K PNG version (free, CC0)
- Recommended variant: Dark aluminum or gunmetal

**Carbon Fiber (Accent)**
- Source: Share Textures (https://www.sharetextures.com) or Unsplash
- Search: "Carbon Fiber Texture" 
- Download: 2K PNG, seamless variant
- License: CC0 or free-to-use

**Soft Fabric (UI Panels)**
- Source: AI Textured (https://www.aitextured.com)
- Search: "Cotton Matte" or "Linen Matte"
- Download: 1K-2K PNG
- License: Free commercial use

**Black Leather (Premium Accents)**
- Source: CC0 Textures (https://cc0-textures.com/c/leather)
- Search: "Black Leather" variants
- Download: 4K PNG available
- License: CC0 (fully unrestricted)

#### 2. Add to Project

```
ui/src/commonMain/resources/drawable/
├── texture_brushed_metal_dark.png
├── texture_carbon_fiber.png
├── texture_fabric_matte.png
└── texture_leather_black.png

androidApp/src/main/res/drawable/
├── texture_brushed_metal_dark.png
├── texture_carbon_fiber.png
├── texture_fabric_matte.png
└── texture_leather_black.png
```

#### 3. Integration Code

Once textures are in place, create composables to load them:

```kotlin
@Composable
fun TexturedBackground(
    texture: TextureAssets.TextureSpec,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.background(Color.Black)) {
        // Load from drawable resources
        Image(
            painter = painterResource(texture.path),
            contentDescription = texture.name,
            modifier = Modifier
                .matchParentSize()
                .alpha(texture.opacity),
            contentScale = ContentScale.Crop
        )
        content()
    }
}
```

#### 4. Apply to Layouts

Use `TexturedSurface` composable:

```kotlin
TexturedSurface(
    baseColor = WorkbenchColors.Background,
    texture = TextureType.BRUSHED_METAL,
    modifier = Modifier.fillMaxSize()
) {
    // Your content here
}
```

## Component Styling

### Buttons
- **Primary Action (Play):** Red background (`#E74C3C`), white text
- **Secondary (Gallery):** Outlined style with muted border
- **Disabled:** 50% opacity

### Panels
- **Header Panel:** Elevated surface with soft shadow (4dp)
- **Content Panels:** Surface variant with subtle texture
- **Input Panels:** Light gray background for contrast

### Text Hierarchy
- **Title:** 18sp, semibold, high contrast white
- **Subtitle:** 14sp, regular, white
- **Metadata:** 12sp, regular, muted gray
- **Captions:** 11sp, regular, muted gray

### Shadows & Elevation
- **Level 1 (Subtle):** 2dp blur, 20% opacity
- **Level 2 (Moderate):** 4dp blur, 25% opacity
- **Level 3 (Elevated):** 8dp blur, 30% opacity
- **Level 4 (Prominent):** 12dp blur, 35% opacity

## Responsive Design

### Desktop (> 720dp)
- Two-column layout (design | tooling)
- Full texture richness
- Larger shadows and elevation

### Phone (< 720dp)
- Single scrolling column
- Procedural textures (real textures optional for performance)
- Compact spacing (14dp padding)
- Smaller shadows for clarity

## Theme Application

### Compose Multiplatform (UI Module)
```kotlin
@Composable
fun WorkbenchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = WorkbenchColors.Accent,      // Red
            background = WorkbenchColors.Background,
            surface = WorkbenchColors.Surface,
        ),
        content = content,
    )
}
```

### Android Native (MainActivity)
```kotlin
val root = LinearLayout(this).apply {
    setBackgroundColor(Color.parseColor("#0F1013"))
}
```

## Performance Considerations

**Procedural Textures (Current - Lightweight)**
- No asset files needed
- Calculated in-memory gradients
- GPU-accelerated by Compose
- ~2KB memory per surface

**Real Texture Integration (Future - Premium)**
- Bitmap textures: ~2-4MB per 2K image
- Recommended: Load on-demand, cache aggressively
- Consider: WebP format (~50% smaller than PNG)
- Performance: Minimal impact on modern devices (2GB+ RAM)

## Testing Checklist

- [ ] Colors render correctly on both desktop and mobile
- [ ] Shadows visible without flattening content
- [ ] Text contrast passes WCAG AA (4.5:1 minimum)
- [ ] Procedural textures don't increase memory usage
- [ ] Red accent clearly indicates interactive elements
- [ ] Responsive layout works on screens < 720dp
- [ ] Headless render tests (PreviewRenderTest) still pass

## Future Enhancements

1. **Theme Customization Panel** - Allow users to adjust accent colors
2. **Light/Dark Mode Toggle** - Implement light aesthetic option
3. **Animated Textures** - Subtle animated grain/shimmer
4. **Material Shadows** - Use Android Material Design 3 elevation
5. **Animation System** - Smooth transitions between states with material motion

## Troubleshooting

### Textures Not Loading
- Check file paths match `TextureAssets.Paths` constants
- Verify resources are in correct drawable directories
- Ensure PNG files are not corrupted

### Low Contrast Text
- Verify text color is `#E6E8EC` on dark backgrounds
- Test with accessibility tools (Android Accessibility Scanner)
- Increase text size if needed for readability

### Performance Issues
- Reduce texture opacity (use 0.5-0.7 range)
- Apply blur filter to reduce texture detail
- Switch to procedural-only for low-end devices
- Use `LocalComposeUiToolingPreviewConfiguration` to detect preview mode

## Related Files

- `Theme.kt` - Color definitions
- `TextureShaders.kt` - Procedural texture generators
- `TextureAssets.kt` - Texture asset references
- `TexturedSurface.kt` - Reusable textured surface component
- `WorkbenchApp.kt` - Main layout with aesthetic applied
- `MainActivity.kt` - Android player with aesthetic applied

## References

- Google Material Design 3: https://material.io/design
- Compose Shadows & Elevation: https://developer.android.com/develop/ui/compose/graphics/draw/overview
- Professional audio UI inspiration: Waveform Editor, Pro Tools, Logic Pro X

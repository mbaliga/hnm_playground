# HAfx Studio app icon

| Light (default) | Dark |
|---|---|
| <img src="hafx-studio-icon-light.svg" alt="HAfx Studio icon, light" width="160"> | <img src="hafx-studio-icon-dark.svg" alt="HAfx Studio icon, dark" width="160"> |

## Files

- `source/hafx-studio-icon-{light,dark}.png`: the icon as delivered (1100 × 1034 px). This is the reference.
- `hafx-studio-icon-{light,dark}.svg`: vector masters rebuilt from those PNGs on a square 1100-unit
  artboard. The artwork is centred on it, with the PNG's 60-unit corner radius.
  - **H** and **A** are exact geometry measured from the PNG to sub-pixel edges. The A's arch is a
    130-unit semicircle.
  - The italic ***fx*** is traced.
  - The circled **ASOC** mark is redrawn as a ring plus Inter Light letters, because it is only ~20 px
    across in the source.

  Rendered back at 1100 px, the masters match the PNGs to within anti-aliasing.

## Where it is used

| Target | Asset | Light / dark |
|---|---|---|
| Android launcher | `androidApp/src/main/res/mipmap-anydpi/ic_launcher.xml`: an adaptive icon made of a colour background and `drawable/ic_launcher_foreground.xml`. The same glyphs are the Android 13+ themed-icon `monochrome` layer. | `values/` vs `values-night/ic_launcher_colors.xml` |
| Desktop window / taskbar | `ui/src/jvmMain/resources/icons/hafx-studio-icon-{light,dark}.png` (512 px renders of the SVGs) | `isSystemInDarkTheme()` in `ui/.../Main.kt` |
| Linux packages (`packageDeb`, `packageAppImage`) | the light PNG, via `nativeDistributions.linux.iconFile` | one icon per package |

## Adaptive-icon geometry

The foreground vector reuses the masters' path data in artwork units. Its `<group>` does two things:

- It scales the artwork's 1100-unit width onto the 72 dp visible viewport (scale 72/1100).
- It centres the artwork's middle point (550, 517) on the 108 dp canvas (translate 18, 20.16).

The farthest glyph point is 29.6 dp from the centre. That is inside the 66 dp safe zone, so no launcher
mask clips the artwork.

## Notes

- **Dark launcher icon.** Android has no official dark launcher icon. The `-night` colours apply when a
  launcher loads icons while the system is in dark mode. Launchers cache icons, so after switching modes
  the icon may not change until the launcher reloads it.
- **Themed icons.** With Android 13+ themed icons switched on, the launcher shows the tinted `monochrome`
  layer instead of the light or dark icon.
- **Original vector.** If the original vector (Figma or SVG) turns up, use it in place of the masters and
  the foreground `pathData`. Keep the same artboard coordinates and the transforms above still hold.

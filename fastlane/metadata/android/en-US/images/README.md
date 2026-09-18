# images/

Sizes and check commands: `Personal-Tracker/store/ASSET_SPECS.md`.

## Needed, none present yet

- `icon.png` 512x512 no alpha · `featureGraphic.png` 1024x500 no alpha
- `phoneScreenshots/` — 2 to 8, 1080x1920, no alpha.

## Shoot these

1. The timeline editor holding a real, non-trivial effect. An empty timeline sells
   nothing.
2. A breakpoint curve mid-drag, so the shaping is visibly direct manipulation.
3. The export dialog, which is the half that makes this a tool rather than a toy.

A haptics app is the hardest thing in the house to screenshot, because the product
is a sensation. Compensate by showing the *authoring*: the frames should look like
an editor with real work in it.

```sh
adb exec-out screencap -p > shot.png
magick shot.png -background black -alpha remove -alpha off phoneScreenshots/01.png
```

# Home-screen widgets

The Android player (`:androidApp`) ships six standard Android App Widgets (`AppWidgetProvider` +
`appwidget-provider` XML + `RemoteViews`), so any launcher that hosts App Widgets can place them: Pixel
Launcher, One UI, Nova, Orrery Home. Each one is resizable and draws a collapsed, small, medium and large
layout. They add no library, no permission, no network access and no telemetry.

> **Status: built and JVM-tested here, not yet seen on a phone.** This environment has no device or
> emulator. How they render, resize, read aloud and feel on a real phone and launcher is
> owner-verified (list at the end).

| Widget | Template | Default size | Shows | Tap |
|---|---|---|---|---|
| Effect pads | `pads` | 4×2 | 2 / 4 / 6 built-in patterns as buttons | a pad: feel it |
| Pattern of the day | `card` | 4×2 | one library pattern per local day | feel it, or open it in the workbench |
| Pattern shape | `image` | 4×2 | intensity and sharpness of the pattern last felt, drawn | open it in the workbench |
| Recently felt | `list` | 3×3 | the last 1 / 3 / 5 patterns felt | a row: feel it again |
| This phone's haptics | `stat` | 3×2 | what the vibrator reports (primitives, amplitude, effects) | the gallery (diagnostics + self-test) |
| Library at a glance | `list` | 3×3 | how many patterns, and how this phone plays them | the gallery |

## How they work

- **Data.** Everything comes from data the app already has. The feel-test gallery's list moved from
  `MainActivity` into `core` as `FeelTestCatalog` (`core/.../library/FeelTestCatalog.kt`): the same 57
  entries in the same order, each with a stable id (a slug of its name). The gallery and the widgets read
  the same list. Device facts come from the player's own probe (`AndroidHaptics.probe`/`supportedEffects`).
  The only new state is the "recently felt" list (below).
- **Pure logic in `core`.** Size choice, pads, the pattern of the day, the shape sketch, the recently-felt
  codec, render-path counts and the phone summary live in `core/.../widget/` with no platform imports.
  They are tested in `core/src/commonTest` (`WidgetLogicTest`, `FeelTestCatalogTest`), which CI runs as
  part of `:core:jvmTest`. The Android side (`androidApp/.../widget/`) only maps them to `RemoteViews`.
- **Sizes.** Each widget sends one size-mapped `RemoteViews` (Android 12+, and `minSdk` is 31). The
  launcher picks the largest layout that fits without waking the app. Thresholds (dp, `WidgetTemplate`)
  follow the platform cell formula (n cells ≈ 70n − 30 dp):

  | Template | small | medium | large | below small |
  |---|---|---|---|---|
  | `pads` | 110×110 | 250×110 | 250×180 | collapsed |
  | `card` | 110×40 | 250×110 | 250×180 | collapsed |
  | `image` | 110×40 | 180×110 | 250×180 | collapsed |
  | `list` | 110×110 (1 row) | 180×180 (3 rows) | 180×276 (5 rows) | collapsed |
  | `stat` | 110×40 | 180×110 | 250×180 | collapsed |

  Collapsed is one tappable summary (a title, a subtitle, one target). Touch targets are at least 48 dp.
  Secondary text is 12 sp and body text (a card's description) 14 sp, every line has a maximum and
  ellipsizes, and layouts use start/end. A pad's sparkline is fixed left-to-right (`textDirection="ltr"`):
  its block characters are bidi-neutral, so an RTL locale would otherwise draw time backwards.
- **TalkBack.** Where a whole widget is one tap target, TalkBack reads only that target's description,
  not the text inside it. So each such description carries what that size shows (the date, name,
  description, shape numbers and "Plays here" line of the pattern of the day; the felt time of the shape;
  the supported and missing primitives on the large phone widget; the empty message) and ends with what
  the tap does. Rows and buttons have their own descriptions; a recently-felt row names its section and
  when it was felt.
- **Look.** At night the widgets use the app's own recorder2 colours: screen `#141210` and ink `#DDDBD6`.
  Their dim ink is `#A39F97`, lighter than the app's InkDim `#5C5A56` (2.7:1 on the screen colour, too
  faint for 12 sp text; `#A39F97` is 7.1:1). The app itself is all-dark and has no day palette, so the
  day set is **new to the widgets**: a warm paper `#EFECE6` with the app's ShellTop `#1C1A18` as ink
  (`res/values*/colors.xml`). It needs the owner's sign-off; to keep the widgets dark by day too, copy
  `values-night/colors.xml` into `values/`. No colour carries a meaning. The player's red accent is not used. Shapes and words carry meaning: ● yes / ○ no,
  ▮ bars for intensity, ╌ a dashed line for sharpness, ▶ for "feel again". Corners use the system widget
  radius.
- **Updates are pushed.** `updatePeriodMillis` is 0 everywhere. `HnmWidgets.refresh` redraws the
  providers whose data changed. `HnmWidgets.recordFelt` (called from the gallery's Play and from
  `FeelActivity`) records the play and redraws "Recently felt" and "Pattern shape". The pattern of the day
  sets one inexact, non-waking alarm (`AlarmManager.set`, no permission) for the next local midnight.
  Providers also redraw on `TIME_SET` / `TIMEZONE_CHANGED` (the widgets that print times or dates) and on
  `LOCALE_CHANGED` (all of them). Rendering runs off the main thread inside `goAsync()`. If a render
  fails, the widget says "Couldn't draw this widget" and its tap opens the app.
- **Feeling a pattern from the home screen.** On Android 12+, a touch-feedback vibration from an app in
  the background is dropped. So a pad or a "Feel it" button does not play from a broadcast. It opens
  `FeelActivity`, a small translucent activity: not exported, no recents entry, its own task. It plays the
  pattern exactly as the gallery's Play does (haptics and the pattern's sound, through
  `AndroidPatternPlayer`), shows "Feeling <name>", records the play, and finishes about 0.4 s after the
  pattern ends (between 0.7 s and 5 s). A tap anywhere ends it early. It accepts only `ACTION_FEEL` with a
  catalogue id; anything else shows "That pattern isn't in this version of the app." and finishes.
- **Opening a pattern.** "Open", the pattern-of-the-day card and the shape image start the workbench
  through `WidgetOpenPattern`, a **non-exported** `<activity-alias>` of `WorkbenchActivity`, with a
  catalogue id in `EXTRA_PATTERN_ID` (`NEW_TASK | CLEAR_TOP | SINGLE_TOP`). The workbench loads a pattern
  only when it was started through that alias, so another app starting the exported launcher activity
  can't replace the pattern being edited. A running workbench keeps its session and its in-memory library
  and loads the pattern into the editor. **This replaces the pattern being edited**, the same as picking
  one from the library or a palette inside the app (none of which ask first). An unknown id is ignored.
- **The workbench stays the task's root.** Every intent that starts the workbench has the launcher's own
  shape (`ACTION_MAIN` + `CATEGORY_LAUNCHER`; the system compares the root's intent without extras or
  flags). So after a widget has started the app, a tap on the app icon brings that task forward instead of
  stacking a second, empty workbench on top. Gallery taps use `PendingIntent.getActivities`: the first
  intent brings the app's task forward as the icon would (or starts it with the workbench as its root),
  the second opens `MainActivity` on top (reusing one already on top). The gallery therefore never becomes
  the root, and Back from it returns to the workbench. `MainActivity` plays its procedural splash on a
  fresh start, as it does from the workbench.
- **Recently felt store.** `RecentlyFeltStore` keeps at most five `id<TAB>millis` lines in
  `noBackupFilesDir/widgets/recently-felt.txt`, so the list never goes to cloud backup. Writes go through
  a temp file and a rename. A missing, oversized (> 16 KB) or damaged file reads as fewer rows or none.
  Only catalogue ids are stored.

## The widgets

### Effect pads (`EffectPadsWidget`, `pads`)
- **Shows:** built-in reference patterns as buttons. Each has its name and its shape as an 8-column text
  sparkline (`▇▇▁▁▁▁▁▁` for Tap). The order is chosen so the smallest size already contrasts a crisp
  single tap with a dull double pulse: Tap, Heartbeat, Confirm, Success, Snap, Notification
  (`EffectPads.DEFAULT_IDS`).
- **Sizes:** small: 2 pads stacked. Medium: a 2×2 grid. Large: a 3×2 grid under an "Effect pads"
  header. Collapsed: "Effect pads / Tap to open the feel-test gallery".
- **Data:** `FeelTestCatalog` + `PatternSketch.sparkline()`. **Refresh:** on placement, update and
  locale change only; the built-ins don't change.
- **Tap:** a pad opens `FeelActivity` for that pattern. **Privacy:** public: built-in names only.
- Only built-ins, as the strategy asks: anything else waits for persisted favourites. The pads can't be
  chosen per widget yet (that needs a configuration activity; not built).

### Pattern of the day (`PatternOfDayWidget`, `card`)
- **Shows:** one entry per local calendar day, cycling through a fixed shuffle of the 42 reference,
  motion, texture and material entries. Each comes round once per 42-day cycle (`PatternOfTheDay`).
  Variations, navigator steps, the buzz and the captured rhythm are left out.
- **Sizes:** small: the date it is for and the name (the whole widget feels it). Medium: the label
  with the date, a two-line description and a "Feel it" button; the rest of the card opens it. Large: adds the drawn shape
  (alt text with its numbers), one line "Plays here: <path> · N haptic events · 0.17 s", and an "Open"
  button.
  Collapsed: the name and the date it is for, tap to feel.
- **Data:** `FeelTestCatalog`, `PatternSketch`, `RenderPaths` against this phone's probe. The
  descriptions are the built-ins' own definitions, the motion and texture enums' descriptions, and each
  material's computed ring-down (`ModalSynth.ringSeconds`).
- **Refresh:** the midnight alarm, plus time/zone/locale changes. The alarm is inexact and can come late
  (Doze, battery managers) or be lost (reboot, force-stop), so the card prints the date it is for at
  every size. The next update after a reboot sets it again.
- **Privacy:** public.

### Pattern shape (`PatternShapeWidget`, `image`)
- **Shows:** the most recently felt pattern, drawn by the app: translucent bars with a solid top edge for
  intensity, and a dashed line (with a square where it starts) for sharpness, over the pattern's length
  (at least 100 ms, so a single tap reads as a tap). It is drawn as a white mask that the ImageView tints
  with the ink colour, so it follows day and night. The alt text carries the numbers: events, length, peak
  intensity, sharpness range.
- **Sizes:** small: the picture alone. Medium: "Last felt · <name>", the picture and a legend
  (▮ intensity ╌ sharpness). Large: adds "2 haptic events over 0.17 s · peak intensity 0.90 · sharpness
  0.20–0.30 · felt 4 Nov, 14:02". Collapsed: the name, tap to open it.
- **Data:** `RecentlyFeltStore` → `FeelTestCatalog` → `PatternSketch` → `ShapeBitmap` (at most 600×240
  px, shared by the three layouts). **Refresh:** after every recorded play; time/zone/locale.
- **Empty:** "Feel a pattern from a pad or the gallery and its shape is drawn here." (tap: gallery). The
  small and collapsed sizes, where that sentence can't fit, say "Nothing felt yet"; the description read
  aloud keeps the full sentence.
- **Tap:** open that pattern in the workbench. **Privacy:** public: catalogue names only.

### Recently felt (`RecentlyFeltWidget`, `list`)
- **Shows:** the last 1 / 3 / 5 catalogue patterns felt (from the gallery's Play, a pad, a row or the
  pattern of the day), newest first, each once, with its gallery section and the date and time it was
  felt ("Reference · 4 Nov, 14:02"). Nothing redraws this widget at midnight, so the date is always
  printed: the text stays true without a redraw.
- **Sizes:** small: 1 row. Medium: 3 rows. Large: 5 rows. Each row is a 48 dp target. Collapsed: the
  latest name, tap to feel it again.
- **Data:** `RecentlyFeltStore`. **Refresh:** after every recorded play; time/zone/locale.
- **Empty:** "Nothing felt yet. Patterns you play from the gallery or a pad appear here."
- **Tap:** a row feels it again; elsewhere opens the gallery.
- **Privacy:** only catalogue entries are ever recorded. Plays in the workbench editor are not recorded,
  because an editor pattern's name can come from the user's own prompt (the assistant names generated
  patterns after it) or from an imported AHAP file, and a home screen is public. No counts, streaks or
  "you haven't…" lines.

### This phone's haptics (`PhoneHapticsWidget`, `stat`)
- **Shows:** how many of the 8 composition primitives the vibrator reports ("8/8"). Then the tier, in the
  player's own honest words: "Native composition primitives", "Amplitude control, no primitives",
  "On/off through the standard API; richer haptics may sit behind a vendor SDK", or "No vibrator
  reported". Many phones keep richer haptics behind a vendor SDK, so on/off is not a verdict on the
  hardware.
- **Sizes:** small: value and label. Medium: adds the "This phone" header and the tier. Large: adds every
  primitive as "● CLICK yes" / "○ SPIN no" and a line for amplitude control and the predefined effects
  reported. Collapsed: value and label.
- **Data:** `AndroidHaptics.probe` + `supportedEffects` → `PhoneGlances`. `AndroidHaptics.actuatorLabel`
  now uses the same tiers, so the gallery's diagnostics and the widget can't disagree. **Refresh:** on
  placement, update and locale change; a phone's actuator doesn't change.
- **Tap:** the gallery (diagnostics, self-test, capability report). **Privacy:** no device model or
  identifier is shown.

### Library at a glance (`LibraryWidget`, `list`)
- **Shows:** "Library · 57 patterns". Then how this phone plays them: "Native primitives 27 / Amplitude
  waveform 30" on a full-primitive phone, or "Predefined effects 27 / Amplitude waveform 30" on an on/off
  one. Each pattern is counted by running the renderer's own schedule against this phone's probe through
  `RenderPaths`, which is the same choice `AndroidHaptics.playPattern` makes. Then the gallery's sections
  with their sizes (Reference 15, Buzz 1, …).
- **Sizes:** small: 1 row. Medium: 3 rows. Large: 5 rows. Collapsed: the header, tap to open the gallery.
- **Data:** `FeelTestCatalog` + `LibraryGlances`. **Refresh:** on placement, update and locale change.
- **Tap:** the gallery. **Privacy:** public.

## Files

- `core/src/commonMain/kotlin/dev/hnm/workbench/core/library/FeelTestCatalog.kt`: the gallery list with
  ids. `MainActivity` now reads it; the names and order are unchanged.
- `core/src/commonMain/kotlin/dev/hnm/workbench/core/widget/`: `WidgetSizing`, `EffectPads` (+
  `PatternOfTheDay`), `PatternSketch`, `RecentlyFelt`, `Glances` (`RenderPaths`, `LibraryGlances`,
  `PhoneGlances`).
- `core/.../dsp/HapticMapping.kt`: `primitiveAsTransient` moved here from a private function in
  `DefaultPatternRenderer`, which now calls it (same values), so the sketch draws primitives as the
  renderer synthesizes them.
- `androidApp/src/main/kotlin/dev/hnm/workbench/android/widget/`: the six providers, `HnmWidgetProvider`
  (shared plumbing), `HnmWidgets` (refresh, midnight alarm), `WidgetIntents`, `RecentlyFeltStore`,
  `ShapeBitmap`, `WidgetText`.
- `androidApp/src/main/kotlin/dev/hnm/workbench/android/FeelActivity.kt`.
- `androidApp/src/main/res/`: `layout/widget_*.xml` (one per template and size, plus loading, collapsed and
  six picker previews), `xml/widget_info_*.xml`, `values/` and `values-night/` colours, strings, styles.
- `AndroidManifest.xml`: six exported widget receivers (exported so launchers can bind them), the
  non-exported `FeelActivity` and the non-exported `WidgetOpenPattern` alias of `WorkbenchActivity`.
- Edited: `MainActivity` (reads the catalogue, records Play), `WorkbenchActivity` (loads
  `EXTRA_PATTERN_ID` when started through the `WidgetOpenPattern` alias, also in `onNewIntent`), `AndroidHaptics` (`playPattern`, `renderingSummary` and `actuatorLabel` use
  `RenderPaths` / `PhoneGlances`; same branches).

## Owner-verify (needs a phone; none of this is confirmed)

1. All six widgets appear in the picker of Pixel Launcher, One UI, Nova and Orrery Home with their
   previews, place at their default sizes, and redraw when resized: collapsed below the template minimum,
   then small, medium and large at the expected cell sizes. The thresholds follow the cell formula; real
   launchers report their own sizes.
2. **A pad's feel:** tapping a pad (and "Feel it", a recent row, the small pattern-of-the-day) plays the
   pattern from the home screen on Android 12+, haptics and sound together, with no visible flash beyond
   the "Feeling …" chip. Rapid taps on different pads replace each other. Back and home behave, and no
   recents entry is left behind.
3. "Open" / the card / the shape image bring up the workbench with the pattern loaded, both cold and with
   the workbench already open (whose library should survive). Gallery taps land on the gallery, and Back
   from there returns to the workbench. **After any widget tap that started the app cold, press Home and
   tap the app icon:** the same task should come forward (the loaded pattern, or the gallery over the
   workbench), not a second, empty workbench.
4. "Recently felt" and "Pattern shape" update within a second or two of a Play in the gallery or a pad.
5. The pattern of the day changes after local midnight. Check how late the inexact alarm lands under
   Doze or an OEM battery manager, and that the next update after a reboot sets it again.
6. Day/night: colours and the tinted shape follow the system switch without the app running.
7. TalkBack reads each pad, row and button, and each whole-widget target's description with what that
   size shows (date, description, shape numbers, primitives, empty message). Layouts survive 1.3× and 2×
   font scale (ellipsized, not clipped) and RTL; in an RTL locale the pad sparklines still run left to
   right. During a pad's feel, TalkBack lands on "Feeling <name>. Tap to stop and go back."
8. The ▁▂▃▄▅▆▇█ sparkline, ● ○ ▮ ╌ ▶ glyphs render in the launcher's font.
9. "This phone's haptics" matches the gallery's diagnostics line on the same phone, and the library's
   render-path counts match what the gallery rows say.
10. The day palette (paper `#EFECE6`, ink `#1C1A18`) is new to the widgets; the owner decides whether to
    keep it or keep the widgets dark by day too.

## Not done

- Choosing which patterns the pads show (a configuration activity), and favourites.
- Recording plays from the workbench editor (privacy, above).
- Lock-screen widgets (not targeted).

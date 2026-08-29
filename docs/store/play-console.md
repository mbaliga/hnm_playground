# Haptics Workbench — Play Console answer sheet

> Only the **deltas** from `Personal-Tracker/store/HOUSE_DEFAULTS.md`.

| | |
|---|---|
| applicationId | `dev.hnm.workbench.android` |
| Version at time of writing | `0.14.0` (versionCode `15`) |
| Category | **Tools** |
| Tags | haptics, vibration, audio, design tool, developer tools |
| Contact email | `haptics@asystemofcells.com` |
| Website | `https://asystemofcells.com/haptics` |
| Privacy policy | `https://asystemofcells.com/haptics/privacy` |

> **The product name is provisional.** The ASOC roster lists this as
> "haptics-workbench" with "product name pending, internally hnm_playground", and
> `NAMES.md` says a rename is owner-gated if it ever productises. This is that
> moment. "Haptics Workbench" is used here because it is what the README calls it
> and it is a usable store title; confirm or replace it before the first upload.
> The **applicationId is permanent after the first production release.**

## Deltas from the house defaults

### Build gating — there is no artifact yet by default
The Android app is gated behind `ENABLE_ANDROID=1` (`CONSTELLATION.md`), so a
default build produces no APK. Nothing to submit until that is resolved for release
builds.

### targetSdk is behind
`targetSdk = 34`. Play raises the required target level every August and will
reject an upload below the current floor. **Bump to 35 or higher before submitting**
and re-run the JVM tests. Check the current requirement at submit time rather than
trusting this line.

### Data safety
**No data collected. No data shared.**

| Question | Answer |
|---|---|
| Collect or share any user data? | **No** |
| Encrypted in transit? | Yes (nothing is transmitted) |
| Deletion? | Users can delete data in the app |

The app has **no `INTERNET` permission**. Say so in the listing; for a developer
tool this is a genuine selling point, not just compliance.

### Permissions
| Permission | Why | Play form? |
|---|---|---|
| `VIBRATE` | The entire product. | No |

One permission, no forms. Along with Crocodyl and Sphere Launcher, this is among the
easiest listings in the house to get through review.

### Content rating
- Category `Utility, Productivity, Communication, or Other`. Expected **Everyone**.

### An honesty note that belongs in the listing
Haptic quality varies enormously by device, and a user on a phone with a poor
actuator will conclude the app is bad. The listing says this plainly rather than
letting a one-star review say it instead. Keep that paragraph.

## F-Droid
- ⛔ **Blocked: no `LICENSE` file** (`CONSTELLATION.md` §2, D-I). An offline
  developer tool with a single permission is close to an ideal F-Droid app.
- No anti-features once licensed.

## Pre-submit checklist

- [ ] Decide the product name and the permanent applicationId.
- [ ] Bump `targetSdk` to Play's current floor.
- [ ] Resolve `ENABLE_ANDROID` for release builds.
- [ ] Add a `LICENSE` file.
- [ ] Screenshots: the timeline editor with a real effect, a breakpoint curve being
      dragged, the export dialog.

# packaging/macos

Everything here is a **disabled template**. The Gradle `Dmg` target (`ui/build.gradle.kts`) builds an
**UNSIGNED** DMG on a macOS host only; nothing in this repo has been run on a Mac.

| File | State |
|---|---|
| `entitlements.plist` | Hardened-runtime template (`allow-jit`); not referenced by the build. |
| `notarize.sh.disabled` | `notarytool` + `stapler` script; secrets from the environment only. |
| `cask.rb.template` | Homebrew cask skeleton with `<PLACEHOLDER>` tokens. |

No `bundleID` is set (a NAMES.md row first, R11).

## OWNER_STEPS (the owner's later work, none of it done)

1. Join the Apple Developer Program; create a Developer ID Application certificate.
2. Decide the bundle id (`com.asoc.<appname>`) and add its NAMES.md row; then set `macOS { bundleID }` in
   `ui/build.gradle.kts`.
3. Put the signing identity and notarisation credentials in a GitHub environment with required approval.
4. Rename `notarize.sh.disabled`, wire `entitlements.plist` through Compose's `macOS { signing / entitlementsFile }`.
5. Rule the licence (OQ-12) before any DMG or cask is published.
6. On a Mac, run `docs/DEVICE_CHECKLIST_MACOS.md`, including the allow-jit self-test.

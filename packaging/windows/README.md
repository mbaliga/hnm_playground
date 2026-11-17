# packaging/windows

Everything here is a **disabled template**. The Gradle `Msi` target (`ui/build.gradle.kts`) builds an
**UNSIGNED** MSI on a Windows host with WiX only; nothing in this repo has been run on Windows.

| File | State |
|---|---|
| `winget/manifest.template.yaml` | winget manifest skeleton with `<PLACEHOLDER>` tokens. |
| `sign.ps1.disabled` | Authenticode re-signing sketch; secrets from the environment only. |

No `upgradeUuid` is set (a NAMES.md row first, R11). An unsigned MSI triggers SmartScreen.

## OWNER_STEPS (the owner's later work, none of it done)

1. Rule the signing route (OQ-3) and buy or obtain a certificate; add it as a GitHub environment secret.
2. Decide the winget PackageIdentifier and MSI upgrade GUID; add their NAMES.md row (R11).
3. Rule the licence (OQ-12) before anything is published or submitted.
4. On Windows, run `docs/DEVICE_CHECKLIST_WINDOWS.md`.

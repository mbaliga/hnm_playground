# Device checklist: macOS

Owner-run checks for the unsigned DMG (plan section 6.3). No Mac is on record (OQ-5), so every row is NOV.

Plain Markdown for now; align with the F11 schema when Shared-Libraries-asoc merges. Every Result cell is empty on purpose: nothing here has been run, and a hosted runner or simulator never upgrades NDV/NOV.

| ID | Item | Evidence class | Result | Date | Notes |
|---|---|---|---|---|---|
| MC-01 | Unsigned DMG: Gatekeeper behaviour on first open (blocked? right-click Open?) | NOV | | | |
| MC-02 | Drag to Applications and launch | NOV | | | |
| MC-03 | Window renders at normal and Retina scale | NOV | | | |
| MC-04 | Play produces audible output | NOV | | | |
| MC-05 | Play text says audio only, no haptic actuator (after X3) | NOV | | | |
| MC-06 | allow-jit self-test (asom S-M3): hardened-runtime JVM starts with entitlements.plist | NOV | | | |
| MC-07 | Notarised build passes Gatekeeper (only after the owner enrols) | NOV | | | |

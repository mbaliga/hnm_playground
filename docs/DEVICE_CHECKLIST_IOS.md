# Device checklist: iPadOS and iPhone

Owner-run checks for the Apple build (plan section 6.5). Devices on record: iPad Pro M4 (layout, audio, file I/O) and iPhone 12 mini (Core Haptics feel). The Simulator has no Taptic Engine.

Plain Markdown for now; align with the F11 schema when Shared-Libraries-asoc merges. Every Result cell is empty on purpose: nothing here has been run, and a hosted runner or simulator never upgrades NDV/NOV.

| ID | Item | Evidence class | Result | Date | Notes |
|---|---|---|---|---|---|
| IP-01 | Simulator: app launches and shows the editor (after the shell exists) | SIMULATOR | | | |
| IP-02 | iPad Pro M4: install a signed build (needs Developer Program) | NDV | | | |
| IP-03 | iPad: wide layout in full screen; narrow layout in Split View | NDV | | | |
| IP-04 | iPad: Play produces audible output (AVAudioEngine) | NDV | | | |
| IP-05 | iPad: Open/Save/Share through the document picker and share sheet | NDV | | | |
| IP-06 | iPad: capabilities probe reports no haptics (expected, unverified) | NDV | | | |
| IP-07 | Safe-area handling on notch and home indicator (fixed 16 dp padding unverified) | NDV | | | |
| IP-08 | iPhone 12 mini: install a signed build | NDV | | | |
| IP-09 | iPhone: Core Haptics pattern plays and matches the Android feel | NOV | | | |
| IP-10 | iPhone: capability probe matches the apple-iphone-15 profile shape | NDV | | | |
| IP-11 | iPhone: engine recovers after backgrounding (stopped/reset handlers) | NDV | | | |
| IP-12 | iPhone: compact-width layout and one-handed reach | NOV | | | |

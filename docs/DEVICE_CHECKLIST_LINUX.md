# Device checklist: Linux desktop

Owner-run checks for the Linux JVM build (plan section 6.2). Audio is NDV; controller haptics (M6) are NOV.

Plain Markdown for now; align with the F11 schema when Shared-Libraries-asoc merges. Every Result cell is empty on purpose: nothing here has been run, and a hosted runner or simulator never upgrades NDV/NOV.

| ID | Item | Evidence class | Result | Date | Notes |
|---|---|---|---|---|---|
| LX-01 | Install the .deb on the target (x86_64) and launch from the menu | NDV | | | |
| LX-02 | Unpack the app-image tarball and launch it (RedMagic-Edge path) | NDV | | | |
| LX-03 | Launch on Steam Deck Desktop Mode | NDV | | | |
| LX-04 | Launch in Gaming Mode (window size, input) | NDV | | | |
| LX-05 | Termux:X11 software-GL fallback: window renders at all (Skiko untested) | NDV | | | |
| LX-06 | Play produces audible output (javax.sound line available) | NDV | | | |
| LX-07 | With no audio line, Play is disabled and the UI says so | NDV | | | |
| LX-08 | Wayland and XWayland: window, input, HiDPI scaling | NDV | | | |
| LX-09 | The jlinked runtime in the package keeps javax.sound (module java.desktop) | NDV | | | |
| LX-10 | Play button text reads "audio only, no haptic actuator here" (after X3 is merged) | NOV | | | |
| LX-11 | arm64 package launches (ubuntu-24.04-arm build) | NDV | | | |
| LX-12 | File open/save and clipboard work (after L2 exists) | NDV | | | |

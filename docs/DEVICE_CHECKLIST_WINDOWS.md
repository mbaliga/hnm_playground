# Device checklist: Windows

Owner-run checks for the unsigned MSI (plan section 6.4). The Dell is the only Windows device (OQ-5).

Plain Markdown for now; align with the F11 schema when Shared-Libraries-asoc merges. Every Result cell is empty on purpose: nothing here has been run, and a hosted runner or simulator never upgrades NDV/NOV.

| ID | Item | Evidence class | Result | Date | Notes |
|---|---|---|---|---|---|
| WN-01 | Install the unsigned MSI; record the SmartScreen prompt | NDV | | | |
| WN-02 | Launch from the Start menu | NDV | | | |
| WN-03 | Window renders at 100%, 150% and 200% scaling | NDV | | | |
| WN-04 | Play produces audible output | NDV | | | |
| WN-05 | Audio latency acceptable (tap-to-sound); note if WASAPI is needed | NDV | | | |
| WN-06 | CRLF checkout (core.autocrlf=true): :core:jvmTest and :ui:jvmTest still pass | NDV | | | |
| WN-07 | Uninstall leaves nothing behind | NDV | | | |
| WN-08 | Play text says audio only, no haptic actuator (after X3) | NOV | | | |

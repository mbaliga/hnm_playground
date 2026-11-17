# apple/

**Nothing here has been built.** No Xcode, XcodeGen, Mac or Simulator was available. These are templates for
the iOS/iPadOS head (plan section 6.5, step I5); whether `project.yml` generates a valid project is unverified.

| File | State |
|---|---|
| `project.yml` | XcodeGen skeleton: iOS 17 floor, iPhone + iPad, `CODE_SIGNING_ALLOWED=NO`, throwaway bundle id from a build setting, no sources, no framework. |
| `Resources/Info.plist` | Template. |
| `Resources/PrivacyInfo.xcprivacy` | No tracking, no collected data (the source has no network code). |

Not written, and waiting on the Kotlin/Compose/Xcode pin (OQ-17/OQ-38) plus `ENABLE_IOS` and `:backend-ios`
(I1 to I4): the `WorkbenchKit` framework link, the SwiftUI shell, and `ios.yml`.

## OWNER_STEPS (the owner's later work, none of it done)

1. Join the Apple Developer Program (not yet enrolled).
2. Add the NAMES.md row and set the real `com.asoc.<appname>` bundle id and display name.
3. Set `DEVELOPMENT_TEAM`, signing and provisioning; put signing material in a GitHub environment secret
   behind required approval (OQ-3); enable TestFlight / App Store Connect.
4. Rule the licence (OQ-12) before any binary is published.
5. Run `docs/DEVICE_CHECKLIST_IOS.md` on the iPad Pro M4 and iPhone 12 mini.

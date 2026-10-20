FingerFlux

Customize your fingerprint icon. Transform your unlock animation.

FingerFlux is an Android customization module designed for AOSP-based ROMs. It lets you personalize the under-display fingerprint (UDFPS) icon and fingerprint unlock animations through an easy-to-use settings interface.

✨ Features

- Fingerprint Icon Catalog — Choose from 24 fingerprint icon styles.
- Unlock Animations — Customize your fingerprint experience with 17 animation styles.
- Icon Scaling — Adjust the fingerprint icon size to your preference.
- Animation Controls — Customize animation duration and size.
- Visual Customization — Option to hide the animation background.
- Haptic Feedback — Toggle haptic feedback in the module settings.
- SystemUI Integration — Uses Xposed hooks to customize supported fingerprint interfaces.
- Update Checker — Check for new releases from within the app.

📱 Requirements

- Android 12 or newer.
- An AOSP-based or compatible custom ROM.
- A working under-display fingerprint sensor (UDFPS).
- Root access.
- LSPosed or another compatible Xposed framework.

Compatibility note: Fingerprint implementations vary between ROMs and devices. Not every icon, animation, or customization is guaranteed to work on every device.

🚀 Installation

1. Download the latest FingerFlux APK from the "Releases" (../../releases/latest) page.
2. Install the APK on your device.
3. Open LSPosed and enable the FingerFlux module.
4. Configure the module scope to include System UI ("com.android.systemui").
5. Reboot your device if required by your Xposed framework.
6. Open FingerFlux and select your preferred fingerprint icon and animation.
7. Restart System UI when prompted to apply the changes.

Root access may be requested when restarting System UI.

⚙️ Configuration

Open FingerFlux to explore the available fingerprint styles and animation settings. Changes may require restarting System UI before they become visible.

If the customization does not work, verify that the module is enabled, System UI is in its scope, and your ROM uses a compatible UDFPS implementation.

🛠️ Built With

- Kotlin
- Android SDK
- Gradle
- Xposed API
- LSPosed-compatible module architecture

🐛 Issues and Feedback

Encountered a bug or compatibility issue? Please open an issue in this repository and include:

- Device model
- Android version
- ROM name and version
- Xposed framework and version
- Relevant logs and steps to reproduce the issue

Please do not include personal information or sensitive device logs.

📄 License

See the repository's license file for the terms governing use, modification, and distribution.

---

FingerFlux — Make your fingerprint experience yours.

# pcm — Offline Phone, Contacts & SMS

**English** · [Türkçe](README.tr.md)

A fully offline, open-source Android dialer, contacts and messaging app written in Kotlin with Jetpack Compose. No network permission, no telemetry, no accounts — calls, contacts and messages never leave the device.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)
![License](https://img.shields.io/badge/license-MIT-blue)

## Features

### Phone

- Dialer with live contact matching and T9-style search.
- Full-screen incoming / outgoing call UI, call timer and call log.
- Spam protection: Turkish spam number blocking, non-TR number filtering and a corporate caller-ID registry (banks, telecom operators, cargo, etc.).
- Blocked numbers and spam call lists.
- Call audio recording with an in-app waveform player.
- Dual-SIM support with per-call SIM selection.
- Configurable as the default dialer (`InCallService` + `CallScreeningService`).

### Contacts

- Read from and write to the system contacts provider.
- Add, edit, delete contacts, photos and notes.
- Duplicate-merging by normalized phone number.
- Local cache so contacts stay available without permissions.

### Messages (SMS)

- Threaded conversations with a Material 3 chat UI.
- Send, receive, multi-part SMS with SIM selection on long press.
- **Per-message deletion** — long-press a bubble to delete just that message.
- **Brand messages are read-only** — alphanumeric sender IDs (banks, couriers, etc.) cannot be replied to.
- Link detection in messages, copy text / link, clipboard paste.
- Rate limiting and duplicate-send protection.
- Spam SMS detection and spam inbox.
- Quick reply and "mark as read" from notifications.
- Configurable as the default SMS app.

### Privacy

- No `INTERNET` permission — the app cannot send data anywhere.
- All data stays in the system providers and a local on-device cache.
- No analytics, no ads, no accounts.

## Requirements

| Tool | Version |
| --- | --- |
| Android SDK | API 37 (`compileSdk` / `targetSdk`), `minSdk` 36 |
| JDK | 11+ |
| Gradle | 9.7.1 (wrapper included) |
| AGP | 9.4.1 |

## Build

1. Point Gradle at your Android SDK by creating `local.properties`:

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

2. Build the debug APK:

   ```bash
   ./gradlew assembleDebug
   ```

   The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

3. Install it on a device or emulator:

   ```bash
   ./gradlew installDebug
   # or
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Permissions

The app requests only what is needed for a dialer/SMS app: contacts, call log, phone state, SMS, call audio recording, notifications and full-screen call intents. It intentionally has **no network access**.

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- AndroidX Activity, Lifecycle, Core
- Coil for contact photos
- Kotlin Coroutines / StateFlow

## License

MIT — see [LICENSE](LICENSE).

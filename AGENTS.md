# Vociferous

Android Phone app named Vociferous, rebuilt from the installed Empty Voicemail APK. The decompiled reference lives in `recovered/` and is gitignored.

## Project type

- Kotlin, Jetpack Compose, Material 3, single module
- Package `com.brian.dialbuddy` so it can sit beside the installed `dev.emptyvoicemail` build
- minSdk 31. This app takes the Phone role
- Dark only. Colors follow the recovered theme: forest background `#101614`, mint accent `#8EE0A8`

## Layout

- `MainActivity` is the mailbox and keypad. There is no navigation graph. The voicemail, known, unknown, stats, and keypad pages are state inside `DialViewModel`.
- `InCallActivity` is a separate lock-screen activity because `CallService` starts it for the in-call UI.
- `domain/VoicemailRules.kt` is the pure mailbox rule set from the old app. Keep deletion and threshold behavior there and cover it with unit tests.
- `telecom/` holds the in-call service, outgoing call gate, and contact lookup.
- Preferences go through `data/AppStorage.kt`.

## Not wired yet

- Reading and deleting the real visual-voicemail provider. Scan explains that, and the list is a sample mailbox.
- Voicemail playback.
- The sample mailbox resets when the process dies. Deletion totals do persist.

Do not replace `dev.emptyvoicemail` on the phone unless asked. This application id is different on purpose.

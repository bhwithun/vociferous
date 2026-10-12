# Vociferous

A new dialer, rebuilt from the Empty Voicemail app. The screens follow the app installed on the Galaxy S22: voicemail, known, unknown, and stats, a coin keypad, and an in-call screen.

Package: `com.brian.dialbuddy`. It does not replace the installed `dev.emptyvoicemail` app.

## What works

- Phone-role request, call permission, and placing a call from the keypad
- Outgoing calls from other apps are cancelled unless they are emergencies or this app permitted the number
- In-call screen with answer, decline, mute, speaker, hold, and hang up, once this app is the Phone app
- Empty-voicemail rules: a message is empty when it is shorter than the threshold (1–20 seconds, default 3). Contacts are kept out of bulk delete. Deletion totals are stored for 7 days, 30 days, a year, and lifetime

## Mailbox

Voicemail comes from the Android shared mailbox once this app is the Phone app. Playback uses the provider recording and can fetch it from the visual-voicemail source. Delete goes through the provider. A speaker icon next to each Play button routes playback to the speaker or the earpiece.

On a Galaxy S22 the carrier list can stay inside Samsung Phone, so an empty shared mailbox shows a note about that.

`recovered/` is a local design reference and is not part of this repository.

```powershell
./gradlew assembleDebug
./gradlew test
```

## Tickets

Open an issue with the Ticket template. The `work-ticket` workflow takes the oldest open issue that does not already have a pull request, implements it on `ticket-<number>`, and opens a pull request. Pass an issue number when you want a specific one. One issue per run.

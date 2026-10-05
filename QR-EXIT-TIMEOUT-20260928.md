# QR screen exit, timeout and layout — v2.0.8

## Changes

- Back, Exit, the menu idle timeout, and moving the activity into the background clear the active QR screen. Opening QR Sale again starts at a blank amount entry; it does not reopen the previous generated QR.
- The QR activity uses the existing **Parameters > Menu timeout, seconds** value. The default is now **30 seconds**. A saved nonzero value remains authoritative; `0` still means OFF. Touches, physical keys, amount keyboard edits and interaction with QR dialogs restart the inactivity timer.
- Exit remains available during a host check. The check keeps the original payment reference, and a late result cannot restore the closed QR or replace a newly generated one.
- A closed but unconfirmed payment is retained in QR Payment History as UNCONFIRMED. Selecting it allows a status check without showing its QR again. This preserves reconciliation information while removing the generated QR from the active menu. Clearing the screen does not send a bank cancellation or reversal.
- Confirmed payments remain in receipt history after leaving.
- The bank logo is now a full-width header using the same asset as the home screen. The generated square QR fills the available content width instead of a fixed 220dp box. The primary payment button stays in a fixed footer; smaller screens can scroll the content.
- The same new QR Sale icon is used on the menu tile and the payment page. Typography, spacing and secondary actions have been simplified.

## Validation

Run: `gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug --console=plain`.

Additional regression cases cover clearing active state across restart, creating a fresh amount, preventing generation after the page closes, protecting a newer QR from a late response or stale exit, checking archived payments without restoring their codes, storage rollback, preserving paid history, and honoring the saved menu timeout.

The September 28 reference video was reviewed. No physical terminal is attached, so on-device timeout timing, appearance and bank checks remain to be confirmed on the terminal. Artifact identity and test totals are recorded in `QR-V208-ARTIFACT.txt`.

## Terminal check

1. Set **Parameters > Menu timeout, seconds** to **30**, then save.
2. Generate a QR, press Exit/Back, and reopen QR Sale: amount entry should be blank.
3. Generate another QR and leave it untouched: the page should close after the configured interval and the next visit should be blank.
4. Touch the page before expiry: the timer should restart from that interaction.
5. Confirm that QR Payment History can check an unconfirmed payment without redisplaying its code.
6. Verify the larger QR and matching bank header/icon on the physical screen.

This update supersedes v2.0.7's automatic reopening of a saved QR. Bank protocol, Tip Adjust packet ordering and existing card transaction behavior are unchanged except for the shared default menu idle timeout.

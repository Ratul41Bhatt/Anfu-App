# Source handover — 29 September 2026

These are source changes for the user's build and terminal testing. The emulator was stopped after the instruction not to test. No build, installation or transaction tests were run for this final set of changes. Earlier v2.0.11 APKs do **not** contain all these changes.

## Changes

- QR, Sale Tip and Tip Adjust amount entry uses minor-unit digits: `1234` displays `12.34`. Invoice entry stays numeric. Keyboard requests run after window focus; Home amount-field taps open the amount keypad even when a previous amount is present.
- QR merchant name/city fields are removed from Parameters. QR uses the common merchant name and the last suitable city address line, omitting country/postcode. The encoded name/city respect EMV field limits. Missing identity is not replaced with invented merchant details.
- QR routing, optional store label and timeout save together. The supplied CHECK STATUS document requires a **different bank-assigned QR NII** from card sales. No SID is required by the supplied flow; optional Store Label is tag 62/03. It is not a switch identifier.
- Successful card/QR/tip flows automatically advance from merchant printing to **Print Customer Copy / Yes / No**. Yes prints the customer copy then returns Home; No returns Home. Printer failure/unavailability can advance with a message; reprints remain in Reports. Transaction decline/failure previews advance automatically instead of waiting for Continue.
- Virtual receipt is displayed at 80% screen width, with smaller heading/status text. Physical receipt bitmap/font remains unchanged.
- VOID, EMI VOID and Pre-auth VOID authenticate inside the invoice screen. The respective Password Manager value is used; if unset/cleared, the **Admin password** is required (existing factory default `0000`, unless changed). Blank invoice is rejected on touch and physical Enter. The Cancel key exits; Delete removes a digit. Selection is checked again before proceeding.
- Contactless runtime data no longer overwrites every AID's 9F66 with NexusPay's TTQ. Visa retains its configured Visa profile, and NexusPay retains its own profile. Runtime EMV merchant/terminal IDs use configured IDs.
- Card search waits for reader ownership instead of proceeding unlocked. A retry releases the completed search before waiting for card removal and starting the next search. Cancellation returns the UI Home without waiting for a blocking SDK close.
- Chip-completion reversal explicitly requires a host-approved transaction. A valid host decline does not generate a chip-decline reversal. Timeouts, invalid/mismatched responses, and failed completion of a host approval still require recovery; they must not be silently treated as final host declines.
- New journal records retain original routing/terminal identifiers and host response date/time. Pre-auth cancellation reuses those fields with original STAN/RRN/auth code. Older journal records remain readable and fall back to their existing fields. This addresses correlation loss; UnionPay host acceptance still needs terminal/bank testing.
- Boot/upgrade handling binds the SDK with a bounded startup retry, reapplies permissions/navigation lock, then launches the POS. Returning from administrator Settings ends the maintenance session and reapplies locking. Android managed lock-task support is also included. Vendor lock still requires a compatible customized firmware; an ordinary APK cannot manufacture device-owner privileges. The system must launch the app once after first installation (or provision it through its device manager).
- Added `TmsManifestClient` for HTTPS download, bank-signature/identity/revision/expiry checks and private atomic staging. It does not automatically apply parameters or install software. See `TMS-INTEGRATION.md` for the proposed contract and bank inputs still needed.

## External confirmations still required

Live QR completion depends on the QR-enabled bank route and a valid successful CHECK STATUS response; pending QR records are not locally marked paid. The app preserves them for a later status check. The supplied QR documents do not define an automatic timeout reversal, so one has not been invented.

Visa acceptance, reader timing, printer callbacks, UnionPay pre-auth cancellation, and Home/Recents lock after cold boot need verification on the actual terminal. TMS enrollment/synchronization needs the bank's endpoint, authentication protocol, trust key and approved parameter schema.

Backups: `_qr-input-lock-backup-20260929` and `_transaction-source-backup-20260929`.

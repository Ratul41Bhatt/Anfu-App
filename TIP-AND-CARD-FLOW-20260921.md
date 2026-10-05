# Tip, Tip Adjust and card-flow update — v2.0.6

## Parameter save fix

The original save implementation called `loadParams()` before reading the two Tip checkboxes. That restored their previous values, which were then saved again. `saveTipParams()` now reads and commits the visible switches and percentage before any refresh. Both the main SAVE button and the separate SAVE TIP SETTINGS button use that method. The separate button does not depend on other parameter fields being valid.

Tip Adjust has its own `TIP_ADJUST` menu-control key. A previously hidden legacy ADJUST menu does not hide this new feature. Home refreshes menu visibility when returning from Parameters.

## Operator steps

1. Open Parameters, then the TIP section.
2. Enable Sale with Tip and/or Tip Adjust. Set the maximum Tip Adjust percentage (0–100; default 50).
3. Press SAVE TIP SETTINGS. Leave Parameters and return to confirm the saved values.
4. Sale with Tip: enter the sale amount, then the tip. The screen shows the combined total before card entry. Zero tip is allowed.
5. Tip Adjust: open TIP ADJUST on home menu page 2, enter the original sale invoice, enter the desired total tip and approve offline. The amount entered is the replacement tip, not an increment to an existing tip.

An adjustment requires an approved, unvoided ordinary sale in the open batch with its host references. An invoice can be adjusted once. EMI, refund, preauthorization and QR sales are not eligible. The percentage limit applies to Tip Adjust; it is measured against the original base amount.

## Amounts and packets

- Sale: the total is supplied to EMV and sent in DE4. A nonzero tip goes in DE54 as 12 ASCII digits representing minor units. Receipts/reprints retain base, tip and total separately.
- Adjustment: MTI 0220, DE3 020000, revised total in DE4, original date/time, expiry, entry mode, RRN and authorization code, tip in DE54, original amount in DE60 and original invoice in DE62. DE54/60 are 12 ASCII digits. No PIN block or track is stored in the advice.
- The completed chip data, when available, is kept separately for advice/reprint. The original authorization ICC data remains intact for reversals.
- Outstanding reversal recovery runs first, then queued Tip Adjust advices in order, then the new financial request. The same upload gate runs before settlement.
- A valid 0230/00 is required to acknowledge an adjustment. MTI/STAN, malformed packets and configured MAC checks are enforced. Amount and terminal echoes are checked when present.
- Timeout, decline, mismatched reply, storage failure or changed host/merchant parameters retain the queue and block the new transaction. Retry uses the saved invoice and STAN.

## Offline persistence

The original sale's revised amount, the pending advice and adjustment history are committed together. This updates settlement amounts without adding a second sale count. Failure to commit does not approve the adjustment. Host acknowledgement and queue removal are also committed together. An audit history remains after upload. Batch clearing is blocked while an adjustment is pending.

Turning off Tip Adjust does not discard or disable upload of adjustments already saved.

## Card and receipt fixes included

- Missing/incomplete initial chip results and chip application endings without readable card data now reach the chip retry counter instead of silently ending or leaving the screen waiting. The third failed attempt offers stripe fallback. GAC2 and post-authorization failures cannot enter this retry path.
- SDK searches remain serialized on a worker with a 30,000 ms timeout. Empty search timeouts do not count as chip failures without insertion evidence.
- A stale reading popup cannot cover an active retry/counter prompt.
- Home retains a card insertion event while another window holds focus. Returning from a transaction requires stable card removal before another automatic amount popup.
- Duplicate financial-start callbacks are ignored for the same transaction screen.
- Merchant/customer receipt choices complete once. Declining the customer copy returns home rather than opening a second screen waiting for an obsolete printer event. The customer prompt is titled CUSTOMER COPY.
- The legacy result screen now supports Back/Enter and exits when its timer ends.

## Validation and remaining device checks

Final build: `tip-v206-verified-build.log` — successful. All 63 JVM tests passed (0 failures/errors). APK signature verification passed with Android apksigner.

APK: `build/app/outputs/apk/debug/DBBL-POS_v2.0.6_debug_202609211840.apk`, versionCode 30.

SHA-256: `40D265A8AE7788719008A9825A07B4D56543B40FD507F20BCB27FE2AAADC1FFD`.

Local JVM tests use a loopback host and explicit in-memory Android preference substitutes. They exercise real packet builders/transport, reversal recovery, tip persistence/reload, invalid values, FIFO uploads, lost replies, invalid acknowledgements, commit failures, original/adjusted totals, reader serialization, chip-failure classification, card-removal debounce and receipt completion.

No bank transactions were sent. No terminal was connected to ADB during this update, so physical reversed-card detection, chip/swipe fallback, home insertion behavior, printer output and bank acceptance still require terminal testing. Automated persistence tests recreate the settings/store objects; they are not a physical power-cut test.

Terminal checks: save both Tip switches and a percentage, reopen Parameters and restart the app; sale 100 + tip 50 should send DE4=15000 and DE54 ASCII 000000005000; offline adjustment 100 + tip 20 should survive restart and upload as 0220 before the next 0200; a lost 0230 must stop the new sale; verify three failed chip presentations lead to stripe-only fallback with DE22 80x; verify customer-copy NO returns home without reopening the amount dialog for the card still inserted.

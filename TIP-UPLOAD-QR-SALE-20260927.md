# Tip upload and QR Sale — v2.0.7 (2026-09-27)

## Tip Adjust upload fix

All OnlineHost entry points for new online work now share the same preflight: finish a pending reversal, upload saved Tip Adjust advices FIFO, then send the requested transaction. This includes Logon, Sale, Void, Pre-auth, Pre-auth Void, Sale Completion, Settlement, batch upload and QR Check Status. Recovery exchanges themselves do not recurse through the queue.

A queued adjustment remains an 0220 with its saved STAN and original sale reference. DE4 is the adjusted total; DE54 is the tip; DE60 is the original authorized amount. Only a validated 0230/00 acknowledges and removes it. A timeout, rejection, mismatched acknowledgement or storage failure blocks the new transaction and retains recovery data. Disabling Tip Adjust does not discard existing queued advices.

The old Sale path already sent queued advices, but discarded their request/response dumps. The sale receipt therefore hid the 0220/0230 exchanges. Logon also bypassed upload entirely. Both are fixed: preflight exchanges are carried into the final debug trace, in their actual order, including when the requested transaction is blocked. Normal merchant/customer receipts still follow the configured packet-debug setting.

Void reloads the sale record after preflight so it uses the latest adjusted total.

## QR Sale

Home menu page 3 contains **QR SALE**. Enter the sale amount, generate the dynamic QR, let the customer pay in NexusPay, then choose **CONFIRM PAYMENT**. This button queries the host; it never approves based on an operator tap.

The screen includes the existing Dutch-Bangla Bank logo above the QR, amount, invoice, payment state, retry action and receipt preview. Confirmed payments have merchant/customer copies and a QR Payment History for reprinting. The code and its reference are durably saved before display. Leaving the screen or restarting retains the unresolved payment; reopening QR Sale resumes it. An unresolved QR is not silently overwritten by a new amount.

Parameters > QR Sale has dedicated saved fields:

- QR NII: enter the value assigned by DBBL.
- QR TPDU: defaults to **6000050000**, matching the provided packet photograph.
- MCC: bank-assigned four-digit merchant category.
- QR merchant name: 1–25 printable English characters.
- Merchant city: 1–15 printable English characters.

TID, MID, communication host/port and MAC configuration use the existing terminal parameters. The QR NII and TPDU do not overwrite card routing. The photograph uses DE24=008, while the DOCX asks for an NII different from the card-sale route. Consequently the QR NII is not guessed; configure the bank-assigned value before use. MCC and merchant city/name also require actual merchant values.

### Protocol

- Dynamic EMVCo payload from the supplied DBBL v0.3 specification: 00/01, NexusPay merchant template 44, MCC 52, currency 050, exact amount 54, country BD, name/city, invoice/TID/purchase-purpose in 62, STAN/network data/NII in 88, CRC 63.
- A TID + millisecond timestamp reference is embedded in QR 62.05 and 88.02 and sent as ASCII `QR` + reference in DE63. This implements the DOCX's unique-reference requirement; the packet photo shows the shorter example `QR` + TID.
- CHECK STATUS is 0200/0210, processing code 000000, entry mode 840, DE4 amount, saved STAN, QR NII, condition 00, TID, MID and original invoice.
- Approval requires a correlated, valid host response with 00, matching TID/NII (and amount if returned), original card PAN, RRN, authorization code and transaction date/time. MAC is verified when enabled.
- Repeated checks retain the same QR reference/STAN/invoice. A confirmed payment is committed once; repeated confirmation does not send or record a second sale.

### Timeout/reversal and settlement scope

Neither attached document defines a QR timeout reversal message, timer, cancellation API or expiry rule. The DOCX mentions the returned PAN being usable for later Void/Reversal/Settlement, but supplies no messages or routing rules for those operations. A check timeout therefore leaves **payment not confirmed**, retains the QR and permits checking again. It does not create an invented 0400, claim the payment failed, or locally approve it. Nonzero response codes are displayed as received; no undocumented meaning is assigned to code 31.

Confirmed QR payments have a **separate durable QR journal**, available through QR Sale > QR Payment History. They are not uploaded or settled through the card route. QR Void/Settlement and explicit QR reversal/cancellation remain outside this release because their bank protocol has not been supplied. Existing card settlement/reversal behavior is preserved.

## Verification

- 76 JVM tests passed, zero failures/errors, including loopback TCP exchanges.
- Tests cover 0220 before each online entry point, retained debug traces, acknowledgement failures, existing card reversals, QR CRC, generated-code encode/decode round trip, route isolation, pending/approval responses, restart recovery, malformed/mismatched approval rejection, timeout without reversal, and failed persistence recovery.
- Android debug APK assembled successfully. Final artifact identity and signature verification are recorded in `QR-V207-ARTIFACT.txt`.
- No physical terminal was connected. No live DBBL payment was submitted. Physical QR scanning, bank acceptance of the unique-reference mapping, printer output and on-device layout still require UAT with the configured terminal.

## Sources reviewed

- `C:/Users/ratul/Downloads/EMVCo QR Code Transaction Flow (DBBL) v0.3.pdf` — dynamic POS example and field definitions; rendered and inspected flow/example pages.
- `C:/Users/ratul/Downloads/Sample Request_Response data for CHECK STATUS For POS Generated QR Transaction.docx` — extracted all paragraphs and packet table; no embedded images.
- Provided QR packet/screen photographs and the September 22 video — amount entry followed by a merchant-presented QR and manual check-status action.

Source edits from this change are backed up under `_qr-backup-20260927`. Existing Gradle cache junction targets must not be deleted.

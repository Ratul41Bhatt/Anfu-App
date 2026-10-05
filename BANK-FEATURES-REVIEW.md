# Bank transaction features — comparison and implementation

Reviewed 15 September 2026. Target: the DBBL/Anfu app in this workspace.
The City Bank and Islami Bank reference projects were read only.

## Reference implementations

| Area | City Bank | Islami Bank | Target app changes |
| --- | --- | --- | --- |
| Online processing | Saves a reversal record on connection; sends the transaction; passes issuer response/DE55 to EMV second authorisation; success then marks the record for printing. Sale approval code is `00`. | Similar pipeline; separate reversal repository; sale approval code is `000`. | Keeps the target's DBBL `00` protocol and Anfu SDK. Validates response MTI, required matching STAN and response code; verifies response MAC when MAC is enabled; completes contact EMV before adding the sale to the batch. |
| Reversal | Configured retry loop after send/receive errors or host approval followed by card decline. The callback treats a received response as completion without explicitly testing its response code. Auto-reversal disabled path deletes pending records. | Checks `000` for reversal approval, waits 30 seconds before the first attempt, temporarily changes communication timeout, and deletes records after exhaustion. Retry count is incremented in both dispatch and failure callbacks. | Preserves original transaction identity; treats an attempted write without a valid response as uncertain; sends an immediate reversal and retries at the existing 30-second interval. Requires a valid `00` acknowledgement. Retains the pending record after automatic attempts stop. Blocks new financial requests and settlement while recovery remains unresolved. |
| Print preview | `ShowReceiptDialog` displays the receipt bitmap and animates it upward; the image is hidden at animation end. | Same bitmap-preview design. | Shared full-screen, scrollable receipt bitmap for sale/failure, merchant/customer copies, settlement and reports. Waits for the printer callback, offers retry on a reported error, and permits screen-only viewing without a printer. |
| Card fallback | EMV errors select retry/contact/stripe routes. Stripe-only fallback disables contactless, chip and manual input, marks the card as fallback, and repeats card read. Chip service codes request chip insertion before ordinary swipe processing. | Same general routing and fallback flag handling. | Preserves the existing three-failed-chip-read ladder and DBBL fallback DE22. Requests chip insertion for chip-capable swipes; preserves chip-only routing during retries; enforces stripe-only reads after fallback; honours entry settings and clears stale chip/PIN data. |

### Source locations

City Bank:

- [SalePresenter.java](<D:/CityBank ECR/CityBank/app/src/main/java/cn/nexgo/inbas/transactions/sale/internal/SalePresenter.java>) — online callbacks, second authorisation, retry loop.
- [EmvBasePresenter.java](<D:/CityBank ECR/CityBank/app/src/main/java/cn/nexgo/inbas/components/emv/presenter/EmvBasePresenter.java>) — chip service-code handling and `onEmvFallbackFlow`.
- [ShowReceiptDialog.java](<D:/CityBank ECR/CityBank/app/src/main/java/cn/nexgo/inbas/components/others/print/showreceipt/ShowReceiptDialog.java>) — bitmap preview.

Islami Bank:

- [SalePresenter.java](<F:/Ratul Handover Files/Ratul Handover Files/Islami Dev 21-07-26/Islami Dev 21-07-26/app/src/main/java/cn/nexgo/inbas/transactions/sale/internal/SalePresenter.java>) — online callbacks, second authorisation, SmartVista reversal delay and retry handling.
- [EmvBasePresenter.java](<F:/Ratul Handover Files/Ratul Handover Files/Islami Dev 21-07-26/Islami Dev 21-07-26/app/src/main/java/cn/nexgo/inbas/components/emv/presenter/EmvBasePresenter.java>) — card/fallback routing.
- [ShowReceiptDialog.java](<F:/Ratul Handover Files/Ratul Handover Files/Islami Dev 21-07-26/Islami Dev 21-07-26/app/src/main/java/cn/nexgo/inbas/components/others/print/showreceipt/ShowReceiptDialog.java>) — bitmap preview.

## Resulting transaction flow

1. Read the card and collect the required PIN.
2. Attempt recovery of an earlier pending reversal. If it remains unresolved, return a failure before sending a new financial request.
3. Synchronously save the recovery record before the host exchange.
4. Send the DBBL request. Connection refusal is unsent; a failed/partial write or missing/untrusted reply is uncertain and requires recovery.
5. Validate the reply before using its issuer data or key updates.
6. For an approved contact-chip transaction, keep the recovery record while the Anfu kernel completes GAC2. On acceptance, atomically save the batch entry and remove recovery data. On refusal/removal/service failure, reverse on the worker thread.
7. Display the receipt bitmap and print. A print error affects receipt delivery, not transaction approval. The customer-copy prompt follows the merchant preview's Continue button.

Contactless keeps the target's existing single-tap completion path. The Nexgo second-authorisation API was not copied into the Anfu app.

## Deliberate differences from the references

- No foreign MID/TID, host address, keys, three-digit response codes or Nexgo SDK calls were imported.
- Unacknowledged reversals are retained. Automatic retry exhaustion does not mean the bank reversed a transaction.
- Removed the old DE56 path that cleared an old reversal merely by packing it into a new sale. The old record must be acknowledged before a new financial request can overwrite its slot.
- Retry workers are tied to the original transaction and host operations are serialized, so a sleeping retry cannot reverse or clear a newer transaction.
- Pending-reversal receipt text says **REVERSAL PENDING**. It does not claim that reversal succeeded.
- The preview remains scrollable after printing. Advancing is based on the printer result and the operator's Continue action, replacing fixed 1.2/1.4-second delays.
- If the printer gives no callback for 45 seconds, Continue becomes available. Retry remains disabled for that uncertain job; check the paper before reprinting from Reports.

## Files changed

- `ans8583/HostComm.java`: distinguish failed connect from uncertain write.
- `ans8583/OnlineHost.java`: response validation/MAC check, reversal retention and guards, serialized recovery, deferred chip approval.
- `ans8583/BatchStore.java`: synchronous recovery write and atomic completion of approved transactions.
- `ans8583/TransactionPolicy.java`: device-independent response and chip-service-code decisions.
- `ui/activity/FindCardActivity.java`: worker-thread completion/reversal, fallback guards, receipt preview flow and accurate failure wording.
- `ui/activity/ReportsActivity.java`: shared preview and printer error handling for reprints/reports.
- `ui/dialog/ReceiptPreviewDialog.java`: bitmap preview and callback-driven print/retry UI.
- Unit tests: `TransactionPolicyTest`, `HostCommTest`.

Java paths above are relative to `app/src/main/java/com/hjq/demo`.
Original versions of modified existing Java files are in `_bank-features-backup-20260915` because this workspace has no Git repository.

## Validation

Build with JDK 17 and the existing Gradle cache:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Automated tests cover fragmented response framing, truncated/invalid response frames, failed connections, response identity checks, rejected reversal responses and chip-service-code classification. Existing KonaMoney tests also run. Final verification: **BUILD SUCCESSFUL**, **12 tests passed**, no test failures or errors. See `bank-features-final-build.log` for details. The debug APK is under `build/app/outputs/apk/debug`.

### Terminal/UAT checks still required

These require an Anfu terminal and the bank's test host; they were not executed as live bank transactions during this change.

| Scenario | Expected result |
| --- | --- |
| Chip approval + successful GAC2 | One approved batch record; no pending reversal; merchant preview followed by optional customer copy. |
| Host approval + AAC/card removed/EMV binder failure | No approved settlement entry; reversal sent off the UI thread; pending recovery retained until acknowledged. |
| Disconnect before connect / while sending / while receiving | No reversal for connect failure; retain and attempt reversal for uncertain write or response failure. |
| Wrong STAN/MTI, missing response code, invalid MAC with MAC enabled | No approval or issuer/key update from the untrusted response; recovery remains available. |
| Reversal returns decline or all retries time out | No new financial request overwrites it; settlement blocked; pending status survives restart. |
| Restart after host approval but before GAC2 completes | Saved recovery record is available for reversal; no completed sale in settlement. |
| Swipe chip card before trying the chip | Ask to insert; retries stay on chip; the redirect itself does not count as a chip-read failure. |
| Three genuine failed chip reads, then accept fallback | Only stripe is armed; DE22 remains the DBBL fallback value; failed-chip DE55/PIN data is not reused. |
| FALLBACK disabled / SWIPE disabled | Enforce each setting; ordinary swipes cannot use the fallback setting to bypass SWIPE being disabled. |
| Out of paper, printer unavailable, slow/no printer callback | Receipt preview remains available; retry after a reported error; no overlapping merchant/customer print jobs caused by fixed delays. |
| Reports, reprint, settlement, activity exit | Same bitmap shown and printed; closing the activity dismisses its preview. |

Storage failure/power interruption, physical chip/fallback behavior and printer callbacks need these device checks; JVM unit tests do not emulate the vendor SDK.

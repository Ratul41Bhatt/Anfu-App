# Card search SDK guidance — v2.0.2

**Superseded for PayActivity by v2.0.3:** the vendor subsequently requested the original OfflineDemo home-screen flow. All separate card-reader binding, searching, removal polling, and card-triggered keypad behavior were removed from PayActivity. FindCardActivity retains its transaction search worker and 30,000 ms timeout. See `PAY-ORIGINAL-FLOW-FIX.md`.

## Changes

- `FindCardActivity` and `PayActivity` pass `30 * 1000` to `openFindCardForEMV`, following the vendor's supplied millisecond guidance. The separate `getMSRCardInfo` timeout remains 5; its parameter was not part of that guidance.
- Transaction searches run through `CardSearchQueue` on a background thread. Retries wait for the current search and result processing to finish. Only the latest pending request is retained.
- Removed active `Handler.postDelayed` search retries, the seven-second forced reader unlock, and the 600 ms chip-failure timer.
- Results are logged as success, timeout, cancellation, or failure. A successful search proceeds to card processing. A cancelled search does not retry. Empty timeouts can queue another search within the page's idle limit.
- Failed chip reads use the existing counter/confirmation and fallback flow. Other failed reads show RETRY/CANCEL. The worker waits for a stable empty chip slot before rearming, so a card held in the slot is not searched repeatedly.
- The slot observer only records insertion history during a search. It cannot report a failure or start another search. An empty timeout does not count as a chip failure.
- Leaving the transaction screen invalidates pending searches and late results. Idle-screen restart requests wait for the previous worker to return, without delayed UI polling.
- An idle-screen service error stops automatic searching for that session; manual amount entry remains available to enter the transaction screen.

## Validation

The JVM suite includes five new queue tests covering serialization, retry coalescing, result-driven retry, cancellation/resume, and duplicate starts after success, plus the existing 17 transaction/reversal tests.

Build command: `gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug` with JDK 17. See `card-search-final-build.log` for the final result.

Final result: BUILD SUCCESSFUL; 22 tests, zero failures/errors. APK: `build/app/outputs/apk/debug/DBBL-POS_v2.0.2_debug_202609171208.apk` (version code 26).

No terminal was connected during this change. SDK timing and physical card behavior still require terminal verification:

1. Leave the card page empty. Check `timeoutMs=30000` and the elapsed time reported with the SDK timeout result. Another request should start only after the previous result.
2. Insert a working card. Confirm one successful search proceeds to processing without another search starting over it.
3. Insert an unreadable/reversed chip. Wait for the SDK result, acknowledge the counter, remove the card, and reinsert correctly. Keeping the card inserted must not start another search.
4. Repeat three actual chip failures with fallback enabled. Confirm the existing approval prompt then arms stripe only.
5. Cancel or leave during a search and return. Confirm the old result cannot process a card or start a second reader call.

Original source copies are in `_card-search-backup-20260917`.

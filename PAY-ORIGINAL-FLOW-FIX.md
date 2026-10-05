# OfflineDemo PayActivity card flow — v2.0.3

**Updated in v2.0.4 at the user's request:** the home amount popup is restored using a passive slot sensor, with no home-screen openFindCardForEMV calls. See `RECOVERY-CARD-REVIEW-20260920.md` for the implementation and validation limits.

## Reference checked

`E:\AFSDK_V333_20260702\offlinepaydemo\offlinepaydemo\app\src\main\java\com\hjq\demo\ui\activity\PayActivity.java`

This is the available folder corresponding to the supplied OfflineDemo path. The reference PayActivity opens AmountActivity on cashier selection; AmountActivity passes the amount to FindCardActivity. PayActivity does not bind ICardBinderService, search for cards, or check insertion/removal.

## App changes

- Removed the entire home-screen card watcher, timeout/polling constants, worker state, lifecycle start/stop calls, and card-reader service binding.
- Removed automatic keypad opening and dismissal on card insertion/removal.
- Restored the original division of responsibility: select a transaction, enter its amount, then let FindCardActivity handle card detection during the transaction.
- Kept the app's transaction menu, amount-entry UI, reversal-status banner, receipt preview, and transaction processing features. The banner handler is now named mReversalHandler.
- FindCardActivity retains the SDK-guided sequential worker and 30,000 ms card-search timeout.
- Version name 2.0.3, version code 27.

## Validation

No card-search or card-existence SDK calls remain in PayActivity. Original and pre-change PayActivity copies are saved in `_pay-original-flow-backup-20260917`.

Build and regression command: `gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug` with JDK 17. Results are recorded in `pay-original-flow-build.log`.

Result: BUILD SUCCESSFUL; 22 tests passed with zero failures/errors. APK: `build/app/outputs/apk/debug/DBBL-POS_v2.0.3_debug_202609171223.apk`.

On the terminal, verify that inserting/removing a card on the home screen does not open/dismiss an amount keypad. Select SALE and confirm the amount, then check that card detection starts on the transaction screen. Physical-device verification is still required.

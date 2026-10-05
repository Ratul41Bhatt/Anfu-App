# QR Sale and terminal controls — v2.0.10

## Merchant flow

- QR Sale has a compact bank header, a prominent amount, a QR sized to the
  remaining screen, and fixed payment/exit actions.
- QR Reports opens a full screen with Paid and Unconfirmed tabs, invoice
  search, confirmed totals and Print QR Report. The same screen is available
  from Reports > QR Sale Reports / Reprint.
- Selecting an unconfirmed invoice checks the original bank reference without
  displaying an old payment QR. Selecting a paid invoice permits receipt reprint.
- The detail report separates unconfirmed inquiries from completed sales and
  excludes them from the paid total.
- Confirm Payment sends the existing bank-defined 0200 status inquiry. With
  Debug / packet printing enabled, each returned check opens the printable
  packet trace, including unsuccessful inquiries. Approved checks print the
  trace and the merchant receipt together; customer copies omit debug packets.
- QR receipts contain the payment amount, invoice, trace, date/time, terminal,
  merchant, RRN and approval. They omit card signatures, expiry, PIN and EMV
  details. Only a validated host approval produces a paid receipt.
- Parameters > QR Sale now includes its own idle timeout, default 60 seconds,
  configurable from 10 to 600 seconds. It clears the active QR on exit/timeout
  while preserving unresolved references for checking. Host calls and receipt
  printing are allowed to complete without the idle timer interrupting them.

## Terminal controls

- Admin > Device Settings asks for its own password, initially `1235`.
  Password Manager > Device Settings changes it; Reset returns it to `1235`.
- On SDK connection the app calls AFSDK system `setSettingsPwd`,
  `setNavBarEnabled(false)` and `setStatusBarEnabled(false)`.
- Authenticated Settings access temporarily permits system navigation. The app
  reapplies its lock when it returns to the foreground. The vendor Settings
  application can also ask for the same password.
- Admin > Device Lock Status / Retry shows the actual API return codes. One
  failed API does not prevent the other controls from being attempted.
- Boot and package-upgrade receivers launch the POS. The vendor's persistent
  overlay authorization supports launching it after boot on the customized
  terminal. The app must be opened once after its initial installation.

## Vendor prerequisites and limits

AFSDKService interface development document V2.1, sections 2.1 and 4.14,
requires a matching customized system image. Password control needs interface
and service 0.0.344+ and `com.pos.sdk.permission.SETTINGS_PASSWORD_CONTROL`.
Independent bar locking needs interface and service 0.0.354+ and customized
framework support. This project uses interface 0.0.356.

An APK alone cannot grant a signature/system permission or add the vendor's
framework support. Nonzero API results are displayed rather than reported as
successful lockdown. Reboot startup is also subject to the terminal's background
launch policy. Android documents those restrictions at
https://developer.android.com/guide/components/activities/background-starts.

No Anfu terminal or bank host was connected during this change. Live printing,
firmware controls and reboot startup require testing on the target terminal.

Build/test logs: `qr-v210-build.log` and `qr-v210-preview-build.log`.

## Verified build

- Version 2.0.10, Android versionCode 34; terminal APK contains armeabi-v7a.
- 92 JVM tests passed, zero failures or errors, including actual loopback QR
  request/response exchange, QR receipt/report content, durable parameters and
  independent vendor API error handling.
- Android 15 emulator at 540 x 960 / 240 dpi: entry, payment and both report
  tabs inspected; exiting clears the active QR; dedicated idle timeout closes
  the screen. Printer bitmap inspected for the customized QR receipt.
- Emulator uses synthetic payments and has no AFSDK service; its SDK toast
  and visible system bars are not evidence of Anfu firmware behavior.
- APK signature verified using v2 signing. Exact artifact/hash is recorded in
  `QR-V210-ARTIFACT.txt`.

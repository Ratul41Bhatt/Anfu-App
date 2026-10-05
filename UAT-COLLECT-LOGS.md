# Collecting logs on site — no PC needed

For the UAT engineer at the bank. The terminal can read and print its own log.

---

## Quickest: print it

1. Open the **admin panel**.
2. **Long-press the status text area** (the box that shows results, on any page).
3. It saves the full log to the device and **prints the key and PIN lines** on
   the receipt printer.

The printed slip shows only the lines that matter — key writes, KCVs, PIN block
checks, host verdicts. That is normally enough to see what went wrong. Photograph
it and send it.

The screen shows the same summary plus the saved filename.

---

## Getting the full log off the device

Every capture is written to:

```
Android/data/com.anfu.offline.pay.demo.test/files/logs/log_YYYYMMDD_HHMMSS.txt
```

Plug the terminal into any computer with a USB cable and browse to that folder
in File Explorer — no drivers, no Android Studio, no root. Copy the newest file.

The app keeps the ten most recent captures and deletes older ones.

---

## What to capture, and when

**Do the capture immediately after the failing transaction.** The Android log
buffer is circular; other activity will push the lines out within a few minutes.

A useful set for a PIN problem is:

1. Inject the master key → capture
2. Run a logon → capture
3. Run the failing sale → capture

Three small files beat one large one, because each is scoped to a single step.

---

## The lines worth reading

| line | meaning |
|---|---|
| `posWriteWorkingKey ... ret=0` | the DE62 working key loaded — **ret must be 0** |
| `working key check: derived=... padKCV=...` | our own derivation vs what the pad holds |
| `working key MISMATCH` / `working key corrected` | the pad decrypted DE62 differently; the terminal rewrote it |
| `THE WORKING KEY WRITE CHANGED THE TMK` | a key write hit the master key slot — serious |
| `onGetPinBlock raw='...' len=16` | the PIN block the pad produced; 16 hex characters is correct |
| `PIN block self-check: VALID ISO-0, 4-digit PIN` | the block is well formed before sending |
| `PIN BLOCK INVALID BEFORE SENDING: ...` | it is not, and the message says which check failed |
| `GAC1 ODA: TVR=... 9F27=...` | chip authentication; `9F27=80` is an ARQC, `00` is a decline |
| `ONLINE RESULT: ...` | what the host answered |

---

## If the printer is not available

The same summary appears on screen after the long-press, so it can be
photographed directly.

---

## Other routes, if ever needed

- **The transaction slip already carries the packet trace.** Every sale prints
  the full 0200 request and 0210 response field by field. For a message-level
  question that is often all that is required, with no log capture at all.
- **ADB over Wi-Fi** works if a laptop is available on the same network, but
  needs `adb` installed — not required for anything above.

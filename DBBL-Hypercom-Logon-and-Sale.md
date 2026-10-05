# DBBL / Hypercom 3.34 — Logon DE62 handling and the SALE transaction

Reference: *Hypercom — The System Message Specification, version 3.34*
(`projectv1/Hypercom - The System Msg Spec version 3.34.pdf`)

Scope: what the `62` field means on the logon response, how to consume it, and
what the SALE (0200) message must look like afterwards. Section numbers below
are the spec's own.

---

## 1. Short answer

DE39 = `00` plus data in DE62 is a **normal, successful logon**. DE62 is *not*
an error and *not* a single blob — it is the **Logon Response key block**
(spec 6.27.2), and it must be split before use:

| Offset | Field | Attr | Bytes | Notes |
|---|---|---|---|---|
| — | Length Attribute | n 3 | 2 | `'00LL'`, BCD, length of the data that follows |
| 0 | **Encrypted Working Key** | b 64 | 8 | the PIN key, enciphered under the TMK |
| 8 | **MAC Key** | b 64 | 8 | enciphered under the TMK |
| 16 | Receipt Line 2 | an 23 | 23 | optional |
| 39 | Receipt Line 3 | an 23 | 23 | optional |
| 62 | Default Merchant Name | an 23 | 23 | optional |

So the legal payload lengths are **8, 16, 39, 62 or 85 bytes**. The first two
blocks are raw binary; everything after byte 16 is ASCII text that replaces
entries in the terminal configuration table.

The correct sequence after `DE39 = 00`:

1. Split DE62 → working key (bytes 0–7), MAC key (bytes 8–15), optional text.
2. Inject the working key into the PIN pad as `POS_TPK` (`0x03`) with
   mode `0x01` (DES-decrypt under the master key), source key index = TMK index.
3. Inject the MAC key as `POS_TAK` (`0x04`), same mode.
4. Read both KCVs back with `posGetKcv` and only then mark the terminal
   logged on.
5. Store any receipt lines / merchant name into the config table.
6. SALE is now permitted. The PIN block in DE52 is produced under the key
   loaded in step 2.

> **Do not pass the whole DE62 payload to `posWriteWorkingKey`.** That was the
> original defect — a 16-byte DE62 was being written as one 16-byte key, so the
> pad ended up holding `WK || MACKEY` as a double-length PIN key. PIN blocks
> then verify against nothing at the host.

---

## 2. The logon request also needed fixing

Spec 6.24 assigns the private-use fields per message type:

```
60   Logon Request       0800
62   Logon Response      0810
62   Invoice/ECR Ref #   0100, 0200, 0220, 0320
```

The request block therefore rides in **DE60**, not DE62 (spec 6.25.6):

| Field | Attr | Bytes | Value |
|---|---|---|---|
| Length Attribute | n 3 | 2 | `'0015'` |
| PIN Pad Id (PPID) | n 16 | 8 | PIN pad serial number, BCD |
| eKTM(PPID) | b 32 | 4 | most significant 32 bits of the PIN key |
| PIN Pad SW Version | n 6 | 3 | BCD |

0800 request also needs DE3 = `920000`, DE11, DE24, DE41 (all mandatory per
spec 7.21). The old code sent the invoice number in DE62 on the 0800, which
the spec has no definition for.

---

## 3. SALE / DEBIT / EBT — 0200 (spec 7.36)

| Bit | Field | Attr | Req | Notes |
|---|---|---|---|---|
| — | Message Type | n 4 | | `0200` (`0210` response) |
| 02 | Primary Acct Num | n ..19 | C01 | keyed entry only |
| 03 | Processing Code | n 6 | M | `00 aa 0x` |
| 04 | Amount | n 12 | M | minor units |
| 11 | STAN | n 6 | M | never `000000`; validates the response |
| 12 | Time | n 6 | | `hhmmss` |
| 13 | Date | n 4 | | `MMDD` |
| 14 | Expiry | n 4 | C02 | keyed entry only |
| 22 | POS Entry Mode | n 3 | C05 | see below |
| 24 | NII | n 3 | M | |
| 25 | POS Condition Code | n 2 | | `00` normal presentment |
| 35 | Track 2 | **z** ..37 | C03 | see §4 |
| 41 | Terminal Id | ans 8 | M | |
| 42 | Card Acq. Id | ans 15 | M | |
| 52 | PIN Data | b 64 | O | 8 bytes from the pad |
| 55 | ICC Sys Related Data | b ...255 | C07 | EMV/chip |
| 62 | Invoice/ECR Ref # | ans ...999 | O | `'0006'` + 6 ASCII chars |
| 63 | Additional Data | ans ...999 | O | table-structured |
| 64 | MAC | b 64 | O | |

**DE3** — `00` (sale) + `aa` account selection (spec 6.2.1: `00` default,
`10` savings, `20` checking, `30` credit, `40` universal) + `0x` where `x` is
processing/flow control, left clear on terminal-originated messages.

**DE22** — positions 1–2 are the PAN entry mode (`01` manual, `02` magstripe,
`05` ICC, `80` magstripe fallback on an ICC-capable card), position 3 is the
PIN entry capability (`1` capable, `2` not). So a chip read on a pad with PIN
entry is `051`.

**DE49 must not be sent.** Currency is not a field in the Hypercom sale layout
at all — it travels as EMV tag `5F2A` inside DE55. The old code sent
DE49 = `050`, which sets a bitmap bit the host does not expect.

**Verified against the spec's own worked example** (6.30): DE62 `998877`
encodes as `0006 393938383737`. Our encoder produces exactly that.

### Response (0210)

- DE39 mandatory, DE37 (RRN) mandatory, DE38 (auth code) optional.
- **DE11 must be echoed and must match** — spec 6.4 states the terminal uses
  the STAN to validate the response.
- DE48 may be present to rotate keys mid-session (see §5).
- DE63 table `'22'` carries alternate host response text (spec 6.28.4.10),
  40 characters, to display instead of the default response text. There is
  **no** response text in DE62 — that was removed in spec revision 3.26.

---

## 4. Track 2 was being encoded wrongly

DE35 has attribute **`z`**, and spec 5.1 defines `z` as *"each data element
represents 1 nibble (2 data elements = 1 byte)"*. Track 2 is therefore
BCD/nibble-packed with `D` as the field separator — **not ASCII**. The old
codec treated DE35 as `LL_ANS`, which roughly doubles the field length and
desynchronises everything after it. Any swiped-card sale would have failed.

Spec 6.30's example confirms it: `29 371449635398431D8912890176666` — an LL of
29 *nibbles*, then 15 bytes of packed data.

Open question for DBBL: the spec does not state the pad nibble for an odd
digit count. We use `0`, matching the zero-fill convention used elsewhere in
the document. `Iso8583.Z_PAD_NIBBLE` switches it to `0xF` if their host
rejects it.

---

## 5. DE48 key rotation (spec 6.19)

DE48 may appear in **any 0210, 0510 or 0810** to replace the current keys:

| Field | Attr | Bytes |
|---|---|---|
| Length Attribute | n 3 | 2 (`'0016'`) |
| eKTM(**KMAC**) | b 64 | 8 |
| eKTM(**KPP**) | b 64 | 8 |

**The order is reversed relative to DE62** — MAC key first here, PIN key
second. Getting this backwards silently swaps the two keys.

---

## 6. MAC — DE64 (spec 6.29)

Two algorithms are defined:

- **6.29.1, full ANSI X9.9**: 8-byte blocks, IV zero, XOR-then-DES on every
  block.
- **6.29.2, "Hypercom MAC"**: XOR all blocks together, DES the final result
  once. Faster, weaker.

Either way the MAC is **the first 32 bits of the MAB, left justified and zero
filled** to the 8 bytes DE64 occupies. Our implementation packs DE64 as a zero
placeholder (so bit 64 is set in the bitmap), MACs from the MTI to just before
the DE64 value — the 5-byte TPDU is excluded — then overwrites the placeholder.

MAC is optional (`O`) on every message. It is off by default; enable it with
`TerminalConfig.setMacEnabled(true)` once DBBL confirms which of the two
algorithms their host uses.

---

## 7. Changes made

New — `app/src/main/java/com/hjq/demo/ans8583/LogonData.java`

- `buildLogonRequest60(ppid, eKtmPpidHex, swVersion)` → the 15-byte DE60 block.
- `parseLogonResponse62(hex, forcedKeyLen)` → splits DE62 into working key,
  MAC key and the three optional text blocks. Auto-detects single-length
  (8-byte, per spec) versus double-length (16-byte, common on modern hosts)
  keys; `TerminalConfig.setWkLen()` forces it.
- `parseKeyUpdate48(hex, forcedKeyLen)` → DE48, MAC key first.

`Iso8583.java`

- DE35 changed from `LL_ANS` to a new nibble-packed `LL_Z` type.
- DE48, 54, 60, 61, 63 changed to binary-safe `LLL_B` so key material and
  mixed ASCII/binary private-use fields survive intact. Added DE43, DE45.
  Helpers `ascii()` / `hexToAscii()` for the text variants.
- Removed DE49 (not in the Hypercom layout).
- **Bitmap fix**: bits are now only set for fields the codec can actually
  encode. Previously an unknown field set its bitmap bit and wrote no payload,
  producing a message the host cannot parse.
- `unpack` now reports `parseError` / `trailingHex` instead of silently
  returning a half-parsed message.
- `macSourceData()` / `applyMac()` / `MAC_PLACEHOLDER` for DE64.

`OnlineHost.java`

- `logon()`: sends DE60, parses DE62, injects `POS_TPK` **and** `POS_TAK`
  separately, reads both KCVs back, stores host receipt text, and sets a
  `loggedOn` flag only when both key writes return 0.
- `transaction()`: correct DE3 with account selection, DE22 derived from the
  actual entry mode and PIN capability, DE14 only on keyed entry, DE35 track 2,
  DE55 EMV, DE62 invoice as ASCII, no DE49. Overload keeps the old 6-argument
  signature working.
- `send()`: STAN echo validation, DE48 key rotation on any response, DE63
  table `'22'` response text, optional DE64 MAC.

`TerminalConfig.java` — added PPID, PIN pad SW version, forced key length, MAC
on/off, logged-on flag, key KCVs, and host-supplied receipt lines.

---

## 7a. The completed sale flow

```
  card read (MSR / ICC / RF)
      │
      ├─ track 2 image kept in full            → DE35  (C03)
      ├─ tag 5F24 or track expiry → YYMM       → DE14  (C02, keyed entry only)
      ├─ EMV tags collected                    → DE55  (C07)
      └─ CardSearchType → entry mode           → DE22  (C05)
      │
  PIN entry ──────────────────────────────────→ DE52
      │
      ├─ no working key from a logon? stop here. An online PIN
      │  enciphered under no key can only be declined.
      │
  flush any pending reversal        (spec 7.35)
      │
  journal the request, arm reversal slot
      │
  0200 ─────────────────────────────────────→ host
      │
      ├─ valid 0210, DE11 matches → clear reversal slot
      │     ├─ DE39 = 00 → EMV GAC2 → batch entry → receipt
      │     └─ DE39 ≠ 00 → decline, DE63 table '22' text shown
      │
      ├─ timeout / socket error → reversal slot stays armed,
      │                           0400 sent now and retried later
      │
      └─ DE11 mismatch, missing DE39, or unparseable → same as timeout
```

Settlement takes a snapshot of the batch, sends the 0500 with DE60 (batch
number) and DE63 (totals), and only clears the batch and advances the batch
number on an approved 0510.

**Reversal (0400, spec 7.35).** Armed *before* the socket write, so a power cut
mid-exchange still leaves something to reverse. Persisted across restarts and
flushed at the start of the next transaction — the spec requires it be "sent
persistently until a valid response to the reversal is received". After 5
attempts it stops auto-retrying and surfaces for manual escalation. The 0400
repeats the original's DE3, DE4, DE11, DE22 and DE35, and adds DE25 which is
mandatory on a reversal.

**Settlement totals (DE63, spec 6.28.1).** 60 bytes of ASCII: capture sale
count/amount, capture refund count/amount, then the same pair for debit. The
authorize and cash-advance groups are compatibility fields the T7 family does
not count (notes 12 and 13), so they are omitted. Declined, voided and
zero-amount entries are excluded per rules 4–10.

**EMV GAC2.** `Emv55.issuerResponse()` pulls tags `91` (Issuer Authentication
Data), `71` and `72` (Issuer Script Templates) out of the response DE55 and
prepends `8A` (Authorisation Response Code), falling back to DE39 when the host
does not send `8A` inside DE55. That TLV blob goes to
`apiEmvKernelAfterOnlineProcess`, and the transaction is only treated as
complete when the kernel then reports status `0x10`. The DE55 parser handles
two-byte tags and long-form lengths, and walks the whole field as spec 6.23
requires.

**No fake approvals.** `simulate()` is gone. An unreachable or misconfigured
host now produces a decline with an explanatory message, and queues a reversal
when the request may have been seen.

---

## 7b. The sale slip

The slip was previously fabricating most of what it printed: a random approval
code, a random RRN, a random STAN, a random ARQC, hardcoded invoice and batch
numbers of `000001`, a fixed TVR/TSI, `EXP DATE: **/**`, and an unconditional
`PIN VERIFIED`. It printed the same thing whether or not the host had approved.

It is now driven entirely by the exchange:

| Slip line | Source |
|---|---|
| Merchant name, address lines | DE62 logon tail (spec 6.27.2), falling back to the built-in constants |
| Batch / invoice / STAN | as actually sent — `TxnRecord` |
| APPR CODE | DE38, **omitted entirely when the host sent none** |
| RREF NUM | DE37, omitted when absent |
| RESP CODE | DE39 |
| Result line | spec section 8 text, or host DE63 table `'22'` text |
| CARD NUM + entry label | DE2 / DE35, and DE22 → CHIP / SWIPE / MANUAL / FALLBACK |
| EXP DATE | DE14 as MM/YY, blank when not sent |
| AID / ARQC / TVR / ATC | EMV tags 84, 9F26, 95, 9F36 out of the DE55 we sent |
| ISSUER AUTHENTICATED | tag 91 present in the response DE55 |
| ISSUER SCRIPT PROCESSED | tag 71 or 72 present in the response DE55 |
| CVM line | EMV tag 9F34 byte 1, falling back to whether DE52 was sent |

**Response text follows the spec's rule exactly.** Section 8 states the
terminal's own text is used "if the host does not send any response text or if
the host response text is less than twenty characters in length". So DE63 table
`'22'` only overrides the table when it is 20 characters or longer.
`ResponseCode.displayText()` implements that, including the `nnnnnn` auth-code
suffix on codes `00` and `09` and the `CALL HELP - IC` catch-all for anything
not in the table. Section 9's terminal-generated codes are in the same table.

Three outcomes now print differently:

- **Approved** — result text plus the real CVM line, then the cardholder
  agreement.
- **Declined** — `*** DECLINED ***` banner and the reason. The approval code
  line is absent, and the cardholder agreement is dropped: there is nothing to
  agree to.
- **Reversed** — `*** TRANSACTION REVERSED ***`, `NO RESPONSE FROM HOST`,
  `CARDHOLDER NOT CHARGED`.

A decline or reversal still prints one merchant copy — the merchant needs the
record — but no longer offers a customer copy.

**GAC2 decline is not an approval.** If the host returns `00` but the chip
refuses at the second Generate AC, the slip prints
`CARD DECLINED TRANSACTION` and suppresses the approval code, rather than
showing a DE39 of `00` for a transaction that never completed.

---

## 7c. Findings from the working-terminal capture

A capture from a working DBBL terminal (0200 request + 0210 response, contact
chip with online PIN) settled three open questions and exposed two real bugs.

**Root cause of "Card declined the transaction" on PIN sales.** Not the card at
all. The sequence was:

1. `transaction()` refuses to send when a PIN block is present but
   `isLoggedOn()` is false, returning `online=false, needsReversal=false`.
2. `completeEmvOnline()` returned `false` whenever `!r.online` — it treated
   "never reached the host" as a chip refusal.
3. `runOnlineTxn()` checked `needsReversal` before `gac2Ok`, so the real
   message was never reached and the generic card-decline toast won.

NFC worked because contactless below the CVM limit is password-free, so no PIN
block is produced, the logon gate never fires, and the transaction goes out
normally. Fixed by making `completeEmvOnline()` return true when there is no
host response to complete against, reordering the checks in `runOnlineTxn()`,
and surfacing `r.error` on both the screen and the slip. **A PIN sale still
requires a successful logon first** — if the slip now says "no working key",
run a logon and check both key writes return 0.

**DE12 and DE13 must not be sent on a 0200.** Spec 7.36 leaves the Request
column blank for bits 12 and 13; they are Response-only. Same for 7.9 cash,
7.27 pre-auth and 7.34 refund. They are mandatory only on the 0220 advices
(7.24 off-line sale, 7.38 sales completion, 7.44 void) and optional on the 0400
reversal. The capture confirms it: the request bitmap `3020058020C01204` carries
bits 3, 4, 11, 22, 24, 25, 35, 41, 42, 52, 55, 62 — no 12 or 13 — while the
response bitmap `303801000E800200` does have them. Now gated on the message
type.

**Track 2 odd-nibble pad is 0xF, not 0x0.** The capture shows DE35 as
`37 | 4840610101024018 d28032260000000147000f` — LL = 37 nibbles in 19 bytes,
so the 38th nibble is padding, and it is `f`. Our encoder now reproduces that
field byte for byte. This closes open item 2 below.

**Packet trace on the slip.** `Iso8583.debugDump()` reproduces the reference
terminal's layout — timestamp, `Length:` in hex, `TPDU:`, `MTI:`, then
`field | hex bytes` eight per line in lowercase — and both the request and
response are printed on the transaction slip so the two terminals' logs can be
compared line for line. Controlled by `TerminalConfig.setPrintPacketTrace()`,
**on by default for certification; turn it off for production** because the
trace contains track data and the PIN block.

## 7d. Second capture: our failing 0200 vs a working terminal, same card

Both logs are the same MasterCard `5576 67** **** 8289`, so the requests are
directly comparable. Ours came back `DE39 = 2a2a` (`**`) with no DE38 and no
DE55; theirs came back `00` with an approval code.

| | ours (declined) | working terminal |
|---|---|---|
| Request bitmap | `3020058020C01204` | `3024058020C01204` |
| DE14 | **absent** | `2608` |
| TPDU | `6000000008` | `6000080000` |
| 5F2A currency | `0156` CNY | `0050` BDT |
| 9F1A country | `0156` China | `0050` Bangladesh |
| 9F33 terminal capabilities | `E0C9C8` | `E0F0C8` |
| 95 TVR | `8400048800` | `0000048000` |
| 9F63 | present, **zero length** | absent |
| 9F1E | present | absent |
| 9F09 / 9F34 / 5F34 | absent | present |

**1. DE14 was missing.** Bitmap byte 2 differs by exactly one bit: `0x20` vs
`0x24`. Spec 7.36 marks DE14 as C02 ("included if the card number was entered
manually"), so our code only sent it for keyed entry — but DBBL's host wants it
on every transaction. Now sent whenever the expiry is known, with a fallback
that reads it from the track 2 image (`expiryFromTrack2`), which recovers
`2608` from this very card — matching what the working terminal sent.

**2. The TPDU is transposed.** The TPDU is `60 | destination(2) | source(2)`.
With NII 008 a request must be `6000080000`. The terminal is configured
`6000000008` — destination `0000`, source `0008`, i.e. the *reply* address. The
host cannot route that to an acquirer application, which is the most likely
source of the `**` response. **This is a configuration value, not code** — fix
the TPDU in the admin panel to `6000080000`. `OnlineHost.checkTpdu()` now logs
a loud error whenever the destination does not match the NII but the source
does, so it cannot recur silently.

**3. The EMV kernel was configured for China.** `strAddition` in
`FindCardActivity` carried `9F1A 0156` and `5F2A 0156` — country and currency
156, which is China and CNY. Every chip transaction was being presented to the
issuer in the wrong currency. Corrected to `0050` (Bangladesh / BDT).

**4. Terminal capabilities.** `9F33` was `E0C9C8`, advertising "no CVM
required" and omitting signature and enciphered offline PIN. The working
terminal sends `E0F0C8`. Corrected.

**5. DE55 contents.** We emitted `9f 63 00` — a zero-length TLV, trailing junk —
and included `9F1E` while omitting `9F09`, `9F34` and `5F34`. `9F34` matters:
it is the CVM result that tells the host the PIN was verified. Tag list
rewritten in ascending order to match the working capture.

**Worth investigating separately:** our TVR byte 1 is `0x84` — *offline data
authentication was not performed* and *ICC data missing* — where the working
terminal has `0x00`. That points at missing CA public keys in the kernel. The
issuer may tolerate it, but it should be clean before certification.

## 7e. Third capture: the message is now correct, the card is declining

The TPDU, DE14, currency and DE55 fixes all landed — the request is now
structurally identical to the working terminal's, same `Length: 00E7`, same
bitmap `3024058020C01204`, `5F2A`/`9F1A` both `0050`, `9F33` `E0F0C8`,
`9F09`/`9F34`/`5F34` present, no `9F63` junk. The host still answers `**`.

**Response parsing is correct.** Verified by byte accounting on the 0210:
TPDU 5 + MTI 2 + bitmap 8 = 15 header bytes; the bitmap
`303801000A800200` selects DE3, 4, 11, 12, 13, 24, 37, 39, 41, 55, whose
lengths sum to 3+6+3+3+2+2+12+2+8+(2+12) = 55; 15 + 55 = 70 = the declared
`Length: 0046`. The field walk consumes the packet exactly, so DE39 really is
`2a 2a`. (Note the DE55 length header `00 12` is BCD for **12**, not 0x12 = 18 —
our codec reads it as decimal, correctly.)

**The problem is in our request, not the response.** Comparing the one field
that decides everything:

| | ours | working terminal |
|---|---|---|
| `9F27` cryptogram information data | `00` = **AAC** | `80` = **ARQC** |
| `95` TVR | `8400048800` | `0000048000` |
| `9F10` IAD, CVR bytes | `8000` | `a040` |

`9F27 = 00` is an **AAC — the card declined the transaction offline**, before
we ever went online. We then asked the host to authorise an already-declined
cryptogram, and it answered `**`. The working terminal sends `9F27 = 80`, an
ARQC, which is a genuine online authorisation request.

**Why the card declines:** TVR byte 1 is `0x84` —

- bit 8: *offline data authentication was not performed*
- bit 3: *CDA failed*

Both set together is the signature of **no matching CA public key**. The kernel
could not find a CAPK for the card's RID and index, so it never performed
offline data authentication, flagged CDA as failed, and the card responded with
an AAC. The working terminal's TVR byte 1 is `0x00` — clean authentication.

**The CAPK table is the thing to fix.** The card is
`AID A0000000041010`, so RID `A000000004` (Mastercard). `PayActivity`
currently loads only indices **03, 04 and 05** for that RID. Modern Mastercard
cards typically use higher indices (`06`, `EF`, `F1`, `F3`, `F5`, `F6`, `F8`).
Two things to check:

1. `downloadInitialParameters()` is called only when
   `AppPreferences.isFirstLaunch()` is true, and only from `PayActivity`. If
   app data was cleared, or the sale flow was reached without passing through
   that path, **no CAPKs are loaded at all**. Verify `loadCapkResult` is true
   in the log on this device.
2. Add the missing Mastercard CAPK indices. The new
   `logOfflineDataAuth()` prints `CAPK index(8F)` on every Generate AC — run
   one sale and it will name the exact index the card wants.

`apiEmvGetKernelProcessResult` also had a real bug: kernel status `0x20`
(card declined) fell through to `startTrans()` and went online anyway.

**That block is now behind `TerminalConfig.setBlockOfflineDecline()`, OFF by
default.** Blocking is strictly correct, but with it on the terminal stops
before sending anything and there is nothing left to diagnose — which is what
happened on the first build. With it off, the decline is logged loudly and the
transaction still goes out, reproducing the `**` capture. Turn it on once chip
transactions authenticate cleanly.

**CAPK loading is no longer once-only.** `downloadInitialParameters()` was
gated on `AppPreferences.isFirstLaunch()`, so a failed or skipped load left the
terminal permanently without CA public keys, with nothing to indicate it. There
is now a separate `isCapkLoaded()` flag set from the actual load result, and
the load is retried on every start until it reports success.

## 7h. PAX reference: sale packet comparison

`PackSale.pack()` sets exactly: `h, m, 41, 42, 3, 4, 11, 14, 22, 24, 25, 35,
52, 55, 62` — **the same 13 data elements we now send**, and the same set the
working terminal's bitmap `3024058020C01204` selects. Field-for-field:

| Field | PAX | ours | |
|---|---|---|---|
| 3 | proc code padded to 6 | `000000` for SALE | match |
| 4 | amount padded to 12 | same | match |
| 11 | 6 digits | same | match |
| 14 | expiry, no padding | `YYMM` | match |
| 22 | see below | **was wrong** | fixed |
| 24 | NII padded to 4 | same | match |
| 25 | `ETransType.SALE` service code `00` | `00` | match |
| 35 | raw track 2 | same | match |
| 52 | PIN block, BCD left-padded | same | match |
| 55 | `strToBcdPaddingLeft` | same | match |
| 62 | invoice padded to 6 | same | match |

`ETransType.SALE("200", "0400", "000000", "00", ...)` confirms MTI `0200`,
processing code `000000` and DE25 `00`.

**DE22 was wrong for no-PIN transactions.** PAX builds it from a *single-digit*
entry code plus a PIN digit, left-padded to four:

```
MANUAL "1"  SWIPE "2"  INSERT "5"  CLSS "7"  FALLBACK "8"
+ (hasPin ? "1" : "0")          //changed
```

The `"0"` is deliberate — the source carries the note *"updated by mobashyer on
24-08-23 to handle field 22,53 problem"*. Spec 6.8 says position 3 should be
`2` for "no PIN entry capability", and that is what we were sending. DBBL wants
`0`.

| | old | new |
|---|---|---|
| chip + PIN | `0051` | `0051` (unchanged, matches capture) |
| chip, no PIN | `0052` | `0050` |
| swipe, no PIN | `0022` | `0020` |
| fallback + PIN | `0801` | `0081` |

Contactless is deliberately left mapped to the ICC code rather than PAX's CLSS
`7`, because the working terminal sends `0051` for a contactless sale and our
NFC transactions already approve with it.

### Other PAX packets that differ from ours — not yet aligned

Flagged rather than changed, since neither is being exercised yet:

- **Reversal.** `PackReversal` sends 41, 42, 2, 4, 11, 14, 22, 24, **38, 39, 60**
  — no DE3, DE25, DE35 or DE62, and it carries a duplicate/reason code in DE39.
  Ours follows spec 7.35 (3, 4, 11, 12, 13, 14, 22, 24, 25, 35, 41, 42, 55, 62).
  Align before testing timeout handling.
- **Settlement.** `PackSettle` sends 7, 11, 12, 17, 24, 37, 53, 63, 64 with
  DE24 of `821`/`500` and DE64 `00000000` — quite unlike our 3, 11, 24, 41, 42,
  60, 63. Align before testing settlement.
- **Void.** `PackSaleVoid` sends 2, 3, 4, 11, 12, 13, 14, 22, 24, 25, 37, 38,
  55, 62 — note DE12/DE13 *are* present here, consistent with the advice rule.

## 7i. PAX reference: key injection and PIN block

### How PAX injects keys

| Step | PAX (`PedHelper`) | ours (Anfu SDK) | |
|---|---|---|---|
| TMK | `writeKey(TLK, 0, TMK, idx, value, KCV_NONE, null)` | `posMasterKey(srcType=0x01 TLK, srcIdx=0, dstType=0x02 TMK, dstIdx=idx)`, `checkMode=0` | same model |
| TPK | `writeKey(TMK, tmkIdx, TPK, INDEX_TPK=3, value, KCV_NONE, null)` | `posWriteWorkingKey(POS_TPK=0x03, len, key, checkMode=0, mode=0x01, mkIndex)` | same |
| TAK | `writeKey(TMK, tmkIdx, TAK, INDEX_TAK=1, ...)` | `posWriteWorkingKey(POS_TAK=0x04, ...)` | same |
| KCV | `KCV_NONE` when no KCV supplied | `kcv.setCheckMode(0)` | same |
| Key size | 16 bytes throughout (`"9D8D00D7DB16D3F8C1C475F192B22751"`) | whole DE62, 16 bytes | same after the 7g fix |

Both inject the TMK in the clear under the transport key, then load working keys
enciphered under it. Our `mode = 0x01` ("decrypt with DES") is the Anfu
equivalent of PAX naming `EPedKeyType.TMK` as the source. **No discrepancy.**

**One PAX quirk that does NOT apply to us.** `PedHelper.getMainKeyIndex(i)`
returns `i + 1`, so the app's "MK index 0" is pad slot 1, and `getKcv` applies
that offset **for TMK only**:

```java
if (type.equals(EPedKeyType.TMK)) {
    return getPed().getKCV(type, (byte) getMainKeyIndex(keyIndex), (byte) 0x00, data);
} else {
    return getPed().getKCV(type, keyIndex, (byte) 0x00, data);
}
// "Updated by Mobashyer D-14-12-23 to handle key inject issue"
```

That is a Neptune/PAX index convention. The Anfu SDK indexes master keys
directly in the range [2,10] and selects working keys by *type*, so our
`mkIndex = 2` with `POS_TPK = 0x03` is correct as written. Do not add a +1.

**The only real defect was the DE62 split**, covered in 7g.

### PIN block

PAX builds the ANSI X9.8 PAN block explicitly and asks for ISO 9564 format 0:

```java
panBlock = "0000" + pan.substring(pan.length() - 13, pan.length() - 1);
ped.getPinBlock(Constants.INDEX_TPK, pinLen, panBlock.getBytes(),
                EPinBlockMode.ISO9564_0, timeout);
```

Our pad does the same internally, **but only if it is handed the real PAN** —
and it was not always being handed one. `showPinPad` was called from six places
with three different sources, including `currentCardNumber`, which is masked for
display (`*****8289`) at one point in the flow, and EMV tag 5A, which is
`F`-padded. Either produces a PIN block the issuer cannot verify.

`panForPinBlock()` now resolves a clean PAN before the pad is shown — trying the
captured sale PAN, tag 5A, the track 2 image, then the caller's value, stripping
non-digits (which removes both the mask and the `F` padding) and requiring
13–19 digits. If none qualifies the transaction is stopped with
"Cannot read card number" rather than sending an unverifiable PIN block.

> Note this produces a *wrong PIN*, not an invalid block, so it is not the cause
> of `HSM DA Error 20` — that was the DE62 split. But it would have surfaced as
> intermittent PIN failures once the key was fixed.

## 7j. The TMK was a key *component*, not the key

DBBL's key ceremony sheet:

```
Clear component1: 54FD 6EF7 C1E9 98DF 4AAD 5716 D35D F7CB
Key check value:  958728
Clear component2: 16F8 8615 C175 A1C2 EAFD 2FC7 0DBF 8657
Key check value:  F8EDD6
Combined Clear XOR key: 4205E8E2009C391DA05078D1DEE2719C
Combined KCV:           65044C
```

The terminal was reading back:

```
Master (TMK)     KCV = 958728D2   <- component 1's KCV
Working (type 3) KCV = F8EDD6AF   <- component 2's KCV
```

So component 1 had been injected as the terminal master key and component 2 as a
working key. **Neither component is a key.** They are XOR shares, split so that
no single custodian ever holds the whole thing, and they must be combined:

```
54FD6EF7C1E998DF4AAD5716D35DF7CB
XOR  16F88615C175A1C2EAFD2FC70DBF8657
  =  4205E8E2009C391DA05078D1DEE2719C     (verified, matches the sheet)
```

With the wrong TMK in the pad, the working key in DE62 decrypts to noise, and
every PIN block the terminal produces is meaningless to their HSM — which is
precisely `HSM T Cmd DA Error 20 / Invalid PIN Block`.

### Fix

1. Master key field: `4205E8E2009C391DA05078D1DEE2719C`, index 2 → **INJECT
   MASTER KEY**.
2. Confirm the read-back TMK KCV starts **`65044C`**. If it does not, the key is
   still wrong — stop and re-check the components.
3. **Do not inject a working key by hand.** The PIN key arrives in DE62 at
   logon and will overwrite whatever is in the type-3 slot.
4. Run **Logon**, then a PIN sale.

`INJECT MASTER KEY` now accepts components directly — paste them separated by a
space, comma, `+` or `^` and they are XORed before injection, with the
breakdown and resulting KCV shown:

```
COMBINING 2 COMPONENTS
  54FD6EF7C1E998DF4AAD5716D35DF7CB
  16F88615C175A1C2EAFD2FC70DBF8657
  XOR = 4205E8E2009C391DA05078D1DEE2719C
```

A single full key still works as before. Three-component ceremonies work too.

> **Always check the KCV against the ceremony sheet after injecting.** It is the
> only way to know the key landed correctly, and it would have caught this
> immediately.

## 7k. Still "Invalid PIN Block" after the TMK fix

Logon is now correct — DE62 arrives as 16 bytes and the whole field goes in as
the PIN key, e.g. `5C9DA8946C0DA774D8E3F95AAA8A1B27`. If the HSM still rejects
the PIN block, only two things remain.

### Fixed on this pass

**Offline PIN was being sent in DE52.** The Anfu `showPinPad` parameter
`ucPinType` is `0 = offline PIN, 1 = online PIN` (documented in
`AmountActivity`). The EMV path passes the kernel's requested type straight
through, so when a card asks for **offline** PIN verification — where the
*card* checks the PIN and nothing should go to the host — we were still
populating DE52. The acquirer's HSM then decrypts that under the TPK and finds
garbage: exactly `DA Error 20`. DE52 is now only sent for an online PIN.

**The PIN pad key index was hardcoded.** `showPinPad` passed a literal `2` as
the master key group while the TPK is written under `TerminalConfig.mkIndex()`.
Those agree today, but a changed index would silently encipher the PIN under a
different key. Now read from config.

### The one measurement that settles the rest

The logon slip prints `TPK KCV`. **Ask DBBL for the KCV of the working key they
sent in DE62 for TID 59007986** and compare:

- **KCVs match** → the key is loaded correctly and the remaining problem is in
  the PIN block itself: format, or the PAN used in the X9.8 block. Ask them to
  confirm they expect ISO 9564 **format 0** with the PAN block being
  `"0000" + rightmost 12 PAN digits excluding the check digit` — that is what
  the PAX reference builds, and what our pad should produce given a clean PAN.
- **KCVs differ** → the key is not landing as they sent it. Since the TMK KCV
  now matches the ceremony sheet, that would point at the write mode
  (`posWriteWorkingKey` mode `0x01`, DES/3DES decrypt under the TMK) rather
  than the key material.

Until that comparison is done, further changes to the PIN path are guesswork.

## 7l. The EMV side is now fixed — what remains is host-side

The 18:00:58 capture shows the card behaving correctly for the first time:

| tag | before | now | |
|---|---|---|---|
| `9F27` | `00` AAC | **`80` ARQC** | card is going online properly |
| `95` TVR | `8400048800` | **`00A0048800`** | byte 1 clean — offline data auth succeeded |
| `5F2A` | `0156` CNY | `0050` BDT | |
| `9F1A` | `0156` | `0050` | |
| `9F33` | `E0C9C8` | `E0F0C8` | |
| `9F34` | absent | `420300` | |

`9F34` byte 1 `0x42` decodes to CVM `0x02` = **enciphered PIN verified online**,
so a genuine online PIN was entered and DE52 belongs in the message. The AAC /
CA public key problem from 7e is resolved.

### Full comparison against PAX — everything matches

| | PAX | ours |
|---|---|---|
| Logon fields | 3, 11, 24, 41, 42 | 3, 11, 24, 41, 42, 62 (matches observed terminal) |
| DE62 handling | whole field → TPK, `KCV_NONE` | same |
| MAC key at logon | not written | not written |
| Sale fields | 3, 4, 11, 14, 22, 24, 25, 35, 41, 42, 52, 55, 62 | identical |
| DE22 | entry digit + (hasPin ? 1 : 0) | identical |
| TMK injection | TLK/0 → TMK, clear | same model |
| TPK injection | TMK → TPK, decrypt under TMK | `mode 0x01`, same |
| PIN block | ISO 9564-0, `"0000"+PAN[len-13:len-1]` | pad derives it from the same PAN |

There is nothing left in the message or the key path that differs from the
reference implementation.

### The remaining clue is in DBBL's own log

```
D-PCI:111: PIN VALIDATION Transaction From Terminal : old_pkvi from table
99:HSM T Cmd DA Error 20
E-AUTH-6511: Invalid PIN Block - pan '44661720****5908'
```

**`old_pkvi from table`** — PKVI is the PIN key verification index. The host is
validating the PIN against an **old PIN key from its own terminal table**, not
the key it issued to us in the DE62 logon response. If their record for TID
59007986 still points at a previous working key, no PIN block this terminal
produces can ever verify, however correct it is.

Every sale slip now prints `MK idx` and `TPK KCV` above the packet trace, so
the evidence travels with the receipt.

### Ask DBBL

1. What is the KCV of the working key you issued to TID 59007986 in the DE62
   logon response? Ours reads `<TPK KCV from the slip>`.
2. Your log says `PIN VALIDATION ... old_pkvi from table`. Is the host
   validating against a stale PIN key index for this terminal? Please refresh
   the terminal's key record.
3. What does your switch return `**` in DE39 for?

## 7m. Our DE62 → TPK → PIN block path, versus PAX

Traced end to end. `PackIso8583.unpack` pulls `map.get("62")` — the field value
with the length prefix already stripped — and hands it to
`PackLogon.unPackField62`, which does exactly two things:

```java
byte[] strTpk = data;              // the whole field
PedHelper.writeTPK(strTpk, null);  // TMK -> TPK, KCV check off
```

`PedHelper.writeTPK` resolves to
`writeKey(TMK, tmkIndex, TPK, INDEX_TPK=3, value, ECheckMode.KCV_NONE, null)`.
At sale time the PIN block comes from
`ped.getPinBlock(INDEX_TPK, pinLen, panBlock, ISO9564_0, timeout)` — the same
slot, with `panBlock = "0000" + pan[len-13 : len-1]`.

Ours, after the fixes:

| step | PAX | ours |
|---|---|---|
| DE62 → key | whole field | whole field (`LogonData`, default path) |
| MAC key | none derived | none derived |
| KCV check on write | `KCV_NONE` | `kcv.setCheckMode(0)` |
| Write | `TMK → TPK` | `posWriteWorkingKey(POS_TPK, len, key, kcv, 0x01, mkIndex)` |
| Key slot at sale | `INDEX_TPK` = 3 | pad's TPK under `mkIndex` group |
| PIN block | ISO 9564-0, X9.8 PAN block | same, pad derives from the PAN we pass |
| DE53 | not sent on a sale | not sent |

There is no remaining difference. Also checked and ruled out: there is **no
separate key-exchange transaction** in PAX — `ETransType.LOGON("800", "",
"920000", "00", ...)` is the only key-bearing message, same as ours.

### New: working-key KCV verification at logon

Because the terminal cannot otherwise tell a good key from a bad one, the logon
now verifies the key that landed against a KCV the acquirer supplies.

**To use it:** admin → key panel → leave **Working key hex empty**, put DBBL's
quoted KCV in **Working key KCV**, press **INJECT WORKING KEY**. It stores the
value rather than injecting anything. Every logon then compares, and on a
mismatch fails with:

```
WORKING KEY MISMATCH: pad holds KCV xxxxxxxx,
acquirer expects yyyyyyyy. The PIN key is not
the one the host issued.
```

Leave both boxes empty and press again to clear it.

That converts the current guesswork into a definite answer: if the KCVs match,
the terminal holds exactly the key DBBL issued and the fault is host-side —
which is what `old_pkvi from table` in their log already suggests.

## 7n. Proof: the keys are right, the PIN block is not

With the TMK now correct (`TMK KCV 65044CBF`, matching the ceremony sheet's
`65044C`) the whole key chain can be verified offline.

### The key path is provably correct

```
TMK                      4205E8E2009C391DA05078D1DEE2719C
TMK KCV computed         65044CBF   = pad reports = ceremony sheet  ✓

DE62 (18:25 logon)       27F69D69D6BF508C7875CF2BA46B95A0
TPK = 3DES-ECB decrypt of DE62 under TMK
                       = 4BF3CCF780BBFCBB7D0B6FE53DDA5265
TPK KCV computed         D81A0385   = pad reports  ✓
```

The pad decrypted DE62 with the correct TMK and holds exactly the working key
DBBL sent. Tested against the alternatives — 3DES encrypt, single-DES, key used
raw — and only "3DES-ECB decrypt under the TMK" reproduces the pad's KCV. **The
logon and key injection are correct.**

### The PIN block is not ISO 9564 format 0 under that key

DE52 from the 18:26:40 sale was `4B0E9F3D98C49452`. Decrypting with the TPK:

```
D(TPK, DE52)             5F10D82D2CE09DF6
XOR X9.8 pan block       0000617209207590
PIN field                5F10B95F25C0E866
                         ^^ format nibble 5, length nibble F
```

In ISO format 0 the PAN block is `0000` + 12 PAN digits, so **the first two
nibbles are never affected by the PAN**. A correct block must decrypt to `0N`
with N between 4 and 12, whatever PAN is used. This one gives `5F`.

Searched every plausible key — the current and previous DE62 keys under the
correct TMK and under each component, the TMK and components themselves, the
raw DE62, and the hardcoded test keys in `KeyManager` — under both 3DES and
single DES, against four PAN-block derivations. **Nothing yields a valid
format-0 block.**

So the pad is either not producing ISO format 0, or not using the key we
loaded at `(index 2, type 3)` despite that key reading back correctly.

### The test that identifies it

**Long-press PRINT KCV** in the admin key panel. It shows the pad with a fixed
known PAN `4466172092075908` and prints the raw block. **Enter PIN `1234`.**

Because the clear TPK is now known, the correct answer can be computed in
advance:

```
clear ISO-0 block   041234FFFFFFFFFF
XOR pan block       0000617209207590
                  = 0412558DF6DF8A6F
3DES encrypt (TPK)= 3E15870725329796
```

- **Pad prints `3E15870725329796`** → the pad is correct and the earlier block
  came from a different PAN or PIN, which points back at
  `panForPinBlock()` picking the wrong source.
- **Pad prints something else** → decrypt it with the TPK to read the format
  nibble directly. That tells us whether it is format 1/2/3 (a pad
  configuration issue) or enciphered under a different key (a key-slot issue
  in `showPinPad`'s `keyIndex` semantics versus `posWriteWorkingKey`'s
  `usMKeyNo`).

Either way this converts the remaining unknown into a single measurement.

## 7o. ROOT CAUSE: the logon was overwriting the terminal master key

The observation that **the TMK KCV changes after a logon** is the whole
problem, and it is our code doing it.

**Nothing in the Hypercom spec changes the TMK at logon.** Section 6.27.2 says
the DE62 logon response replaces the *working* key ("This key is encrypted
under the master key"); 6.19 lets DE48 replace the PIN and MAC keys. The master
key is injected once, out of band, and is never touched by a transaction.

### Why it happened

We were writing the working key with:

```java
posWriteWorkingKey(usKeyType, ucKeyLen, keyData, kcvInfoParam, ucMode, usMKeyNo)
```

That call has **no destination index** — only `usMKeyNo`, the source. With
source and destination collapsed into a single index, the pad wrote the
incoming working key over the master key at that index. So each logon:

1. decrypted DE62 under the current TMK,
2. then replaced the TMK with it.

The TPK had been derived from the *old* TMK while everything afterwards used
the *new* one. The PIN block was therefore enciphered under a key the acquirer
could not reproduce — `HSM T Cmd DA Error 20 / Invalid PIN Block` — and the
next logon decrypted DE62 under a TMK that was no longer the one DBBL shares,
so it could never recover on its own.

It also explains why the arithmetic in 7n came out inconsistent: the KCV read
back matched a TPK derived from the TMK *as it was during that logon*, but by
the time the PIN was entered the slot held something else.

### The fix — mirror PAX exactly

PAX writes keys with an explicit source **and** destination:

```java
writeKey(EPedKeyType.TMK, tmkIndex,     // source
         EPedKeyType.TPK, INDEX_TPK,    // destination
         tpkValue, ECheckMode.KCV_NONE, null);
```

The Anfu SDK has the direct equivalent, which we were not using:

```java
int dalPosWriteKey(PstKeyInfoIn info, PstKcvInfoIn kcv);

PstKeyInfoIn: srcKeyType, srcKeyIdx, dstKeyType, dstKeyIdx, dstKeyLen, dstKeyValue
```

`OnlineHost.writeKey` now uses it:

| | value |
|---|---|
| `srcKeyType` | `POS_TMK` (0x02) — decrypt under the master key |
| `srcKeyIdx` | `TerminalConfig.mkIndex()` — where the TMK lives |
| `dstKeyType` | `POS_TPK` (0x03) |
| `dstKeyIdx` | `IDX_TPK` (3) — **a different slot from the TMK** |
| `dstKeyLen` | 16 |
| `dstKeyValue` | the whole DE62 field |
| `checkMode` | 0 — DE62 carries no KCV, matching PAX's `KCV_NONE` |

Source and destination are now separate, so **a logon can no longer touch the
TMK**. The ambiguous `ucMode` / `usMKeyNo` pair is gone entirely — the source
key type implies the decryption, exactly as in PAX.

Everything that reads or uses the PIN key now points at the same slot:

- `readKcv(IDX_TPK, POS_TPK)` for the KCV shown on the logon slip and KCV report
- `showPinPad(OnlineHost.IDX_TPK, ...)` for the PIN block — PAX likewise calls
  `getPinBlock(Constants.INDEX_TPK, ...)`
- MAC key, when one is ever used, goes to `IDX_TAK` (1), as PAX does

### DE62 now follows spec 6.27.2 exactly

The spec page is unambiguous:

| Field | Attribute | Bytes | Values |
|---|---|---|---|
| Length Attribute | n 3 | 2 | `'00LL'` |
| **Encrypted Working Key** | b 64 | **8** | replaces the current encryption key, encrypted under the master key |
| **MAC Key** | b 64 | **8** | replaces the current MAC key |
| Receipt Line 2 / 3 / Merchant Name | an 23 | 23 each | optional |

So a 16-byte DE62 is an **8-byte working key followed by an 8-byte MAC key** —
not one 16-byte key. `LogonData` is back to the spec layout, with
`TerminalConfig.wkLen()` available only as an override.

DE52 is then just what the pad produces under that working key — spec 6.20:
*"PIN data as received from the PIN pad… The Ice family terminals have secure
internal PIN pads and both encrypt and pass the PIN data."* The terminal does
not construct it.

### The TMK is now protected three ways

1. **Separate destination slot.** `dalPosWriteKey` writes the working key to
   `IDX_TPK` (3) and the MAC key to `IDX_TAK` (1), sourced from the TMK at
   `mkIndex`. Source and destination are distinct parameters, so a logon has no
   way to address the master key slot.
2. **KCV recorded at injection.** Injecting the master key stores its KCV.
3. **Verified on every logon.** The TMK KCV is read back after each logon and
   compared. If it moved, the logon reports
   `TMK CHANGED DURING LOGON: was xxxx, now yyyy` and the KCV report prints
   `*** TMK CHANGED - RE-INJECT ***`. Otherwise it prints
   `TMK UNCHANGED - correct`.

### What to expect after this build

1. Inject the TMK once: `4205E8E2009C391DA05078D1DEE2719C`, KCV `65044CBF`.
2. Run **Logon**.
3. Print the KCV report. **The TMK KCV must still read `65044CBF`.** If it does,
   the overwrite is fixed. The TPK KCV will be a separate value at slot 3.
4. Run several logons — the TMK KCV must not move.
5. PIN sale.

## 7p. Research: the likely remaining cause is the HSM key-export scheme

### Error 20 confirmed

The published Thales payShield error table gives:

| Code | Meaning |
|---|---|
| **20** | **PIN block does not contain valid values** |
| 24 | PIN is fewer than 4 or more than 12 digits |
| 23 | Invalid PIN block format code |
| 27 | Incompatible key length |
| 75 | Single length key masquerading as double or triple length key |
| 01 | Verification failure |

So `DA Error 20` means the decrypted block is structurally invalid — not a
wrong PIN (that would be `01`) and not a length problem (`24`, `27`, `75`).
That matches what we computed by hand: the block decrypts to `5F…`, not `0N`.

### The cause we had not considered: export key scheme

When a host HSM exports a working key encrypted under a terminal master key, it
does so under one of two schemes:

- **X9.17, scheme `'X'`** — plain 3DES-ECB over the key
- **Thales/RACAL native variant, scheme `'U'`** — a variant transform is applied
  to the KEK first

A well-known troubleshooting write-up on Thales key exchange puts it plainly:

> *"We find some endpoint partners have no idea which Key Scheme (X9.17 or Racal
> Native) they employed to create their keys. So, you may have to experiment…
> You absolutely must maintain consistency."*

It also notes a third case: if the far side runs an **Atalla** HSM, an Atalla
variant must be specified.

**Our PIN pad can only do plain 3DES-ECB** — that is what
`dalPosWriteKey(srcKeyType = TMK, …)` performs. That is scheme `'X'`. If DBBL's
HSM exported the working key under scheme `'U'`, the pad decrypts DE62 to a
key that is silently wrong, and no amount of terminal-side change will fix it.

### Why this fits every observation

- The TMK is correct — its KCV matches the ceremony sheet.
- The key write returns 0. It cannot fail: DE62 carries no KCV, so
  `checkMode = 0` and the pad has nothing to check against.
- The derived working key is therefore **plausible but possibly not the intended
  one**, and nothing on the terminal can detect that.
- The PIN block enciphered under it decrypts to noise at the acquirer →
  error 20.

**Important caveat on our earlier "proof".** Computing the TPK as a plain
3DES-ECB decrypt of DE62 under the TMK produced a KCV matching the pad's. That
proves the pad does plain 3DES-ECB. It does **not** prove the result is the key
DBBL intended — under scheme `'U'` the pad and our calculation would agree with
each other and both be wrong.

### The question for DBBL

> Under which key scheme does your HSM export the working key sent in the DE62
> logon response — **X9.17 (`'X'`)** or **Thales/RACAL native variant (`'U'`)**?
> Our PIN pad performs a plain 3DES-ECB decrypt under the TMK, which requires
> `'X'`. Also please confirm the KCV of the working key you issued to TID
> 59007986; ours reads `D81A0385`.

If they answer `'U'`, they must re-export under `'X'`, or the terminal will
never derive the right key. If they answer `'X'` and the KCVs still differ, the
next suspect is the `old_pkvi` entry in their terminal table.

### Sources

- Thales payShield error codes — <https://krestfield.github.io/docs/ezsign/payshield_error_codes.html>
- Thales key exchange troubleshooting, scheme `'X'` vs `'U'` — <https://arthurvandermerwe.com/2014/08/06/thales-key-exchange-examples-and-troubleshooting/>

## 7q. DE62 handling set to match PAX line for line

`PackLogon.unPackField62` does exactly three things:

```java
byte[] strTpk = data;                              // 1. the WHOLE field
PedHelper.writeTPK(strTpk, null);                  // 2. TMK -> TPK, KCV_NONE
Arrays.copyOf(PedHelper.getKcv(...), 3);           // 3. read the KCV back, 3 bytes
```

Ours now mirrors each step:

| PAX | ours |
|---|---|
| `byte[] strTpk = data` — no split | `parseLogonResponse62(hex, 0)` → whole field, `macKey = null` |
| no MAC key taken from DE62 | none |
| no receipt text taken from DE62 | none |
| `writeTPK(value, null)` → `KCV_NONE` | `dalPosWriteKey`, `checkMode = 0` |
| source TMK → destination TPK slot | `srcKeyType = POS_TMK @ mkIndex`, `dstKeyType = POS_TPK @ IDX_TPK` |
| `Arrays.copyOf(getKcv(...), 3)` — **3-byte KCV** | `readKcv` now returns 6 hex chars |
| `PedDevException` → `ERR_WRITE_LOGON_KEY` | non-zero write ret fails the logon |

**KCVs are now reported as 3 bytes / 6 hex characters** — the standard KCV
length, what PAX reads, and the format on DBBL's ceremony sheet. The TMK reads
`65044C`, which is character-for-character what the sheet says, instead of the
8-character `65044CBF` we printed before. That removes a needless difference
when comparing values with DBBL.

The spec 6.27.2 split (8-byte working key + 8-byte MAC key) is still available
via `TerminalConfig.setWkLen(8)` for a host that needs it, but it is no longer
the default. Rationale: 6.27.2 describes the single-length DES era; DBBL issue
one double-length key, and PAX is the implementation certified against their
host.

## 7r. DE52 aligned to PAX

```java
protected void setBitData52(@NonNull TransData transData) throws Iso8583Exception {
    if (transData.isHasPin() && transData.getPin() != null) {
        setBitData("52", ConvertHelper.getConvert().strToBcdPaddingLeft(transData.getPin()));
    }
}
```

Two things that matter: the **whole** PIN block is used, and it is padded on the
**left**. Ours did `substring(0, 16)` and let the codec pad on the right.

| pad returns | old | new |
|---|---|---|
| `4B0E9F3D98C49452` (16 chars) | `4B0E9F3D98C49452` | `4B0E9F3D98C49452` |
| 15 chars | field omitted | `0B0E9F3D98C49452` |
| 8 chars | field omitted | `000000004B0E9F3D` |
| 20 chars | silently truncated to 16 | sent whole, with a warning |
| empty | omitted | omitted |

The 16-character case — what the pad actually returns today — is identical
either way, so this is not the current failure. But right-padding a short block
shifts every nibble, and truncating a long one discards the tail, either of
which would produce exactly the "PIN block does not contain valid values" the
acquirer reports. Now it cannot happen, and an unexpected length is logged
rather than quietly mangled.

The `hasPin` gate is already matched: DE52 is omitted for a password-free
transaction and for an offline PIN, where the card does the verification.

## 7s. Correction: Anfu addresses keys as (index, TYPE), not by separate slot

`onPinPadError-5011`, and a KCV report showing `TPK(3) KCV: ret=-4528`.

I had copied PAX's model of writing the TPK to its own index
(`Constants.INDEX_TPK = 3`). **That is a Neptune convention and does not
transfer.** The Anfu pad addresses keys as **(master key index, key type)** —
which `posGetKcv(keyIdx, keyType)` makes plain. A working key therefore lives at
the *same* index as the master key it was loaded under, distinguished only by
its type. Index 3 holds no key set at all, hence:

- `posGetKcv(3, POS_TPK)` → `ret=-4528`
- `showPinPad(3, …)` → `onPinPadError-5011`

Reverted: the working key is written to, read from, and used at
`TerminalConfig.mkIndex()`, with `dstKeyType = POS_TPK`.

**The TMK is still protected.** That never depended on a separate index — it
depends on `dalPosWriteKey` naming the destination *type* explicitly. The master
key is `POS_TMK` and the working key `POS_TPK` at the same index; different
type, no collision. The original overwrite came from `posWriteWorkingKey`, which
has no destination type/index pair at all and left the pad to guess.

PIN length limits also backed off from `{0,4,12}` to `{0,4,6}` — the range this
pad is known to accept. The bypass flag stays `0` so a PIN cannot be skipped.

### Still to do

- **DE55 tag set and order.** The reference sends tags in ascending order:
  `5F2A 82 84 95 9A 9C 9F02 9F03 9F09 9F10 9F1A 9F26 9F27 9F33 9F34 9F35 9F36
  9F37 9F41 5F34`. Ours (`icDataTAGList` in `FindCardActivity`) is in a
  different order and includes `9F1E`/`9F63` while omitting `9F09`/`5F34`.
  Spec 6.23 says PDSs "can appear in any order", so this is probably harmless,
  but align it to remove a variable before certification.
- **Slip should print TC, not ARQC, after GAC2.** The reference prints
  `TC : 9B01E10C18D884FC` — tag 9F26 re-read *after* the second Generate AC.
  We currently print the ARQC from the request. Re-read 9F26 in
  `completeEmvOnline()` once GAC2 succeeds.
- Consider auto-running a logon when a PIN sale finds no working key, instead
  of dead-ending.

---

## 7f. Reinstalling the app — commissioning checklist

Uninstalling wipes SharedPreferences, so **every terminal setting returns to
its default**. The terminal master key is not affected: it lives in the PIN
pad's secure hardware, not in app storage.

### Before you uninstall

1. **Settle the open batch.** Run a settlement so the host and terminal agree.
   Anything left in the batch is lost from the terminal's side while the host
   still holds it.
2. **Check no reversal is pending.** If the last transaction timed out, let the
   terminal deliver its 0400 first.
3. **Write down** the current TID, MID, host IP, port, NII, TPDU, and the
   current **STAN, batch and invoice numbers**.

### What resets, and what it resets to

| Setting | Default after reinstall | Action |
|---|---|---|
| TID | `48290000` | **change to `59007986`** |
| MID | `002948290000001` | **change to `000591100447600`** |
| Host IP | *(empty)* | **must be set** |
| Host port | `5000` | set as issued |
| NII | `008` | correct already |
| TPDU | `6000080000` | correct already — **this is the value that was wrong before** |
| Timeout | 30000 ms | fine |
| STAN / batch / invoice | all `1` | **continue from the old app's last values** |
| Logged on | false | logon required |
| MAC (DE64) | off | leave off until DBBL confirms the algorithm |
| Packet trace on slip | **on** | leave on for testing, off for production |
| Block offline decline | off | leave off until chip auth is clean |

Only TID, MID, host IP/port, NII, TPDU, timeout, STAN, batch and invoice are
editable from the admin panel. The rest have correct defaults for testing.

> **Do not leave STAN, batch and invoice at 1.** The host has already seen those
> numbers from this terminal. Duplicates can come back as DE39 = `94`
> (duplicate transmission). Set them past the highest values the old app used.

### Order of operations

1. **Install and launch.** Watch logcat immediately:
   ```
   AID/CAPK not confirmed loaded - loading now
   downloadInitialParameters: loadCapkResult-->true
   ```
   If that last line says `false`, **stop** — the CA public keys are not in the
   kernel and every chip card will decline offline. Nothing else will work.
2. **Admin panel:** set TID, MID, host IP and port. Confirm NII is `008` and
   TPDU is `6000080000`.
3. **Set STAN, batch and invoice** past the old app's last values.
4. **Run Logon.** Expect `LOGON SUCCESSFUL`. In the log confirm:
   ```
   posWriteWorkingKey type=0x3 ... ret=0     (PIN key)
   posWriteWorkingKey type=0x4 ... ret=0     (MAC key)
   ```
   Both must be 0, and both KCVs non-zero. If they are not, the terminal master
   key at index 2 is wrong or absent — fix that before going further.
5. **Run one small chip sale.** The slip prints the full request and response.

### The four lines that tell you whether it is fixed

On the printed trace and in logcat:

| Look for | Good | Bad |
|---|---|---|
| `GAC1 ODA: ... 9F27=` | `80` (ARQC) | `00` (AAC — card declined offline) |
| `GAC1 ODA: TVR=` | starts `00` | starts `84` (no CA public key) |
| Request `14 \|` | present, e.g. `26 08` | absent |
| Response `39 \|` | `30 30` = `00` | `2a 2a` = `**` |

If `9F27` is still `00`, read the `CAPK index(8F)` value on the same log line —
that is the key the card wants, and the Mastercard table in `PayActivity`
currently holds only indices `03`, `04` and `05`.

---

## 7g. Host log: "Invalid PIN Block" — the working key is wrong

DBBL's host log gives the answer directly:

```
D-PCI:111: PIN VALIDATION Transaction From Terminal : old_pkvi from table
99:HSM T Cmd DA Error 20
TEST ret is [-1]
E-AUTH-6511: Invalid PIN Block - pan '44661720****5908'
D-AUTH8745: CDM Check PCode[000000],Respcode[81]
```

`DA` is the HSM's verify-PIN command and **error 20 means the decrypted PIN
block is not structurally valid** — after decryption it is not a well-formed
ISO format-0 block at all. That is a different failure from a wrong PIN, which
would come back as a verification failure. Respcode `81` is a cryptographic /
PIN key error.

So the host decrypted our DE52 with the working key it believes this terminal
holds, and got noise. **The PIN block itself is fine; the key it was enciphered
under does not match.** Three possible causes, in order of likelihood:

1. **DE62 split into the wrong key lengths.** Spec 6.27.2 defines the logon
   response as an 8-byte working key followed by an 8-byte MAC key, and that is
   what we implement. But a 16-byte DE62 is genuinely ambiguous: it is equally
   consistent with a **single 16-byte double-length PIN key**, which is what
   most modern hosts issue. If that is what DBBL sends, we are loading the first
   half of their key as the whole PIN key, and every PIN block we produce is
   garbage to them — exactly what the HSM is reporting.
2. **Terminal master key mismatch.** DE62's keys arrive enciphered under the
   TMK. If the TMK at index 2 is not the one DBBL used, the working key decrypts
   to noise with the same symptom.
3. PIN block format or PAN mismatch — unlikely here, because those produce a
   *wrong PIN*, not an invalid block.

### RESOLVED — the PAX reference settles it

`DBBL Dev Main/bizlib/.../onlinepacket/impl/PackLogon.java`, the known-good
DBBL implementation:

```java
protected int unPackField62(TransData transData, byte[] data) {
    //encrypted tpk
    byte[] strTpk = data;              // THE WHOLE FIELD
    PedHelper.writeTPK(strTpk, null);  // written as TPK, KCV check off
}
```

**The entire DE62 is the PIN key.** There is no 8+8 split, no MAC key derived
from DE62 (`PedHelper.writeTAK` exists but `PackLogon` never calls it), and no
receipt text parsed. `writeTPK(value, null)` passes a null KCV, which
`PedHelper` turns into `ECheckMode.KCV_NONE` — matching our
`kcv.setCheckMode(0)`.

So on a 16-byte DE62 we were loading only the first 8 bytes as the PIN key and
the second 8 as a MAC key. Every PIN block the terminal produced was enciphered
under half of the host's key, and their HSM saw noise:
`HSM T Cmd DA Error 20 / Invalid PIN Block`. **This was my error** — I changed
working code to follow spec 6.27.2 during the first pass. Now corrected:
`LogonData.parseLogonResponse62` uses the whole field by default, and
`TerminalConfig.wkLen()` can force the spec split for other hosts.

The PAX logon request is also just DE3, 11, 24, 41, 42 (`setMandatoryData` =
header, MTI, 41, 42; `PackLogon` adds 3, 11, 24). It sends no DE60 and no DE62.
Ours sends DE62 = invoice, matching the observed working terminal, which is
harmless either way.

### How to tell which

The logon slip now prints everything needed:

```
--- DE62 KEY BLOCK ---
raw len : 16 bytes
raw     : ....
keyLen  : 8 bytes each
PIN key : ....
MAC key : ....
--- KEYS IN PIN PAD ---
MK index: 2
TPK write ret=0
TAK write ret=0
TPK KCV : ........
TAK KCV : ........
TMK KCV : ........
```

**Send those three KCVs to DBBL and ask them to compare against their HSM.**

- TMK KCV differs → cause 2, the master key is wrong. Nothing else will work
  until it is re-injected.
- TMK KCV matches but TPK KCV differs → cause 1, the split is wrong.

### Trying the other split without a rebuild

**Long-press READ KCV in the admin key panel** to cycle
`TerminalConfig.wkLen()` between auto (8 + 8), 16 (single double-length PIN
key) and 8. It clears the logged-on flag, so run **Logon** again afterwards,
then retry a PIN sale. If the 16-byte interpretation is right, the TPK KCV will
change and start matching DBBL's.

> Note this is independent of the AAC / CA public key problem in section 7e.
> Both have to be fixed: the card must produce an ARQC, *and* the PIN block
> must verify. The host log above shows the PIN failure because that check runs
> before the cryptogram is considered.

---

## 8. Open items to confirm with DBBL

1. ~~**DE62 key length**~~ **RESOLVED: the whole field is the PIN key**, and no
   MAC key comes from DE62. Confirmed from the PAX reference
   (`PackLogon.unPackField62`) and corroborated by the host's "Invalid PIN
   Block" error. See section 7g.
2. ~~**Track 2 odd-nibble pad** — `0` or `F`.~~ **RESOLVED: `0xF`**, confirmed
   from the working-terminal capture. See section 7c.
3. **MAC algorithm** — full ANSI X9.9 (6.29.1) or the Hypercom fast variant
   (6.29.2)? And is DE64 required at all on their host?
4. **Message framing** — the spec (section 4) describes an HDLC-style
   `ADR/CB/TPDU … CRC` frame. Over TCP we use a 2-byte big-endian total-length
   header with no CRC, which is the usual Hypercom-over-IP adaptation.
   Confirm this is what their host expects.
5. **PPID and PIN pad SW version** — what values do they want in DE60? Right
   now these default to zeros and must be set per terminal.
6. **eKTM(PPID)** — on a first logon there is no PIN key to derive it from.
   Confirm whether zeros are acceptable.
7. **TMK injection** — DE62 keys are enciphered under the terminal master key,
   so the TMK must already be present at the configured index (default 2)
   before logon. Confirm how DBBL delivers it.
8. **DE53** (Security Control Info) is marked optional and
   "application dependent" — does their host require it?
9. **Debit vs capture split in DE63** — we classify an entry as debit when a PIN
   block was present. Spec 6.28.1 rules 1–3 define it by the card table's
   capture/debit issuer flags instead. Confirm which they reconcile against.
10. **Account selection** — DE3 digits 3–4 are hardcoded to `00` (default
    account). Does DBBL want a savings/checking prompt on the pad?
11. **Reversal on a declined 0210** — we only reverse when no valid response
    arrived. Confirm they do not also expect a reversal after a decline.

---

## 9. Test checklist

- [ ] Logon returns DE39 `00`; log the raw DE62 hex and its byte count.
- [ ] `posWriteWorkingKey` returns 0 for both `POS_TPK` and `POS_TAK`.
- [ ] `posGetKcv` returns a non-zero KCV for both keys.
- [ ] SALE with a chip card: DE22 = `051`, DE55 populated, DE49 absent.
- [ ] SALE with a swiped card: DE35 present and nibble-packed, DE2 absent.
- [ ] SALE with a keyed PAN: DE2 and DE14 present, DE22 = `011`/`012`.
- [ ] Online PIN sale approves — proves the working key split was right.
- [ ] DE11 in the 0210 matches the STAN sent.
- [ ] Decline path: check DE63 table `'22'` for host text.
- [ ] Pull the network cable mid-sale: transaction must decline, and a 0400
      must go out on the next attempt with the *original* STAN.
- [ ] Kill the app mid-sale: the reversal must still be sent after restart.
- [ ] Chip sale: confirm `apiEmvKernelAfterOnlineProcess` returns 0 and the
      kernel reports `0x10`.
- [ ] Settlement: DE63 totals must equal the printed slip totals, and the batch
      must only clear on an approved 0510.
- [ ] Slip on approval: approval code and RRN match DE38 / DE37 exactly.
- [ ] Slip on decline: no approval code printed, no cardholder agreement.
- [ ] Slip on reversal: reversed banner, "CARDHOLDER NOT CHARGED".
- [ ] Ask DBBL to send a >= 20 character DE63 table `'22'` text and confirm it
      replaces the terminal's own decline text.

### Test sale setup

Before the first test sale, in the admin panel:

1. Host IP and port, NII, TPDU, TID, MID — as issued by DBBL.
2. Terminal master key loaded at the configured index (default 2).
3. PPID and PIN pad software version for DE60.
4. Run **Logon** and confirm both key writes return 0 and both KCVs read back
   non-zero. Nothing else will work until this passes.
5. Leave MAC disabled until DBBL confirms the algorithm.

# Terminal master key injection — DBBL, TID 59007986

## The key

| | value |
|---|---|
| Component 1 | `B67CA1C44C736D231073CE29A8EA94E0` — KCV `7DB972` |
| Component 2 | `6DA1079BFE1CA438D3AEFBFED02C4A04` — KCV `60DBA6` |
| **Key to inject** | **`DBDDA65FB26FC91BC3DD35D778C6DEE4`** |
| **Expected KCV** | **`63C7AE`** |

All three KCVs were verified independently, and the XOR of the two components
reproduces the combined key exactly. The sheet is correct.

**The two components are not keys.** They are XOR shares, so no single custodian
holds the whole key. Injecting either one on its own gives a master key the
acquirer knows nothing about.

---

## Key index

**Use index 2.**

The Anfu PIN pad reserves master key slots **2 to 10** — `KeyManager` enforces
this with `MIN_MAINKEY_INDEX = 2`, `MAX_MAINKEY_INDEX = 10`. Index 0 and 1 are
not valid master key slots and the SDK will reject them.

Index 2 is the app's default (`TerminalConfig.DEF_MK_INDEX`), and everything
downstream reads it from there:

- the working key from DE62 is written with the TMK at this index as its source
- `showPinPad` takes the PIN key from this index
- the KCV report reads this index

So there is nothing else to change — set it once in the key panel and leave it.

> The `0` you may have seen is `srcKeyIdx = 0` inside `pinpad_LoadMasterKey`.
> That is the *source* key, not the destination. Source type `0x01` / index `0`
> is the SDK's way of saying "this key is being supplied in the clear". The
> destination is `dstKeyIdx = 2`, `dstKeyType = 0x02` (TMK). Do not change it.

---

## Injection steps

1. Admin panel → **KEY INJECTION**.
2. **Key index**: `2`
3. **Master key (TMK) hex**: paste either

   ```
   DBDDA65FB26FC91BC3DD35D778C6DEE4
   ```

   or paste both components separated by a space and let the app XOR them:

   ```
   B67CA1C44C736D231073CE29A8EA94E0 6DA1079BFE1CA438D3AEFBFED02C4A04
   ```

   The second form prints the working so you can check it.
4. Press **INJECT MASTER KEY**.
5. **Confirm the result says KCV `63C7AE`.** If it says
   `*** MASTER KEY NOT LOADED ***`, stop — the key is not in the pad and nothing
   downstream can work. Note the `posMasterKey returned N` value.

**Leave the working key fields empty. Do not inject a working key by hand.**
The PIN key arrives from the host in DE62 at logon. A manual working-key
injection is the most likely cause of the TMK being wiped last time — see below.

---

## After injecting

1. **PRINT KCV** — confirm `TMK KCV: 63C7AE`.
2. Run **LOGON**.
3. **PRINT KCV again — the TMK must still read `63C7AE`.**

If it changed, the logon's working-key write is overwriting the master key
slot. That is the fault that put the pad into the state you saw.

---

## What a wiped key looks like

A key slot that has never been written reads back with the KCV of sixteen zero
bytes:

```
TMK KCV: 8CA64D      *** NO KEY ***
```

`8CA64D` is **not a key**. It is what an empty slot reports, and it is easy to
mistake for a real value. The app now labels it explicitly and refuses to record
a successful injection when it sees it.

Your terminal has been in this state: the slip showed `TMK at injection: 63C7AE`
(the correct key, loaded earlier) alongside `TMK KCV: 8CA64D` (the slot now
empty). So the key went in, then something erased it.

---

## Why it was erased, and what changed

The logon used to load the working key with:

```java
posWriteWorkingKey(usKeyType, ucKeyLen, keyData, kcvInfoParam, ucMode, usMKeyNo)
```

That call has **no destination index** — only `usMKeyNo`, the source. With
source and destination collapsed into one value the pad wrote the working key
over the master key slot.

It has been replaced with `dalPosWriteKey`, which names the source and
destination separately:

| | |
|---|---|
| `srcKeyType` | `POS_TMK` (0x02) — decrypt under the master key |
| `srcKeyIdx` | 2 |
| `dstKeyType` | `POS_TPK` (0x03) |
| `dstKeyIdx` | 2 |

Different *type*, same index — so the working key cannot land on the master key.
There is deliberately **no fallback** to the old call: losing the TMK is worse
than a failed key load, because the TMK can only be restored by repeating the
key ceremony.

The logon also reads the TMK KCV before and after the write, so any recurrence
is reported rather than discovered days later.

---

## ROOT CAUSE FOUND — the app was overwriting its own keys

`FindCardActivity.devicesSign()` was the Anfu demo's key bootstrap. With
`FORMAL_TRANSACTION = false` it ran on **every transaction**, from three call
sites in the card flow, and did this:

```java
private boolean setPOSMasterKey() {
    keyInfoParam.setDstKeyIdx((short) 2);
    keyInfoParam.setDstKeyLen(16);
    keyInfoParam.setDstKeyType((byte) 0x02);          // TMK
    byte[] mainKey = HexUtil.hexStringToByte(
        "000000000000000000000000000000008CA64DE9C1B123A7");
    keyInfoParam.setDstKeyValue(mainKey);
    ...
}
```

`dstKeyLen` is 16, so only the **first sixteen bytes** are written — and those
are all zeros. It then loaded three hardcoded demo working keys:

| slot | key | KCV in source |
|---|---|---|
| keyID 3 (TPK) | `8FDAF0CB17FFA9D24A87FC2447CE7EEB` | `4481F582` |
| keyID 4 (TAK) | `F7596E31000D9B23B3F011E87071A6B8` | `5BB56FBE` |
| keyID 5 (TDK) | `17B6319EFDDBE41771AE66550DFB7D12` | `20727651` |

Compare with what the terminal reported:

```
TMK KCV : 8CA64D     = KCV of sixteen zero bytes
TPK KCV : 4481F5     = the demo PIN key above
TAK KCV : 5BB56F     = the demo MAC key above
```

**Every key in the pad was this demo block.** The master key you injected was
destroyed on the next transaction, and the PIN block was being enciphered under
an Anfu demo PIN key — which is exactly why DBBL's HSM reported
`Invalid PIN Block`. It was never a message-format problem, a key-scheme
problem, or a stale `pkvi` on their side.

It also explains the sequence you saw: inject → `63C7AE` correct → run a
transaction → `8CA64D`.

### Fixed

- `devicesSign()` no longer writes any keys. It logs and returns.
- `setPOSMasterKey()` is neutered: it logs an error and returns false, so
  reviving it cannot destroy the TMK again.
- Nothing in the transaction flow writes to the pad's key store now. Keys come
  from exactly two places: the admin panel (TMK) and DE62 at logon (TPK).

---

## Known open issue

`dalPosWriteKey` currently returns **-4528** on this firmware, so the working
key is not loading. The sale is still sent (with a warning) so the host response
can be seen, but the PIN block will not verify until this is resolved.

`-4528` is the same code returned by `posGetKcv` for an index that holds no key,
which suggests a parameter the pad rejects rather than an unsupported call. Two
things to try, in order:

1. Inject the master key **first**, then logon. The call may be failing simply
   because the source TMK slot is empty — which it currently is.
2. If it still returns -4528 with a valid TMK present, ask Anfu which
   `PstKeyInfoIn` values their firmware expects for writing a TPK received
   enciphered under a TMK.

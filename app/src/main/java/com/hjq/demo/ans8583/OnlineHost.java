package com.hjq.demo.ans8583;

import android.content.Context;
import android.util.Log;

import com.szanfu.sdk.api.pinpad.IPinPadBinderService;
import com.szanfu.sdk.api.pinpad.KcvInfoParam;
import com.szanfu.sdk.api.pinpad.PosCalcDesResult;
import com.szanfu.sdk.api.pinpad.PosGetKcvResult;
import com.szanfu.sdk.api.pinpad.PosGetMacResult;
import com.szanfu.sdk.api.pinpad.PstKcvInfoIn;
import com.szanfu.sdk.api.pinpad.PstKeyInfoIn;

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;

/**
 * High-level online driver for the Dutch-Bangla Bank host, implementing the
 * Hypercom "The System" Message Specification v3.34.
 *
 * <p><b>Logon (0800/0810), spec 7.21 + 6.27.2.</b> The request carries DE3, 11,
 * 24, 41, 42 and DE62 (invoice reference), matching a working DBBL terminal.
 * On a DE39='00' response the host returns <b>DE62</b>, and the whole field is
 * the working key, enciphered under the terminal master key. It is written to
 * the pad as POS_TPK with the TMK as the source key. Naming the destination
 * type explicitly is what keeps the TMK untouched.</p>
 *
 * <p><b>Sale (0200/0210), spec 7.36.</b> Processing code is {@code 00aa0x}
 * where {@code aa} is the account selection (spec 6.2.1) and {@code x} is
 * processing/flow control. DE62 carries the 6-character invoice / ECR reference
 * number as ASCII (spec 6.27.1). Note that <b>DE49 is not part of the Hypercom
 * sale message</b> - the currency travels in EMV tag 5F2A inside DE55.</p>
 *
 * <p>Any 0210, 0510 or 0810 may carry <b>DE48</b> to rotate the MAC and PIN
 * keys mid-session (spec 6.19); that is handled centrally in {@link #send}.</p>
 *
 * <p>All methods block on network I/O, so callers must invoke them on a
 * background thread. An unreachable host produces a decline, never an
 * approval.</p>
 */
public final class OnlineHost {

    private static final String TAG = "OnlineHost";

    // PIN pad key types, per the SDK contract documented in KeyManager.
    private static final int POS_TMK = 0x02; // terminal master key
    private static final int POS_TPK = 0x03; // PIN / working key
    private static final int POS_TAK = 0x04; // MAC key

    // The Anfu pad addresses keys as (master key index, key TYPE) - that is
    // what posGetKcv(keyIdx, keyType) takes. Working keys therefore live at the
    // SAME index as the master key they were loaded under, distinguished only
    // by their type. Writing them to a separate index like PAX's INDEX_TPK=3
    // fails: posGetKcv(3, TPK) returns ret=-4528 and showPinPad(3) raises
    // onPinPadError-5011, because index 3 holds no key set.
    //
    // The TMK is still safe: dalPosWriteKey names dstKeyType = POS_TPK, and the
    // master key is POS_TMK at the same index. Different type, no collision.
    // The old overwrite came from posWriteWorkingKey, which has no destination
    // type/index pair at all.

    /** posWriteWorkingKey mode 0x01: the value arrives enciphered, DES-decrypt
     *  it under the source key (the TMK at mkIndex). */
    private static final int MODE_DES_DECRYPT = 0x01;

    private OnlineHost() { }
    private static TxnRecord sCardCompletionPending;

    public static final class HostResult {
        public boolean online;
        public boolean approved;
        public String responseCode = "";
        public String responseAmount = "", responseTid = "";
        public String authCode = "";
        public String rrn = "";
        public String stan = "";
        /** Parsed DE62 logon response (null for non-logon messages). */
        public LogonData logon;
        public byte[] workingKey;      // eTMK(TPK) from DE62
        public byte[] macKey;          // eTMK(TAK) from DE62
        public int keyWriteRet = Integer.MIN_VALUE;
        public int macKeyWriteRet = Integer.MIN_VALUE;
        public String wkKcv = "";
        public String macKcv = "";
        /** TMK KCV read back after the logon - must equal the injected value. */
        public String tmkKcv = "";
        /** Alternate host response text from DE63 table '22', if present. */
        public String hostText = "";
        /** DE55 returned by the host - feeds the EMV second Generate AC. */
        public String responseIcc = "";
        /**
         * DE54 additional amounts, as received.
         *
         * <p>On a prepaid scheme this is the remaining balance, and the Kona
         * spec 2.2 requires it on the screen and on the receipt: a prepaid
         * cardholder has no other way to find out what is left. Kept raw so
         * {@link KonaMoney#balanceMinor} can decide whether the host actually
         * sent one - an absent balance and a balance of zero must not print
         * the same.</p>
         */
        public String additionalAmounts = "";
        /** DE72 returned by the host - the EMI package table on a download. */
        public String field72 = "";
        /** The record as sent, for the batch and for any reversal. */
        public TxnRecord record;
        /** True when the exchange failed in a way that requires a reversal (spec 7.35). */
        public boolean needsReversal;
        /** The host approved; the contact chip must still complete. */
        public boolean awaitingCardCompletion;
        public boolean reversalAcknowledged;
        /**
         * True once the request bytes were flushed to the socket.
         *
         * <p>PAX arms a reversal only after a successful {@code tcp.onSend()}.
         * A message that never left the terminal cannot have been seen by the
         * host, and arming one for it puts a reversal on the wire for a
         * transaction the host has never heard of.</p>
         */
        public boolean requestSent;
        public String precedingTrace = "";
        public String responsePan = "", responseTime = "", responseDate = "", responseNii = "";
        public String requestDump = "";
        public String responseDump = "";
        public String error = "";
        private HostResult reversalExchange;
        public String reversalDecision = "";

        /** Keep the real recovery exchange with the sale without changing its verdict. */
        public void attachReversal(HostResult exchange) {
            if (exchange == null || exchange == this) return;
            reversalExchange = exchange;
            reversalAcknowledged = TransactionPolicy.reversalAccepted(
                    exchange.online && !exchange.needsReversal, exchange.responseCode);
        }

        /** The receipt must show both exchanges, in their actual sequence. */
        public String packetTrace() {
            StringBuilder trace = new StringBuilder();
            trace.append(precedingTrace);
            trace.append(requestDump).append('\n').append(responseDump).append('\n');
            if (reversalExchange != null) {
                trace.append("\nREVERSAL (0400)\n");
                trace.append(reversalExchange.requestDump).append('\n');
                trace.append(reversalExchange.responseDump).append('\n');
                trace.append(reversalAcknowledged ? "REVERSAL ACKNOWLEDGED (0410/00)"
                        : reversalExchange.requestSent ? "0400 WRITE ATTEMPTED - ACK PENDING"
                        : "0400 NOT SENT - RECOVERY PENDING").append('\n');
                if (!reversalExchange.error.isEmpty()) {
                    trace.append("REVERSAL ERROR: ").append(reversalExchange.error).append('\n');
                }
            }
            if (!reversalDecision.isEmpty()) trace.append(reversalDecision).append('\n');
            return trace.toString();
        }

        /** Text to show the operator: host text if any, otherwise the response code. */
        public String displayMessage() {
            if (!hostText.isEmpty()) {
                return hostText;
            }
            if (approved) {
                return "APPROVED";
            }
            if (needsReversal) return reversalAcknowledged
                    ? "TRANSACTION NOT COMPLETED - REVERSED" : "TRANSACTION NOT COMPLETED - REVERSAL PENDING";
            if (!error.isEmpty()) {
                return error;
            }
            return "DECLINED " + responseCode;
        }
    }

    // ----------------------------------------------------------------- logon

    /**
     * Logon (MT 0800, processing code 92 00 0x - spec 5.3 and 7.21).
     *
     * <p>Sends DE60 = PPID(8) + eKTM(PPID)(4) + PIN pad SW version(3) and, on
     * success, splits DE62 into the working key and MAC key and injects both.</p>
     */
    public static synchronized HostResult logon(Context ctx, IPinPadBinderService pinpad) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        HostResult recovery = prepareOnline(ctx, pinpad);
        if (!recovery.error.isEmpty()) return recovery;
        String stan = tc.nextStan();

        TreeMap<Integer, String> de = new TreeMap<>();
        de.put(3, "920000");                 // 92 00 0x - logon
        de.put(11, stan);
        de.put(24, tc.nii());
        de.put(41, tc.tid());
        // Spec 7.21 marks DE42 optional on the request; DBBL's host expects it.
        de.put(42, tc.mid());
        // DE62 = invoice / ECR reference, ASCII.
        //
        // Spec 6.24 assigns the logon request block to DE60 and defines its
        // layout in 6.25.6, but DBBL does not implement that. A working
        // terminal's 0800 has bitmap 2020010000C00004 - fields 3, 11, 24, 41,
        // 42, 62 - with DE62 = "000431" and no DE60 at all, and its declared
        // Length 0036 accounts for exactly those fields. Sending DE60 here put
        // a field on the wire their host does not expect.
        de.put(62, Iso8583.ascii(tc.invoiceNo()));

        HostResult r = send(ctx, "LOGON (0800)", "0800", de, stan, pinpad);
        r.precedingTrace = recovery.precedingTrace;

        if (!r.online) {
            return r;
        }
        if (!"00".equals(r.responseCode)) {
            r.error = "logon declined, DE39=" + r.responseCode;
            tc.setLoggedOn(false);
            return r;
        }
        if (r.logon == null || !r.logon.valid) {
            r.error = "logon approved but DE62 held no usable key ("
                    + (r.logon == null ? "field absent" : r.logon.note) + ")";
            tc.setLoggedOn(false);
            return r;
        }

        // Keep the raw field so the admin panel can re-derive the key under
        // every reading of spec 6.27.2 without needing another logon.
        tc.setLastDe62(r.logon.rawHex);

        // Read the TMK BEFORE the working key is written, so any change can be
        // attributed to the write itself rather than guessed at afterwards.
        String tmkBefore = readKcv(pinpad, tc.mkIndex(), POS_TMK);

        // Write the working key (POS_TPK) and, when present, the MAC key
        // (POS_TAK). Both arrive enciphered under the TMK, hence mode 0x01.
        r.keyWriteRet = writeKey(pinpad, POS_TPK, tc.mkIndex(), r.logon.pinKey, tc.mkIndex());

        String tmkAfterWrite = readKcv(pinpad, tc.mkIndex(), POS_TMK);
        if (!tmkBefore.isEmpty() && !tmkAfterWrite.isEmpty()
                && !tmkBefore.equalsIgnoreCase(tmkAfterWrite)) {
            Log.e(TAG, "THE WORKING KEY WRITE CHANGED THE TMK: " + tmkBefore
                    + " -> " + tmkAfterWrite + ". The key write path is wrong.");
        }

        if (r.logon.macKey != null) {
            r.macKeyWriteRet = writeKey(pinpad, POS_TAK, tc.mkIndex(), r.logon.macKey, tc.mkIndex());
        }
        // The terminal master key must be exactly as injected. No Hypercom
        // message replaces it: 6.27.2 replaces the working key, 6.19 the PIN and
        // MAC keys. If it moved, a key write went to the wrong slot and every
        // PIN block from here on would be unverifiable - so say so loudly
        // rather than letting sales fail mysteriously.
        String tmkNow = tmkAfterWrite.isEmpty()
                ? readKcv(pinpad, tc.mkIndex(), POS_TMK) : tmkAfterWrite;
        String tmkWas = tc.tmkKcv();
        if (!tmkBefore.isEmpty() && !tmkNow.isEmpty() && !tmkBefore.equalsIgnoreCase(tmkNow)) {
            r.error = "THE KEY WRITE CHANGED THE TMK: " + tmkBefore + " -> " + tmkNow;
        }
        if (!tmkWas.isEmpty() && !tmkNow.isEmpty() && !tmkWas.equalsIgnoreCase(tmkNow)) {
            r.error = "TMK CHANGED DURING LOGON: was " + tmkWas + ", now " + tmkNow
                    + ". Re-inject the master key.";
            Log.e(TAG, r.error);
        }
        r.tmkKcv = tmkNow;

        // Verify the pad decrypted DE62 the way we expect, and correct it if not.
        //
        // We hold the clear TMK, so the working key can be derived here with a
        // known-good 3DES implementation: TPK = 3DES-ECB-decrypt(DE62, TMK).
        // Its KCV is 3DES-ECB-encrypt(0x00 x8) under that key. If the pad
        // reports a different KCV then its own derivation differs from ours -
        // a different mode, a key variant, whatever - and every PIN block it
        // produces will be meaningless to the acquirer.
        //
        // In that case write the key we derived, in the clear, with mode 0x00.
        // That is the same path the admin panel's manual working-key injection
        // uses, and it is known to store the key verbatim on this pad.
        reconcileWorkingKey(pinpad, tc, r);

        r.wkKcv = readKcv(pinpad, tc.mkIndex(), POS_TPK);
        r.macKcv = readKcv(pinpad, tc.mkIndex(), POS_TAK);
        tc.setKeyKcvs(r.wkKcv, r.macKcv);
        tc.setHostReceiptText(r.logon.receiptLine2, r.logon.receiptLine3, r.logon.merchantName);

        boolean keysOk = r.keyWriteRet == 0
                && (r.logon.macKey == null || r.macKeyWriteRet == 0);

        // If the acquirer has quoted a KCV, log a mismatch - but never block the
        // logon on it.
        String expect = tc.expectedTpkKcv();
        if (!expect.isEmpty() && !r.wkKcv.isEmpty()) {
            String got = r.wkKcv.toUpperCase(Locale.US);
            int n = Math.min(expect.length(), got.length());
            Log.i(TAG, expect.regionMatches(0, got, 0, n)
                    ? "working key KCV matches the acquirer value " + expect
                    : "working key KCV " + got + " differs from acquirer value " + expect);
        }

        tc.setLoggedOn(keysOk);
        if (!keysOk) {
            r.approved = false;
            r.error = "key injection failed: TPK ret=" + r.keyWriteRet
                    + ", TAK ret=" + r.macKeyWriteRet
                    + " (check the terminal master key at index " + tc.mkIndex() + ")";
        }

        tc.setLastLogonReport(buildLogonReport(tc, r));
        Log.i(TAG, tc.lastLogonReport());
        return r;
    }

    /**
     * Everything the logon did with the key, in one block.
     *
     * <p>Written to preferences so the admin panel can show it without another
     * capture. The DE62 content is the one piece of evidence that has never been
     * visible, and it answers on its own whether the field is 8, 16 or 32 bytes
     * and whether the pad's derivation agrees with ours.</p>
     */
    private static String buildLogonReport(TerminalConfig tc, HostResult r) {
        StringBuilder b = new StringBuilder();
        // The DE62 SPLIT button cycles, so it is easy to leave the terminal on a
        // reading nobody chose. Whole-field is what the certified PAX terminal
        // uses; say so loudly when the terminal is on anything else.
        if (tc.wkLen() != 0) {
            b.append("*** DE62 READING IS NOT WHOLE-FIELD ***\n")
             .append("wkLen=").append(tc.wkLen())
             .append(" - the certified PAX terminal takes\n")
             .append("the WHOLE DE62 as the working key. Press\n")
             .append("DE62 SPLIT until it reads 0, then LOGON.\n\n");
        }
        b.append("LAST LOGON\n");
        b.append("DE39      : ").append(r.responseCode).append('\n');
        b.append("mkIndex   : ").append(tc.mkIndex()).append('\n');
        b.append("TMK KCV   : ").append(nz(r.tmkKcv).isEmpty() ? "(unread)" : r.tmkKcv)
         .append("   recorded at injection: ")
         .append(tc.tmkKcv().isEmpty() ? "(none)" : tc.tmkKcv()).append('\n');
        if (r.logon != null) {
            int n = r.logon.rawHex.length() / 2;
            b.append("DE62 len  : ").append(n).append(" bytes\n");
            b.append("DE62      : ").append(r.logon.rawHex).append('\n');
            b.append("reading   : wkLen=").append(tc.wkLen()).append("  ")
             .append(r.logon.note).append('\n');
        } else {
            b.append("DE62      : (absent)\n");
        }
        b.append("TPK write : ret=").append(r.keyWriteRet).append('\n');
        b.append("TPK KCV   : ").append(r.wkKcv.isEmpty() ? "(unread)" : r.wkKcv);
        if (!tc.expectedTpkKcv().isEmpty()) {
            b.append("   acquirer says: ").append(tc.expectedTpkKcv());
        }
        b.append('\n');
        b.append("TAK KCV   : ").append(r.macKcv.isEmpty() ? "(unread)" : r.macKcv).append('\n');

        // Derive the key ourselves and compare, so a pad that decrypts DE62
        // differently from us is visible here rather than as a declined sale.
        java.util.List<LogonData.Candidate> cands =
                LogonData.candidates(tc.lastDe62(), tc.tmkClear());
        if (cands.isEmpty()) {
            b.append("\nNo clear TMK stored - cannot check the pad's\n")
             .append("derivation. Re-inject the master key.");
        } else {
            b.append("\nDerived from DE62 under our TMK:\n");
            for (LogonData.Candidate c : cands) {
                b.append("  wkLen=").append(c.wkLen).append("  KCV=").append(c.kcv);
                if (!r.wkKcv.isEmpty() && c.kcv.equalsIgnoreCase(r.wkKcv)) {
                    b.append("  <== the pad is holding this");
                }
                b.append('\n');
            }
        }
        if (!r.error.isEmpty()) {
            b.append('\n').append(r.error);
        }
        return b.toString();
    }

    // ----------------------------------------------------------- transaction

    /** How the card was read - drives DE22 (spec 6.8, condition C05). */
    /**
     * How the card reached the terminal, as DE22's PAN entry mode.
     *
     * <p>{@code QR} and {@code CLESS_MSD} exist for KonaMoney. Its spec's own
     * samples send {@code 0031} for a scanned QR (ISO entry mode 03, "merchant
     * has a bar code reader") and {@code 0910} for contactless MSD (entry mode
     * 91). Ordinary contactless on this terminal uses {@code CLESS_ICC} and
     * sends {@code 0071} - see {@link #entryMode} - which is what DBBL's own
     * reference terminal sends and what their host approves. NexusPay phone
     * NFC uses the DBBL-specific {@code 910}, independently of PIN entry.</p>
     */
    public enum EntryMode { UNSPECIFIED, MANUAL, MAGSTRIPE, ICC, FALLBACK, QR,
                            CLESS_ICC, CLESS_MSD, NEXUSPAY_PHONE_NFC }

    /** Backwards-compatible entry point; assumes an ICC read. */
    public static synchronized HostResult transaction(Context ctx, String type, String amountMajor,
                                         String pan, String pinBlockHex,
                                         IPinPadBinderService pinpad) {
        return transaction(ctx, type, amountMajor, pan, pinBlockHex, null, null, null,
                EntryMode.ICC, "00", pinpad);
    }

    /**
     * Map the app's CardSearchType to a spec DE22 entry mode.
     *
     * @param cardType   CardSearchType.MAGNETIC_STRIPE_CARD / IC_CARD / RF_CARD
     * @param magFallback true when an ICC-capable card had to be swiped
     */
    public static EntryMode entryModeFor(int cardType, boolean magFallback) {
        if (magFallback) {
            return EntryMode.FALLBACK;   // '80' - magstripe though ICC capable
        }
        switch (cardType) {
            case 1:  return EntryMode.MAGSTRIPE; // MAGNETIC_STRIPE_CARD
            case 2:  return EntryMode.ICC;       // IC_CARD
            case 4:  return EntryMode.CLESS_ICC; // RF_CARD - contactless EMV chip, DE22 '0071'
            default: return EntryMode.UNSPECIFIED;
        }
    }

    /** Resolve phone NFC after card data is available, for every transaction type. */
    public static EntryMode entryModeFor(int cardType, boolean magFallback, String aid, String track2) {
        if (!magFallback && cardType == 4 && KonaMoney.isPhoneNfc(aid, track2))
            return EntryMode.NEXUSPAY_PHONE_NFC;
        return entryModeFor(cardType, magFallback);
    }

    private static EntryMode contactlessEntryMode(EntryMode entry, String iccDataHex, String track2) {
        if ((entry == EntryMode.CLESS_ICC || entry == EntryMode.CLESS_MSD)
                && iccDataHex != null && !iccDataHex.isEmpty()) {
            Map<String, String> tags = Emv55.parse(iccDataHex);
            if (KonaMoney.isPhoneNfc(tags.get("84"), track2)
                    || KonaMoney.isPhoneNfc(tags.get("4F"), track2))
                return EntryMode.NEXUSPAY_PHONE_NFC;
        }
        return entry;
    }

    /**
     * Sale and its relatives (MT 0200 / 0100 / 0220 - spec 7.36 and 5.3).
     *
     * @param type        SALE, CASH_WITHDRAW, REFUND, VOID, PREAUTH, OFFLINE, ...
     * @param amountMajor amount in major units, e.g. "150.00"
     * @param pan         PAN, sent in DE2 only for manual entry (condition C01)
     * @param pinBlockHex encrypted PIN block from the pad, 8 bytes, or null
     * @param track2      track 2 image for a read card (condition C03), or null
     * @param expiry      YYMM, sent in DE14 for manual entry (condition C02)
     * @param iccDataHex  DE55 EMV data for a chip transaction (condition C07)
     * @param entry       how the card was read
     * @param accountSel  two-digit account selection for DE3 (spec 6.2.1), "00" = default
     */
    /**
     * Last look at the assembled fields before they are packed. EMI uses it to
     * override the processing code and NII and to append its DE48 block,
     * rather than duplicating this whole builder for one variant.
     */
    public static final class SaleTip implements Extra {
        final long base, tip;
        public SaleTip(long base, long tip) { this.base = base; this.tip = tip; }
        @Override public void apply(TreeMap<Integer, String> de) {
            if (tip > 0) de.put(54, Iso8583.ascii(TipAmounts.field(tip)));
        }
    }

    private static String tipRoute(TerminalConfig tc) {
        return tc.tid() + "|" + tc.mid() + "|" + tc.nii() + "|" + tc.tpdu()
                + "|" + tc.hostIp() + "|" + tc.hostPort();
    }

    public static synchronized TxnRecord queueTipAdjustment(Context ctx, String invoice, long tip) {
        if (sCardCompletionPending != null) throw new IllegalStateException("Finish the current transaction first");
        TerminalConfig tc = TerminalConfig.get(ctx);
        return BatchStore.get(ctx).queueTip(invoice, tip, tc, tipRoute(tc));
    }

    /** Send FIFO advices; only a validated 0230/00 removes a durable entry. */
    public static synchronized String flushPendingTips(Context ctx, IPinPadBinderService pinpad) {
        return flushPendingTips(ctx, pinpad, new StringBuilder());
    }

    private static String flushPendingTips(Context ctx, IPinPadBinderService pinpad, StringBuilder trace) {
        BatchStore batch = BatchStore.get(ctx);
        if (batch.tipStorageFault()) return "Storage failure: check storage and restart before a new transaction";
        if (!batch.hasPendingTips()) return "";
        if (batch.hasPendingReversal() || sCardCompletionPending != null) return "Finish reversal/card recovery before Tip Adjust upload";
        TerminalConfig tc = TerminalConfig.get(ctx);
        if (!tipRoute(tc).equals(batch.tipRoute())) return "Tip Adjust pending: restore original host and merchant parameters";
        try {
            for (TxnRecord rec : batch.pendingTips()) {
                TreeMap<Integer, String> de = new TreeMap<>();
                de.put(2, rec.pan); de.put(3, "020000"); de.put(4, rec.amountMinor);
                de.put(11, rec.stan); de.put(12, rec.time); de.put(13, rec.date);
                if (isValidExpiry(rec.expiry)) de.put(14, rec.expiry);
                de.put(22, rec.entryMode); de.put(24, tc.nii()); de.put(25, "00");
                de.put(37, rec.rrn); de.put(38, rec.authCode);
                de.put(41, tc.tid()); de.put(42, tc.mid());
                de.put(54, Iso8583.ascii(TipAmounts.field(rec.tip())));
                if (!rec.iccHex.isEmpty()) de.put(55, rec.iccHex);
                de.put(60, Iso8583.ascii(TipAmounts.field(Long.parseLong(rec.originalAmountMinor))));
                de.put(62, Iso8583.ascii(rec.invoice));
                HostResult response = send(ctx, "TIP ADJUST (0220)", "0220", de, rec.stan, pinpad, tc.tpdu(), false);
                trace.append("TIP ADJUST (0220) invoice ").append(rec.invoice).append("\n")
                        .append(response.packetTrace());
                if ((!response.responseAmount.isEmpty() && !TipAmounts.field(rec.amount()).equals(response.responseAmount))
                        || (!response.responseTid.isEmpty() && !tc.tid().equals(response.responseTid)))
                    return "Tip Adjust " + rec.invoice + " acknowledgement does not match the saved amount or terminal. New transaction not sent.";
                if (!response.online || !response.approved || response.needsReversal)
                    return "Tip Adjust " + rec.invoice + " upload pending: "
                            + (response.error.isEmpty() ? "host response " + response.responseCode : response.error)
                            + ". New transaction not sent.";
                batch.acknowledgeTip(rec);
            }
            return "";
        } catch (RuntimeException e) { return "Tip Adjust upload pending: " + e.getMessage(); }
    }

    public static synchronized HostResult prepareOnline(Context ctx, IPinPadBinderService pinpad) {
        HostResult result = new HostResult();
        if (sCardCompletionPending != null)
            return fail(result, "Finish the current card transaction first", false);
        HostResult reversal = flushPendingReversal(ctx, pinpad);
        if (reversal != null) result.precedingTrace = "PENDING REVERSAL\n" + reversal.packetTrace();
        if (BatchStore.get(ctx).hasPendingReversal())
            return fail(result, "Previous reversal pending - retry recovery before a new transaction", false);
        StringBuilder trace = new StringBuilder(result.precedingTrace);
        String error = flushPendingTips(ctx, pinpad, trace);
        result.precedingTrace = trace.toString();
        return error.isEmpty() ? result : fail(result, error, false);
    }

    private static String qrRoute(TerminalConfig tc) {
        return tc.tid() + "|" + tc.mid() + "|" + tc.qrNii() + "|" + tc.qrTpdu()
                + "|" + tc.hostIp() + "|" + tc.hostPort();
    }

    public interface QrScreen { boolean isOpen(); }
    public static synchronized DbblQr.Session createQrSale(Context ctx, long amount) {
        return createQrSale(ctx, amount, () -> true);
    }
    public static synchronized DbblQr.Session createQrSale(Context ctx, long amount, QrScreen screen) {
        BatchStore batch = BatchStore.get(ctx);
        synchronized (batch) {
        if (!screen.isOpen()) throw new IllegalStateException("QR screen closed");
        DbblQr.Session saved = batch.qrSession();
        if (saved != null) return saved;
        TerminalConfig tc = TerminalConfig.get(ctx);
        DbblQr.validateSettings(tc.qrNii(), tc.qrTpdu(), tc.qrMcc(), tc.qrCity(), tc.qrMerchant());
        if (tc.qrNii().equals(tc.nii())) throw new IllegalArgumentException("DBBL requires a separate QR NII. Enter the bank-assigned QR NII in Parameters.");
        TxnRecord r = new TxnRecord();
        r.type = "SALE"; r.entryMode = "840"; r.amountMinor = TipAmounts.field(amount);
        r.stan = tc.nextStan(); r.invoice = tc.nextInvoice();
        java.util.Date created = new java.util.Date();
        r.date = new java.text.SimpleDateFormat("MMdd", Locale.US).format(created);
        r.time = new java.text.SimpleDateFormat("HHmmss", Locale.US).format(created);
        String reference = tc.tid() + new java.text.SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(created);
        String payload = DbblQr.payload(tc.mid(), tc.tid(), tc.qrMcc(), tc.qrMerchant(), tc.qrCity(),
                tc.qrNii(), amount, r.invoice, r.stan, reference, tc.qrSid());
        DbblQr.Session session = new DbblQr.Session(r, reference, payload, qrRoute(tc));
        batch.saveQrSession(session);
        return session;
        }
    }

    public static synchronized HostResult checkQrPayment(Context ctx, IPinPadBinderService pinpad) {
        DbblQr.Session active = BatchStore.get(ctx).qrSession();
        return checkQrPayment(ctx, pinpad, active == null ? "" : active.reference);
    }

    /** Reference-bound so exiting the screen cannot redirect an in-flight check. */
    public static synchronized HostResult checkQrPayment(Context ctx, IPinPadBinderService pinpad, String reference) {
        BatchStore batch = BatchStore.get(ctx);
        DbblQr.Session session = batch.findQrSession(reference);
        if (session == null) return fail(new HostResult(), "No saved QR payment", false);
        if (session.record.approved) {
            HostResult saved = new HostResult(); saved.approved = true; saved.online = true;
            saved.responseCode = "00"; saved.record = session.record; return saved;
        }
        TerminalConfig tc = TerminalConfig.get(ctx);
        if (!session.route.equals(qrRoute(tc))) return fail(new HostResult(), "Restore the original QR host and merchant settings to check this payment", false);
        HostResult recovery = prepareOnline(ctx, pinpad);
        if (!recovery.error.isEmpty()) return recovery;
        HostResult r = send(ctx, "QR CHECK STATUS (0200)", "0200",
                DbblQr.statusFields(session, tc.tid(), tc.mid(), tc.qrNii()), session.record.stan, pinpad, tc.qrTpdu(), false);
        r.precedingTrace = recovery.precedingTrace;
        boolean valid = r.online && r.approved && !r.needsReversal;
        // Neither supplied QR specification defines a timeout reversal. A failed inquiry
        // cannot decide whether the customer's separate mobile payment completed.
        r.needsReversal = false;
        r.responseDump = r.responseDump.replace("Reversal queued.\n", "QR status unresolved; retry the same saved reference.\n");
        if (valid && ((!r.responseAmount.isEmpty() && !TipAmounts.field(session.record.amount()).equals(r.responseAmount))
                || !tc.tid().equals(r.responseTid) || !tc.qrNii().equals(r.responseNii)
                || !r.responsePan.matches("[0-9]{12,19}") || r.responsePan.matches("0+")
                || !r.rrn.matches("[0-9A-Za-z]{12}") || !r.authCode.matches("[0-9A-Za-z]{6}")
                || !r.responseTime.matches("[0-9]{6}") || !r.responseDate.matches("[0-9]{4}"))) {
            valid = false; r.error = "QR response is missing required payment details or does not match this payment";
        }
        r.approved = valid;
        if (!valid) {
            r.hostText = "Payment not confirmed" + (r.responseCode.isEmpty() ? "" : " (host " + r.responseCode + ")")
                    + ". Keep this QR and check again.";
            return r;
        }
        TxnRecord record = TxnRecord.parse(session.record.serialize());
        record.pan = r.responsePan; record.rrn = r.rrn; record.authCode = r.authCode;
        record.time = r.responseTime; record.date = r.responseDate;
        record.approved = true; record.hostOnline = true; record.respCode = "00";
        record.issuer = "NEXUSPAY"; record.cvm = "VERIFIED ON MOBILE";
        try {
            batch.completeQr(new DbblQr.Session(record, session.reference, session.payload, session.route));
            r.record = record;
        } catch (RuntimeException e) {
            r.approved = false; r.error = e.getMessage(); r.hostText = r.error;
        }
        return r;
    }

    public interface Extra {
        void apply(TreeMap<Integer, String> de);
    }

    public static synchronized HostResult transaction(Context ctx, String type, String amountMajor,
                                         String pan, String pinBlockHex,
                                         String track2, String expiry, String iccDataHex,
                                         EntryMode entry, String accountSel,
                                         IPinPadBinderService pinpad) {
        return transaction(ctx, type, amountMajor, pan, pinBlockHex, track2, expiry,
                iccDataHex, entry, accountSel, pinpad, null);
    }

    public static synchronized HostResult transaction(Context ctx, String type, String amountMajor,
                                         String pan, String pinBlockHex,
                                         String track2, String expiry, String iccDataHex,
                                         EntryMode entry, String accountSel,
                                         IPinPadBinderService pinpad, Extra extra) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        String t = nz(type).isEmpty() ? "SALE" : type;
        if (extra instanceof SaleTip) {
            SaleTip st = (SaleTip) extra;
            try {
                if (!"SALE".equals(t) || TipAmounts.total(st.base, st.tip) != Long.parseLong(minor(amountMajor)))
                    return fail(new HostResult(), "Sale and tip amounts do not match the total", false);
            } catch (RuntimeException e) { return fail(new HostResult(), e.getMessage(), false); }
        }

        // An online PIN needs the working key from the last logon. Without it
        // the PIN block is enciphered under nothing the host can check, so stop
        // here rather than sending a request that can only be declined.
        // The working key may not have loaded, but do NOT block the sale on it.
        // Sending the transaction and seeing what the host says is more useful
        // than refusing to send, and the receipt carries the packet trace either
        // way. Warn only.
        String pinWarning = "";
        if (pinBlockHex != null && !pinBlockHex.isEmpty() && !tc.isLoggedOn()) {
            pinWarning = "WARNING: no confirmed working key in the PIN pad"
                    + (tc.wkKcv().isEmpty() ? "" : " (last KCV " + tc.wkKcv() + ")")
                    + " - the PIN block may not verify at the host";
            Log.w(TAG, pinWarning);
        }

        // Spec 7.35: any outstanding reversal must be delivered before new
        // business goes into the network.
        HostResult recovery = prepareOnline(ctx, pinpad);
        if (!recovery.error.isEmpty()) return recovery;

        String mti = "0200";
        String txnCode;                      // first two digits of DE3
        switch (t) {
            case "CASH_WITHDRAW":  txnCode = "01"; break;
            case "SALE_AND_CASH":  txnCode = "09"; break;
            case "REFUND":         txnCode = "20"; break;
            case "PREAUTH":
            case "PREAUTH_VOID":   mti = "0100"; txnCode = "30"; break;
            case "BALANCE":        mti = "0100"; txnCode = "31"; break;
            case "VOID":
            case "EMI_VOID":       txnCode = "02"; break;
            case "REFUND_VOID":    txnCode = "22"; break;
            case "ADJUST":         mti = "0220"; txnCode = "02"; break;
            case "SALE_COMPLETE":  mti = "0220"; txnCode = "00"; break;
            case "OFFLINE":        mti = "0220"; txnCode = "00"; break;
            case "OFFLINE_REFUND": mti = "0220"; txnCode = "20"; break;
            default:               txnCode = "00"; break;
        }
        // A pre-authorisation and its cancellation differ from a sale in two
        // fields: DE25 below, and DE14, which the capture carries on every
        // brand rather than Mastercard only.
        final boolean isPreAuth = "PREAUTH".equals(t) || "PREAUTH_VOID".equals(t);
        // DE3 = txnCode(2) + account selection(2) + "0" + flow control(1).
        // Terminal-originated messages leave the flow-control nibble clear.
        String acct = nz(accountSel).length() == 2 ? accountSel : "00";
        String proc = txnCode + acct + "00";

        String stan = tc.nextStan();
        String invoice = tc.nextInvoice();
        Calendar c = Calendar.getInstance();
        String time = String.format(Locale.US, "%02d%02d%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
        String date = String.format(Locale.US, "%02d%02d",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));

        TreeMap<Integer, String> de = new TreeMap<>();
        boolean haveTrack2 = track2 != null && track2.trim().length() > 0;
        String digits = pan == null ? "" : pan.replaceAll("[^0-9]", "");

        // C03: when the card was read, send the track. C01: PAN only for manual
        // entry, and on advices where the terminal stores no track.
        if (haveTrack2) {
            de.put(35, track2.trim());
        }
        if (!digits.isEmpty() && (!haveTrack2 || entry == EntryMode.MANUAL || "0220".equals(mti))) {
            de.put(2, digits);
        }
        de.put(3, proc);
        de.put(4, minor(amountMajor));
        de.put(11, stan);
        // DE12/DE13 are RESPONSE-ONLY on an authorisation request. Spec 7.36
        // (sale), 7.9 (cash), 7.27 (pre-auth) and 7.34 (refund) all leave the
        // Request column blank for bits 12 and 13 - they are only mandatory on
        // the advices (7.24 off-line sale, 7.38 sales completion, 7.44 void),
        // which are the 0220 messages. A working DBBL terminal capture confirms
        // this: its 0200 bitmap 3020058020C01204 has no bit 12 or 13, while the
        // 0210 coming back does.
        boolean isAdvice = "0220".equals(mti);
        if (isAdvice) {
            de.put(12, time);
            de.put(13, date);
        }
        // DE14 (expiry, YYMM) - MASTERCARD ONLY.
        //
        // Spec 7.36 marks it C02, i.e. conditional, and DBBL's condition is the
        // brand: Mastercard carries it, everything else must leave bit 14
        // clear. A Visa sale sent with DE14 came back DE39 = '**'.
        String exp = nz(expiry).replaceAll("[^0-9]", "");
        if (exp.length() > 4) {
            exp = exp.substring(0, 4);          // tag 5F24 is YYMMDD
        }
        if (exp.length() != 4 && haveTrack2) {
            exp = expiryFromTrack2(track2);     // the track always carries it
        }
        // The third argument means "send it regardless of brand". A void always
        // does; so does a pre-auth - the captured 0100 for a UnionPay card
        // carries DE14 = 3010, and the 0400 that cancels it repeats it.
        if (!sendsExpiry(digits, track2, isPreAuth)) {
            Log.i(TAG, "DE14 omitted - sale, and this is not Mastercard");
        } else if (!isValidExpiry(exp)) {
            Log.w(TAG, "DE14 omitted - expiry '" + exp + "' is not a valid YYMM");
        } else {
            if (isExpired(exp)) {
                // Reported, not blocked: for a chip transaction the card and the
                // issuer decide. PAX rejects here with ERR_CARD_EXPIRED, driven
                // by a per-issuer isCheckExpiry flag we do not carry.
                Log.w(TAG, "card expiry " + exp + " is in the past");
            }
            de.put(14, exp);
            Log.i(TAG, "DE14 sent: " + exp);
        }

        de.put(22, entryMode(contactlessEntryMode(entry, iccDataHex, track2), pinBlockHex != null));
        de.put(24, tc.nii());
        // DE25, POS condition code. "00" is normal presentment (spec 6.10);
        // a pre-authorisation carries "06" instead. Both the 0100 pre-auth and
        // the 0400 that cancels it show DE25 = 06 in the Move/2500 capture
        // against this acquirer, and the host matches the two on it.
        de.put(25, isPreAuth ? "06" : "00");
        de.put(41, tc.tid());
        de.put(42, tc.mid());
        // DE52, matching PAX setBitData52:
        //
        //   if (transData.isHasPin() && transData.getPin() != null) {
        //       setBitData("52", strToBcdPaddingLeft(transData.getPin()));
        //   }
        //
        // The whole PIN block goes in, converted to BCD and padded on the LEFT.
        // It is never truncated, and the field is omitted entirely when there is
        // no PIN. We previously took substring(0,16) and the codec padded on the
        // right - both wrong for anything other than an exact 16 hex characters.
        String pinBlock = bcdPadLeft(pinBlockHex, 16);
        String pinBlockFault = "";
        if (!pinBlock.isEmpty()) {
            // Verify the block before it goes on the wire. Whatever the acquirer
            // would say about it, we can say first.
            String panForCheck = !nz(de.get(2)).isEmpty()
                    ? de.get(2) : panFromTrack2(haveTrack2 ? track2 : "");
            pinBlockFault = checkPinBlock(pinpad, tc.mkIndex(), pinBlock, panForCheck);
            if (!pinBlockFault.isEmpty()) {
                Log.e(TAG, "PIN BLOCK INVALID BEFORE SENDING: " + pinBlockFault);
            }
            de.put(52, pinBlock);
        }
        // C07: EMV data for a chip transaction. Currency lives here as tag 5F2A;
        // DE49 is deliberately NOT sent - it is not in the Hypercom sale layout.
        if (iccDataHex != null && iccDataHex.replaceAll("[^0-9A-Fa-f]", "").length() > 0) {
            de.put(55, iccDataHex.replaceAll("[^0-9A-Fa-f]", ""));
        }
        // DE62 = invoice / ECR reference number, 6 ASCII characters (spec 6.27.1).
        de.put(62, Iso8583.ascii(invoice));

        // Journal the request before it goes out, so a timeout or a power cut
        // mid-exchange still leaves enough to build the reversal from.
        TxnRecord rec = new TxnRecord();
        rec.type = t;
        rec.mti = mti;
        rec.proc = proc;
        rec.amountMinor = de.get(4);
        rec.stan = stan;
        rec.time = time;
        rec.date = date;
        rec.pan = nz(de.get(2));
        rec.track2 = haveTrack2 ? track2.trim() : "";
        rec.expiry = exp;
        rec.entryMode = de.get(22);
        rec.invoice = invoice;
        rec.iccHex = nz(de.get(55));
        rec.pinUsed = de.containsKey(52);
        if (extra instanceof SaleTip) {
            rec.baseAmountMinor = String.valueOf(((SaleTip) extra).base);
            rec.tipAmountMinor = String.valueOf(((SaleTip) extra).tip);
        }

        // Last look before packing. EMI uses this to swap the processing code
        // and NII for the instalment route and to append its DE48 block.
        if (extra != null) {
            extra.apply(de);
            rec.proc = nz(de.get(3));
        }

        rec.requestNii = nz(de.get(24)); rec.requestTid = tc.tid();
        rec.requestMid = tc.mid(); rec.requestTpdu = tc.tpdu();

        BatchStore batch = BatchStore.get(ctx);

        batch.setPendingReversal(rec);

        HostResult r = send(ctx, prettyTitle(t) + " (" + mti + ")", mti, de, stan, pinpad);
        r.precedingTrace = recovery.precedingTrace;
        r.record = rec;
        if (!pinBlockFault.isEmpty()) {
            r.error = r.error.isEmpty() ? pinBlockFault : pinBlockFault + "; " + r.error;
        }
        if (!pinWarning.isEmpty()) {
            r.error = r.error.isEmpty() ? pinWarning : pinWarning + "; " + r.error;
        }

        if (r.online && !r.needsReversal) {
            rec.hostTime = r.responseTime.matches("[0-9]{6}") ? r.responseTime : "";
            rec.hostDate = r.responseDate.matches("[0-9]{4}") ? r.responseDate : "";
            rec.approved = r.approved;
            rec.rrn = r.rrn;
            rec.authCode = r.authCode;
            if (r.approved && entry == EntryMode.ICC) {
                // Preserve recovery data across a restart during GAC2. This
                // record must not enter settlement before the chip accepts.
                r.awaitingCardCompletion = true;
                batch.setPendingReversal(rec);
                sCardCompletionPending = rec;
                return r;
            }
            if (r.approved) {
                batch.completeApproved(rec);
            } else {
                batch.clearPendingReversal();
            }
        }
        // PAX's rules decide whether anything is owed at all...
        settleReversalSlot(ctx, rec, r);
        // ...and then, unlike PAX, it goes out now rather than
        // waiting to be carried by the next sale.
        reverseNowIfQueued(ctx, pinpad, r, prettyTitle(t));
        recordIfFailed(ctx, rec, r);
        return r;
    }

    /**
     * Keep a transaction that did not succeed, for the Error TRXN Report.
     *
     * <p>Only approved transactions enter the batch, so without this a decline
     * leaves no trace once its slip is torn off - and a decline is exactly the
     * thing a merchant later wants to look up.</p>
     */
    static void recordIfFailed(Context ctx, TxnRecord rec, HostResult r) {
        if (rec == null || r == null || r.approved) {
            return;
        }
        ReportStore.FailedTxn f = new ReportStore.FailedTxn();
        f.dateTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date());
        f.type = rec.type;
        f.pan = !nz(rec.pan).isEmpty() ? rec.pan : panFromTrack2(rec.track2);
        f.amountMinor = rec.amountMinor;
        f.stan = rec.stan;
        f.invoice = rec.invoice;
        f.entryMode = rec.entryMode;
        f.expiry = rec.expiry;
        f.responseCode = r.responseCode;
        f.reason = !r.error.isEmpty() ? r.error
                : ResponseCode.displayText(r.responseCode, r.authCode, r.hostText);
        ReportStore.get(ctx).addFailed(f);
    }

    // ----------------------------------------------------------------- void

    /**
     * Void a previous sale (MT 0200, processing code {@code 02a00x} - spec 7.44).
     *
     * <p>A void cancels a transaction already in the open batch. The host finds
     * the original from the values echoed back to it, so most of this message
     * describes the <b>original</b> sale, not a new one:</p>
     *
     * <table>
     *   <tr><td>DE12</td><td>M</td><td>"Request contains original transaction time"</td></tr>
     *   <tr><td>DE13</td><td>M</td><td>"Request contains original transaction date"</td></tr>
     *   <tr><td>DE37</td><td>M</td><td>"Request contains original retrieval reference no."</td></tr>
     *   <tr><td>DE60</td><td>O</td><td>"Original amount before void"</td></tr>
     * </table>
     *
     * <p>Only DE11 is new - a void is its own transaction on the wire and needs
     * its own STAN. DE38 carries the original authorisation code: the spec lists
     * bit 38 as response-only, but the PAX reference sends it on the request
     * ({@code setBitData38 -> transData.getOrigAuthCode()}) and it is what lets
     * the host match the original unambiguously.</p>
     *
     * <p>On approval the original is marked voided in the batch, so the
     * settlement totals drop both its amount and its count (spec 6.28.1
     * rule 9).</p>
     *
     * @param original the sale being cancelled, from the batch
     * @param pinBlockHex PIN block if the card required one for the void, else null
     */
    public static synchronized HostResult voidSale(Context ctx, TxnRecord original,
                                      String pinBlockHex, IPinPadBinderService pinpad) {
        return voidSale(ctx, original, pinBlockHex, pinpad, null);
    }

    /**
     * @param niiOverride NII for DE24, or null for the acquirer NII. Nothing
     *                    uses it today - EMI voids go out on the acquirer NII,
     *                    the same as PAX. Kept because a future transaction
     *                    that needs a different route must not do it by writing
     *                    the NII into saved configuration and restoring it,
     *                    where a crash in between would strand every later
     *                    sale on the wrong host.
     */
    public static synchronized HostResult voidSale(Context ctx, TxnRecord original,
                                      String pinBlockHex, IPinPadBinderService pinpad,
                                      String niiOverride) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        BatchStore batch = BatchStore.get(ctx);

        if (original == null) {
            HostResult nr = new HostResult();
            return fail(nr, "no original transaction selected to void", false);
        }
        if (original.voided) {
            HostResult nr = new HostResult();
            return fail(nr, "invoice " + original.invoice + " is already voided", false);
        }
        if (original.rrn.isEmpty()) {
            // DE37 is mandatory and must be the original's RRN. Without it the
            // host cannot identify what to cancel.
            HostResult nr = new HostResult();
            return fail(nr, "original has no RRN (DE37) - it cannot be voided", false);
        }

        HostResult recovery = prepareOnline(ctx, pinpad);
        if (!recovery.error.isEmpty()) return recovery;

        // The invoice may have acquired an offline tip since the lookup screen opened.
        TxnRecord latest = batch.findByInvoice(original.invoice);
        if (latest != null && latest.stan.equals(original.stan)) original = latest;
        if (original.voided) return fail(recovery, "This invoice is already voided", false);

        String stan = tc.nextStan();          // the void gets its OWN trace number
        String invoice = tc.nextInvoice();

        TreeMap<Integer, String> de = new TreeMap<>();
        // C06: the terminal stores no track for an advice, so the PAN is sent.
        if (!original.pan.isEmpty()) {
            de.put(2, original.pan);
        } else if (!original.track2.isEmpty()) {
            de.put(2, panFromTrack2(original.track2));
        }
        de.put(3, "020000");                  // 02 a0 0x - void, sale
        de.put(4, original.amountMinor);      // M - the original amount
        de.put(11, stan);                     // M - new STAN for the void itself
        de.put(12, original.time);            // M - ORIGINAL time
        de.put(13, original.date);            // M - ORIGINAL date
        // DE14 on a void: EVERY brand, unlike the sale. The reference
        // terminal's Visa void (DE2 484061..., DE55 tag 84 = A0000000031010)
        // carried it, and DBBL require it on all cards here.
        if (sendsExpiry(original.pan, original.track2, true)
                && isValidExpiry(original.expiry)) {
            de.put(14, original.expiry);
        }
        if (!original.entryMode.isEmpty()) {
            de.put(22, original.entryMode);   // C05
        }
        de.put(24, nz(niiOverride).isEmpty() ? tc.nii() : niiOverride);   // M
        de.put(25, "00");                     // normal presentment
        de.put(37, original.rrn);             // M - ORIGINAL retrieval reference
        if (!original.authCode.isEmpty()) {
            de.put(38, original.authCode);    // ORIGINAL auth code, as PAX sends
        }
        de.put(41, tc.tid());                 // M
        de.put(42, tc.mid());                 // M
        // No DE52. A void is an advice against a transaction the host has
        // already authorised, so there is nothing to verify a PIN against. The
        // working reference terminal sends no DE52 (bitmap bit 52 clear) and
        // PAX clears the PIN before packing: SaleVoidTrans.checkPin() sets
        // setPin("") / setHasPin(false), and PackSaleVoid never calls
        // setBitData52.
        if (!original.iccHex.isEmpty()) {
            de.put(55, original.iccHex);      // PAX sends the original ICC data
        }
        // No DE60. Spec 7.44 offers it for "original amount before void", but
        // the working reference terminal leaves bit 60 clear and PackSaleVoid
        // never calls setBitData60. DE4 already carries the original amount.
        de.put(62, Iso8583.ascii(invoice));

        TxnRecord rec = new TxnRecord();
        rec.type = "VOID";
        rec.mti = "0200";
        rec.proc = "020000";
        rec.amountMinor = original.amountMinor;
        rec.stan = stan;
        rec.time = original.time;
        rec.date = original.date;
        rec.pan = nz(de.get(2));
        rec.track2 = original.track2;
        rec.expiry = original.expiry;
        rec.entryMode = original.entryMode;
        rec.invoice = invoice;
        rec.iccHex = original.iccHex;
        rec.pinUsed = de.containsKey(52);

        batch.setPendingReversal(rec);
        HostResult r = send(ctx, "VOID (0200)", "0200", de, stan, pinpad);
        r.precedingTrace = recovery.precedingTrace;
        r.record = rec;

        if (r.online && !r.needsReversal) {
            batch.clearPendingReversal();
            rec.approved = r.approved;
            rec.rrn = r.rrn;
            rec.authCode = r.authCode;
            if (r.approved) {
                // Spec 6.28.1 rule 9: a void removes the original's amount AND
                // its count from the reconciliation totals.
                boolean marked = batch.markVoided(original.invoice);
                Log.i(TAG, "void approved, original invoice " + original.invoice
                        + (marked ? " marked voided" : " NOT FOUND in the batch"));
                batch.add(rec);
            }
        }
        // PAX's rules decide whether anything is owed at all...
        settleReversalSlot(ctx, rec, r);
        // ...and then, unlike PAX, it goes out now rather than
        // waiting to be carried by the next sale.
        reverseNowIfQueued(ctx, pinpad, r, "VOID");
        recordIfFailed(ctx, rec, r);
        return r;
    }

    /** Right-pad with spaces to a fixed width, for the {@code ans} fields. */
    private static String padRight(String v, int width) {
        StringBuilder b = new StringBuilder(nz(v));
        while (b.length() < width) {
            b.append(' ');
        }
        return b.length() > width ? b.substring(0, width) : b.toString();
    }

    /** Left-pad with zeros to a fixed width. */
    private static String padLeft(String v, int width) {
        StringBuilder b = new StringBuilder(nz(v));
        while (b.length() < width) {
            b.insert(0, '0');
        }
        return b.toString();
    }

    // ----------------------------------------------------- sale completion

    /**
     * Complete a pre-authorisation (MT 0220 advice, processing code
     * {@code 000000}).
     *
     * <p><b>The layout here is inferred, not captured.</b> The Move/2500 roll
     * supplied for this covers a pre-auth, its cancellation, and then a second
     * pre-auth - the 16:52:29 message is an 0100 with {@code DE3 300000},
     * {@code DE25 06}, a fresh STAN and a fresh invoice, and nothing pointing
     * back at an earlier authorisation. That is another pre-auth, not a
     * completion, so it cannot be copied.</p>
     *
     * <p>What is sent instead follows the shape the rest of this file already
     * uses for an advice, and the one thing a completion must do: name the
     * authorisation it is settling. The issuer is holding funds against
     * {@code DE37}/{@code DE38} from the 0110, and without them it has no way
     * to release the right hold.</p>
     *
     * <pre>
     *   0220  DE3  000000        completion, not a pre-auth
     *         DE4  final amount  may differ from the amount authorised
     *         DE11 new STAN      this advice is its own message
     *         DE12 DE13          the ORIGINAL's time and date, as every advice
     *         DE37 DE38          the ORIGINAL's reference and approval code
     *         DE55               the ORIGINAL's ICC data
     *         DE62 new invoice
     * </pre>
     *
     * <p>If DBBL come back with a different layout, the two lines most likely
     * to be wrong are the processing code and whether DE62 repeats the
     * original's invoice rather than taking a new one. Both are single lines
     * below, and the Menu Control switch for Sale Complete turns the tile off
     * in the meantime.</p>
     *
     * @param original    the pre-auth being completed, from the batch
     * @param amountMajor the final amount, "120.00" - the authorised amount
     *                    when the operator does not change it
     */
    public static synchronized HostResult saleComplete(Context ctx, TxnRecord original,
                                          String amountMajor,
                                          IPinPadBinderService pinpad) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        BatchStore batch = BatchStore.get(ctx);

        if (original == null) {
            HostResult nr = new HostResult();
            return fail(nr, "no pre-authorisation selected to complete", false);
        }
        if (!"PREAUTH".equals(original.type)) {
            HostResult nr = new HostResult();
            return fail(nr, "invoice " + original.invoice
                    + " is not a pre-authorisation", false);
        }
        if (original.voided) {
            HostResult nr = new HostResult();
            return fail(nr, "invoice " + original.invoice
                    + " was cancelled - there is no hold left to complete", false);
        }
        if (original.rrn.isEmpty() && original.authCode.isEmpty()) {
            // Without either the issuer cannot tell which hold to release.
            HostResult nr = new HostResult();
            return fail(nr, "the pre-authorisation has no reference (DE37) or"
                    + " approval code (DE38) - it cannot be completed", false);
        }

        HostResult recovery = prepareOnline(ctx, pinpad);
        if (!recovery.error.isEmpty()) return recovery;

        String stan = tc.nextStan();
        String invoice = tc.nextInvoice();

        TreeMap<Integer, String> de = new TreeMap<>();
        if (!original.track2.isEmpty()) {
            de.put(35, original.track2);
        } else if (!original.pan.isEmpty()) {
            de.put(2, original.pan);
        }
        de.put(3, "000000");                  // completion, not 300000
        de.put(4, minor(amountMajor));        // the FINAL amount
        de.put(11, stan);                     // this advice's own trace number
        de.put(12, original.time);            // ORIGINAL time  (advice: mandatory)
        de.put(13, original.date);            // ORIGINAL date
        if (isValidExpiry(original.expiry)) {
            de.put(14, original.expiry);
        }
        if (!original.entryMode.isEmpty()) {
            de.put(22, original.entryMode);
        }
        de.put(24, tc.nii());
        de.put(25, "00");                     // normal presentment
        if (!original.rrn.isEmpty()) {
            de.put(37, original.rrn);         // ORIGINAL retrieval reference
        }
        if (!original.authCode.isEmpty()) {
            de.put(38, original.authCode);    // ORIGINAL approval code
        }
        de.put(41, tc.tid());
        de.put(42, tc.mid());
        // No DE52. The cardholder was verified when the hold was taken; a
        // completion carries no new PIN, the same as every other advice here.
        if (!original.iccHex.isEmpty()) {
            de.put(55, original.iccHex);
        }
        de.put(62, Iso8583.ascii(invoice));

        TxnRecord rec = new TxnRecord();
        rec.type = "SALE_COMPLETE";
        rec.mti = "0220";
        rec.proc = "000000";
        rec.amountMinor = minor(amountMajor);
        rec.stan = stan;
        rec.time = original.time;
        rec.date = original.date;
        rec.pan = !original.pan.isEmpty()
                ? original.pan : panFromTrack2(original.track2);
        rec.track2 = original.track2;
        rec.expiry = original.expiry;
        rec.entryMode = original.entryMode;
        rec.invoice = invoice;
        rec.iccHex = original.iccHex;
        rec.authCode = original.authCode;
        rec.rrn = original.rrn;

        batch.setPendingReversal(rec);
        HostResult r = send(ctx, "SALE COMPLETION (0220)", "0220", de, stan, pinpad);
        r.precedingTrace = recovery.precedingTrace;
        r.record = rec;

        if (r.online && !r.needsReversal) {
            batch.clearPendingReversal();
            rec.approved = r.approved;
            if (!r.rrn.isEmpty()) {
                rec.rrn = r.rrn;
            }
            if (!r.authCode.isEmpty()) {
                rec.authCode = r.authCode;
            }
            if (r.approved) {
                // The hold has become a sale. Take the authorisation out of the
                // batch so a settlement cannot bill for both, and put the
                // completion in its place.
                boolean marked = batch.markVoided(original.invoice);
                Log.i(TAG, "completion approved, pre-auth invoice "
                        + original.invoice
                        + (marked ? " closed" : " NOT FOUND in the batch"));
                batch.add(rec);
            }
        }
        // PAX's rules decide whether anything is owed at all...
        settleReversalSlot(ctx, rec, r);
        // ...and then, unlike PAX, it goes out now rather than
        // waiting to be carried by the next sale.
        reverseNowIfQueued(ctx, pinpad, r, "SALE COMPLETION");
        recordIfFailed(ctx, rec, r);
        return r;
    }

    // ------------------------------------------------------ pre-auth void

    /**
     * Cancel a pre-authorisation (MT 0400).
     *
     * <p>Not a void. A void of a sale is an 0200 with processing code
     * {@code 020000} and its own STAN; cancelling a pre-auth is a
     * <b>reversal</b> that re-sends the original message's identity so the
     * issuer releases the hold. Field for field from a Move/2500 capture
     * against this acquirer and merchant:</p>
     *
     * <pre>
     *   16:51:43  0100  DE3 300000  DE11 000468  DE25 06  DE35 track  DE62 000205
     *             0110  DE39 00  DE37 624210000468  DE38 002443
     *   16:52:09  0400  DE3 300000  DE11 000468  DE25 06  DE2 pan     DE62 000205
     *                   DE12 165145  DE13 0830  DE37 624210000468  DE38 002443
     *             0410  DE39 00
     * </pre>
     *
     * <p>Three things follow from that and are easy to get wrong:</p>
     * <ul>
     *   <li>DE11 is the <b>original's</b> STAN, not a new one. A reversal that
     *       invents its own trace number is one the host cannot match.</li>
     *   <li>The card is sent as DE2, not DE35 - bit 35 is clear in the 0400's
     *       bitmap ({@code 703C05800CC00204}) even though the 0100 that it
     *       reverses carried the track.</li>
     *   <li>DE37 and DE38 echo the authorisation's retrieval reference and
     *       approval code. The generic {@link #reversal} does not send them,
     *       because it exists for a request that never got an answer and so has
     *       neither.</li>
     * </ul>
     *
     * @param original the pre-auth being cancelled, from the batch
     */
    public static synchronized HostResult preAuthVoid(Context ctx, TxnRecord original,
                                         IPinPadBinderService pinpad) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        BatchStore batch = BatchStore.get(ctx);

        if (original == null) {
            HostResult nr = new HostResult();
            return fail(nr, "no pre-authorisation selected to cancel", false);
        }
        if (!"PREAUTH".equals(original.type)) {
            HostResult nr = new HostResult();
            return fail(nr, "invoice " + original.invoice
                    + " is not a pre-authorisation", false);
        }
        if (original.voided) {
            HostResult nr = new HostResult();
            return fail(nr, "invoice " + original.invoice
                    + " is already cancelled", false);
        }
        if (original.stan.isEmpty()) {
            HostResult nr = new HostResult();
            return fail(nr, "original has no STAN (DE11) - it cannot be reversed",
                    false);
        }

        HostResult recovery = prepareOnline(ctx, pinpad);
        if (!recovery.error.isEmpty()) return recovery;

        TreeMap<Integer, String> de = new TreeMap<>();
        // DE2, not DE35 - see the bitmap note above.
        String pan = !original.pan.isEmpty()
                ? original.pan : panFromTrack2(original.track2);
        if (!nz(pan).isEmpty()) {
            de.put(2, pan);
        }
        de.put(3, nz(original.proc).isEmpty() ? "300000" : original.proc);
        de.put(4, original.amountMinor);
        de.put(11, original.stan);            // the ORIGINAL trace number
        de.put(12, original.hostTime.isEmpty() ? original.time : original.hostTime);            // ORIGINAL time
        de.put(13, original.hostDate.isEmpty() ? original.date : original.hostDate);            // ORIGINAL date
        if (isValidExpiry(original.expiry)) {
            de.put(14, original.expiry);      // every brand, as the capture has it
        }
        if (!original.entryMode.isEmpty()) {
            de.put(22, original.entryMode);
        }
        de.put(24, original.requestNii.isEmpty() ? tc.nii() : original.requestNii);
        de.put(25, "06");                     // pre-auth, as on the 0100
        if (!original.rrn.isEmpty()) {
            de.put(37, original.rrn);         // ORIGINAL retrieval reference
        }
        if (!original.authCode.isEmpty()) {
            de.put(38, original.authCode);    // ORIGINAL approval code
        }
        de.put(41, original.requestTid.isEmpty() ? tc.tid() : original.requestTid);
        de.put(42, original.requestMid.isEmpty() ? tc.mid() : original.requestMid);
        // No DE52. Nothing is being authorised here, so there is no PIN to
        // verify - the same reason a void carries none.
        if (!original.iccHex.isEmpty()) {
            de.put(55, original.iccHex);
        }
        if (!original.invoice.isEmpty()) {
            de.put(62, Iso8583.ascii(original.invoice));
        }

        TxnRecord rec = new TxnRecord();
        rec.type = "PREAUTH_VOID";
        rec.mti = "0400";
        rec.proc = nz(de.get(3));
        rec.amountMinor = original.amountMinor;
        rec.stan = original.stan;
        rec.time = original.time;
        rec.date = original.date;
        rec.pan = nz(de.get(2));
        rec.track2 = original.track2;
        rec.expiry = original.expiry;
        rec.entryMode = original.entryMode;
        rec.invoice = original.invoice;
        rec.iccHex = original.iccHex;
        rec.authCode = original.authCode;
        rec.rrn = original.rrn;

        HostResult r = send(ctx, "PRE-AUTH VOID (0400)", "0400", de,
                original.stan, pinpad, original.requestTpdu.isEmpty() ? tc.tpdu() : original.requestTpdu, false);
        r.precedingTrace = recovery.precedingTrace;
        r.record = rec;
        if (!r.online && r.requestSent) r.error = "Pre-auth cancellation status unknown. Retry the same invoice; do not create another authorisation.";

        if (r.online && !r.needsReversal) {
            batch.clearPendingReversal();
            rec.approved = r.approved;
            if (r.approved) {
                // The hold is released, so the authorisation must not be
                // completed or settled afterwards. Same bookkeeping a void
                // does - the original drops out of the reconciliation totals.
                boolean marked = batch.markVoided(original.invoice);
                Log.i(TAG, "pre-auth void approved, original invoice "
                        + original.invoice
                        + (marked ? " marked voided" : " NOT FOUND in the batch"));
                batch.add(rec);
            }
        }
        recordIfFailed(ctx, rec, r);
        return r;
    }

    // ------------------------------------------------------------- reversal

    /**
     * Reversal (MT 0400 - spec 7.35).
     *
     * <p>Sent when a financial request went into the network and no valid
     * response came back before the timeout. The spec requires the reversal to
     * be sent persistently until the host answers, and the host "should always
     * send '00'". The message repeats the original's processing code, amount,
     * STAN, entry mode and track; DE25 is mandatory here.</p>
     */
    public static synchronized HostResult reversal(Context ctx, TxnRecord orig,
                                      IPinPadBinderService pinpad) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        BatchStore batch = BatchStore.get(ctx);
        HostResult r = new HostResult();
        if (orig == null) {
            r.error = "no pending reversal";
            return r;
        }
        TxnRecord queued = batch.pendingReversal();
        if (sCardCompletionPending != null || !sameTransaction(orig, queued)) {
            return fail(r, "Recovery deferred: transaction is completing or the queue changed", false);
        }

        TreeMap<Integer, String> de = new TreeMap<>();

        // The reversal does NOT mirror the original's bitmap. DBBL's rule for
        // the 0400 is its own:
        //
        //     DE2  PAN          MANDATORY, whatever the original carried
        //     DE14 expiry       always, when one is known
        //     DE13 date         NOT sent
        //     DE35 track 2      NOT sent
        //
        // This is deliberately different from every other message this class
        // builds, where DE2/DE35 follow how the card was read and DE14 follows
        // the brand. A reversal identifies a transaction the host already has;
        // it is not a fresh presentment, so the host wants the account plainly
        // in DE2 and nothing that would make it look like one.
        //
        // PAN and expiry are recovered from the track when the original was a
        // swipe or a fallback that sent no DE2 - the track image carries both,
        // so "the original had no PAN" is never a reason to leave DE2 out.
        String revPan = nz(orig.pan);
        if (revPan.isEmpty()) {
            revPan = panFromTrack2(orig.track2);
            if (!revPan.isEmpty()) {
                Log.i(TAG, "reversal DE2 recovered from the stored track");
            }
        }
        if (revPan.isEmpty()) {
            // Nothing to identify the account with. Sending a 0400 the host
            // cannot match is worse than saying so here, where it is still
            // attached to the transaction that caused it.
            Log.e(TAG, "reversal has no PAN and none can be derived from the"
                    + " track - DE2 is mandatory, refusing to send a 0400"
                    + " the host cannot match");
            return fail(r, "reversal has no PAN for DE2", false);
        }
        de.put(2, revPan);                 // M - DBBL: always, never DE35

        String revExp = nz(orig.expiry);
        if (!isValidExpiry(revExp)) {
            revExp = expiryFromTrack2(orig.track2);
        }

        de.put(3, orig.proc);              // same as the original (M)
        de.put(4, orig.amountMinor);       // M
        de.put(11, orig.stan);             // M - the original's STAN
        de.put(12, orig.time);
        // No DE13. DBBL take the date from DE12 and the original's STAN.
        if (isValidExpiry(revExp)) {
            de.put(14, revExp);            // always on a reversal, any brand
        }
        if (!orig.entryMode.isEmpty()) {
            de.put(22, orig.entryMode);
        }
        de.put(24, tc.nii());              // M
        de.put(25, "00");                  // M on a reversal
        // DE39 - the reason, exactly as PAX's PackReversal.setBitData39 sends
        // its dupReason. Our 0400 used to leave bit 39 clear, so the host was
        // never told whether this was a timeout, a bad MAC or a retry.
        if (!nz(orig.dupReason).isEmpty()) {
            de.put(39, orig.dupReason);
        }
        de.put(41, tc.tid());              // M
        de.put(42, tc.mid());              // M
        if (!orig.iccHex.isEmpty()) {
            de.put(55, orig.iccHex);
        }
        if (!orig.invoice.isEmpty()) {
            de.put(62, Iso8583.ascii(orig.invoice));
        }

        if (orig.tip() > 0) de.put(54, Iso8583.ascii(TipAmounts.field(orig.tip())));
        int tries = batch.bumpReversalTries();
        r = send(ctx, "REVERSAL (0400) attempt " + tries, "0400", de, orig.stan, pinpad);

        if (TransactionPolicy.reversalAccepted(r.online && !r.needsReversal, r.responseCode)) {
            batch.clearPendingReversal();
        } else if (tries >= tc.reversalMaxTries()) {
            r.error = "reversal still unacknowledged after " + tries
                    + " attempt(s) - retained for recovery; settlement remains blocked";
            Log.e(TAG, r.error);
        }
        return r;
    }

    /**
     * Send any outstanding reversal before starting new business. Call this at
     * the top of a transaction and after a logon.
     *
     * @return null when there was nothing to do
     */
    /**
     * The host approved, but the chip declined at the second Generate AC.
     *
     * <p>This is the dangerous case. The issuer has authorised the amount and
     * will settle it, while the terminal is showing the merchant a decline. If
     * nothing is sent, the cardholder is charged for a sale the merchant was
     * told did not happen, and the entry sits in the batch waiting to be
     * settled on top of that.</p>
     *
     * <p>Spec 7.35 is the answer: reverse it. The entry is dropped from the
     * batch first so it cannot be settled even if the reversal itself fails -
     * in that case the reversal stays queued and is retried before the next
     * transaction.</p>
     */
    public static synchronized HostResult reverseCardDecline(Context ctx, TxnRecord rec,
                                                IPinPadBinderService pinpad) {
        HostResult r = new HostResult();
        if (rec == null || !rec.approved) {
            return fail(r, "No host-approved transaction to reverse after chip completion", false);
        }
        BatchStore batch = BatchStore.get(ctx);
        sCardCompletionPending = null;
        batch.remove(rec.invoice);
        Log.w(TAG, "host approved but the card declined at GAC2 - reversing "
                + "invoice " + rec.invoice + " stan " + rec.stan);
        // Arm the persistent slot first: if the reversal does not get through
        // now it must survive a restart and be retried (spec 7.35).
        batch.setPendingReversal(rec);
        return flushPendingReversalNow(ctx, pinpad);
    }

    /** Attach the 0400/0410 evidence to the original transaction for its receipt. */
    public static synchronized HostResult reverseCardDecline(Context ctx, HostResult original,
                                                             IPinPadBinderService pinpad) {
        if (original == null || !original.approved || original.needsReversal)
            return fail(new HostResult(), "Chip completion cannot reverse a host decline", false);
        HostResult recovery = reverseCardDecline(ctx, original.record, pinpad);
        if (original != null) original.attachReversal(recovery);
        return recovery;
    }

    /** Called on the transaction worker after successful contact-chip completion. */
    public static synchronized void completeCardApproval(Context ctx, HostResult result) {
        if (result == null || !result.awaitingCardCompletion) return;
        BatchStore.get(ctx).completeApproved(result.record);
        result.awaitingCardCompletion = false;
        sCardCompletionPending = null;
    }

    private static boolean sameTransaction(TxnRecord first, TxnRecord second) {
        return first != null && second != null && first.invoice.equals(second.invoice)
                && first.stan.equals(second.stan) && first.mti.equals(second.mti);
    }

    /**
     * Deliver the queued reversal NOW, retrying up to the configured limit,
     * instead of leaving it for the next transaction to carry.
     *
     * <p>The old behaviour sent one 0400 and, if that did not land, queued the
     * rest behind whatever the merchant happened to do next - which could be
     * minutes, or the end of the day. For the whole of that window the issuer
     * is holding an authorisation the merchant has been told did not happen.
     * The bank asked for the reversal to go immediately and keep going, and
     * that is what spec 7.35's "persistently" means.</p>
     *
     * <p>Attempts come from {@code TerminalConfig.reversalMaxTries()}, spaced
     * {@link #REVERSAL_RETRY_GAP_MS} apart. Exhausting automatic attempts keeps
     * the record for recovery and leaves new transactions and settlement blocked.</p>
     *
     * <p>The reversal is a standalone 0400 and nothing else. It is NOT carried
     * in DE56 of a later sale the way PAX piggybacks its own - that was tried
     * and removed deliberately: this terminal's reversal is an independent
     * message with its own delivery and its own retry budget.</p>
     *
     * <p>Runs on the caller's thread, which is already a background thread in
     * every path that reaches here.</p>
     */
    public static synchronized HostResult flushPendingReversalNow(Context ctx,
                                                     IPinPadBinderService pinpad) {
        BatchStore batch = BatchStore.get(ctx);
        HostResult r = new HostResult();
        if (!batch.hasPendingReversal()) {
            return r;
        }
        TxnRecord pending = batch.pendingReversal();
        if (pending == null) {
            return r;
        }

        // Attempt one, here, on this thread. This is the attempt that matters:
        // it goes out while the transaction that caused it is still on screen,
        // so a working host shows 0400 out / 0410 back in the packet trace
        // directly beneath the 0200 that needed reversing.
        r = reversal(ctx, pending, pinpad);
        if (!batch.hasPendingReversal()) {
            Log.i(TAG, "reversal delivered on the first attempt");
            return r;
        }

        // It did not land. The rest of the budget is spent in the background:
        // with a dead host each attempt costs a full socket timeout and there
        // is REVERSAL_RETRY_GAP_MS between them, which is minutes of a terminal
        // the merchant cannot use if it were done here.
        Log.w(TAG, "first reversal attempt did not land (" + r.error
                + ") - handing the rest to the background retry");
        startReversalRetry(ctx, pinpad);
        return r;
    }

    /**
     * Why a transaction is being reversed - PAX's {@code dupReason}, sent as
     * DE39 of the 0400 and copied from {@code TransData}.
     */
    /** No response received at all. */
    public static final String DUP_REASON_NO_RECV = "98";
    /** A response arrived but could not be trusted - bad MAC, bad packet. */
    public static final String DUP_REASON_MACWRONG = "A0";
    /** A previous reversal attempt was not acknowledged. */
    public static final String DUP_REASON_OTHERS = "06";

    /**
     * Gap between reversal attempts - DBBL asked for thirty seconds.
     *
     * <p>It used to be 1.5s, which is not a retry policy so much as a burst: a
     * host that has just dropped the connection is given no time to come back,
     * and three attempts are spent inside a couple of seconds on a link that
     * was never going to answer in that window.</p>
     */
    private static final long REVERSAL_RETRY_GAP_MS = 30000L;

    /** The background retry, so only one can ever be in flight. */
    private static Thread sReversalRetry;
    private static TxnRecord sRetryRecord;

    /** Retry up to the configured limit; retain unacknowledged recovery data. */
    private static synchronized void startReversalRetry(Context ctx,
                                                        final IPinPadBinderService pinpad) {
        TxnRecord current = BatchStore.get(ctx).pendingReversal();
        if (sReversalRetry != null && sReversalRetry.isAlive()) {
            if (sameTransaction(sRetryRecord, current)) return;
            sReversalRetry.interrupt();
        }
        sRetryRecord = current;
        // The application context, not the activity's: this outlives the screen
        // that started it.
        final Context app = ctx.getApplicationContext();
        final TxnRecord retryRecord = current;
        sReversalRetry = new Thread(new Runnable() {
            @Override
            public void run() {
                BatchStore batch = BatchStore.get(app);
                int max = TerminalConfig.get(app).reversalMaxTries();
                while (batch.hasPendingReversal() && batch.reversalTries() < max) {
                    try {
                        Thread.sleep(REVERSAL_RETRY_GAP_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        synchronized (OnlineHost.class) {
                            TxnRecord pending = batch.pendingReversal();
                            if (!sameTransaction(retryRecord, pending)) return;
                            if (batch.reversalTries() >= max) break;
                            pending.dupReason = DUP_REASON_OTHERS;
                            reversal(app, pending, pinpad);
                        }
                    } catch (Throwable e) {
                        // The pin pad binder can go away with the activity that
                        // owned it. That is not a reason to abandon the queue -
                        // it counts as a failed attempt like any other.
                        Log.e(TAG, "reversal retry threw: " + e);
                    }
                }
                if (batch.hasPendingReversal()) {
                    Log.e(TAG, "REVERSAL PENDING: " + batch.reversalTries()
                            + " attempt(s) of " + max + " unacknowledged."
                            + " Retaining the record for the next recovery attempt.");
                } else {
                    Log.i(TAG, "reversal delivered after "
                            + batch.reversalTries() + " attempt(s)");
                }
            }
        }, "ReversalRetry");
        sReversalRetry.setDaemon(true);
        sReversalRetry.start();
    }

    /**
     * Settle the reversal slot once {@code send} has returned - PAX's rules.
     *
     * <p>Three things happen here, all of them copied from
     * {@code Online.online()} and {@code Transmit.transmit()}:</p>
     *
     * <ol>
     *   <li><b>Nothing was sent, nothing is owed.</b> PAX arms the reversal
     *       only after {@code tcp.onSend()} succeeds. Our builders arm before
     *       the socket so a power cut mid-exchange still leaves one queued, so
     *       instead the slot is DISARMED here when the request never reached
     *       the wire - same outcome, and the power-cut case still works.</li>
     *   <li><b>The reason goes in DE39.</b> No reply at all is "98"; a reply
     *       that could not be trusted - bad MAC, unparseable, STAN mismatch, no
     *       DE39 - is "A0".</li>
     *   <li><b>A host decline deletes the reversal.</b> PAX calls
     *       {@code deleteDupRecord()} for every response code that is not "00".
     *       The host answered; there is nothing outstanding.</li>
     * </ol>
     */
    private static void settleReversalSlot(Context ctx, TxnRecord rec, HostResult r) {
        BatchStore batch = BatchStore.get(ctx);
        if (!batch.hasPendingReversal()) {
            return;
        }
        if (!r.requestSent) {
            Log.i(TAG, "request never reached the wire - disarming the reversal");
            batch.clearPendingReversal();
            return;
        }
        if (r.online && !r.needsReversal) {
            // A valid response arrived. Approved or declined, the host has
            // spoken and PAX drops the reversal either way.
            batch.clearPendingReversal();
            return;
        }
        rec.dupReason = r.online ? DUP_REASON_MACWRONG : DUP_REASON_NO_RECV;
        batch.setPendingReversal(rec);
        Log.w(TAG, "reversal armed, DE39 reason " + rec.dupReason);
    }

    /**
     * Deliver this transaction's own reversal before the flow that created it
     * ends, instead of leaving it for whatever the merchant does next.
     *
     * <p>Every builder arms {@link BatchStore#setPendingReversal} before it
     * sends and disarms it when a valid response comes back. If the slot is
     * still armed once {@code send} returns, the request went into the network
     * and nothing came back - the issuer may be holding an authorisation the
     * merchant has just been told did not happen. The old code queued that
     * behind the next online transaction, which could be minutes or the end of
     * the day. DBBL asked for it to go out immediately, and spec 7.35's
     * "persistently" says the same thing.</p>
     *
     * <p>Attempts are bounded by {@code TerminalConfig.reversalMaxTries()}.
     * Running out does not empty the slot: settlement stays blocked, the home
     * screen keeps showing REVERSAL, and the opportunistic flush at the top of
     * the next transaction still gets a go. The transaction result is never
     * rewritten here - the reversal outcome is appended to {@code r.error} so
     * it reaches the slip and the log without turning an approval into a
     * decline or the other way round.</p>
     *
     * <p>Runs on the caller's thread, which is a background thread in every
     * path that reaches here, so the terminal is still showing its processing
     * screen while the 0400 goes out.</p>
     */
    private static void reverseNowIfQueued(Context ctx, IPinPadBinderService pinpad,
                                           HostResult r, String what) {
        BatchStore batch = BatchStore.get(ctx);
        if (r == null || !batch.hasPendingReversal()) {
            return;
        }
        Log.w(TAG, what + " left a reversal armed - sending the 0400 now,"
                + " without waiting for the next online transaction");
        HostResult rv = flushPendingReversalNow(ctx, pinpad);
        r.attachReversal(rv);

        String note;
        if (!batch.hasPendingReversal()) {
            note = "reversal sent and acknowledged after "
                    + batch.reversalTries() + " attempt(s)";
            Log.i(TAG, note);
        } else {
            note = "reversal NOT acknowledged after " + batch.reversalTries()
                    + " attempt(s) - it stays armed and blocks settlement";
            if (rv != null && !nz(rv.error).isEmpty()) {
                note = note + ": " + rv.error;
            }
            Log.e(TAG, note);
        }
        r.error = nz(r.error).isEmpty() ? note : r.error + "; " + note;
    }

    public static synchronized HostResult flushPendingReversal(Context ctx, IPinPadBinderService pinpad) {
        BatchStore batch = BatchStore.get(ctx);
        TxnRecord pending = batch.pendingReversal();
        if (pending == null) {
            return null;
        }
        Log.i(TAG, "flushing pending reversal: " + pending);
        return reversal(ctx, pending, pinpad);
    }

    // ------------------------------------------------------------------ EMI

    /**
     * EMI is a DBBL extension - the Hypercom spec has no instalment
     * transaction, so everything here follows the PAX reference rather than
     * section 7.
     *
     * <p>PAX sends ZIP and Want2Buy as MTI 1200, processing code 92 00 00, with
     * DE24 = 295. That processing code means settlement on the acquirer route,
     * so the NII is doing the work: 295 selects DBBL's EMI host application,
     * where 92 00 00 means an instalment sale. Both are configurable
     * ({@link TerminalConfig#emiNii()}) because they are routing decisions the
     * bank owns.</p>
     */
    private static final String EMI_MTI = "1200";
    private static final String EMI_PROC = "920000";
    // No package download. PAX have an EMI_PACKAGE message (MTI 1304) that
    // pulls the plans from the host in DE72, but DBBL do not serve it - the
    // EMI plans are configured by hand in the admin panel instead. A button
    // that sends a message the host will not answer is worse than no button.

    /**
     * The EMI block in DE48, byte for byte as PAX builds it:
     *
     * <pre>
     *   E01 003 &lt;product code&gt;      the EMI product
     *   E02 002 &lt;tenure&gt;            term in months, two digits
     *   E03 003 &lt;interest index&gt;    the rate the host should apply
     * </pre>
     */
    static String emiField48(EmiPackage pkg, int tenure) {
        if (pkg == null) {
            return "";
        }
        return "E01003" + padRight(pkg.productCode, 3)
                + "E02002" + String.format(java.util.Locale.US, "%02d", tenure)
                + "E03003" + padRight(pkg.interestIndex, 3);
    }

    /**
     * EMI sale. Same card data as an ordinary sale, plus the instalment terms
     * in DE48 and the EMI routing in DE24.
     *
     * @param pkg    the product the cardholder chose
     * @param tenure the term in months
     */
    /** DBBL EMI product: 0% Insta Pay. First digit of DE61. */
    public static final int EMI_TYPE_INSTA_PAY = 0;

    /** DBBL EMI product: Easy Buy. First digit of DE61. */
    public static final int EMI_TYPE_EASY_BUY = 1;

    /** What the receipt and the tenure screen call each product. */
    public static String emiTypeName(int emiType) {
        return emiType == EMI_TYPE_EASY_BUY ? "EASY BUY" : "0% INSTA PAY";
    }

    /**
     * DE61 for an EMI sale: the product and the term, three ASCII digits.
     *
     * <p>One digit of product then two of tenure, sent as a LLLVAR of length
     * 003. Taken from captures of a Move/2500 on this same host and merchant:
     * a six-month 0% Insta Pay sale sends {@code 006}, a twelve-month Easy Buy
     * sends {@code 112}.</p>
     *
     * <pre>
     *   61 | 00 03 | 30 30 36        "006"  0% Insta Pay, 6 months
     *   61 | 00 03 | 31 31 32        "112"  Easy Buy,    12 months
     * </pre>
     */
    static String emiField61(int emiType, int tenure) {
        return String.format(java.util.Locale.US, "%d%02d",
                emiType == EMI_TYPE_EASY_BUY ? 1 : 0, tenure);
    }

    /**
     * EMI sale. An ordinary sale plus the instalment terms in DE61.
     *
     * <p>This used to follow the PAX reference: processing code and NII
     * overridden for an instalment route, and the plan written into DE48 as
     * {@code E01/E02/E03}. DBBL's host does not work that way. Captures from a
     * Move/2500 against this acquirer show an EMI sale going out with the
     * <em>same</em> processing code ({@code 000000}) and the <em>same</em> NII
     * ({@code 0008}) as any other sale, no EMI block in DE48 at all, and the
     * whole instalment instruction carried in DE61 as three digits.</p>
     *
     * <p>{@link #emiField48} is kept below for reference in case DBBL ever ask
     * for the PAX form, but nothing calls it.</p>
     *
     * @param emiType {@link #EMI_TYPE_INSTA_PAY} or {@link #EMI_TYPE_EASY_BUY}
     * @param pkg     the configured plan, or null - DE61 does not need one,
     *                and 0% Insta Pay has no rate to look up
     * @param tenure  the term in months
     */
    public static synchronized HostResult emiSale(Context ctx, String amountMajor, String pan,
                                     String pinBlockHex, String track2, String expiry,
                                     String iccDataHex, EntryMode entry, EmiPackage pkg,
                                     int emiType, int tenure, IPinPadBinderService pinpad) {
        if (tenure <= 0) {
            HostResult nr = new HostResult();
            return fail(nr, "no EMI tenure selected", false);
        }
        final String de61 = emiField61(emiType, tenure);
        HostResult r = transaction(ctx, "EMI_SALE", amountMajor, pan, pinBlockHex,
                track2, expiry, iccDataHex, entry, "00", pinpad,
                new Extra() {
                    @Override
                    public void apply(java.util.TreeMap<Integer, String> de) {
                        de.put(61, Iso8583.ascii(de61));
                    }
                });
        if (r.record != null) {
            r.record.type = "EMI_SALE";
            r.record.tenure = tenure;
            r.record.emiProduct = de61;
            long amt = r.record.amount();
            // 0% Insta Pay is exactly the amount split across the term. Easy
            // Buy carries a rate, so use the configured plan when there is
            // one and fall back to a straight split when there is not - an
            // instalment figure that is merely un-marked-up is better on a
            // receipt than one that is missing.
            long instal = (pkg != null && emiType == EMI_TYPE_EASY_BUY)
                    ? pkg.installment(amt)
                    : Math.round((double) amt / tenure);
            r.record.installmentMinor = String.valueOf(instal);
        }
        return r;
    }

    /**
     * A "Nexus (KonaMoney)" prepaid sale - Kona I terminal spec v1.0.7.
     *
     * <p>One method for all three ways the card is presented, because they
     * differ only in where the 37-digit track-2 came from:</p>
     *
     * <ul>
     *   <li><b>QR</b> - the phone app's 40 digits, split by
     *       {@link KonaMoney#parseQr}. The 37 go in DE35 and the 3-digit
     *       {@code dCVV_MD} in DE48 sub-element 31 (spec 2.1).</li>
     *   <li><b>Contactless</b> - EMV CL in MSD mode; the kernel returns the
     *       same track in tag 57 (spec 2.2).</li>
     *   <li><b>Contact IC</b> - ordinary EMV, with the card's tags forwarded
     *       (spec 2.1).</li>
     * </ul>
     *
     * <p>DE2 and DE14 are filled from the track rather than taken from the
     * caller. The track is the credential the issuer will verify, and a PAN
     * typed beside it that disagrees is how a terminal ends up charging a card
     * that was never presented.</p>
     *
     * @param track2Full the 37-digit track-2 equivalent, however it arrived
     * @param dcvvMd     the QR's extra 3 digits, or null on CL and IC
     * @param iccDataHex DE55 built by {@link KonaMoney}, or null
     */
    public static synchronized HostResult konaSale(Context ctx, String amountMajor,
                                      String track2Full, String dcvvMd,
                                      String iccDataHex, EntryMode entry,
                                      String pinBlockHex, IPinPadBinderService pinpad) {
        final KonaMoney.Track2 t2 = KonaMoney.parseTrack2(track2Full);
        if (!t2.ok()) {
            HostResult nr = new HostResult();
            return fail(nr, "KonaMoney card data is not usable: " + t2.error, false);
        }
        final String extraDe48 = dcvvMd == null || dcvvMd.isEmpty()
                ? "" : KonaMoney.de48DcvvMd(dcvvMd);
        if (dcvvMd != null && !dcvvMd.isEmpty() && extraDe48.isEmpty()) {
            HostResult nr = new HostResult();
            return fail(nr, "KonaMoney QR carried a malformed dCVV_MD", false);
        }

        HostResult r = transaction(ctx, "SALE", amountMajor, t2.pan, pinBlockHex,
                t2.raw, t2.expiry, iccDataHex, entry, "00", pinpad,
                new Extra() {
                    @Override
                    public void apply(java.util.TreeMap<Integer, String> de) {
                        if (!extraDe48.isEmpty()) {
                            // Append rather than replace: DE48 already carries
                            // the P10 random key block when a PIN was taken,
                            // and dropping that would break the PIN block.
                            de.put(48, nz(de.get(48)) + extraDe48);
                        }
                    }
                });
        if (r.record != null) {
            r.record.pan = t2.pan;
            r.record.expiry = t2.expiry;
        }
        return r;
    }

    /**
     * Void an EMI sale.
     *
     * <p>The packet is an <b>ordinary void</b> - same MTI, same processing
     * code, same NII as voiding any other sale. That is what the PAX reference
     * does: {@code ZipVoidTrans extends SaleVoidTrans} and constructs itself
     * with {@code ETransType.VOID}, so it packs through {@code PackSaleVoid}
     * with {@code setBitData24} taking the acquirer NII from the terminal
     * configuration. The only EMI-specific part is the check that the original
     * really was an EMI sale, which {@code ZipVoidTrans} adds by overriding
     * {@code validateOrigTransData}.</p>
     *
     * <p>An earlier version of this method sent the void on the EMI NII. That
     * was wrong: the void is cancelling an entry in the acquirer's batch, and
     * routing it elsewhere would leave the acquirer holding a sale nobody had
     * cancelled.</p>
     */
    public static synchronized HostResult emiVoid(Context ctx, TxnRecord original,
                                     IPinPadBinderService pinpad) {
        if (original != null && !original.isEmi()) {
            HostResult nr = new HostResult();
            return fail(nr, "invoice " + original.invoice + " is not an EMI sale", false);
        }
        HostResult r = voidSale(ctx, original, null, pinpad);
        if (r.record != null && original != null) {
            // Keep the EMI details on the void record so the slip and the EMI
            // report can show what was cancelled.
            r.record.type = "EMI_VOID";
            r.record.tenure = original.tenure;
            r.record.emiProduct = original.emiProduct;
            r.record.installmentMinor = original.installmentMinor;
        }
        return r;
    }

    // ------------------------------------------------------------ settlement

    /** Host asked for the batch before it will reconcile (spec section 8, "95"). */
    private static final String RC_BATCH_TRANSFER = "95";
    /** Reconcile error - the terminal and host totals disagree. */
    private static final String RC_RECONCILE_ERROR = "77";
    private static final String RC_BAD_BATCH_NUMBER = "80";
    private static final String RC_BATCH_NOT_FOUND = "85";

    /** How many upload-then-trailer rounds to attempt before giving up. */
    private static final int MAX_UPLOAD_ROUNDS = 2;

    /** Progress callback so the UI can say what stage settlement has reached. */
    public interface Progress {
        void step(String text);
    }

    /** Everything the settlement slip and the operator prompt need. */
    public static final class SettleResult {
        /** True only when the host has accepted the totals and the batch is closed. */
        public boolean approved;
        /** True when the host demanded the batch and the terminal uploaded it. */
        public boolean uploaded;
        public int uploadSent, uploadFailed;
        /** Set when there was nothing in the batch to settle. */
        public boolean noTrans;
        /** Set when a reversal is still outstanding, which blocks settlement. */
        public boolean reversalPending;
        /** The batch number that was settled, captured before it advances. */
        public String batchNo = "";
        /** Snapshot of the batch, taken before an approval clears it. */
        public java.util.List<TxnRecord> batch = new java.util.ArrayList<>();
        public BatchStore.Totals totals = new BatchStore.Totals();
        /** The 0500 / 92 00 0x exchange. */
        public HostResult settle;
        /** The 0500 / 96 00 0x trailer, only sent after a batch upload. */
        public HostResult trailer;
        public String message = "";

        /** The exchange whose packet trace should go on the slip. */
        public HostResult lastExchange() {
            return trailer != null ? trailer : settle;
        }

        public String responseCode() {
            HostResult r = lastExchange();
            return r == null ? "" : r.responseCode;
        }

        /** One line for the slip and the result screen. */
        public String displayMessage() {
            if (approved) {
                return "SETTLEMENT SUCCESSFUL";
            }
            if (!message.isEmpty()) {
                return message;
            }
            HostResult r = lastExchange();
            if (r == null) {
                return "SETTLEMENT FAILED";
            }
            String code = r.responseCode;
            String text = !r.hostText.isEmpty() ? r.hostText
                    : ResponseCode.displayText(code, r.authCode, r.hostText);
            return text.isEmpty() ? "SETTLEMENT FAILED " + code : text;
        }
    }

    /**
     * Close the batch (spec 7.39, and 7.40 when the host wants it uploaded).
     *
     * <p>The sequence mirrors the PAX reference, expressed in the codes this
     * spec actually defines:</p>
     * <ol>
     *   <li>refuse to settle an empty batch,</li>
     *   <li>flush any outstanding reversal first - the host cannot reconcile
     *       against a transaction the terminal has not yet cancelled
     *       (spec 7.35),</li>
     *   <li>send the settlement request, MT 0500 processing code 92 00 0x,
     *       carrying the batch number in DE60 and the reconciliation totals in
     *       DE63,</li>
     *   <li>on DE39 = "95" (BATCH TRANSFER, WAIT) upload every batch entry as
     *       an 0320 and then send the settlement trailer, MT 0500 processing
     *       code 96 00 0x, with the same DE60 and DE63,</li>
     *   <li>only an approved response closes the batch: the journal is cleared
     *       and the batch number advances.</li>
     * </ol>
     *
     * <p>A failed settlement never clears the batch. The transactions stay
     * where they are so the merchant can retry.</p>
     */
    public static synchronized SettleResult settle(Context ctx, IPinPadBinderService pinpad, Progress p) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        BatchStore batch = BatchStore.get(ctx);

        SettleResult res = new SettleResult();
        res.batchNo = tc.batchNo();
        res.batch = batch.all();
        res.totals = batch.totals();

        // 1. Nothing to settle. PAX shows "NO TRANSACTION" rather than sending
        //    a batch of zeros, because a zero batch the host has never seen is
        //    a reconcile error waiting to happen.
        if (res.totals.isZero()) {
            res.noTrans = true;
            res.message = "NO TRANSACTIONS IN BATCH";
            Log.i(TAG, "settlement refused: batch " + res.batchNo + " is empty");
            return res;
        }

        step(p, "SENDING PENDING TRANSACTIONS");
        HostResult recovery = prepareOnline(ctx, pinpad);
        if (!recovery.error.isEmpty()) {
            res.settle = recovery; res.message = recovery.error;
            res.reversalPending = batch.hasPendingReversal(); return res;
        }
        // 3. Settlement request.
        step(p, "SETTLEMENT");
        res.settle = settlementRequest(ctx, pinpad, "920000", res.batchNo, res.totals,
                "SETTLEMENT (0500)");
        res.settle.precedingTrace = recovery.precedingTrace;
        if (res.settle.approved) {
            closeBatch(ctx, res);
            return res;
        }

        String rc = res.settle.responseCode;
        if (!RC_BATCH_TRANSFER.equals(rc)) {
            res.message = settleFailureText(res.settle, rc);
            Log.w(TAG, "settlement declined: " + res.message);
            return res;
        }

        // 4. "95" - the host and the terminal are out of balance, so it wants
        //    the batch. Upload it and follow with the settlement trailer. The
        //    trailer may itself answer "95", which per spec 7.17 means repeat
        //    the upload; do that a bounded number of times.
        for (int round = 1; round <= MAX_UPLOAD_ROUNDS; round++) {
            step(p, "BATCH UPLOAD " + round);
            uploadBatch(ctx, pinpad, res, p);

            step(p, "SETTLEMENT TRAILER");
            res.trailer = settlementRequest(ctx, pinpad, "960000", res.batchNo, res.totals,
                    "SETTLEMENT TRAILER (0500)");
            if (res.trailer.approved) {
                closeBatch(ctx, res);
                return res;
            }
            if (!RC_BATCH_TRANSFER.equals(res.trailer.responseCode)) {
                break;
            }
            Log.w(TAG, "trailer asked for the batch again, round " + round);
        }

        res.message = settleFailureText(res.trailer, res.trailer == null
                ? rc : res.trailer.responseCode);
        return res;
    }

    /**
     * Settlement request or settlement trailer - the same layout, only the
     * processing code differs (spec 7.39 / 7.40).
     *
     * <p>Request fields are exactly the ones the spec marks mandatory in the
     * request column: DE11, DE24, DE41, DE42, DE60 and DE63. DE12, DE13, DE37
     * and DE39 are response-only and are deliberately not sent.</p>
     */
    private static HostResult settlementRequest(Context ctx, IPinPadBinderService pinpad,
                                                String procCode, String batchNo,
                                                BatchStore.Totals totals, String title) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        String stan = tc.nextStan();

        TreeMap<Integer, String> de = new TreeMap<>();
        de.put(3, procCode);                          // 92 00 0x or 96 00 0x
        de.put(11, stan);                             // M
        de.put(24, tc.nii());                         // M
        de.put(41, tc.tid());                         // M
        de.put(42, tc.mid());                         // M
        de.put(60, Iso8583.ascii(batchNo));           // M - spec 6.25.1
        de.put(63, Iso8583.ascii(totals.de63()));     // M - spec 6.28.1

        Log.i(TAG, title + " batch=" + batchNo + " DE63=" + totals.de63());
        return send(ctx, title, "0500", de, stan, pinpad);
    }

    /**
     * Send the open batch to the host, one 0320 per transaction (spec 7.7).
     *
     * <p>Every entry is uploaded, including the ones a later void cancelled -
     * the host is rebuilding its own copy of the batch, so it needs the void
     * advices too in order to arrive at the same totals.</p>
     */
    private static void uploadBatch(Context ctx, IPinPadBinderService pinpad,
                                    SettleResult res, Progress p) {
        int i = 0;
        int n = res.batch.size();
        for (TxnRecord r : res.batch) {
            i++;
            step(p, "UPLOADING " + i + "/" + n);
            HostResult up = batchUpload(ctx, r, pinpad);
            if (up.approved) {
                res.uploadSent++;
            } else {
                res.uploadFailed++;
                Log.w(TAG, "batch upload rejected for invoice " + r.invoice
                        + ": " + up.responseCode + " " + up.error);
            }
        }
        res.uploaded = true;
        Log.i(TAG, "batch upload finished: " + res.uploadSent + " accepted, "
                + res.uploadFailed + " rejected");
    }

    /**
     * One transaction of a batch upload, MT 0320 (spec 7.7).
     *
     * <p>Mandatory in the request: DE3, DE4, DE11, DE12, DE13, DE22, DE24,
     * DE25, DE37, DE41, DE42. DE39 carries the response code the host
     * originally gave - the spec says assume "00" when it is absent, but
     * sending it removes the guesswork. DE60 carries the original message data
     * (spec 6.25.3) so the host can tie the upload back to the original, since
     * the 0320 has its own trace number.</p>
     *
     * <p>DE11 is a fresh STAN for the upload itself; the original trace goes in
     * DE60 where the spec puts it.</p>
     */
    public static synchronized HostResult batchUpload(Context ctx, TxnRecord r, IPinPadBinderService pinpad) {
        HostResult recovery = prepareOnline(ctx, pinpad);
        if (!recovery.error.isEmpty()) return recovery;
        TerminalConfig tc = TerminalConfig.get(ctx);
        String stan = tc.nextStan();

        TreeMap<Integer, String> de = new TreeMap<>();
        String pan = !nz(r.pan).isEmpty() ? r.pan : panFromTrack2(r.track2);
        if (!pan.isEmpty()) {
            de.put(2, pan);                            // C06
        }
        de.put(3, r.proc);                             // M - same as the original
        de.put(4, r.amountMinor);                      // M
        de.put(11, stan);                              // M - new trace for the upload
        de.put(12, r.time);                            // M - original time
        de.put(13, r.date);                            // M - original date
        // Same rule as the original: Mastercard for a sale, every brand for a
        // void.
        if (sendsExpiry(pan, r.track2, r.isVoid()) && isValidExpiry(r.expiry)) {
            de.put(14, r.expiry);                      // C06
        }
        // DE22 is mandatory on the upload. Use whatever the original was sent
        // with; the fallback claims no PIN rather than inventing one.
        de.put(22, nz(r.entryMode).isEmpty() ? "020" : r.entryMode);   // M
        de.put(24, tc.nii());                          // M
        de.put(25, "00");                              // M - normal presentment
        if (!nz(r.rrn).isEmpty()) {
            de.put(37, r.rrn);                         // M - original retrieval reference
        }
        if (!nz(r.authCode).isEmpty()) {
            de.put(38, r.authCode);                    // O
        }
        de.put(39, r.approved ? "00" : "05");          // O - the original outcome
        de.put(41, tc.tid());                          // M
        de.put(42, tc.mid());                          // M
        if (!nz(r.iccHex).isEmpty()) {
            de.put(55, r.iccHex);
        }
        de.put(60, Iso8583.ascii(originalMessageData(r)));   // O - spec 6.25.3
        if (!nz(r.invoice).isEmpty()) {
            de.put(62, Iso8583.ascii(r.invoice));      // O - invoice / ECR reference
        }

        HostResult uploaded = send(ctx, "BATCH UPLOAD (0320) inv " + r.invoice, "0320", de, stan, pinpad);
        uploaded.precedingTrace = recovery.precedingTrace;
        return uploaded;
    }

    /**
     * DE60 "Original Message Data" for a batch upload (spec 6.25.3):
     * original message type an 4, original trace number an 6, then 12 spaces
     * reserved for future use.
     */
    private static String originalMessageData(TxnRecord r) {
        String mti = padRight(nz(r.mti).isEmpty() ? "0200" : r.mti, 4);
        String trace = padLeft(nz(r.stan).isEmpty() ? "0" : r.stan, 6);
        return mti + trace + "            ";
    }

    /** Clear the journal and advance the batch number - only on an approval. */
    private static void closeBatch(Context ctx, SettleResult res) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        BatchStore batch = BatchStore.get(ctx);
        res.approved = true;
        res.message = "SETTLEMENT SUCCESSFUL";

        // Snapshot the totals BEFORE clearing the journal - once it is gone
        // there is nothing left for "Last Settlement" to reprint.
        ReportStore.SettleSnapshot snap = new ReportStore.SettleSnapshot();
        snap.batchNo = res.batchNo;
        snap.dateTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date());
        snap.saleCount = res.totals.saleCount;
        snap.saleAmt = res.totals.saleAmt;
        snap.voidCount = res.totals.voidCount;
        snap.voidAmt = res.totals.voidAmt;
        snap.refundCount = res.totals.refundCount;
        snap.refundAmt = res.totals.refundAmt;
        HostResult last = res.lastExchange();
        snap.rrn = last == null ? "" : last.rrn;
        snap.stan = last == null ? "" : last.stan;
        ReportStore.get(ctx).saveSettlement(snap);

        // A settled batch leaves nothing behind but the totals above (the
        // "Last Settlement" reprint): every transaction, its tip-adjust
        // history and the error log of this batch are erased, and the next
        // batch starts clean.
        batch.clear();
        ReportStore.get(ctx).clearFailed();
        SettledBatchStore.purgeAll(ctx);
        int next = tc.batch() + 1;
        if (next > 999999) {
            next = 1;
        }
        tc.setBatch(next);
        tc.setLastSettleTime(System.currentTimeMillis());
        Log.i(TAG, "batch " + res.batchNo + " settled, next batch " + tc.batchNo());
    }

    /** Turn a settlement decline into something a merchant can act on. */
    private static String settleFailureText(HostResult r, String rc) {
        if (r != null && !r.hostText.isEmpty()) {
            return r.hostText;
        }
        if (RC_RECONCILE_ERROR.equals(rc)) {
            return "RECONCILE ERROR - TOTALS DO NOT MATCH";
        }
        if (RC_BAD_BATCH_NUMBER.equals(rc)) {
            return "BAD BATCH NUMBER";
        }
        if (RC_BATCH_NOT_FOUND.equals(rc)) {
            return "BATCH NOT FOUND";
        }
        if (r != null && rc.isEmpty()) {
            return r.error.isEmpty() ? "NO RESPONSE FROM HOST" : r.error;
        }
        String text = r == null ? "" : ResponseCode.displayText(rc, "", r.hostText);
        return text.isEmpty() ? "SETTLEMENT FAILED " + rc : text;
    }

    private static void step(Progress p, String text) {
        if (p != null) {
            p.step(text);
        }
        Log.i(TAG, "settlement step: " + text);
    }

    // -------------------------------------------------------------- core I/O

    private static HostResult send(Context ctx, String title, String mti,
                                   TreeMap<Integer, String> de, String stan,
                                   IPinPadBinderService pinpad) {
        return send(ctx, title, mti, de, stan, pinpad, TerminalConfig.get(ctx).tpdu(), true);
    }

    private static HostResult send(Context ctx, String title, String mti,
            TreeMap<Integer, String> de, String stan, IPinPadBinderService pinpad, String tpdu, boolean reversalOnTimeout) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        HostResult r = new HostResult();
        r.stan = stan;

        boolean wantMac = tc.macEnabled();
        if (wantMac && pinpad == null) {
            return fail(r, "MAC is enabled but the PIN pad service is unavailable", false);
        }
        if (wantMac) {
            de.put(64, Iso8583.MAC_PLACEHOLDER);   // reserve the bitmap bit
        }

        checkTpdu(tc);

        byte[] req;
        try {
            req = Iso8583.pack(tpdu, mti, de);
        } catch (Exception e) {
            return fail(r, "pack error: " + e.getMessage(), false);
        }

        if (wantMac) {
            byte[] mac = calcMac(pinpad, tc.mkIndex(), Iso8583.macSourceData(req));
            if (mac == null) {
                return fail(r, "MAC calculation failed - is the MAC key loaded?", false);
            }
            Iso8583.applyMac(req, mac);
            de.put(64, Iso8583.bytesToHex(mac));
        }

        Iso8583 reqMsg = new Iso8583();
        reqMsg.mti = mti;
        reqMsg.tpdu = tpdu;
        reqMsg.de.putAll(de);
        // Show which host this went to. A packet that looks perfect and still
        // gets no answer is almost always addressed wrongly, and the slip is
        // the only thing the engineer at the bank has to look at.
        r.requestDump = reqMsg.debugDump("Request", req)
                + "TID : " + shown(tc.tid()) + "   MID: " + shown(tc.mid()) + "\n"
                + "HOST: " + tc.hostIp() + ":" + tc.hostPort()
                + "  timeout " + (tc.timeout() / 1000) + "s\n";
        Log.i(TAG, r.requestDump);
        Log.i(TAG, reqMsg.dump(title + " REQUEST", req));

        // Identity before network. A blank terminal ID packs into DE41 as eight
        // spaces, so the message goes out looking perfectly well formed and the
        // switch simply drops the connection - which the terminal then reports
        // as a network fault. Say what it really is, before the socket is
        // opened, for every message type.
        String idFault = identityFault(tc);
        if (idFault != null) {
            return fail(r, idFault, false);
        }
        if (!tc.hasHost()) {
            return fail(r, "no host configured: IP='" + tc.hostIp() + "' port="
                    + tc.hostPort() + " - set them in Parameters, Communication", false);
        }
        boolean[] wasSent = new boolean[1];
        try {
            byte[] resp = HostComm.exchange(tc.hostIp(), tc.hostPort(), tc.timeout(),
                    req, wasSent);
            r.requestSent = wasSent[0];
            Iso8583 m = Iso8583.unpack(resp);
            r.online = true;
            r.responseDump = m.debugDump("Response", resp);
            Log.i(TAG, r.responseDump);
            Log.i(TAG, m.dump(title + " RESPONSE", resp));

            r.responsePan = nz(m.get(2)); r.responseTime = nz(m.get(12));
            r.responseDate = nz(m.get(13)); r.responseNii = nz(m.get(24));
            r.responseCode = nz(m.get(39));
            r.responseAmount = nz(m.get(4)); r.responseTid = nz(m.get(41));
            r.authCode = nz(m.get(38));
            r.rrn = nz(m.get(37));
            r.approved = "00".equals(r.responseCode);

            // DE11 echo check (spec 6.4): the STAN validates the response. A
            // mismatch means this is not our answer, so the original request is
            // still unaccounted for and must be reversed.
            String responseFault = TransactionPolicy.responseFault(mti, m.mti,
                    stan, nz(m.get(11)), r.responseCode);
            if (!responseFault.isEmpty()) {
                r.error = responseFault;
                r.approved = false;
                r.needsReversal = true;
                Log.w(TAG, r.error);
            }
            if (m.parseError != null) {
                r.error = (r.error.isEmpty() ? "" : r.error + "; ") + m.parseError;
                r.approved = false;
                r.needsReversal = true;
            }
            if (r.responseCode.isEmpty()) {
                r.error = (r.error.isEmpty() ? "" : r.error + "; ") + "response carried no DE39";
                r.approved = false;
                r.needsReversal = true;
            }
            // An unrelated or malformed response must never reach the chip or
            // rotate terminal keys, even if some fields could be decoded.
            if (r.needsReversal) return r;
            if (wantMac) {
                byte[] expectedMac = m.has(64)
                        ? calcMac(pinpad, tc.mkIndex(), Iso8583.macSourceData(resp)) : null;
                if (expectedMac == null || !java.security.MessageDigest.isEqual(
                        expectedMac, Iso8583.hexToBytes(nz(m.get(64))))) {
                    r.approved = false;
                    r.needsReversal = true;
                    r.error = "Missing or invalid response MAC";
                    return r;
                }
            }
            r.responseIcc = nz(m.get(55));
            r.field72 = nz(m.get(72));
            r.additionalAmounts = nz(m.get(54));

            // DE62 on an 0810 is the logon key block; on an 0210 it is just the
            // invoice echoed back, so only parse it for a logon response.
            if ("0810".equals(m.mti) && m.has(62)) {
                r.logon = LogonData.parseLogonResponse62(m.get(62), tc.wkLen());
                r.workingKey = r.logon.pinKey;
                r.macKey = r.logon.macKey;
                r.responseDump = r.responseDump + "DE62: " + r.logon.note + "\n";
            }
            // DE48 can rotate keys on any 0210/0510/0810 (spec 6.19).
            if (m.has(48)) {
                applyKeyUpdate48(tc, pinpad, m.get(48), r);
            }
            // DE63 table '22' carries alternate host response text (spec 6.28.4.10).
            if (m.has(63)) {
                r.hostText = de63Table(m.get(63), "22");
            }
            return r;
        } catch (Exception e) {
            // Timeout or socket failure: the host may or may not have processed
            // the request, so a financial message must now be reversed
            // (spec 7.35).
            // Name the host and the failure class. "host unreachable:
            // Connection ..." on its own does not say whether the IP is wrong,
            // the port is wrong, or the network is down - and on a slip it is
            // usually truncated before the useful part.
            String where = tc.hostIp() + ":" + tc.hostPort();
            String why = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " - " + e.getMessage());
            Log.w(TAG, "host exchange failed to " + where + ": " + why);
            // A network message (0800 logon / echo) moves no money, so there is
            // nothing to reverse. Printing "Reversal queued" on a failed logon
            // sends the engineer looking for a stranded transaction that does
            // not exist.
            r.requestSent = wasSent[0];
            boolean financial = reversalOnTimeout && !"0800".equals(mti) && wasSent[0];
            return fail(r, (wasSent[0] ? "no reply from " : "could not reach ")
                    + where + "  (" + why + ")", financial);
        }
    }

    /**
     * Warn when the configured TPDU looks like a response TPDU.
     *
     * <p>The TPDU is {@code 60 | destination(2) | source(2)} (spec 4.1.1). On a
     * request the destination is the acquirer's NII and the source is the
     * terminal, so with NII 008 it must be {@code 6000080000}. A terminal
     * observed failing with DE39 = '**' was configured {@code 6000000008} -
     * destination and source transposed, i.e. the reply address - so the host
     * could not route the message to an acquirer application.</p>
     */
    private static void checkTpdu(TerminalConfig tc) {
        String tpdu = nz(tc.tpdu()).replaceAll("[^0-9A-Fa-f]", "");
        if (tpdu.length() != 10) {
            Log.e(TAG, "TPDU must be 5 bytes, got '" + tpdu + "'");
            return;
        }
        String dest = tpdu.substring(2, 6);
        String src = tpdu.substring(6, 10);
        String nii = nz(tc.nii()).replaceAll("[^0-9]", "");
        if (nii.isEmpty()) {
            return;
        }
        String niiPadded = ("0000" + nii).substring(nii.length());
        if (!dest.equalsIgnoreCase(niiPadded) && src.equalsIgnoreCase(niiPadded)) {
            Log.e(TAG, "TPDU LOOKS REVERSED: " + tpdu + " has destination " + dest
                    + " and source " + src + ", but NII is " + nii
                    + ". A request TPDU should be 60 " + niiPadded + " 0000."
                    + " The host will not be able to route this message.");
        }
    }

    /**
     * Terminate the exchange as a decline. There is deliberately no simulated
     * approval path - an unreachable host must never look like an approval.
     *
     * @param reversible true when the request may have reached the host, so a
     *                   0400 reversal is required
     */
    private static HostResult fail(HostResult r, String why, boolean reversible) {
        r.online = false;
        r.approved = false;
        r.responseCode = "";
        r.error = why;
        r.needsReversal = reversible;
        r.responseDump = "*** NO HOST RESPONSE ***\n" + why + "\n"
                + (reversible ? "Reversal queued.\n" : "");
        Log.w(TAG, "transaction failed: " + why);
        return r;
    }

    /** A value as it will be read on a slip - empty is invisible otherwise. */
    private static String shown(String v) {
        return nz(v).trim().isEmpty() ? "<NOT SET>" : v;
    }

    // --------------------------------------------------------- DE14 by brand

    /**
     * Whether DE14 (expiry) belongs in this message. The rule is not the same
     * for a sale as for a void:
     *
     * <pre>
     *   SALE  - Mastercard only.  A Visa sale carrying DE14 came back '**'.
     *   VOID  - every brand.      Visa, Mastercard, TakaPay, NexusPay, JCB.
     * </pre>
     *
     * <p>A reversal or a batch upload follows whichever rule its <em>original
     * </em> followed, not the message it happens to be. A 0400 whose bitmap
     * differs from the 0200 it reverses is one the host cannot match.</p>
     *
     * @param isVoid true when this message is, or replays, a void
     */
    static boolean sendsExpiry(String pan, String track2, boolean isVoid) {
        String d = nz(pan).replaceAll("[^0-9]", "");
        if (d.isEmpty()) {
            d = nz(panFromTrack2(track2)).replaceAll("[^0-9]", "");
        }
        // A void always carries it, whatever the card. Only the sale is
        // brand-conditional. Note this does not depend on the PAN at all - it
        // is written this way so a card the range table does not recognise
        // still voids correctly rather than silently dropping the field.
        return isVoid || isMastercard(d);
    }

    /**
     * Mastercard by BIN: 51-55, plus the 2221-2720 series Mastercard added in
     * 2017. The second range matters - a 2-series card treated as "not
     * Mastercard" would silently drop DE14 on exactly the brand that needs it.
     */
    static boolean isMastercard(String panDigits) {
        String d = nz(panDigits).replaceAll("[^0-9]", "");
        if (d.length() >= 2) {
            String two = d.substring(0, 2);
            if (two.compareTo("51") >= 0 && two.compareTo("55") <= 0) {
                return true;
            }
        }
        if (d.length() >= 4) {
            try {
                int four = Integer.parseInt(d.substring(0, 4));
                return four >= 2221 && four <= 2720;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    /**
     * Whether this terminal is identified well enough to be worth sending.
     *
     * <p>DE41 is ANS(8) and DE42 ANS(15) (spec 6.15, 6.16), both fixed length,
     * both space padded. That means an unset terminal ID does not fail to
     * pack - it packs as {@code 20 20 20 20 20 20 20 20} and produces a
     * textbook 0800 that the host answers with a TCP reset. Every symptom then
     * points at the network and none of them point at the parameter that is
     * actually missing.</p>
     *
     * <p>Only the cases that cannot possibly be right are refused: absent, or
     * longer than the field. A short TID is left alone, because the host record
     * is space padded too and a 7-character ID may legitimately match.</p>
     *
     * @return the reason not to send, or null if the terminal is identified
     */
    private static String identityFault(TerminalConfig tc) {
        String tid = nz(tc.tid()).trim();
        String mid = nz(tc.mid()).trim();
        if (tid.isEmpty()) {
            return "TERMINAL ID NOT SET\n"
                    + "DE41 would be sent as 8 spaces and the host will reject\n"
                    + "or drop the connection.\n"
                    + "Set it in Parameters > Communication.";
        }
        if (tid.length() > 8) {
            return "TERMINAL ID IS " + tid.length() + " CHARACTERS\n"
                    + "DE41 is fixed at 8, so it would be truncated and would\n"
                    + "not match the host record.\n"
                    + "Correct it in Parameters > Communication.";
        }
        if (mid.isEmpty()) {
            return "MERCHANT ID NOT SET\n"
                    + "Set it in Parameters > Communication.";
        }
        if (mid.length() > 15) {
            return "MERCHANT ID IS " + mid.length() + " CHARACTERS\n"
                    + "DE42 is fixed at 15, so it would be truncated.\n"
                    + "Correct it in Parameters > Communication.";
        }
        return null;
    }

    // ------------------------------------------------------- key management

    /**
     * Write a working key received from the host, decrypting it under the TMK.
     *
     * <p>Uses {@code dalPosWriteKey}, which takes an explicit source <em>and</em>
     * destination — the direct equivalent of the PAX reference call:</p>
     *
     * <pre>
     *   writeKey(EPedKeyType.TMK, tmkIndex,      // source
     *            EPedKeyType.TPK, INDEX_TPK,     // destination
     *            tpkValue, KCV_NONE, null);
     * </pre>
     *
     * <p><b>Why not {@code posWriteWorkingKey}.</b> That call has no destination
     * index parameter — only {@code usMKeyNo}, the source. With the source index
     * and the destination slot collapsed into one value the pad wrote the
     * working key over the master key at that index, so the TMK changed after
     * every logon. The TPK had then been derived from the old TMK while
     * everything afterwards used the new one, which is why PIN blocks would not
     * verify. Nothing in the Hypercom spec changes the TMK at logon: 6.27.2
     * replaces the working key only, and 6.19 the PIN and MAC keys only.</p>
     *
     * @param dstKeyType POS_TPK or POS_TAK
     * @param dstKeyIdx  slot to store it in - PAX uses a fixed index per key type
     * @param mkIndex    index of the TMK it is enciphered under
     */
    private static int writeKey(IPinPadBinderService pinpad, int dstKeyType,
                                int dstKeyIdx, byte[] key, int mkIndex) {
        if (pinpad == null || key == null || key.length == 0) {
            return Integer.MIN_VALUE;
        }

        // posWriteWorkingKey is the call this firmware actually supports.
        //
        // It was removed earlier on the belief that it overwrote the terminal
        // master key. That was wrong: the TMK was being destroyed by
        // FindCardActivity.devicesSign(), the Anfu demo key bootstrap, which
        // wrote an all-zero master key on every transaction. With that disabled
        // there is no reason to avoid this call, and dalPosWriteKey returns
        // -4528 here, so avoiding it means no working key loads at all.
        //
        // The logon reads the TMK KCV either side of this write, so if it ever
        // does move it is reported rather than discovered later.
        try {
            KcvInfoParam kcv = new KcvInfoParam();
            kcv.setCheckMode(0);       // DE62 carries no KCV to verify against
            int ret = pinpad.posWriteWorkingKey(dstKeyType, key.length, key, kcv,
                    MODE_DES_DECRYPT, mkIndex);
            Log.i(TAG, "posWriteWorkingKey type=0x" + Integer.toHexString(dstKeyType)
                    + " len=" + key.length + " mkIndex=" + mkIndex + " ret=" + ret);
            if (ret == 0) {
                return 0;
            }
            Log.w(TAG, "posWriteWorkingKey returned " + ret + " - trying dalPosWriteKey");
        } catch (Exception e) {
            Log.w(TAG, "posWriteWorkingKey unavailable (" + e.getMessage()
                    + ") - trying dalPosWriteKey");
        }

        // Fallback: the newer call with an explicit source and destination.
        try {
            PstKeyInfoIn info = new PstKeyInfoIn();
            info.setSrcKeyType((byte) POS_TMK);
            info.setSrcKeyIdx((short) mkIndex);
            info.setDstKeyType((byte) dstKeyType);
            info.setDstKeyIdx((short) dstKeyIdx);
            info.setDstKeyLen(key.length);
            info.setDstKeyValue(key);
            PstKcvInfoIn kcv = new PstKcvInfoIn();
            kcv.setCheckMode(0);
            int ret = pinpad.dalPosWriteKey(info, kcv);
            Log.i(TAG, "dalPosWriteKey src=TMK/" + mkIndex + " dst=0x"
                    + Integer.toHexString(dstKeyType) + "/" + dstKeyIdx + " ret=" + ret);
            return ret;
        } catch (Exception e) {
            Log.e(TAG, "both key write calls failed: " + e.getMessage());
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Check the pad's DE62 decryption against our own, and fix it if they differ.
     *
     * <p>Requires the clear TMK ({@link TerminalConfig#tmkClear()}); does
     * nothing without it. Derives {@code TPK = 3DES-ECB-decrypt(DE62, TMK)},
     * computes its KCV, and compares with what the pad reports for its TPK
     * slot. On a mismatch it rewrites the slot with the derived key in the
     * clear, using mode 0x00.</p>
     */
    private static void reconcileWorkingKey(IPinPadBinderService pinpad,
                                            TerminalConfig tc, HostResult r) {
        String tmkHex = tc.tmkClear();
        if (pinpad == null || tmkHex.isEmpty() || r.logon == null || r.logon.pinKey == null) {
            return;
        }
        try {
            byte[] tmk = Iso8583.hexToBytes(tmkHex);
            byte[] derived = com.hjq.demo.utils.TriDES.des3DecodeECB(tmk, r.logon.pinKey);
            if (derived == null || derived.length == 0) {
                return;
            }
            String derivedKcv = com.hjq.demo.utils.TriDES.kcv(derived);
            if (derivedKcv.isEmpty()) {
                Log.w(TAG, "working key check skipped: cannot compute a KCV for a "
                        + derived.length + "-byte derived key");
                return;
            }
            String padKcv = readKcv(pinpad, tc.mkIndex(), POS_TPK);

            Log.i(TAG, "working key check: derived=" + Iso8583.bytesToHex(derived)
                    + " derivedKCV=" + derivedKcv + " padKCV=" + padKcv);

            if (padKcv.isEmpty() || derivedKcv.equalsIgnoreCase(padKcv)) {
                Log.i(TAG, "working key check: pad matches our derivation - nothing to do");
                return;
            }

            Log.e(TAG, "working key MISMATCH: pad holds KCV " + padKcv
                    + " but DE62 under the TMK gives " + derivedKcv
                    + " - rewriting the slot with the derived key in the clear");

            KcvInfoParam kcv = new KcvInfoParam();
            kcv.setCheckMode(0);
            int ret = pinpad.posWriteWorkingKey(POS_TPK, derived.length, derived, kcv,
                    0x00 /* write verbatim */, tc.mkIndex());
            String after = readKcv(pinpad, tc.mkIndex(), POS_TPK);
            Log.i(TAG, "clear working key write ret=" + ret + " padKCV now " + after);
            if (ret == 0 && derivedKcv.equalsIgnoreCase(after)) {
                r.keyWriteRet = 0;
                Log.i(TAG, "working key corrected - pad now holds the DE62 key");
            } else {
                r.error = "working key could not be corrected: pad KCV " + after
                        + ", expected " + derivedKcv;
            }
        } catch (Exception e) {
            Log.w(TAG, "working key check skipped: " + e.getMessage());
        }
    }

    /**
     * Work out WHICH reading of DE62 the PIN pad actually enciphered this block
     * under.
     *
     * <p>An ISO 9564 format 0 block is highly structured: nibble 0 is 0, nibble
     * 1 is the PIN length 4-12, the PIN digits are decimal and the tail is F.
     * Deciphering DE52 with the wrong key and XORing the PAN block back out
     * gives essentially random nibbles, which pass that shape less than 1% of
     * the time. So trying each candidate key and keeping the one that yields a
     * well-formed field identifies the key the pad is holding.</p>
     *
     * <p>This is the missing half of the DE62 question. {@code candidateReport}
     * says what the readings would produce; this says which one is actually
     * loaded - so after changing the split you can confirm the change reached
     * the pad, instead of assuming it did.</p>
     *
     * <p>Requires the clear TMK and a stored DE62. Never logs the PIN.</p>
     *
     * @param pinBlockHex DE52 as returned by the pad, 16 hex characters
     * @param pan         the PAN the block was built against
     */
    /**
     * The X9.8 PAN block these PIN-block checks XOR against.
     *
     * <p>ISO 9564-1 format 0: {@code "0000"} then the rightmost 12 PAN digits
     * excluding the check digit. The old inline
     * {@code substring(len - 13, len - 1)} only works from 13 digits up, so it
     * was guarded by {@code length() >= 13} and every shorter card dropped out
     * of the check entirely - which matters here because DBBL issue from 11.</p>
     *
     * <p>The standard's answer for a short PAN is to left-pad the field with
     * zeros, which is what this does. Verified byte-identical to the old
     * formula at every length from 13 to 19, so nothing changes for the cards
     * that already worked.</p>
     *
     * @param digits the PAN, digits only
     * @return 16 hex characters
     */
    static String x98PanBlock(String digits) {
        String core = digits.substring(0, digits.length() - 1);   // drop check digit
        String twelve = core.length() >= 12
                ? core.substring(core.length() - 12)
                : ("000000000000" + core).substring(core.length());
        return "0000" + twelve;
    }

    public static String identifyPinKey(Context ctx, String pinBlockHex, String pan) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        String block = nz(pinBlockHex).replaceAll("[^0-9A-Fa-f]", "");
        String digits = nz(pan).replaceAll("[^0-9]", "");
        if (block.length() != 16 || digits.length() < TerminalConfig.PAN_LEN_FLOOR) {
            return "";
        }
        java.util.List<LogonData.Candidate> cands =
                LogonData.candidates(tc.lastDe62(), tc.tmkClear());
        if (cands.isEmpty()) {
            return "PIN key check: need the clear TMK and a stored DE62 - "
                    + "re-inject the master key and run a logon.";
        }

        byte[] panBlk = Iso8583.hexToBytes(
x98PanBlock(digits));
        StringBuilder sb = new StringBuilder("PIN KEY IN USE\n");
        String matched = null;
        for (LogonData.Candidate c : cands) {
            String verdict;
            try {
                byte[] clear = com.hjq.demo.utils.TriDES.des3DecodeECB(
                        c.pinKey, Iso8583.hexToBytes(block));
                StringBuilder f = new StringBuilder();
                for (int i = 0; i < 8; i++) {
                    f.append(String.format(Locale.US, "%02X", (clear[i] ^ panBlk[i]) & 0xFF));
                }
                String field = f.toString();
                int len = Character.digit(field.charAt(1), 16);
                boolean ok = field.charAt(0) == '0' && len >= 4 && len <= 12
                        && field.substring(2, 2 + len).matches("\\d+");
                if (ok) {
                    matched = c.label;
                    verdict = "<== MATCH, " + len + "-digit PIN";
                } else {
                    verdict = "no";
                }
            } catch (Exception e) {
                verdict = "err";
            }
            sb.append("  wkLen=").append(c.wkLen)
              .append(" KCV=").append(c.kcv)
              .append("  ").append(verdict).append('\n');
        }
        if (matched == null) {
            sb.append("NO CANDIDATE MATCHES. The pad is not using any key\n")
              .append("derivable from this DE62 under this TMK - so either the\n")
              .append("TMK is not the one the acquirer holds, or the pad still\n")
              .append("has a key from an earlier logon. Re-inject and re-logon.");
        } else {
            sb.append("Pad is using: ").append(matched).append('\n')
              .append("If the acquirer rejects the PIN block, this is NOT\n")
              .append("the reading they want - change the DE62 split and\n")
              .append("run LOGON again.");
        }
        String out = sb.toString();
        Log.i(TAG, out);
        return out;
    }

    /** KCV of an all-zeros double-length key - what an EMPTY key slot reads as. */
    private static final String KCV_ALL_ZEROS = "8CA64D";

    /**
     * Check that the PIN pad still holds the terminal master key that was
     * injected from the admin panel.
     *
     * <p>Nothing may replace the TMK: Hypercom 6.27.2 replaces the working key,
     * 6.19 the PIN and MAC keys, and neither touches the master key. If it has
     * moved, something in the application wrote a key to the master key slot -
     * historically {@code AmountActivity.devicesSign()}, which wrote the Anfu
     * demo key on every entry to the amount screen and so wiped the TMK after
     * every sale. Every working key loaded under the old TMK is meaningless from
     * that point on, and the acquirer reports the resulting PIN block as
     * invalid.</p>
     *
     * <p>Call this before showing the PIN pad so the operator is told to
     * re-inject the key, rather than finding out from a declined sale.</p>
     *
     * @return a description of the fault, or "" when the TMK is intact
     */
    public static String tmkFault(Context ctx, IPinPadBinderService pinpad) {
        if (pinpad == null || ctx == null) {
            return "";
        }
        TerminalConfig tc = TerminalConfig.get(ctx);
        String expected = tc.tmkKcv();
        String now = readKcv(pinpad, tc.mkIndex(), POS_TMK);

        if (now.isEmpty()) {
            return "The PIN pad holds no terminal master key at index "
                    + tc.mkIndex() + ". Inject it from the admin panel.";
        }
        if (KCV_ALL_ZEROS.equalsIgnoreCase(now)) {
            return "The master key slot at index " + tc.mkIndex()
                    + " reads as ALL ZEROS (KCV " + now + ") - the terminal master"
                    + " key has been erased. Re-inject it from the admin panel.";
        }
        if (expected.isEmpty()) {
            // Never recorded, e.g. the key predates this check. Record it now so
            // the next call has something to compare against.
            tc.setTmkKcv(now);
            Log.i(TAG, "TMK KCV recorded for the first time: " + now);
            return "";
        }
        if (!expected.equalsIgnoreCase(now)) {
            return "THE TERMINAL MASTER KEY HAS CHANGED: was " + expected
                    + ", now " + now + ". Re-inject it and run a logon.";
        }
        return "";
    }

    private static String readKcv(IPinPadBinderService pinpad, int mkIndex, int keyType) {
        if (pinpad == null) {
            return "";
        }
        try {
            PosGetKcvResult res = pinpad.posGetKcv(mkIndex, keyType);
            if (res == null || res.getRet() != 0) {
                return "";
            }
            String all = Iso8583.bytesToHex(res.getKcvInfo());
            // Layout is 00LL0000 <kcv...>; the KCV proper starts at byte 4.
            // Report 3 bytes / 6 hex characters - the standard KCV length, what
            // PAX takes (Arrays.copyOf(getKcv(...), 3)) and the format DBBL
            // quote on their key ceremony sheet, e.g. "65044C".
            return all.length() >= 14 ? all.substring(8, 14) : all;
        } catch (Exception e) {
            return "";
        }
    }

    private static void applyKeyUpdate48(TerminalConfig tc, IPinPadBinderService pinpad,
                                         String hex, HostResult r) {
        LogonData d = LogonData.parseKeyUpdate48(hex, tc.wkLen());
        if (!d.valid) {
            return;
        }
        int macRet = writeKey(pinpad, POS_TAK, tc.mkIndex(), d.macKey, tc.mkIndex());
        int pinRet = writeKey(pinpad, POS_TPK, tc.mkIndex(), d.pinKey, tc.mkIndex());
        r.responseDump = r.responseDump + "DE48 key rotation: " + d.note
                + " (TAK ret=" + macRet + ", TPK ret=" + pinRet + ")\n";
        if (macRet == 0 && pinRet == 0) {
            tc.setKeyKcvs(readKcv(pinpad, tc.mkIndex(), POS_TPK),
                    readKcv(pinpad, tc.mkIndex(), POS_TAK));
        }
    }

    private static byte[] calcMac(IPinPadBinderService pinpad, int mkIndex, byte[] data) {
        if (pinpad == null || data == null || data.length == 0) {
            return null;
        }
        try {
            // mode 0x01 = ANSI X9.9 (spec 6.29.1). Use the Hypercom fast variant
            // (spec 6.29.2) instead if DBBL's host expects that.
            PosGetMacResult res = pinpad.posGetMac(mkIndex, data, data.length, 0x01);
            if (res == null || res.getRet() != 0) {
                Log.w(TAG, "posGetMac ret=" + (res == null ? "null" : res.getRet()));
                return null;
            }
            byte[] full = res.getMac();
            byte[] out = new byte[Iso8583.MAC_LEN];
            // MAC is the first 32 bits of the MAB, left justified and zero
            // filled to 8 bytes (spec 6.29.1 step 6).
            System.arraycopy(full, 0, out, 0, Math.min(4, full.length));
            return out;
        } catch (Exception e) {
            Log.e(TAG, "MAC calc failed: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------- helpers

    /**
     * Is this a well-formed DE14 value - four digits, month 01-12?
     *
     * <p>PAX validates the same thing at entry: {@code dateProcess} parses the
     * operator's input as {@code MM/yy} with a non-lenient
     * {@code SimpleDateFormat}, which rejects month 00 or 13. A malformed value
     * should never reach the field.</p>
     */
    static boolean isValidExpiry(String yymm) {
        String d = nz(yymm).replaceAll("[^0-9]", "");
        if (d.length() != 4) {
            return false;
        }
        int month = Integer.parseInt(d.substring(2, 4));
        return month >= 1 && month <= 12;
    }

    /**
     * Has this YYMM expiry already passed?
     *
     * <p>Mirrors {@code Issuer.validCardExpiry} in the PAX reference, including
     * its two-digit year window: a year above 80 is 19xx, otherwise 20xx.</p>
     */
    static boolean isExpired(String yymm) {
        if (!isValidExpiry(yymm)) {
            return false;
        }
        String d = nz(yymm).replaceAll("[^0-9]", "");
        int year = Integer.parseInt(d.substring(0, 2));
        year += year > 80 ? 1900 : 2000;
        int month = Integer.parseInt(d.substring(2, 4));
        Calendar now = Calendar.getInstance();
        int y = now.get(Calendar.YEAR);
        int m = now.get(Calendar.MONTH) + 1;
        return year < y || (year <= y && month < m);
    }


    /**
     * DE22: two-digit PAN entry mode, then one digit of PIN entry capability.
     *
     * <p>Written as full TWO-DIGIT entry codes. PAX keeps a single digit per
     * mode and lets the packer's left-pad supply the leading zero, which is
     * correct for 01, 02, 03, 05 and 07 - and silently wrong for the two codes
     * that do not start with a zero. Fallback is <b>80</b>, so PAX's {@code "8"}
     * plus a PIN digit padded to four gives {@code 0081}; DBBL's own reference
     * terminal sends {@code 0801}, captured on an approved fallback sale
     * (TID 59001174, 10-09-2026 15:23, DE22 {@code 08 01}, DE52 present, no
     * DE55). Two-digit codes make that class of bug impossible.</p>
     *
     * <pre>
     *   01 manual   02 magstripe   03 QR   05 chip
     *   07 contactless chip        80 fallback     91 contactless magstripe
     * </pre>
     *
     * <p>Position 3 is the terminal's PIN entry capability: 0 unspecified,
     * 1 has PIN entry, 2 has none. DBBL do not use 2 - the PAX source carries
     * the note <em>"updated by mobashyer on 24-08-23 to handle field 22,53
     * problem"</em> - so a transaction with no PIN block sends 0.</p>
     *
     * <p>Every value this produces is confirmed against a captured message
     * from DBBL's reference terminal: 0051 chip+PIN, 0071 contactless,
     * 0801 fallback+PIN.</p>
     */
    private static String entryMode(EntryMode e, boolean pinEntered) {
        String pan;
        switch (e == null ? EntryMode.UNSPECIFIED : e) {
            case MANUAL:    pan = "01"; break;
            case MAGSTRIPE: pan = "02"; break;
            case ICC:       pan = "05"; break;
            case QR:        pan = "03"; break;
            // Contactless keeps position 3 at 1 whatever happened, because on
            // a tap it really is the terminal's PIN-entry CAPABILITY that the
            // field reports. DBBL's reference terminal sends 0071 on a
            // contactless sale that carried NO DE52 at all (TID 59001174,
            // 10 TK, bitmap with 52 clear), so deriving it from pinEntered
            // here would send 0070 and disagree with the terminal we are
            // matching. This is the one mode that does not take the digit
            // below.
            case NEXUSPAY_PHONE_NFC: return KonaMoney.ENTRY_PHONE_NFC;
            case CLESS_ICC: return "071";
            case CLESS_MSD: pan = "91"; break;
            case FALLBACK:  pan = "80"; break;
            default:        pan = "00"; break;
        }
        return pan + (pinEntered ? "1" : "0");
    }

    /**
     * Pull one table out of private-use field 63 (spec 6.28.4). Layout is a
     * concatenation of {@code [BCD len 2 bytes][table id 2 ascii][data]} blocks,
     * where the length covers the table id plus the data.
     */
    public static String de63Table(String hex, String tableId) {
        byte[] d = Iso8583.hexToBytes(hex);
        int p = 0;
        while (p + 4 <= d.length) {
            int len = (((d[p] >> 4) & 0x0F) * 1000) + ((d[p] & 0x0F) * 100)
                    + (((d[p + 1] >> 4) & 0x0F) * 10) + (d[p + 1] & 0x0F);
            p += 2;
            if (len < 2 || p + len > d.length) {
                break;
            }
            String id = new String(d, p, 2, java.nio.charset.Charset.forName("US-ASCII"));
            if (tableId.equals(id)) {
                return new String(d, p + 2, len - 2,
                        java.nio.charset.Charset.forName("US-ASCII")).trim();
            }
            p += len;
        }
        return "";
    }

    private static String minor(String major) {
        try {
            double v = Double.parseDouble(nz(major).replace(",", "").trim());
            return String.valueOf(Math.round(v * 100.0));
        } catch (Exception e) {
            return "0";
        }
    }

    private static String prettyTitle(String t) {
        switch (nz(t)) {
            case "CASH_WITHDRAW": return "CASH WITHDRAW";
            case "SALE_AND_CASH": return "SALE & CASH";
            case "PREAUTH":       return "PRE-AUTH";
            case "PREAUTH_VOID":  return "PRE-AUTH VOID";
            case "SALE_COMPLETE": return "SALE COMPLETE";
            case "EMI_SALE":      return "EMI SALE";
            case "EMI_VOID":      return "EMI VOID";
            case "":              return "SALE";
            default:              return t;
        }
    }

    /**
     * Decrypt the PIN block we are about to send, with the very key the pad
     * holds, and check it is a well-formed ISO 9564 format 0 block.
     *
     * <p>The terminal can do this: {@code posCalcDes} will DES-decrypt with the
     * TPK, and the X9.8 PAN block is derived from the PAN we already have. XOR
     * the two and the first two nibbles are the format and the PIN length.</p>
     *
     * <p>This is what the acquirer's HSM does before reporting
     * "PIN block does not contain valid values" (DA error 20) or
     * "PIN is fewer than 4 or more than 12 digits" (DA error 24). Doing it here
     * turns a bank-side rejection into a line in our own log that names the
     * problem.</p>
     *
     * @return a description of the fault, or "" when the block is valid
     */
    static String checkPinBlock(IPinPadBinderService pinpad, int mkIndex,
                                String pinBlockHex, String pan) {
        String h = nz(pinBlockHex).replaceAll("[^0-9A-Fa-f]", "");
        String digits = nz(pan).replaceAll("[^0-9]", "");
        if (pinpad == null || h.length() != 16
                || digits.length() < TerminalConfig.PAN_LEN_FLOOR) {
            return "";   // nothing to check against
        }
        try {
            // keyType POS_TPK, mode 0x01 = decrypt, ECB, no IV.
            PosCalcDesResult res = pinpad.posCalcDes(mkIndex, POS_TPK, 0x02, 0x00,
                    Iso8583.hexToBytes(h), 0x01, null);
            if (res == null || res.getRet() != 0 || res.getDataOut() == null) {
                return "";   // pad would not decrypt; nothing learned
            }
            byte[] clear = res.getDataOut();
            byte[] panBlk = Iso8583.hexToBytes(
    x98PanBlock(digits));
            StringBuilder f = new StringBuilder();
            for (int i = 0; i < 8 && i < clear.length; i++) {
                f.append(String.format(Locale.US, "%02X", (clear[i] ^ panBlk[i]) & 0xFF));
            }
            String pinField = f.toString();
            char fmt = pinField.charAt(0);
            int len = Character.digit(pinField.charAt(1), 16);

            // Mask the PIN digits: a self-check must prove the block is well
            // formed without ever putting a cardholder PIN in a log or on paper.
            // Only the format nibble, the length nibble and the padding are
            // shown; the digit positions become '*'.
            String masked = maskPinField(pinField, len);
            Log.i(TAG, "PIN block self-check: format=" + fmt + " len=" + len
                    + " field=" + masked);

            if (fmt != '0') {
                return "PIN block is NOT ISO 9564 format 0 - format nibble is '"
                        + fmt + "', field " + masked;
            }
            if (len < 4 || len > 12) {
                return "PIN LENGTH " + len + " is outside 4-12 - the host will reject this"
                        + " (DA error 24). field " + masked;
            }
            String pinDigits = pinField.substring(2, 2 + len);
            if (!pinDigits.matches("\\d+")) {
                return "PIN digits are not decimal - field " + masked;
            }
            Log.i(TAG, "PIN block self-check: VALID ISO-0, " + len + "-digit PIN");
            return "";
        } catch (Exception e) {
            Log.w(TAG, "PIN block self-check unavailable: " + e.getMessage());
            return "";
        }
    }

    /** Replace the PIN digit nibbles of an ISO-0 field with '*' for logging. */
    private static String maskPinField(String pinField, int len) {
        if (pinField.length() < 2 || len < 0 || 2 + len > pinField.length()) {
            return pinField.charAt(0) + "" + pinField.charAt(1) + "…";
        }
        StringBuilder b = new StringBuilder();
        b.append(pinField, 0, 2);                 // format + length nibbles
        for (int i = 0; i < len; i++) {
            b.append('*');                          // PIN digits, hidden
        }
        b.append(pinField.substring(2 + len));      // trailing padding
        return b.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * The equivalent of PAX's {@code strToBcdPaddingLeft}: strip non-hex, then
     * pad on the left only far enough to make a whole number of bytes.
     *
     * <p><b>Do not pad to a fixed width.</b> An ISO 9564 format 0 block carries
     * the PIN length in nibble 1. Prepending zeros to reach 16 characters
     * shifts every nibble right, so the host reads the format nibble as the
     * length and rejects it - Thales reports that as command DA error 24,
     * "PIN is fewer than 4 or more than 12 digits in length". PAX pads for BCD
     * alignment only, and so does this.</p>
     *
     * @param hexChars ignored for padding; kept so an unexpected width is logged
     * @return the block as received, byte aligned, or "" when there is none
     */
    static String bcdPadLeft(String hex, int hexChars) {
        String h = nz(hex).replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        if (h.isEmpty()) {
            return "";
        }
        if (h.length() % 2 != 0) {
            h = "0" + h;                    // BCD alignment only
        }
        if (h.length() != hexChars) {
            Log.w(TAG, "PIN block is " + h.length() + " hex chars, expected " + hexChars
                    + " - sending as received, not padded: " + h);
        }
        return h;
    }

    /** PAN from a track 2 image, up to the '=' or 'D' separator. */
    static String panFromTrack2(String track2) {
        String t = nz(track2).trim().toUpperCase(Locale.US);
        if (t.isEmpty()) {
            return "";
        }
        int i = t.indexOf('=');
        if (i < 0) {
            i = t.indexOf('D');
        }
        return i > 0 ? t.substring(0, i) : t;
    }

    /**
     * YYMM from a track 2 image: the four digits that follow the field
     * separator ('=' or 'D'). Empty when the track has no separator.
     */
    static String expiryFromTrack2(String track2) {
        if (track2 == null) {
            return "";
        }
        String t = track2.trim().toUpperCase(Locale.US);
        int i = t.indexOf('=');
        if (i < 0) {
            i = t.indexOf('D');
        }
        if (i < 0 || t.length() < i + 5) {
            return "";
        }
        String exp = t.substring(i + 1, i + 5);
        return exp.matches("\\d{4}") ? exp : "";
    }
}

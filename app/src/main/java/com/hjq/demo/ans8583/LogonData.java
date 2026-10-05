package com.hjq.demo.ans8583;

import android.util.Log;

import java.util.Locale;

/**
 * Hypercom "The System" Message Specification v3.34 - private-use field helpers
 * for the logon key-exchange.
 *
 * <p>Three structures are handled here:</p>
 *
 * <h3>DE60 - Logon Request (spec 6.25.6, MT 0800)</h3>
 * <pre>
 *   Length Attribute        n 3    2 bytes   '0015' BCD length of data to follow
 *   PIN Pad Id (PPID)       n 16   8 bytes   current PIN pad serial number, BCD
 *   eKTM(PPID)              b 32   4 bytes   most significant 32 bits of the PIN key
 *   PIN Pad SW Version      n 6    3 bytes   PIN pad software version, BCD
 * </pre>
 * Note this rides in <b>field 60</b>, not 62. Spec 6.24 maps
 * "Logon Request -> 0800 -> field 60" and "Logon Response -> 0810 -> field 62".
 *
 * <h3>DE62 - Logon Response (spec 6.27.2, MT 0810)</h3>
 * <pre>
 *   Length Attribute        n 3    2 bytes   '00LL' BCD length of data to follow
 *   Encrypted Working Key   b 64   8 bytes   PIN/working key, encrypted under the TMK
 *   MAC Key                 b 64   8 bytes   MAC key, encrypted under the TMK
 *   Receipt Line 2          an 23  23 bytes  optional
 *   Receipt Line 3          an 23  23 bytes  optional
 *   Default Merchant Name   an 23  23 bytes  optional
 * </pre>
 * So the legal data lengths are 8, 16, 39, 62 and 85 bytes. A host that ships
 * double-length keys instead can be accommodated with
 * {@link TerminalConfig#wkLen()}.
 *
 * <h3>DE48 - Additional Data, key rotation (spec 6.19)</h3>
 * <pre>
 *   Length Attribute        n 3    2 bytes   '0016' BCD length of data to follow
 *   eKTM(KMAC)              b 64   8 bytes   MAC key
 *   eKTM(KPP)               b 64   8 bytes   PIN encryption key
 * </pre>
 * <b>Beware the order is reversed relative to DE62</b> - MAC key first here.
 * DE48 may arrive on any 0210, 0510 or 0810 and replaces the current keys.
 */
public final class LogonData {

    private static final String TAG = "LogonData";

    /** Key length the 3.34 spec assumes (single-length DES). */
    public static final int SPEC_KEY_LEN = 8;
    /** Length of each optional trailing text block in DE62. */
    private static final int TEXT_BLOCK = 23;

    /** eTMK(TPK) - PIN / working key, still encrypted under the terminal master key. */
    public byte[] pinKey;
    /** eTMK(TAK) - MAC key, still encrypted under the terminal master key. */
    public byte[] macKey;

    public String receiptLine2 = "";
    public String receiptLine3 = "";
    public String merchantName = "";

    /** Byte length detected (or forced) for each key. */
    public int keyLen;
    /** Raw field content as received, for logging / receipt dumps. */
    public String rawHex = "";
    /** Human-readable note about how the field was interpreted. */
    public String note = "";
    public boolean valid;

    // ------------------------------------------------------------- DE60 build

    /**
     * Build the DE60 logon-request block (spec 6.25.6).
     *
     * @param ppid       PIN pad serial number, up to 16 digits (BCD, left padded)
     * @param eKtmPpidHex most significant 32 bits of the PIN key, 4 bytes / 8 hex chars
     * @param swVersion  PIN pad software version, 6 digits
     * @return 15 bytes as an uppercase hex string, ready for {@code de.put(60, ...)}
     */
    public static String buildLogonRequest60(String ppid, String eKtmPpidHex, String swVersion) {
        StringBuilder b = new StringBuilder();
        b.append(bcdHex(ppid, 16));         // 8 bytes
        b.append(fixedHex(eKtmPpidHex, 4)); // 4 bytes
        b.append(bcdHex(swVersion, 6));     // 3 bytes
        return b.toString();
    }

    // ------------------------------------------------------------ DE62 parse

    /**
     * Parse the DE62 logon response.
     *
     * <h3>What the field is</h3>
     * <p>Spec 6.27.2 defines DE62 on an 0810 as an 8-byte encrypted working
     * (PIN) key followed by an 8-byte MAC key, each enciphered under the
     * terminal master key, optionally followed by three 23-byte ASCII text
     * blocks. "b 64" in that table is 64 <em>bits</em>, i.e. 8 bytes - the
     * single-length DES layout the spec assumes.</p>
     *
     * <h3>The whole key exchange, end to end</h3>
     * <ol>
     *   <li>The acquirer's HSM and the terminal share a terminal master key
     *       (TMK), injected once by key ceremony. In this app that is the admin
     *       panel; the KCV is recorded and never changes again.</li>
     *   <li>The terminal sends an 0800 logon (processing code 92 00 0x).</li>
     *   <li>The host replies 0810 with DE39 = '00' and DE62 carrying
     *       {@code eTMK(TPK) || eTMK(TAK)}.</li>
     *   <li>The terminal hands each 8-byte block to the PIN pad with
     *       {@code posWriteWorkingKey(type, len, data, kcv, 0x01, mkIndex)}.
     *       Mode 0x01 means "this arrives enciphered - decrypt it under the key
     *       at mkIndex first", so the pad ends up holding the clear TPK and TAK
     *       without either ever being in the clear outside the pad.</li>
     *   <li>At sale time the pad builds an ISO 9564 format 0 PIN block - the
     *       PIN field {@code 0 L PPPP F...F} XORed with
     *       {@code 0000} + the rightmost 12 PAN digits excluding the check
     *       digit - and enciphers it under the TPK. That goes in DE52.</li>
     *   <li>The acquirer's HSM deciphers DE52 with its copy of the TPK, XORs the
     *       PAN block back out, and reads the length nibble. If the TPK the
     *       terminal used is not the TPK the HSM holds, the result is noise and
     *       the length nibble is effectively random - which the HSM reports as
     *       "PIN is fewer than 4 or more than 12 digits" (Thales command DA,
     *       error 24). <b>A PIN length error from the host almost always means a
     *       wrong PIN key, not a wrong PIN.</b></li>
     * </ol>
     *
     * <p>Nothing in this exchange touches the TMK: 6.27.2 replaces the working
     * key, 6.19 the PIN and MAC keys, and no message replaces the master key.</p>
     *
     * @param hex          field content from {@link Iso8583#get(int)} (hex, no length header)
     * @param forcedKeyLen 8 = spec 6.27.2 (default), 16 = double-length keys,
     *                     0 = legacy "whole field is the working key"
     */
    public static LogonData parseLogonResponse62(String hex, int forcedKeyLen) {
        LogonData d = new LogonData();
        d.rawHex = hex == null ? "" : hex.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        byte[] data = Iso8583.hexToBytes(d.rawHex);
        int n = data.length;

        if (n == 0) {
            d.note = "DE62 empty - host sent no working key";
            return d;
        }

        // forcedKeyLen <= 0: the WHOLE field is the PIN key.
        //
        // On a 16-byte DE62 this gives a double-length TPK, so the pad
        // 3DES-enciphers the PIN block - what a Thales HSM holding a
        // double-length TPK expects. Spec 6.27.2 was written for single-length
        // DES; an acquirer running double-length keys fills the same two
        // 8-byte slots with the two halves of one 16-byte key.
        //
        // A DES key must be 8, 16 or 24 bytes. DE62 may legally carry the three
        // optional 23-byte text blocks after the keys (6.27.2), which would make
        // the raw length 39, 62 or 85 - not a key length at all. Take the
        // longest valid key prefix in that case instead of handing the pad
        // something it must reject.
        if (forcedKeyLen <= 0) {
            if (n < 8) {
                d.note = "DE62 is only " + n + " bytes - too short for any key";
                Log.e(TAG, d.note + " raw=" + d.rawHex);
                return d;
            }
            int kl;
            if (n == 8 || n == 16 || n == 24) {
                kl = n;                 // the whole field is exactly a key
            } else if (n > 16) {
                kl = 16;                // double-length TPK; the tail is the
                                        // TAK and/or the optional text blocks.
                                        // Triple length is not used here - a
                                        // 32-byte DE62 is 16+16, not 24+8.
            } else {
                kl = 8;                 // 9..15 bytes: single-length TPK
            }
            d.keyLen = kl;
            d.pinKey = slice(data, 0, kl);
            d.macKey = null;
            d.valid = true;
            d.note = n + " bytes received, whole-field PIN key using the first "
                    + kl + " bytes"
                    + (kl == n ? "" : " (the remaining " + (n - kl)
                        + " bytes are the optional receipt/merchant text)")
                    + ": " + Iso8583.bytesToHex(d.pinKey);
            Log.i(TAG, "DE62 logon response: " + d.note);
            return d;
        }

        // ---- spec 6.27.2 split
        int kl = forcedKeyLen;
        d.keyLen = kl;
        if (n < kl) {
            d.note = "DE62 too short: " + n + " bytes, expected at least " + kl;
            Log.w(TAG, d.note + " raw=" + d.rawHex);
            return d;
        }
        int p = 0;
        d.pinKey = slice(data, p, kl);
        p += kl;

        if (n - p >= kl) {
            d.macKey = slice(data, p, kl);
            p += kl;
        }

        // Optional 23-byte ASCII blocks: receipt line 2, receipt line 3, merchant name.
        if (n - p >= TEXT_BLOCK) {
            d.receiptLine2 = ascii(data, p, TEXT_BLOCK);
            p += TEXT_BLOCK;
        }
        if (n - p >= TEXT_BLOCK) {
            d.receiptLine3 = ascii(data, p, TEXT_BLOCK);
            p += TEXT_BLOCK;
        }
        if (n - p >= TEXT_BLOCK) {
            d.merchantName = ascii(data, p, TEXT_BLOCK);
            p += TEXT_BLOCK;
        }

        d.valid = d.pinKey != null;
        StringBuilder note = new StringBuilder();
        note.append(n).append(" bytes, forced spec 6.27.2 split, keyLen=").append(kl);
        note.append(", WK=").append(d.pinKey == null ? "-" : Iso8583.bytesToHex(d.pinKey));
        note.append(", MACK=").append(d.macKey == null ? "(none)" : Iso8583.bytesToHex(d.macKey));
        if (n - p > 0) {
            note.append(", ").append(n - p).append(" trailing bytes unparsed");
        }
        d.note = note.toString();
        Log.i(TAG, "DE62 logon response: " + d.note);
        return d;
    }

    // ------------------------------------------------------------ DE48 parse

    /**
     * Parse a DE48 key-rotation block (spec 6.19). Note the reversed order:
     * MAC key first, then PIN key.
     */
    public static LogonData parseKeyUpdate48(String hex, int forcedKeyLen) {
        LogonData d = new LogonData();
        d.rawHex = hex == null ? "" : hex.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        byte[] data = Iso8583.hexToBytes(d.rawHex);
        int n = data.length;
        if (n == 0) {
            return d;
        }
        int kl = forcedKeyLen > 0 ? forcedKeyLen : (n >= 32 ? 16 : 8);
        d.keyLen = kl;
        if (n < kl * 2) {
            d.note = "DE48 too short for a key pair: " + n + " bytes";
            return d;
        }
        d.macKey = slice(data, 0, kl);   // eKTM(KMAC) comes FIRST in DE48
        d.pinKey = slice(data, kl, kl);  // eKTM(KPP)
        d.valid = true;
        d.note = "DE48 key update, keyLen=" + kl
                + ", MACK=" + Iso8583.bytesToHex(d.macKey)
                + ", WK=" + Iso8583.bytesToHex(d.pinKey);
        Log.i(TAG, d.note);
        return d;
    }

    // ---------------------------------------------------------- candidates

    /** One reading of DE62, with the clear PIN key it produces. */
    public static final class Candidate {
        /** The {@link TerminalConfig#wkLen()} value that selects this reading. */
        public final int wkLen;
        public final String label;
        /** Clear PIN key, decrypted under the TMK. */
        public final byte[] pinKey;
        public final String kcv;

        Candidate(int wkLen, String label, byte[] pinKey, String kcv) {
            this.wkLen = wkLen;
            this.label = label;
            this.pinKey = pinKey;
            this.kcv = kcv;
        }
    }

    /**
     * Derive the clear PIN key for every reading of DE62 that this terminal
     * supports. Requires the clear TMK.
     */
    public static java.util.List<Candidate> candidates(String de62Hex, String tmkHex) {
        java.util.List<Candidate> out = new java.util.ArrayList<>();
        String raw = de62Hex == null ? "" : de62Hex.replaceAll("[^0-9A-Fa-f]", "");
        String tmk = tmkHex == null ? "" : tmkHex.replaceAll("[^0-9A-Fa-f]", "");
        if (raw.isEmpty() || tmk.isEmpty()) {
            return out;
        }
        byte[] tmkBytes = Iso8583.hexToBytes(tmk);
        byte[] data = Iso8583.hexToBytes(raw);
        int n = data.length;

        // Whole-field FIRST: it is the default and the reading the certified PAX
        // terminal uses, and on a 16-byte DE62 it produces the same key as
        // "16+16". Listing it first means the surviving label after the dedup
        // describes the reading that is actually configured, instead of
        // reporting a "16+16 split" that was never selected.
        int[] lens = {0, 8, 16};
        String[] labels = {
                "0  whole field is the TPK (default, matches PAX)",
                "8  spec 6.27.2: TPK(8) + TAK(8)",
                "16 double length: TPK(16) + TAK(16)",
        };
        for (int i = 0; i < lens.length; i++) {
            int kl = lens[i] <= 0
                    ? (n == 8 || n == 16 || n == 24 ? n : (n > 16 ? 16 : 8))
                    : lens[i];
            if (n < kl || (kl != 8 && kl != 16 && kl != 24)) {
                continue;
            }
            try {
                byte[] clear = com.hjq.demo.utils.TriDES.des3DecodeECB(tmkBytes, slice(data, 0, kl));
                String kcv = com.hjq.demo.utils.TriDES.kcv(clear);
                // Different wkLen values can select the SAME key - on a 16-byte
                // DE62, "whole field" and "16+16" both take all 16 bytes. Listing
                // that twice makes it look like there are three things to try when
                // there are only two. Keep the first reading that produces each key.
                boolean dup = false;
                for (Candidate seen : out) {
                    if (seen.kcv.equalsIgnoreCase(kcv)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    out.add(new Candidate(lens[i], labels[i], clear, kcv));
                }
            } catch (Exception ignored) {
                // that reading is not derivable, skip it
            }
        }
        return out;
    }

    // ------------------------------------------------------ candidate report

    /**
     * Derive the PIN key from a DE62 under every plausible reading of spec
     * 6.27.2 and report each one's KCV.
     *
     * <p>The terminal cannot tell on its own which reading the acquirer means:
     * whichever key it loads, the pad produces a PIN block that is internally
     * consistent with that key, so a local self-check always passes. Only the
     * acquirer's HSM can say. This turns that into one question you can put to
     * them: "which of these KCVs is the PIN key you expect?"</p>
     *
     * <p>Requires the clear TMK ({@link TerminalConfig#tmkClear()}), which the
     * admin panel records at injection.</p>
     *
     * @param de62Hex  raw DE62 as received
     * @param tmkHex   clear terminal master key
     * @return a human-readable report, ready for the status panel or a printout
     */
    public static String candidateReport(String de62Hex, String tmkHex) {
        String raw = de62Hex == null ? "" : de62Hex.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        String tmk = tmkHex == null ? "" : tmkHex.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        StringBuilder b = new StringBuilder();
        b.append("DE62 WORKING KEY CANDIDATES\n");
        if (raw.isEmpty()) {
            return b.append("No DE62 stored - run a LOGON first.").toString();
        }
        b.append("DE62 (").append(raw.length() / 2).append(" bytes)\n").append(raw).append("\n\n");
        if (tmk.isEmpty()) {
            return b.append("No clear TMK stored, cannot derive.\n"
                    + "Re-inject the master key from this panel.").toString();
        }

        byte[] tmkBytes = Iso8583.hexToBytes(tmk);
        byte[] data = Iso8583.hexToBytes(raw);
        int n = data.length;

        // wkLen value -> label. These are the readings the terminal supports.
        // Whole-field FIRST: it is the default and the reading the certified PAX
        // terminal uses, and on a 16-byte DE62 it produces the same key as
        // "16+16". Listing it first means the surviving label after the dedup
        // describes the reading that is actually configured, instead of
        // reporting a "16+16 split" that was never selected.
        int[] lens = {0, 8, 16};
        String[] labels = {
                "0  whole field is the TPK (default, matches PAX)",
                "8  spec 6.27.2: TPK(8) + TAK(8)",
                "16 double length: TPK(16) + TAK(16)",
        };
        for (int i = 0; i < lens.length; i++) {
            int kl = lens[i] <= 0 ? n : lens[i];
            b.append(labels[i]).append('\n');
            if (n < kl) {
                b.append("   n/a - DE62 is only ").append(n).append(" bytes\n\n");
                continue;
            }
            byte[] enc = slice(data, 0, kl);
            try {
                byte[] clear = com.hjq.demo.utils.TriDES.des3DecodeECB(tmkBytes, enc);
                String kcv = com.hjq.demo.utils.TriDES.kcv(clear);
                b.append("   TPK KCV = ").append(kcv.isEmpty() ? "(n/a)" : kcv).append('\n');
                if (lens[i] > 0 && n >= kl * 2) {
                    byte[] mac = com.hjq.demo.utils.TriDES.des3DecodeECB(
                            tmkBytes, slice(data, kl, kl));
                    b.append("   TAK KCV = ").append(com.hjq.demo.utils.TriDES.kcv(mac)).append('\n');
                }
            } catch (Exception e) {
                b.append("   derive failed: ").append(e.getMessage()).append('\n');
            }
            b.append('\n');
        }
        b.append("Ask the acquirer which TPK KCV they\n"
                + "hold for this TID, then set the matching\n"
                + "split with the DE62 SPLIT button.\n"
                + "A host 'PIN length error' means the key\n"
                + "is wrong, not the PIN.");
        return b.toString();
    }

    // --------------------------------------------------------------- helpers

    /**
     * Work out the key length from the total DE62 payload size.
     * Tries the spec's 8-byte keys first, then double-length 16-byte keys.
     */
    private static int detectKeyLen(int n) {
        // spec layout: 8 | 16 | 16+23k
        if (n == SPEC_KEY_LEN) {
            return SPEC_KEY_LEN;
        }
        if (n == 16 || (n > 16 && (n - 16) % TEXT_BLOCK == 0)) {
            return SPEC_KEY_LEN;
        }
        // double-length variant: 16 | 32 | 32+23k
        if (n == 32 || (n > 32 && (n - 32) % TEXT_BLOCK == 0)) {
            return 16;
        }
        if (n == 24) {
            return 24; // single triple-length key
        }
        if (n % 16 == 0) {
            return 16;
        }
        return SPEC_KEY_LEN;
    }

    private static byte[] slice(byte[] src, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, off, out, 0, len);
        return out;
    }

    private static String ascii(byte[] src, int off, int len) {
        StringBuilder b = new StringBuilder();
        for (int i = off; i < off + len && i < src.length; i++) {
            int c = src[i] & 0xFF;
            b.append(c >= 0x20 && c < 0x7F ? (char) c : ' ');
        }
        return b.toString().trim();
    }

    /** BCD-encode {@code digits} decimal digits, left padded with zeros, as hex. */
    private static String bcdHex(String value, int digits) {
        String v = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (v.length() > digits) {
            v = v.substring(v.length() - digits);
        }
        while (v.length() < digits) {
            v = "0" + v;
        }
        return v.toUpperCase(Locale.US);
    }

    /** Right-pad / truncate a hex string to exactly {@code bytes} bytes. */
    private static String fixedHex(String hex, int bytes) {
        String h = hex == null ? "" : hex.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        while (h.length() < bytes * 2) {
            h = h + "0";
        }
        return h.substring(0, bytes * 2);
    }
}

package com.hjq.demo.ans8583;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;
import java.util.TreeMap;

/**
 * Offline ISO-8583 packet builder for the demo.
 *
 * <p>Mirrors the DBBL PAX "packet sending mechanism with parameters" used by the
 * Logon (0800) and Sale (0200) transactions. Because this is an OFFLINE demo,
 * the assembled packet is logged (and toasted) instead of being transmitted over
 * a socket &mdash; i.e. a <b>simulated send</b>.</p>
 */
public final class OfflinePacket8583 {

    private static final String TAG = "OfflinePacket8583";

    // ---- Terminal parameters (same style as the PAX terminal config) ----
    public static final String TERMINAL_ID   = "48290000";        // DE41 (8)
    public static final String MERCHANT_ID   = "002948290000001";  // DE42 (15)
    public static final String CURRENCY_CODE = "050";              // DE49 BDT
    public static final String NII           = "001";              // DE24 / DE70

    private static int stan = 1;

    private OfflinePacket8583() { }

    private static synchronized String nextStan() {
        int s = stan++;
        if (stan > 999999) {
            stan = 1;
        }
        return pad(String.valueOf(s), 6);
    }

    /** Build + "send" (log/toast) the Logon 0800 packet. Returns the human-readable dump. */
    public static String simulateLogon(Context ctx) {
        TreeMap<Integer, String> de = new TreeMap<>();
        de.put(11, nextStan());              // STAN
        de.put(24, NII);                     // Function code / NII
        de.put(41, pad(TERMINAL_ID, 8));     // Terminal ID
        de.put(42, padRight(MERCHANT_ID, 15)); // Merchant ID
        de.put(70, "001");                   // Network management info code (sign-on)
        String packet = assemble("0800", de);
        String dump = render("LOGON (0800)", "0800", de, packet);
        Log.i(TAG, dump);
        toast(ctx, "LOGON packet built & sent (simulated)\nSTAN " + de.get(11));
        return dump;
    }

    /**
     * Build + "send" (log/toast) the Sale 0200 packet with the given amount.
     *
     * @param amountMajor amount in major units, e.g. "100.00"
     * @param pan         card PAN (may be null before the card is read)
     */
    public static String simulateSale(Context ctx, String amountMajor, String pan) {
        return simulateTxn(ctx, "SALE", amountMajor, pan);
    }

    /**
     * Build + "send" (log/toast) any PAX transaction packet. The MTI and processing
     * code (DE3) are chosen per transaction type, matching the PAX online packer.
     */
    public static String simulateTxn(Context ctx, String type, String amountMajor, String pan) {
        String t = (type == null) ? "SALE" : type;
        String mti = "0200";
        String proc = "000000";
        switch (t) {
            case "CASH_WITHDRAW": proc = "010000"; break;
            case "REFUND":        proc = "200000"; break;
            case "PREAUTH":       mti = "0100"; proc = "030000"; break;
            case "PREAUTH_VOID":  mti = "0100"; proc = "030000"; break;
            case "VOID":
            case "EMI_VOID":      proc = "020000"; break;
            case "ADJUST":        mti = "0220"; proc = "020000"; break;
            case "OFFLINE":       mti = "0220"; proc = "000000"; break;
            case "SALE_COMPLETE":
            case "EMI_SALE":
            case "SALE":
            default:               proc = "000000"; break;
        }
        TreeMap<Integer, String> de = new TreeMap<>();
        if (pan != null && !pan.isEmpty()) {
            de.put(2, pan.replaceAll("[^0-9]", ""));
        }
        de.put(3, proc);
        de.put(4, amountToMinor(amountMajor));
        de.put(11, nextStan());
        de.put(22, "051");
        de.put(24, NII);
        de.put(41, pad(TERMINAL_ID, 8));
        de.put(42, padRight(MERCHANT_ID, 15));
        de.put(49, CURRENCY_CODE);
        String packet = assemble(mti, de);
        String dump = render(t + " (" + mti + ")", mti, de, packet);
        Log.i(TAG, dump);
        toast(ctx, t + " packet built & sent (simulated)\nAmt TK " + amountMajor + "  STAN " + de.get(11));
        return dump;
    }

    /** Build + "send" the batch-upload / settlement 0500 packet. */
    public static String simulateSettlement(Context ctx) {
        TreeMap<Integer, String> de = new TreeMap<>();
        de.put(11, nextStan());
        de.put(24, NII);
        de.put(41, pad(TERMINAL_ID, 8));
        de.put(42, padRight(MERCHANT_ID, 15));
        de.put(70, "301"); // batch upload / settlement
        String packet = assemble("0500", de);
        String dump = render("SETTLEMENT (0500)", "0500", de, packet);
        Log.i(TAG, dump);
        toast(ctx, "SETTLEMENT packet built & sent (simulated)\nSTAN " + de.get(11));
        return dump;
    }

    // ---------------- assembly ----------------

    private static String assemble(String mti, TreeMap<Integer, String> de) {
        byte[] bitmap = new byte[8];
        for (Integer f : de.keySet()) {
            if (f >= 1 && f <= 64) {
                int idx = f - 1;
                bitmap[idx / 8] |= (byte) (0x80 >> (idx % 8));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(asciiToHex(mti));
        sb.append(bytesToHex(bitmap));
        for (Integer f : de.keySet()) {
            sb.append(encodeField(f, de.get(f)));
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    /** Minimal field encoder: LLVAR for PAN (DE2); fixed ASCII-hex for the rest. */
    private static String encodeField(int f, String v) {
        if (f == 2) { // LLVAR
            String len = pad(String.valueOf(v.length()), 2);
            return asciiToHex(len) + asciiToHex(v);
        }
        return asciiToHex(v);
    }

    private static String render(String title, String mti, TreeMap<Integer, String> de, String packet) {
        StringBuilder b = new StringBuilder();
        b.append("\n===== ").append(title).append(" =====\n");
        b.append("MTI : ").append(mti).append('\n');
        for (Integer f : de.keySet()) {
            b.append(String.format(Locale.US, "DE%-3d: %s%n", f, de.get(f)));
        }
        b.append("RAW : ").append(packet).append('\n');
        b.append("(offline demo - simulated send)\n");
        return b.toString();
    }

    // ---------------- helpers ----------------

    private static String amountToMinor(String major) {
        try {
            double v = Double.parseDouble(major.replace(",", "").trim());
            long minor = Math.round(v * 100.0);
            return pad(String.valueOf(minor), 12);
        } catch (Exception e) {
            return pad("0", 12);
        }
    }

    private static String pad(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < n; i++) {
            b.append('0');
        }
        return b.append(s).toString();
    }

    private static String padRight(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) {
            b.append(' ');
        }
        return b.substring(0, n);
    }

    private static String asciiToHex(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            b.append(String.format("%02X", s.charAt(i) & 0xFF));
        }
        return b.toString();
    }

    private static String bytesToHex(byte[] a) {
        StringBuilder b = new StringBuilder();
        for (byte x : a) {
            b.append(String.format("%02X", x & 0xFF));
        }
        return b.toString();
    }

    private static void toast(Context ctx, String msg) {
        if (ctx == null) {
            return;
        }
        try {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception ignore) {
            // no Looper on this thread; ignore for the demo
        }
    }
}

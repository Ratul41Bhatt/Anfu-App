package com.hjq.demo.utils;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * The CA public key table, built from {@code assets/capk.json}.
 *
 * <p>The keys used to be a hardcoded Java string holding about two dozen
 * entries. That is how a Mastercard sale came to fail with TVR byte 1 = 0x84
 * ("offline data authentication was not performed", "CDA failed"): the card
 * asked for a CA public key index the terminal did not have, so offline
 * authentication could not run, and the card returned an AAC at the second
 * Generate AC even though the issuer had approved the transaction.</p>
 *
 * <p>The PAX reference does not hardcode keys - it ships the full table as an
 * asset and loads all of it. This class does the same, from the same file, so
 * the two terminals accept the same cards.</p>
 *
 * <p>Each JSON entry becomes the TLV block the Anfu loader expects:</p>
 * <pre>
 *   9F06 05 &lt;RID&gt;                 registered application provider identifier
 *   9F22 01 &lt;index&gt;               CA public key index (tag 8F on the card)
 *   DF02 ll &lt;modulus&gt;             81 xx length form above 127 bytes
 *   DF03 14 &lt;checksum&gt;            SHA-1 over RID || index || modulus || exponent
 *   DF04 ll &lt;exponent&gt;            usually 03 or 010001
 *   DF05 08 &lt;"YYYYMMDD"&gt;          expiry, ASCII
 *   DF06 01 &lt;hash indicator&gt;
 *   DF07 01 &lt;arithmetic indicator&gt;
 * </pre>
 */
public final class CapkTable {

    private static final String ASSET = "capk.json";

    private CapkTable() { }

    /**
     * Build the newline-separated CAPK parameter string for the SDK loader.
     *
     * @return the table, or an empty string when the asset is missing or
     *         unreadable - the caller should then fall back to its built-in
     *         list rather than loading nothing
     */
    public static String build(Context ctx) {
        StringBuilder out = new StringBuilder();
        int n = 0;
        try {
            JSONArray arr = new JSONArray(readAsset(ctx));
            for (int i = 0; i < arr.length(); i++) {
                String tlv = entry(arr.getJSONObject(i));
                if (tlv != null) {
                    out.append(tlv).append('\n');
                    n++;
                }
            }
        } catch (Exception e) {
            LogUtils.e("CapkTable: cannot build from " + ASSET + ": " + e.getMessage());
            return "";
        }
        LogUtils.i("CapkTable: built " + n + " CA public keys from " + ASSET);
        return out.toString();
    }

    /** How many keys the asset holds, for the diagnostic report. 0 on failure. */
    public static int count(Context ctx) {
        try {
            return new JSONArray(readAsset(ctx)).length();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * True when a CA public key for this RID and index is in the table. Used to
     * turn "the card declined" into "index 0x06 for A000000004 is not loaded".
     */
    public static boolean has(Context ctx, String rid, String indexHex) {
        if (rid == null || indexHex == null) {
            return false;
        }
        try {
            JSONArray arr = new JSONArray(readAsset(ctx));
            int want = Integer.parseInt(indexHex.trim(), 16);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (rid.equalsIgnoreCase(o.optString("rID"))
                        && o.optInt("keyID", -1) == want) {
                    return true;
                }
            }
        } catch (Exception e) {
            LogUtils.e("CapkTable.has: " + e.getMessage());
        }
        return false;
    }

    // ------------------------------------------------------------- internals

    private static String entry(JSONObject o) {
        String rid = o.optString("rID", "");
        String mod = o.optString("module", "");
        String exp = o.optString("exponent", "");
        String sum = o.optString("checkSum", "");
        String expiry = o.optString("expDate", "");
        int index = o.optInt("keyID", -1);
        if (rid.isEmpty() || mod.isEmpty() || exp.isEmpty() || index < 0 || index > 255) {
            LogUtils.e("CapkTable: skipping malformed entry " + o.optString("name"));
            return null;
        }
        StringBuilder b = new StringBuilder();
        tlv(b, "9F06", rid);
        tlv(b, "9F22", String.format(Locale.US, "%02X", index));
        tlv(b, "DF02", mod);
        if (!sum.isEmpty()) {
            tlv(b, "DF03", sum);
        }
        tlv(b, "DF04", exp);
        if (expiry.length() == 6) {
            // yymmdd in the file, YYYYMMDD ASCII on the wire
            tlv(b, "DF05", ascii("20" + expiry));
        }
        tlv(b, "DF06", String.format(Locale.US, "%02X", o.optInt("hashInd", 1)));
        tlv(b, "DF07", String.format(Locale.US, "%02X", o.optInt("arithInd", 1)));
        return b.toString();
    }

    /** BER-TLV with the short/long length forms the loader expects. */
    private static void tlv(StringBuilder b, String tag, String valueHex) {
        int len = valueHex.length() / 2;
        b.append(tag);
        if (len < 0x80) {
            b.append(String.format(Locale.US, "%02X", len));
        } else {
            b.append(String.format(Locale.US, "81%02X", len));
        }
        b.append(valueHex.toUpperCase(Locale.US));
    }

    private static String ascii(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            b.append(String.format(Locale.US, "%02X", (int) s.charAt(i)));
        }
        return b.toString();
    }

    private static String readAsset(Context ctx) throws Exception {
        InputStream in = ctx.getAssets().open(ASSET);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                bos.write(buf, 0, r);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            try {
                in.close();
            } catch (Exception ignore) {
                // nothing useful to do
            }
        }
    }
}

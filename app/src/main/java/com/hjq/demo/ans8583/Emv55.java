package com.hjq.demo.ans8583;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * BER-TLV helper for DE55 (spec 6.23).
 *
 * <p>DE55 is a run of Private Data Sub-elements in TLV form. Spec 6.23 lists
 * the tags the acquirer may return in a response:</p>
 * <ul>
 *   <li>{@code 91} Issuer Authentication Data - present when the issuer wants
 *       the card to authenticate it online,</li>
 *   <li>{@code 71} Issuer Script Template 1,</li>
 *   <li>{@code 72} Issuer Script Template 2,</li>
 *   <li>{@code 8A} Authorisation Response Code, when the host echoes it here.</li>
 * </ul>
 *
 * <p>Those are exactly what the EMV kernel needs for the second Generate AC.
 * {@link #issuerResponse(String, String)} assembles them into the TLV blob
 * {@code apiEmvKernelAfterOnlineProcess} expects.</p>
 *
 * <p>Note the spec allows two-byte tags where the continuation bit of the first
 * byte is set, and states that a member system "must scan the full length of
 * DE055 to ensure that all PDS's are captured" - so this parser walks the whole
 * field rather than stopping at the first tag it recognises.</p>
 */
public final class Emv55 {

    private static final String TAG = "Emv55";

    private Emv55() { }

    /**
     * Parse a DE55 payload into tag - value pairs, both as uppercase hex.
     * Insertion order is preserved. A malformed tail is ignored rather than
     * throwing, so a partially readable response still yields its good tags.
     */
    public static Map<String, String> parse(String hex) {
        Map<String, String> out = new LinkedHashMap<>();
        byte[] d = Iso8583.hexToBytes(hex);
        int p = 0;
        while (p < d.length) {
            // --- tag: one byte, or two when the low 5 bits are all set
            int first = d[p] & 0xFF;
            if (first == 0x00 || first == 0xFF) {
                p++;                       // padding between sub-elements
                continue;
            }
            int tagStart = p;
            p++;
            if ((first & 0x1F) == 0x1F) {
                while (p < d.length && (d[p] & 0x80) != 0) {
                    p++;
                }
                p++;                       // final tag byte
            }
            if (p > d.length) {
                break;
            }
            String tag = Iso8583.bytesToHex(sub(d, tagStart, p - tagStart));

            // --- length: short form, or long form when bit 8 of the first byte is set
            if (p >= d.length) {
                break;
            }
            int lenByte = d[p] & 0xFF;
            p++;
            int len;
            if ((lenByte & 0x80) == 0) {
                len = lenByte;
            } else {
                int n = lenByte & 0x7F;
                if (n == 0 || n > 3 || p + n > d.length) {
                    break;
                }
                len = 0;
                for (int i = 0; i < n; i++) {
                    len = (len << 8) | (d[p + i] & 0xFF);
                }
                p += n;
            }
            if (len < 0 || p + len > d.length) {
                Log.w(TAG, "DE55 tag " + tag + " claims " + len
                        + " bytes but only " + (d.length - p) + " remain; stopping");
                break;
            }
            out.put(tag, Iso8583.bytesToHex(sub(d, p, len)));
            p += len;
        }
        return out;
    }

    /**
     * Build the issuer-response TLV blob for the EMV kernel's second Generate AC.
     *
     * <p>Includes tag 91 (Issuer Authentication Data) and any issuer scripts
     * (71, 72) found in the response DE55, plus tag 8A (Authorisation Response
     * Code). If the host did not send 8A inside DE55, DE39 is used - the kernel
     * needs an ARC to decide between TC and AAC.</p>
     *
     * @param responseDe55Hex DE55 from the 0210, may be null or empty
     * @param de39            response code from the 0210, used as the ARC fallback
     * @return TLV bytes, never null; empty when there is nothing to send
     */
    public static byte[] issuerResponse(String responseDe55Hex, String de39) {
        Map<String, String> tags = parse(responseDe55Hex);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // Tag 8A - Authorisation Response Code, 2 ASCII characters.
            String arc = tags.get("8A");
            if (arc == null || arc.isEmpty()) {
                String rc = de39 == null || de39.length() < 2 ? "Z1" : de39.substring(0, 2);
                arc = Iso8583.ascii(rc);
            }
            writeTlv(out, "8A", arc);

            // Tag 91 - Issuer Authentication Data, only when the issuer sent it.
            String iad = tags.get("91");
            if (iad != null && !iad.isEmpty()) {
                writeTlv(out, "91", iad);
            }
            // Tags 71 / 72 - Issuer Script Templates.
            for (String t : new String[] {"71", "72"}) {
                String v = tags.get(t);
                if (v != null && !v.isEmpty()) {
                    writeTlv(out, t, v);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "issuerResponse build failed: " + e.getMessage());
        }
        byte[] r = out.toByteArray();
        Log.i(TAG, "issuer response TLV: " + Iso8583.bytesToHex(r));
        return r;
    }

    /** True when the issuer asked the card to run online issuer authentication. */
    public static boolean hasIssuerAuthData(String responseDe55Hex) {
        String v = parse(responseDe55Hex).get("91");
        return v != null && !v.isEmpty();
    }

    /** Pretty-print DE55 for the packet dump on the receipt. */
    public static String describe(String hex) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : parse(hex).entrySet()) {
            b.append(String.format(Locale.US, "  %-6s (%2d) %s%n",
                    e.getKey(), e.getValue().length() / 2, e.getValue()));
        }
        return b.toString();
    }

    // --------------------------------------------------------------- helpers

    private static void writeTlv(ByteArrayOutputStream out, String tagHex, String valueHex) {
        byte[] tag = Iso8583.hexToBytes(tagHex);
        byte[] val = Iso8583.hexToBytes(valueHex);
        out.write(tag, 0, tag.length);
        if (val.length < 0x80) {
            out.write(val.length);
        } else {
            out.write(0x81);
            out.write(val.length & 0xFF);
        }
        out.write(val, 0, val.length);
    }

    private static byte[] sub(byte[] src, int off, int len) {
        byte[] o = new byte[len];
        System.arraycopy(src, off, o, 0, len);
        return o;
    }
}

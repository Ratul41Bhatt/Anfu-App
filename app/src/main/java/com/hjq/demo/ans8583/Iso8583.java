package com.hjq.demo.ans8583;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal but real binary ISO-8583 codec used for online host communication.
 *
 * <p>Message layout produced by {@link #pack}:
 * {@code TPDU(5) + MTI(2 BCD) + primary bitmap(8) + fields...}. The outer
 * 2-byte total-length header is added by {@link HostComm}, not here.</p>
 *
 * <p>Field encodings follow the common EDC dialect: numeric fields are BCD
 * (right-justified), AN/ANS are ASCII (space padded), binary fields are raw
 * bytes, and variable fields carry a BCD length header (LL = 1 byte,
 * LLL = 2 bytes).</p>
 */
public final class Iso8583 {

    public enum T { N, AN, ANS, B, LL_N, LL_Z, LL_ANS, LLL_ANS, LLL_B }

    /**
     * Pad nibble used when a {@code z} (track 2) field has an odd digit count.
     *
     * <p>Confirmed as 0xF from a working DBBL terminal capture: DE35 arrived as
     * {@code 37 | 48 40 61 01 01 02 40 18 d2 80 32 26 00 00 00 01 47 00 0f} -
     * LL = 37 nibbles carried in 19 bytes, and the 38th (pad) nibble is 'f'.</p>
     */
    private static final int Z_PAD_NIBBLE = 0xF;

    private static final class Spec {
        final T type;
        final int len; // fixed length (digits for N, chars for AN/ANS, bytes for B); ignored for LL*/LLL*
        Spec(T type, int len) { this.type = type; this.len = len; }
    }

    private static final Map<Integer, Spec> FIELDS = new TreeMap<>();
    static {
        FIELDS.put(2,  new Spec(T.LL_N, 0));    // PAN
        FIELDS.put(3,  new Spec(T.N, 6));       // processing code
        FIELDS.put(4,  new Spec(T.N, 12));      // amount
        FIELDS.put(11, new Spec(T.N, 6));       // STAN
        FIELDS.put(12, new Spec(T.N, 6));       // time hhmmss
        FIELDS.put(13, new Spec(T.N, 4));       // date MMDD
        FIELDS.put(14, new Spec(T.N, 4));       // expiry YYMM
        FIELDS.put(22, new Spec(T.N, 3));       // entry mode
        FIELDS.put(23, new Spec(T.N, 3));       // card seq
        FIELDS.put(24, new Spec(T.N, 3));       // NII / function code
        FIELDS.put(25, new Spec(T.N, 2));       // POS condition code
        // Field 35 is attribute "z": each data element is one NIBBLE, so track 2
        // is BCD/nibble-packed with 'D' as the field separator - not ASCII.
        FIELDS.put(35, new Spec(T.LL_Z, 0));    // track 2
        FIELDS.put(37, new Spec(T.AN, 12));     // RRN
        FIELDS.put(38, new Spec(T.AN, 6));      // auth code
        FIELDS.put(39, new Spec(T.AN, 2));      // response code
        FIELDS.put(41, new Spec(T.ANS, 8));     // TID
        FIELDS.put(42, new Spec(T.ANS, 15));    // MID
        FIELDS.put(43, new Spec(T.ANS, 40));    // card acceptor name
        FIELDS.put(45, new Spec(T.LL_ANS, 0));  // track 1
        // DE48 carries binary key material (spec 6.19), so keep it binary-safe.
        FIELDS.put(48, new Spec(T.LLL_B, 0));   // additional data - private / key update
        FIELDS.put(52, new Spec(T.B, 8));       // PIN block
        FIELDS.put(53, new Spec(T.N, 16));      // security control info
        FIELDS.put(54, new Spec(T.LLL_B, 0));   // additional amounts
        FIELDS.put(55, new Spec(T.LLL_B, 0));   // ICC / EMV data
        // Private-use fields 60-63 mix ASCII and binary depending on message
        // type, so they are all carried as hex here. Use ascii()/hexToAscii()
        // at the call site for the text variants.
        FIELDS.put(60, new Spec(T.LLL_B, 0));   // private use 60 (batch no / logon request / sw id)
        FIELDS.put(61, new Spec(T.LLL_B, 0));   // private use 61 (product codes)
        FIELDS.put(62, new Spec(T.LLL_B, 0));   // private use 62 (invoice / logon response)
        FIELDS.put(63, new Spec(T.LLL_B, 0));   // private use 63 (additional data tables)
        FIELDS.put(64, new Spec(T.B, 8));       // MAC
        FIELDS.put(70, new Spec(T.N, 3));       // network management code
        // Private use. Not in the Hypercom spec; kept because a host that does
        // return it should be parsed rather than dropped.
        FIELDS.put(72, new Spec(T.LLL_B, 0));   // private use
    }

    /** Number of bytes the DE64 MAC value occupies. */
    public static final int MAC_LEN = 8;
    /** Placeholder DE64 value: reserves the bitmap bit while the MAC is computed. */
    public static final String MAC_PLACEHOLDER = "0000000000000000";

    public String mti;
    public String tpdu;
    public final TreeMap<Integer, String> de = new TreeMap<>();
    /** Non-null when the response could not be fully parsed. */
    public String parseError;
    /** Bytes left over after the last decoded field, if any. */
    public String trailingHex;

    public String get(int field) { return de.get(field); }
    public boolean has(int field) { return de.containsKey(field); }

    // ------------------------------------------------------------------ pack

    public static byte[] pack(String tpduHex, String mti, TreeMap<Integer, String> de) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(hexToBytes(tpduHex));            // TPDU (5 bytes)
            out.write(bcd(mti, 4));                    // MTI (2 bytes BCD)

            boolean secondary = false;
            for (Integer f : de.keySet()) {
                if (f >= 65 && f <= 128) {
                    secondary = true;
                    break;
                }
            }
            byte[] bitmap = new byte[secondary ? 16 : 8];
            if (secondary) {
                bitmap[0] |= (byte) 0x80; // bit 1 => secondary bitmap present
            }
            int maxField = secondary ? 128 : 64;
            // Only flag fields we can actually encode - a bitmap bit with no
            // payload behind it desynchronises the host's parser.
            for (Integer f : de.keySet()) {
                if (f >= 2 && f <= maxField && FIELDS.containsKey(f)) {
                    int idx = f - 1;
                    bitmap[idx / 8] |= (byte) (0x80 >> (idx % 8));
                }
            }
            out.write(bitmap);

            for (Integer f : de.keySet()) {
                Spec s = FIELDS.get(f);
                if (s == null || f < 2 || f > maxField) {
                    continue;
                }
                out.write(encode(s, de.get(f)));
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("ISO pack failed: " + e.getMessage(), e);
        }
    }

    private static byte[] encode(Spec s, String v) throws Exception {
        if (v == null) {
            v = "";
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        switch (s.type) {
            case N:
                b.write(bcd(v, s.len));
                break;
            case AN:
            case ANS:
                b.write(padRight(v, s.len).getBytes("US-ASCII"));
                break;
            case B:
                b.write(fixHex(v, s.len));
                break;
            case LL_N: {
                String digits = v.replaceAll("[^0-9]", "");
                b.write(bcd(String.valueOf(digits.length()), 2)); // LL
                // PAN digits are left-justified; odd length pads trailing nibble with 0xF.
                b.write(packNibbles(digits));
                break;
            }
            case LL_Z: {
                // Track 2 image: '=' becomes 'D', then nibble-pack.
                String t = v.toUpperCase(Locale.US).replace('=', 'D').replaceAll("[^0-9A-D]", "");
                b.write(bcd(String.valueOf(t.length()), 2)); // LL = number of nibbles
                b.write(packNibbles(t));
                break;
            }
            case LL_ANS: {
                b.write(bcd(String.valueOf(v.length()), 2));
                b.write(v.getBytes("US-ASCII"));
                break;
            }
            case LLL_ANS: {
                b.write(bcd(String.valueOf(v.length()), 4)); // LLL -> 2 bytes
                b.write(v.getBytes("US-ASCII"));
                break;
            }
            case LLL_B: {
                byte[] data = hexToBytes(v);
                b.write(bcd(String.valueOf(data.length), 4));
                b.write(data);
                break;
            }
            default:
                break;
        }
        return b.toByteArray();
    }

    // ---------------------------------------------------------------- unpack

    public static Iso8583 unpack(byte[] full) {
        Iso8583 m = new Iso8583();
        try {
            int[] p = {0};
            m.tpdu = bytesToHex(read(full, p, 5));
            m.mti = bcdToDigits(read(full, p, 2), 4);
            byte[] primary = read(full, p, 8);
            boolean secondary = (primary[0] & 0x80) != 0;
            byte[] bitmap;
            int maxField;
            if (secondary) {
                byte[] sec = read(full, p, 8);
                bitmap = new byte[16];
                System.arraycopy(primary, 0, bitmap, 0, 8);
                System.arraycopy(sec, 0, bitmap, 8, 8);
                maxField = 128;
            } else {
                bitmap = primary;
                maxField = 64;
            }
            for (int f = 2; f <= maxField; f++) {
                int idx = f - 1;
                boolean present = (bitmap[idx / 8] & (0x80 >> (idx % 8))) != 0;
                if (!present) {
                    continue;
                }
                Spec s = FIELDS.get(f);
                if (s == null) {
                    // Unknown field in the stream - we no longer know where the
                    // next field starts, so stop and say so rather than
                    // silently returning a half-parsed message.
                    m.parseError = "unsupported field DE" + f + " present in response; "
                            + "stopped at offset " + p[0];
                    return m;
                }
                m.de.put(f, decode(s, full, p));
            }
            if (p[0] < full.length) {
                m.trailingHex = bytesToHex(read(full, p, full.length - p[0]));
            }
        } catch (Exception e) {
            m.parseError = "truncated/short field: " + e.getClass().getSimpleName()
                    + " " + e.getMessage();
        }
        return m;
    }

    private static String decode(Spec s, byte[] buf, int[] p) throws Exception {
        switch (s.type) {
            case N: {
                int bytes = (s.len + 1) / 2;
                String digits = bcdToDigits(read(buf, p, bytes), bytes * 2);
                return digits.substring(digits.length() - s.len);
            }
            case AN:
            case ANS:
                return new String(read(buf, p, s.len), "US-ASCII").trim();
            case B:
                return bytesToHex(read(buf, p, s.len));
            case LL_N: {
                int ll = Integer.parseInt(bcdToDigits(read(buf, p, 1), 2));
                int bytes = (ll + 1) / 2;
                String digits = bytesToHex(read(buf, p, bytes));
                return digits.substring(0, Math.min(ll, digits.length()));
            }
            case LL_Z: {
                int ll = Integer.parseInt(bcdToDigits(read(buf, p, 1), 2));
                int bytes = (ll + 1) / 2;
                String nib = bytesToHex(read(buf, p, bytes));
                return nib.substring(0, Math.min(ll, nib.length()));
            }
            case LL_ANS: {
                int ll = Integer.parseInt(bcdToDigits(read(buf, p, 1), 2));
                return new String(read(buf, p, ll), "US-ASCII");
            }
            case LLL_ANS: {
                int lll = Integer.parseInt(bcdToDigits(read(buf, p, 2), 4));
                return new String(read(buf, p, lll), "US-ASCII");
            }
            case LLL_B: {
                int lll = Integer.parseInt(bcdToDigits(read(buf, p, 2), 4));
                return bytesToHex(read(buf, p, lll));
            }
            default:
                return "";
        }
    }

    // --------------------------------------------------------------- helpers

    private static byte[] read(byte[] buf, int[] p, int n) {
        byte[] out = new byte[n];
        System.arraycopy(buf, p[0], out, 0, n);
        p[0] += n;
        return out;
    }

    /** BCD-encode a decimal string into {@code digits} nibbles (left padded with 0). */
    private static byte[] bcd(String value, int digits) {
        String v = value.replaceAll("[^0-9]", "");
        if (digits % 2 != 0) {
            digits++;
        }
        while (v.length() < digits) {
            v = "0" + v;
        }
        if (v.length() > digits) {
            v = v.substring(v.length() - digits);
        }
        byte[] out = new byte[digits / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = v.charAt(i * 2) - '0';
            int lo = v.charAt(i * 2 + 1) - '0';
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String bcdToDigits(byte[] b, int digits) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append((char) (((x >> 4) & 0x0F) + '0'));
            sb.append((char) ((x & 0x0F) + '0'));
        }
        String s = sb.toString();
        if (s.length() > digits) {
            s = s.substring(0, digits);
        }
        return s;
    }

    private static String padRight(String v, int len) {
        StringBuilder b = new StringBuilder(v == null ? "" : v);
        while (b.length() < len) {
            b.append(' ');
        }
        return b.substring(0, len);
    }

    private static byte[] fixHex(String hex, int bytes) {
        byte[] src = hexToBytes(hex);
        byte[] out = new byte[bytes];
        System.arraycopy(src, 0, out, 0, Math.min(src.length, bytes));
        return out;
    }

    /** Pack a string of hex nibbles into bytes, padding an odd tail nibble. */
    private static byte[] packNibbles(String nibbles) {
        String s = nibbles;
        if (s.length() % 2 != 0) {
            s = s + Character.forDigit(Z_PAD_NIBBLE, 16);
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** Hex-encode an ASCII string, for the text variants of DE60-DE63. */
    public static String ascii(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            b.append(String.format(Locale.US, "%02X", s.charAt(i) & 0xFF));
        }
        return b.toString();
    }

    /** Decode a hex string back to printable ASCII (non-printables become '.'). */
    public static String hexToAscii(String hex) {
        byte[] d = hexToBytes(hex);
        StringBuilder b = new StringBuilder();
        for (byte x : d) {
            int c = x & 0xFF;
            b.append(c >= 0x20 && c < 0x7F ? (char) c : '.');
        }
        return b.toString();
    }

    // ------------------------------------------------------------------- MAC

    /**
     * Return the byte range a DE64 MAC must be computed over (spec 6.29).
     *
     * <p>The MAC covers the message from the MTI up to but excluding the DE64
     * value itself, with bit 64 already set in the bitmap. The 5-byte TPDU is
     * <em>not</em> included. Call this on the output of {@link #pack} where
     * DE64 was supplied as {@link #MAC_PLACEHOLDER}, then overwrite the last
     * {@value #MAC_LEN} bytes with the resulting MAC.</p>
     */
    public static byte[] macSourceData(byte[] packedWithPlaceholder) {
        int from = 5;                                        // skip TPDU
        int to = packedWithPlaceholder.length - MAC_LEN;      // exclude DE64 value
        if (to <= from) {
            return new byte[0];
        }
        byte[] out = new byte[to - from];
        System.arraycopy(packedWithPlaceholder, from, out, 0, out.length);
        return out;
    }

    /** Overwrite the trailing DE64 placeholder with the computed MAC, in place. */
    public static void applyMac(byte[] packedWithPlaceholder, byte[] mac) {
        if (mac == null || packedWithPlaceholder.length < MAC_LEN) {
            return;
        }
        int off = packedWithPlaceholder.length - MAC_LEN;
        for (int i = 0; i < MAC_LEN; i++) {
            packedWithPlaceholder[off + i] = i < mac.length ? mac[i] : 0;
        }
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String h = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (h.length() % 2 != 0) {
            h = "0" + h;
        }
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static String bytesToHex(byte[] a) {
        StringBuilder b = new StringBuilder();
        for (byte x : a) {
            b.append(String.format(Locale.US, "%02X", x & 0xFF));
        }
        return b.toString();
    }

    /**
     * Packet dump in the format the reference DBBL terminal prints, so logs can
     * be compared side by side with the acquirer's:
     *
     * <pre>
     *   14:42:20.980 Request DEBU 0
     *   Length: 00DA
     *   TPDU: 6000080000
     *   MTI: 0200
     *    1  | 30 20 05 80 20 c0 12 04
     *    3  | 00 00 00
     *   ...
     * </pre>
     *
     * @param label "Request" or "Response"
     * @param raw   the packed bytes, TPDU first (no 2-byte length header)
     */
    public String debugDump(String label, byte[] raw) {
        StringBuilder b = new StringBuilder();
        String ts = new java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                .format(new java.util.Date());
        b.append(ts).append(' ').append(label).append(" DEBU 0\n");
        if (raw != null) {
            b.append(String.format(Locale.US, "Length: %04X%n", raw.length));
        }
        b.append("TPDU: ").append(tpdu == null ? "" : tpdu).append('\n');
        b.append("MTI: ").append(mti == null ? "" : mti).append('\n');

        // Bit map, as it went on the wire.
        if (raw != null && raw.length >= 15) {
            boolean secondary = (raw[7] & 0x80) != 0;
            byte[] bm = new byte[secondary ? 16 : 8];
            System.arraycopy(raw, 7, bm, 0, Math.min(bm.length, raw.length - 7));
            appendField(b, 1, bytesToHex(bm));
        }
        for (Integer f : de.keySet()) {
            Spec s = FIELDS.get(f);
            if (s == null) {
                continue;
            }
            try {
                appendField(b, f, bytesToHex(encode(s, de.get(f))));
            } catch (Exception e) {
                appendField(b, f, "(encode error)");
            }
        }
        if (parseError != null) {
            b.append("WARN: ").append(parseError).append('\n');
        }
        return b.toString();
    }

    /** One field: number, then its bytes as lowercase hex, 8 per line. */
    private static void appendField(StringBuilder b, int field, String hex) {
        String h = hex == null ? "" : hex.toLowerCase(Locale.US);
        int bytes = h.length() / 2;
        for (int i = 0; i < bytes; i += 8) {
            StringBuilder row = new StringBuilder();
            for (int j = i; j < Math.min(i + 8, bytes); j++) {
                if (row.length() > 0) {
                    row.append(' ');
                }
                row.append(h, j * 2, j * 2 + 2);
            }
            if (i == 0) {
                b.append(String.format(Locale.US, "%4d | %s%n", field, row));
            } else {
                b.append(String.format(Locale.US, "     | %s%n", row));
            }
        }
        if (bytes == 0) {
            b.append(String.format(Locale.US, "%4d |%n", field));
        }
    }

    /** Human-readable dump for logging / the on-screen packet view. */
    public String dump(String title, byte[] raw) {
        StringBuilder b = new StringBuilder();
        b.append("===== ").append(title).append(" =====\n");
        b.append("TPDU: ").append(tpdu).append('\n');
        b.append("MTI : ").append(mti).append('\n');
        for (Integer f : de.keySet()) {
            String v = de.get(f);
            b.append(String.format(Locale.US, "DE%-3d: %s%n", f, v));
            // Private-use fields are carried as hex; also show the ASCII view
            // when the content is printable text (invoice, batch no, tables).
            if (f >= 60 && f <= 63 && v != null && !v.isEmpty()) {
                String txt = hexToAscii(v);
                if (txt.indexOf('.') < 0) {
                    b.append(String.format(Locale.US, "       (ascii \"%s\")%n", txt));
                }
            }
        }
        if (trailingHex != null && !trailingHex.isEmpty()) {
            b.append("TAIL: ").append(trailingHex).append('\n');
        }
        if (parseError != null) {
            b.append("WARN: ").append(parseError).append('\n');
        }
        if (raw != null) {
            b.append("RAW : ").append(bytesToHex(raw)).append('\n');
        }
        return b.toString();
    }
}

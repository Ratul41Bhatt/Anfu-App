package com.hjq.demo.ans8583;

import android.content.Context;

import com.hjq.demo.utils.LogUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "Nexus (KonaMoney)" acceptance - Kona I terminal transaction spec v1.0.7.
 *
 * <p>Kona Money is a prepaid scheme. A customer presents it three ways, and the
 * spec treats them as one transaction with three front ends:</p>
 *
 * <ul>
 *   <li><b>QR</b> - the KonaMoney phone app shows 40 digits: a 37-digit track-2
 *       equivalent followed by a 3-digit {@code dCVV_MD}. The terminal scans it
 *       and sends the 37 in DE35 and the 3 in DE48 sub-element 31 (spec 2.1,
 *       2.4).</li>
 *   <li><b>Contactless</b> - EMV CL in <em>MSD mode</em>. The kernel selects
 *       {@link #AID}, runs GPO, and the same 37-digit track-2 comes back in tag
 *       57 - out of the GPO response if the card put it there, otherwise out of
 *       READ RECORD (spec 2.2). There is no cryptogram to check; the dCVV
 *       inside the track is what the issuer verifies.</li>
 *   <li><b>Contact IC</b> - ordinary EMV, online-only because it is a prepaid
 *       card (spec 2.1). The card's own tags are passed through.</li>
 * </ul>
 *
 * <p>The single idea that makes all three the same transaction: <b>the 37-digit
 * track-2 equivalent is the card data</b>. Whichever way it arrives, it carries
 * the PAN, the expiry, a per-transaction {@code dCVV} and the {@code ATC}, and
 * the terminal's job is to move it to the host unaltered. This class is that
 * layout and the fields around it - see {@link Track2}.</p>
 *
 * <p><b>What this class does not do.</b> It builds no cryptogram and computes
 * no dCVV: those come off the card or the phone. It also does not speak Kona's
 * own ISO-8583. Section 3 of the spec shows what the <em>VAN</em> forwards to
 * the Kona server and says in as many words that the terminal-to-VAN message
 * "has difference in accordance with VAN Co." - so the terminal keeps sending
 * the message it already sends, with these values in it. Section 2.3 is the
 * contract that matters here, and it is written in EMV tags, not in a wire
 * format.</p>
 */
public final class KonaMoney {

    private static final String TAG = "KonaMoney";

    /** Kona Money application, spec 2.2 C2. The one AID for all card products. */
    public static final String AID = "A0000002514025260010";

    /** The contactless directory every CL transaction starts at. */
    public static final String PPSE = "325041592E5359532E4444463031";

    /**
     * Terminal Transaction Qualifier, tag 9F66 (spec 2.2, table at p.12).
     *
     * <p>Byte 1 bit 8 = contactless MSD supported (0x80), bit 6 = EMV CL (0x20),
     * bit 5 = EMV contact (0x10), bit 3 = online PIN (0x04), bit 2 = signature (0x02).
     * 0x80 | 0x36 = 0xB6.
     * Kona is online-only prepaid in MSD mode for contactless/HCE.</p>
     *
     * <p>Spec 2.2 notes that when the PDOL contains 9F66 with MSD supported,
     * the card/HCE app answers directly with tags 57, 9F7E and 5F34.</p>
     */
    public static final String TTQ = "B6000000";

    /** DE48 sub-element carrying dCVV_MD on a QR transaction (spec 2.1). */
    public static final int DE48_TAG_DCVV_MD = 0x1F;      // 31 decimal

    /** DE22 entry modes the spec's own samples use. */
    public static final String ENTRY_QR = "031";
    public static final String ENTRY_CL = "091";
    public static final String ENTRY_IC = "051";

    /** DBBL terminal entry mode for NexusPay phone NFC (HCE). */
    public static final String ENTRY_PHONE_NFC = "910";

    /** The selected Kona application and its HCE-only account parameter identify a phone. */
    public static boolean isPhoneNfc(String aid, String track2) {
        if (aid == null || !AID.equalsIgnoreCase(aid.trim())) return false;
        Track2 parsed = parseTrack2(track2);
        return parsed.ok() && !"FFFFFF".equals(parsed.accountParamIndex);
    }

    /** A QR payload is exactly this: 37 track-2 digits plus 3 dCVV_MD digits. */
    public static final int QR_LEN = 40;
    public static final int TRACK2_LEN = 37;
    public static final int DCVV_MD_LEN = 3;

    private KonaMoney() { }

    // ------------------------------------------------------------ card data

    /**
     * The 37-digit track-2 equivalent, spec 2.3 note 1 ("Fixed to the full 37
     * digit").
     *
     * <pre>
     *   4000001234567892 D 0212 000 112233 123 9999
     *   |--- PAN 16 ---| |  |    |    |     |    |
     *                    |  |    |    |     |    +-- ATC          4
     *                    |  |    |    |     +------- dCVV         3
     *                    |  |    |    +------------- acct param   6
     *                    |  |    +------------------ service code 3
     *                    |  +----------------------- expiry YYMM  4
     *                    +-------------------------- separator 'D'
     * </pre>
     *
     * <p>The last seven digits are the point of the whole scheme. {@code dCVV}
     * changes every transaction and {@code ATC} counts them, so a replayed
     * track is detectable at the issuer. That is also why the terminal must
     * pass the track through untouched - re-deriving or reformatting it
     * destroys the only thing that makes it safe to send.</p>
     */
    public static final class Track2 {
        public String raw = "";
        public String pan = "";
        public String expiry = "";          // YYMM
        public String serviceCode = "";
        public String accountParamIndex = "";
        public String dcvv = "";
        public String atc = "";
        /** Empty when the layout is right; otherwise why it is not. */
        public String error = "";

        public boolean ok() {
            return error.isEmpty();
        }

        /** The seven discretionary digits, as one field. */
        public String discretionary() {
            return dcvv + atc;
        }

        @Override
        public String toString() {
            return ok()
                    ? "PAN " + pan + " exp " + expiry + " svc " + serviceCode
                            + " api " + accountParamIndex + " dCVV " + dcvv + " ATC " + atc
                    : "invalid track2: " + error;
        }
    }

    /**
     * Parse a Kona 37-digit track-2 equivalent.
     *
     * <p>Accepts the separator as '=' or 'D' and tolerates a trailing 'F' pad,
     * because that is how it arrives depending on whether it came from tag 57
     * (nibble-packed, so an odd length is padded) or from a QR string.</p>
     */
    public static Track2 parseTrack2(String s) {
        Track2 t = new Track2();
        String v = s == null ? "" : s.trim().toUpperCase(Locale.US).replace('=', 'D');
        // 37 nibbles do not fill a whole number of bytes, so tag 57 carries a
        // single 'F' in the low nibble of the last byte. Strip exactly one, and
        // only when the length says there is one to strip - a loop that ate
        // every trailing 'F' would eat real data off a card whose account
        // parameter index is F-filled, which is most of them.
        if (v.length() == TRACK2_LEN + 1 && v.endsWith("F")) {
            v = v.substring(0, TRACK2_LEN);
        }
        t.raw = v;
        if (v.length() != TRACK2_LEN) {
            t.error = "expected " + TRACK2_LEN + " digits, got " + v.length();
            return t;
        }
        int sep = v.indexOf('D');
        if (sep != 16) {
            t.error = sep < 0 ? "no field separator" : "separator at " + sep + ", expected 16";
            return t;
        }
        t.pan = v.substring(0, 16);
        t.expiry = v.substring(17, 21);
        t.serviceCode = v.substring(21, 24);
        t.accountParamIndex = v.substring(24, 30);
        t.dcvv = v.substring(30, 33);
        t.atc = v.substring(33, 37);
        if (!t.pan.matches("\\d{16}")) {
            t.error = "PAN is not 16 digits";
            return t;
        }
        // The account parameter index is the one field that is not numeric.
        // Spec 2.3 note 1 calls it "Only for HCE / RFU for Card SE": a phone
        // fills it in, a plastic card leaves it padded, and the spec's own
        // contactless sample has FFFFFF sitting there. Requiring digits across
        // the whole tail rejects every physical Kona card.
        if (!t.expiry.matches("\\d{4}")) {
            t.error = "expiry is not 4 digits";
            return t;
        }
        if (!t.serviceCode.matches("\\d{3}")) {
            t.error = "service code is not 3 digits";
            return t;
        }
        if (!t.accountParamIndex.matches("[0-9A-F]{6}")) {
            t.error = "account parameter index is not 6 hex digits";
            return t;
        }
        if (!t.dcvv.matches("\\d{3}")) {
            t.error = "dCVV is not 3 digits";
            return t;
        }
        if (!t.atc.matches("\\d{4}")) {
            t.error = "ATC is not 4 digits";
        }
        return t;
    }

    // ------------------------------------------------------------------- QR

    /** A scanned KonaMoney QR, split the way spec 2.1 and 2.4 split it. */
    public static final class Qr {
        public String track2 = "";
        public String dcvvMd = "";
        public Track2 parsed = new Track2();
        public String error = "";

        public boolean ok() {
            return error.isEmpty();
        }
    }

    /**
     * Split a scanned QR payload.
     *
     * <p>40 digits: the first 37 are the track-2 equivalent and go to DE35, the
     * last 3 are {@code dCVV_MD} and go to DE48 sub-element 31. The spec's own
     * example, {@code 9800008001397724D20122011234565380000345}, splits at
     * {@code ...380000} / {@code 345}.</p>
     *
     * <p>Anything that is not 40 digits is refused rather than guessed at. A
     * terminal that pads or truncates a payment credential to make it fit is
     * one that sends a different card number than the one presented.</p>
     */
    public static Qr parseQr(String scanned) {
        Qr q = new Qr();
        String v = scanned == null ? "" : scanned.trim();
        // Scanners vary on whitespace and some prepend a symbology id.
        v = v.replaceAll("[^0-9A-Fa-f=]", "").toUpperCase(Locale.US);
        if (v.length() != QR_LEN) {
            q.error = "a KonaMoney QR is " + QR_LEN + " digits; this one is " + v.length();
            return q;
        }
        q.track2 = v.substring(0, TRACK2_LEN);
        q.dcvvMd = v.substring(TRACK2_LEN);
        if (!q.dcvvMd.matches("\\d{3}")) {
            q.error = "dCVV_MD is not 3 digits";
            return q;
        }
        q.parsed = parseTrack2(q.track2);
        if (!q.parsed.ok()) {
            q.error = q.parsed.error;
        }
        return q;
    }

    /**
     * DE48 sub-element 31 carrying dCVV_MD, as tag-length-value.
     *
     * <p>{@code 1F 03 33 34 35} - tag 0x1F is sub-element 31, length 3, and the
     * value is the three digits in ASCII. The spec's sample DE48 is exactly
     * {@code 1F0333435} for a dCVV_MD of "345".</p>
     */
    public static String de48DcvvMd(String dcvvMd) {
        String d = dcvvMd == null ? "" : dcvvMd.replaceAll("[^0-9]", "");
        if (d.length() != DCVV_MD_LEN) {
            LogUtils.e(TAG + ": dCVV_MD must be " + DCVV_MD_LEN + " digits, got '" + dcvvMd + "'");
            return "";
        }
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.US, "%02X", DE48_TAG_DCVV_MD));
        b.append(String.format(Locale.US, "%02X", d.length()));
        for (int i = 0; i < d.length(); i++) {
            b.append(String.format(Locale.US, "%02X", (int) d.charAt(i)));
        }
        return b.toString();
    }

    // ----------------------------------------------------------------- DE55

    /**
     * DE55 for the three paths, spec 2.3.
     *
     * <p>The table there reads "No.1~7 used in Barcode/QR, No.1~8 in RF, all
     * No.1~14 in IC", and the samples show what that means in practice: on a
     * contactless MSD transaction the EMV-only tags are <b>present with length
     * zero</b>, not left out. The spec's own CL sample carries
     * {@code 8200}, {@code 9F1000}, {@code 9F2600} and {@code 9F3600} - empty -
     * beside a populated {@code 9505}. A host that counts fields will reject a
     * message that simply omits them, which is the sort of thing that reads as
     * "contactless doesn't work" for a fortnight.</p>
     *
     * @param amountMinor    transaction amount in minor units
     * @param cashbackMinor  always zero for Kona (spec 2.2 table, tag 9F03)
     * @param countryCode    tag 9F1A, e.g. "0410" Korea, "0050" Bangladesh
     * @param currencyCode   tag 5F2A
     * @param yymmdd         tag 9A, transaction date
     * @param txnType        tag 9C, "00" = purchase
     * @param terminalRandom tag 9F37, the unpredictable number sent at GPO
     */
    public static String de55Contactless(long amountMinor, long cashbackMinor,
                                         String countryCode, String currencyCode,
                                         String yymmdd, String txnType,
                                         String terminalRandom, String tvr) {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("5F2A", n(currencyCode, 4));
        t.put("82", "");                                   // AIP - MSD, empty
        t.put("95", n(tvr, 10));                           // TVR
        t.put("9A", n(yymmdd, 6));
        t.put("9C", n(txnType, 2));
        t.put("9F02", amount12(amountMinor));
        t.put("9F03", amount12(cashbackMinor));
        t.put("9F10", "");                                 // IAD - none in MSD
        t.put("9F1A", n(countryCode, 4));
        t.put("9F26", "");                                 // AC - none in MSD
        t.put("9F36", "");                                 // ATC - it is in the track
        t.put("9F37", n(terminalRandom, 8));
        return tlv(t);
    }

    /**
     * DE55 for a contact IC transaction: the card's own tags, passed through.
     *
     * <p>The terminal contributes the amounts, the country, the currency, the
     * date, the type and the unpredictable number. Everything else - AIP, TVR,
     * CID, ATC, the cryptogram and the issuer application data - is read off
     * the card and forwarded unaltered. The IAD layout in spec 2.3 (CVN 43,
     * DKI, CVR, wallet id, derivation data, MD cryptogram) describes what the
     * <em>card</em> puts in 9F10; the terminal neither builds nor checks it.</p>
     *
     * @param cardTags tags read from the kernel, keyed uppercase without spaces
     */
    public static String de55Contact(long amountMinor, long cashbackMinor,
                                     String countryCode, String currencyCode,
                                     String yymmdd, String txnType,
                                     String terminalRandom, Map<String, String> cardTags) {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("5F2A", n(currencyCode, 4));
        t.put("82", card(cardTags, "82"));
        t.put("95", card(cardTags, "95"));
        t.put("9A", n(yymmdd, 6));
        t.put("9C", n(txnType, 2));
        t.put("9F02", amount12(amountMinor));
        t.put("9F03", amount12(cashbackMinor));
        t.put("9F10", card(cardTags, "9F10"));
        t.put("9F1A", n(countryCode, 4));
        t.put("9F26", card(cardTags, "9F26"));
        t.put("9F27", card(cardTags, "9F27"));
        t.put("9F36", card(cardTags, "9F36"));
        t.put("9F37", n(terminalRandom, 8));
        return tlv(t);
    }

    // ----------------------------------------------------------------- PDOL

    /**
     * Build the GPO data field from the card's PDOL (tag 9F38).
     *
     * <p>Generic on purpose. The spec fixes Kona's PDOL as
     * {@code 9F02 06 | 9F03 06 | 9F1A 02 | 5F2A 02 | 9A 03 | 9C 01 | 9F37 04}
     * and the CL sample adds {@code 9F66 04}, but a DOL is a request from the
     * card and the right answer is to read it rather than assume it. A terminal
     * that hardcodes the byte string works until the card is reissued with one
     * more tag in its PDOL, and then fails in a way nobody can see from the
     * outside.</p>
     *
     * <p>A tag the terminal has no value for is sent as the right number of
     * zero bytes, which is what EMV Book 3 section 5.4 requires - the card is
     * parsing by position, so the length must be right even when the value is
     * not known.</p>
     *
     * @param pdolHex tag 9F38 from the FCI, tag-length pairs with no values
     * @param values  what the terminal has, keyed by uppercase tag
     * @return the concatenated values, ready to wrap in tag 83 for GPO
     */
    public static String buildDol(String pdolHex, Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        String h = hexOnly(pdolHex);
        int p = 0;
        while (p + 2 <= h.length()) {
            int first = Integer.parseInt(h.substring(p, p + 2), 16);
            int tagStart = p;
            p += 2;
            if ((first & 0x1F) == 0x1F) {
                while (p + 2 <= h.length()
                        && (Integer.parseInt(h.substring(p, p + 2), 16) & 0x80) != 0) {
                    p += 2;
                }
                p += 2;
            }
            if (p + 2 > h.length()) {
                LogUtils.e(TAG + ": PDOL ends mid-tag at " + tagStart);
                break;
            }
            String tag = h.substring(tagStart, p);
            int len = Integer.parseInt(h.substring(p, p + 2), 16);
            p += 2;
            String v = values == null ? null : values.get(tag);
            out.append(fit(v, len));
        }
        return out.toString();
    }

    /** The GPO command data: tag 83, length, then the DOL values. */
    public static String gpoData(String dolValuesHex) {
        String v = hexOnly(dolValuesHex);
        return "83" + String.format(Locale.US, "%02X", v.length() / 2) + v;
    }

    /**
     * The values Kona's PDOL asks for, ready for {@link #buildDol}.
     *
     * <p>Spec 2.2: amount, cashback (always zero), country, currency, date,
     * type "00", an unpredictable number, and the TTQ.</p>
     */
    public static Map<String, String> pdolValues(long amountMinor, long cashbackMinor,
                                                 String countryCode, String currencyCode,
                                                 String yymmdd, String txnType,
                                                 String terminalRandom) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("9F02", amount12(amountMinor));
        v.put("9F03", amount12(cashbackMinor));
        v.put("9F1A", n(countryCode, 4));
        v.put("5F2A", n(currencyCode, 4));
        v.put("9A", n(yymmdd, 6));
        v.put("9C", n(txnType, 2));
        v.put("9F37", n(terminalRandom, 8));
        v.put("9F66", TTQ);
        return v;
    }

    // ---------------------------------------------------------------- APDUs

    /** SELECT the contactless directory. */
    public static String selectPpse() {
        return select(PPSE);
    }

    /** SELECT the Kona Money application. */
    public static String selectKona() {
        return select(AID);
    }

    private static String select(String dfName) {
        String n = hexOnly(dfName);
        return "00A40400" + String.format(Locale.US, "%02X", n.length() / 2) + n + "00";
    }

    /** GET PROCESSING OPTIONS with the PDOL values already wrapped in tag 83. */
    public static String getProcessingOptions(String gpoDataHex) {
        String v = hexOnly(gpoDataHex);
        return "80A80000" + String.format(Locale.US, "%02X", v.length() / 2) + v + "00";
    }

    /** READ RECORD, the way an AFL entry names one. */
    public static String readRecord(int sfi, int record) {
        return "00B2" + String.format(Locale.US, "%02X", record)
                + String.format(Locale.US, "%02X", (sfi << 3) | 0x04) + "00";
    }

    /** One AFL entry: which records of which file to read. */
    public static final class Afl {
        public int sfi, firstRecord, lastRecord, sdaRecords;
    }

    /**
     * Split tag 94 into its four-byte entries.
     *
     * <p>The spec's sample AFL is {@code 08010100}: SFI 1, records 1 to 1, none
     * of them signed. That single record is where the track-2 lives when the
     * card did not put it in the GPO response.</p>
     */
    public static java.util.List<Afl> parseAfl(String aflHex) {
        java.util.List<Afl> out = new java.util.ArrayList<>();
        String h = hexOnly(aflHex);
        for (int i = 0; i + 8 <= h.length(); i += 8) {
            Afl a = new Afl();
            a.sfi = Integer.parseInt(h.substring(i, i + 2), 16) >> 3;
            a.firstRecord = Integer.parseInt(h.substring(i + 2, i + 4), 16);
            a.lastRecord = Integer.parseInt(h.substring(i + 4, i + 6), 16);
            a.sdaRecords = Integer.parseInt(h.substring(i + 6, i + 8), 16);
            out.add(a);
        }
        return out;
    }

    /**
     * Pull the track-2 out of whatever the card answered with.
     *
     * <p>Spec 2.2: "Terminal will get Track-2 data in either GPO response or by
     * READ RECORD command. Terminal should check GPO response first for Track-2
     * data, if it is not available Terminal will get it at READ RECORD." Tag 57
     * either way; the GPO form is inside template 77, the record form inside
     * template 70, and {@link Emv55#parse} walks both.</p>
     *
     * @return the track-2, or "" when this response does not carry one
     */
    public static String track2From(String responseHex) {
        Map<String, String> t = Emv55.parse(responseHex);
        String v = t.get("57");
        if (v != null && !v.isEmpty()) {
            return v;
        }
        // Templates 77 and 70 wrap the tags rather than replacing them, so if
        // the flat walk missed it, look inside.
        for (String wrapper : new String[] {"77", "70", "A5", "6F", "BF0C", "61"}) {
            String inner = t.get(wrapper);
            if (inner != null && !inner.isEmpty()) {
                String found = track2From(inner);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    // -------------------------------------------------------------- balance

    /**
     * The prepaid balance out of DE54.
     *
     * <p>Spec 2.2 is explicit that this is not optional: "It is important to
     * manage the balance due to the nature of 'Kona Money' system, the balance
     * of prepaid card included in the response message is displayed on Terminal
     * screen and it is printed out at receipt." A prepaid cardholder has no
     * other way to know what is left.</p>
     *
     * <p>DE54 is 20 ASCII characters: account type (2), amount type (2),
     * currency (3), sign (1), amount (12). The spec's samples decode to 100000
     * and 38400 - {@code 0002410D000000100000} and
     * {@code 0002410D000000038400}.</p>
     *
     * @param de54Hex the field as received, hex of the ASCII characters
     * @return the balance in minor units, or -1 when the field is absent or
     *         malformed. -1 rather than 0, because a balance of zero is a real
     *         answer and "we do not know" must not print as "you have nothing"
     */
    public static long balanceMinor(String de54Hex) {
        String ascii = hexToAscii(de54Hex);
        if (ascii.length() < 20) {
            return -1;
        }
        // Take the last 20 characters: some hosts repeat the field for several
        // account types, and the spec's own samples carry one.
        String f = ascii.substring(ascii.length() - 20);
        String digits = f.substring(8);
        if (!digits.matches("\\d{12}")) {
            return -1;
        }
        try {
            long v = Long.parseLong(digits);
            return f.charAt(7) == 'D' ? v : v;   // sign is the ledger's, not ours
        } catch (Exception e) {
            return -1;
        }
    }

    /** The currency the balance is in, from the same field. "" when unknown. */
    public static String balanceCurrency(String de54Hex) {
        String ascii = hexToAscii(de54Hex);
        if (ascii.length() < 20) {
            return "";
        }
        return ascii.substring(ascii.length() - 20 + 4, ascii.length() - 20 + 7);
    }

    /**
     * The balance as it goes on the screen and the slip, e.g. "TK 384.00".
     *
     * @return "" when the host sent no balance, so the caller prints no line
     *         rather than a line that says nothing
     */
    public static String balanceText(Context ctx, String de54Hex) {
        long minor = balanceMinor(de54Hex);
        if (minor < 0) {
            return "";
        }
        String symbol = TerminalConfig.get(ctx).currencySymbol();
        return symbol + " " + String.format(Locale.US, "%d.%02d", minor / 100, minor % 100);
    }

    // -------------------------------------------------------- AID parameters

    /**
     * The Kona AID as the terminal's kernel wants it loaded.
     *
     * <p>Same shape as every other entry in {@code PayActivity}'s parameter
     * blob: {@code 9F06} the AID, {@code DF01} the application selection
     * indicator, then the risk-management limits. Two things are deliberate
     * here and both come from the card being prepaid and online-only:</p>
     *
     * <ul>
     *   <li>{@code 9F1B} floor limit and {@code DF19} contactless floor limit
     *       are zero, so nothing is ever approved offline;</li>
     *   <li>{@code DF13} terminal action code - online is all bits set, so any
     *       condition at all sends the transaction to the host;</li>
     *   <li>{@code 9F7B} and {@code DF21} are the contactless <em>ceilings</em>
     *       and are set high. They were zero, which is not the same kind of
     *       limit at all: a reader contactless transaction limit of zero means
     *       every amount is above it, so the kernel refuses the tap outright.
     *       {@code 9F09} is the application version the card answers with,
     *       {@code 0001}, taken off a working transaction on the reference
     *       terminal - it was {@code 0020}, which is the Visa/Mastercard
     *       value.</li>
     * </ul>
     */
    public static String aidParamContact() {
        return "9F060AA0000002514025260010"
                + "DF010100"
                + "DF13050010000000"
                + "DF1205fc60acf800"
                + "DF1105fc60242800"
                + "DF14039F3704"
                + "DF150400000000"
                + "DF160100"
                + "DF170100"
                + "9F1B0400000000"
                + "DF180101"
                + "9F08020020"
                + "9F7B06000000100000"
                + "DF1906000000100000"
                + "DF2006000999999999"
                + "DF2106000000100000";
    }

    /** Contactless parameter string for NexusPay / KonaMoney. */
    public static String aidParamContactless() {
        return aidParamContact() + "9F6604" + TTQ;
    }

    // --------------------------------------------------------------- helpers

    /** True when this PAN is a Kona Money / Nexus card. */
    public static boolean isKonaPan(Context ctx, String pan) {
        return "NEXUSPAY".equalsIgnoreCase(
                com.hjq.demo.utils.CardRangeTable.nameFor(ctx, pan));
    }

    private static String card(Map<String, String> tags, String tag) {
        if (tags == null) {
            return "";
        }
        String v = tags.get(tag);
        return v == null ? "" : hexOnly(v);
    }

    /** Tag-length-value, skipping nothing: an empty value becomes length 00. */
    private static String tlv(Map<String, String> tags) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : tags.entrySet()) {
            String v = hexOnly(e.getValue());
            if (v.length() % 2 != 0) {
                v = v + "0";
            }
            b.append(e.getKey())
                    .append(String.format(Locale.US, "%02X", v.length() / 2))
                    .append(v);
        }
        return b.toString();
    }

    /** 12-digit BCD amount, EMV tags 9F02 and 9F03. */
    public static String amount12(long minor) {
        return String.format(Locale.US, "%012d", Math.max(0, minor));
    }

    /** A numeric field padded left to a fixed width of hex characters. */
    private static String n(String v, int hexChars) {
        String d = v == null ? "" : v.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        if (d.length() >= hexChars) {
            return d.substring(d.length() - hexChars);
        }
        StringBuilder b = new StringBuilder(d);
        while (b.length() < hexChars) {
            b.insert(0, '0');
        }
        return b.toString();
    }

    /** A DOL value cut or zero-padded to exactly the length the card asked for. */
    private static String fit(String v, int lenBytes) {
        int chars = lenBytes * 2;
        String h = hexOnly(v);
        if (h.length() == chars) {
            return h;
        }
        if (h.length() > chars) {
            return h.substring(0, chars);
        }
        StringBuilder b = new StringBuilder(h);
        while (b.length() < chars) {
            b.append('0');
        }
        return b.toString();
    }

    private static String hexOnly(String s) {
        return s == null ? "" : s.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
    }

    private static String hexToAscii(String hex) {
        String h = hexOnly(hex);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i + 2 <= h.length(); i += 2) {
            b.append((char) Integer.parseInt(h.substring(i, i + 2), 16));
        }
        return b.toString();
    }
}

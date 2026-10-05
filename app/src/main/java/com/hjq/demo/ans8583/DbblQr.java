package com.hjq.demo.ans8583;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.TreeMap;

/** DBBL v0.3 merchant-presented dynamic QR and CHECK STATUS request. */
public final class DbblQr {
    private static final String GUID = "com.konasl.konapay.nexuspay";
    private DbblQr() { }

    /** EMVCo tag 59 is limited to 25 English characters; the receipt retains the full name. */
    public static String merchantLabel(String name) {
        String value = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        value = value.replaceAll("[^ -~]", "");
        return value.length() > 25 ? value.substring(0, 25).trim() : value;
    }

    /** Reuse the last town/city address line, omitting country and postal-code suffixes. */
    public static String merchantCity(String... address) {
        if (address != null) {
            for (int i = address.length - 1; i >= 0; i--) {
                if (address[i] == null) continue;
                String[] parts = address[i].split(",");
                for (int j = parts.length - 1; j >= 0; j--) {
                    String value = parts[j].trim().replaceFirst("(?i)\\s*[-,]?\\s*BANGLADESH\\s*$", "")
                            .replaceFirst("\\s*[- ]?\\s*[0-9]{4}\\s*$", "").trim();
                    if (value.isEmpty() || value.equalsIgnoreCase("BD")) continue;
                    // A street, phone number or postcode by itself is not a city.
                    if (!value.matches("[A-Za-z][A-Za-z .'-]*")) continue;
                    return value.length() > 15 ? value.substring(0, 15).trim() : value;
                }
            }
        }
        return "";
    }

    public static void validateSettings(String nii, String tpdu, String mcc, String city, String merchant) {
        require(nii != null && nii.matches("[0-9]{3}"), "Enter the bank-assigned QR NII (3 digits)");
        require(tpdu != null && tpdu.matches("60[0-9A-Fa-f]{8}"), "Enter the QR TPDU (10 hex characters starting 60)");
        require(mcc != null && mcc.matches("[0-9]{4}"), "Enter the bank-assigned MCC (4 digits)");
        text(city, 15, "City from terminal address"); text(merchant, 25, "Terminal merchant name");
    }

    public static String payload(String mid, String tid, String mcc, String merchant, String city,
            String nii, long amount, String invoice, String stan, String reference) {
        return payload(mid, tid, mcc, merchant, city, nii, amount, invoice, stan, reference, null);
    }

    public static String payload(String mid, String tid, String mcc, String merchant, String city,
            String nii, long amount, String invoice, String stan, String reference, String sid) {
        require(mid.matches("[0-9A-Za-z]{15}"), "Merchant ID must contain 15 characters");
        require(tid.matches("[0-9A-Za-z]{8}"), "Terminal ID must contain 8 characters");
        require(mcc != null && nii != null && mcc.matches("[0-9]{4}") && nii.matches("[0-9]{3}"), "Invalid MCC or QR NII");
        require(invoice.matches("[0-9]{6}") && stan.matches("[0-9]{6}"), "Invalid invoice or STAN");
        require(amount > 0 && amount <= 999999999999L, "Enter a positive amount of up to 12 digits in minor units");
        text(merchant, 25, "QR merchant name"); text(city, 15, "QR merchant city");
        text(reference, 25, "QR reference");
        String tag62 = tlv("01", invoice);
        if (sid != null && !sid.trim().isEmpty()) {
            String s = sid.trim();
            text(s, 25, "Store label (optional)");
            tag62 += tlv("03", s);
        }
        tag62 += tlv("05", reference) + tlv("07", tid) + tlv("08", "00");
        String data = tlv("00", "01") + tlv("01", "12")
                + tlv("44", tlv("00", GUID) + tlv("01", mid))
                + tlv("52", mcc) + tlv("53", "050") + tlv("54", TipAmounts.major(amount))
                + tlv("58", "BD") + tlv("59", merchant) + tlv("60", city)
                + tlv("62", tag62)
                + tlv("88", tlv("00", GUID) + tlv("01", stan) + tlv("02", reference) + tlv("03", nii))
                + "6304";
        require(data.length() + 4 <= 512, "QR payload is too long");
        return data + crc(data);
    }

    public static String crc(String data) {
        int crc = 0xffff;
        for (byte value : data.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (value & 255) << 8;
            for (int bit = 0; bit < 8; bit++) crc = ((crc << 1) ^ ((crc & 0x8000) != 0 ? 0x1021 : 0)) & 0xffff;
        }
        return String.format(Locale.US, "%04X", crc);
    }

    static String tlv(String tag, String value) {
        require(value.length() <= 99, "QR field too long");
        return tag + String.format(Locale.US, "%02d", value.length()) + value;
    }
    private static void text(String s, int max, String name) {
        require(s != null && s.length() > 0 && s.length() <= max && s.matches("[ -~]+"),
                name + " must be 1-" + max + " printable English characters");
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    public static TreeMap<Integer, String> statusFields(Session session, String tid, String mid, String nii) {
        TxnRecord r = session.record;
        TreeMap<Integer, String> de = new TreeMap<>();
        de.put(3, "000000"); de.put(4, TipAmounts.field(r.amount())); de.put(11, r.stan);
        de.put(22, "840"); de.put(24, nii); de.put(25, "00");
        de.put(41, tid); de.put(42, mid); de.put(62, Iso8583.ascii(r.invoice));
        // The supplied CHECK STATUS capture has ASCII QR followed directly by the reference.
        de.put(63, Iso8583.ascii("QR" + session.reference));
        return de;
    }

    public static final class Session {
        public final TxnRecord record;
        public final String reference, payload, route;
        public Session(TxnRecord record, String reference, String payload, String route) {
            this.record = record; this.reference = reference; this.payload = payload; this.route = route;
        }
        public String serialize() { return record.serialize() + "\n" + reference + "\n" + payload + "\n" + route; }
        public static Session parse(String raw) {
            if (raw == null || raw.isEmpty()) return null;
            String[] p = raw.split("\n", -1);
            if (p.length != 4 || TxnRecord.parse(p[0]) == null) throw new IllegalStateException("Saved QR requires recovery");
            return new Session(TxnRecord.parse(p[0]), p[1], p[2], p[3]);
        }
    }
}

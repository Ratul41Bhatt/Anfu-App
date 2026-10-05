package com.hjq.demo.ans8583;

import java.util.Locale;

/**
 * One transaction as the terminal remembers it.
 *
 * <p>Serves three purposes:</p>
 * <ul>
 *   <li>the batch entry a settlement's reconciliation totals are built from
 *       (spec 6.28.1),</li>
 *   <li>the source of a reversal, which must repeat the original processing
 *       code, amount, STAN, track and entry mode (spec 7.35),</li>
 *   <li>the receipt / journal record.</li>
 * </ul>
 *
 * <p>Persisted as a single pipe-delimited line so the whole batch fits in one
 * SharedPreferences string. Fields are never allowed to contain '|'.</p>
 */
public final class TxnRecord {

    /** Field separator; stripped from any value on the way in. */
    private static final String SEP = "|";
    private static final int FIELD_COUNT = 21;
    // Host correlation and route snapshots for subsequent pre-authorisation cancellation.
    public String hostTime = "", hostDate = "";
    public String requestNii = "", requestTid = "", requestMid = "", requestTpdu = "";

    public String type = "SALE";        // SALE, REFUND, VOID, CASH_WITHDRAW, ...
    public String mti = "0200";
    public String proc = "000000";      // DE3 as sent
    public String amountMinor = "0";    // DE4, minor units
    public String stan = "";            // DE11
    public String time = "";            // DE12 hhmmss
    public String date = "";            // DE13 MMDD
    public String pan = "";             // DE2 (may be empty when a track was sent)
    public String track2 = "";          // DE35 image, '=' separator retained
    public String expiry = "";          // DE14 YYMM
    public String entryMode = "";       // DE22 as sent
    public String invoice = "";         // DE62 invoice / ECR reference
    public String rrn = "";             // DE37 from the response
    public String authCode = "";        // DE38 from the response
    public String iccHex = "";          // DE55 request data, needed for a reversal
    public boolean approved;
    /** True when a PIN block was present - classifies the txn as debit for totals. */
    public boolean pinUsed;
    /** Set when a later VOID cancelled this entry (spec 6.28.1 rule 9). */
    public boolean voided;
    /** EMI term in months, 0 for an ordinary sale. */
    public int tenure;
    /** Monthly instalment in minor units, for the slip and the EMI report. */
    public String installmentMinor = "0";
    /** EMI product code (DE48 tag E01), empty for an ordinary sale. */
    public String emiProduct = "";

    // ------------------------------------------------------ receipt snapshot
    //
    // What the slip printed, kept so a reprint can be the same document rather
    // than a summary of it. Most of it could in principle be re-derived - the
    // cryptogram and TVR are inside iccHex, the scheme name is a card-range
    // lookup - but three things cannot: the cardholder name (5F20) and TSI
    // (9B) are read from the card and never travel in DE55, and the CVM text
    // is the kernel's verdict, not a stored tag. Re-deriving the rest and
    // storing only those would leave a reprint that mostly matches, which is
    // the failure this block exists to end.
    //
    // All of it is appended after the original 21 fields, so a batch written
    // by an older build still parses - the tail simply comes back empty.

    /** Cardholder name, EMV tag 5F20. */
    public String cardholder = "";
    /** Transaction Status Information, EMV tag 9B. */
    public String tsi = "";
    /** How the cardholder was verified, as the slip words it. */
    public String cvm = "";
    /** Application label, the name the card calls itself. */
    public String appLabel = "";
    /** Scheme name as printed - VISA, NEXUSPAY, TAKAPAY. */
    public String issuer = "";
    /** DE39 as received. */
    public String respCode = "";
    /** True when the card is the acquirer's own - see CardRangeTable.isOnUs. */
    public boolean onUs;
    /**
     * Remaining prepaid balance as printed, e.g. "TK 384.00". Empty when the
     * card is not prepaid or the host sent no DE54 - the Kona spec requires
     * the balance on the slip, and an empty string is how the slip knows to
     * leave the line out rather than print a zero nobody vouched for.
     */
    public String balance = "";

    /**
     * DE39 of the reversal - WHY this transaction is being reversed.
     *
     * <p>Copied from the PAX reference, which sends its {@code dupReason} as
     * field 39 of the 0400: "98" no response received, "A0" the response failed
     * its MAC, "06" a previous reversal attempt was not acknowledged. Our 0400
     * used to carry no DE39 at all.</p>
     */
    public String dupReason = "";
    /** True when the request actually reached the host. */
    public boolean hostOnline;
    /** Tag 91 was present in the response DE55. */
    public boolean issuerAuth;
    /** An issuer script (71/72) came back and was run. */
    public boolean scriptRun;

    // ---- the transaction a void cancelled, for the void slip's own block
    public String origInvoice = "";
    public String origRrn = "";
    public String origAuth = "";
    public String origDateTime = "";

    public String baseAmountMinor = "";
    public String tipAmountMinor = "0";
    public String originalAmountMinor = "";
    public boolean tipAdjusted;
    /** Final chip data for advice/reprint; original iccHex stays intact for reversal. */
    public String completedIccHex = "";
    public long tip() { try { return Long.parseLong(tipAmountMinor); } catch (Exception e) { return 0; } }
    public long baseAmount() { try { return Long.parseLong(baseAmountMinor); } catch (Exception e) { return amount() - tip(); } }

    public TxnRecord() { }

    /**
     * True when this record is itself a void advice rather than a financial
     * sale. Voids are kept in the batch so the settlement slip can show a VOID
     * line, but they must never be counted again in the DE63 totals - the void
     * already removed the original's amount and count (spec 6.28.1 rule 9).
     */
    /** True when this was sold on instalments. */
    public boolean isEmi() {
        return tenure > 0 || (type != null && type.startsWith("EMI"));
    }

    /** Monthly instalment in minor units. */
    public long installment() {
        try {
            return Long.parseLong(installmentMinor.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    public boolean isVoid() {
        String t = type == null ? "" : type;
        return t.equals("VOID") || t.endsWith("_VOID") || t.startsWith("VOID_");
    }

    /** True when this moves funds from the merchant back to the cardholder. */
    public boolean isRefund() {
        String t = type == null ? "" : type;
        return t.startsWith("REFUND") || t.startsWith("OFFLINE_REFUND");
    }

    /** Amount in minor units as a long, 0 on anything unparseable. */
    public long amount() {
        try {
            return Long.parseLong(amountMinor.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    // ------------------------------------------------------- serialisation

    /**
     * The 36 fields, joined with {@code |}.
     *
     * <p>Built as an array and joined rather than appended one at a time.
     * Appending with "add a separator if the buffer is not empty" is correct
     * only while the first field is never blank: an empty leading field
     * swallows its own separator and shifts every later field by one on the
     * way back in, so an amount is read as a STAN and nothing announces it.
     * A join cannot do that.</p>
     */
    public String serialize() {
        String[] f = {
                type, mti, proc, amountMinor, stan, time, date, pan, track2, expiry,
                entryMode, invoice, rrn, authCode, iccHex,
                approved ? "1" : "0", pinUsed ? "1" : "0", voided ? "1" : "0",
                String.valueOf(tenure), installmentMinor, emiProduct,
                cardholder, tsi, cvm, appLabel, issuer, respCode,
                onUs ? "1" : "0", hostOnline ? "1" : "0",
                issuerAuth ? "1" : "0", scriptRun ? "1" : "0",
                origInvoice, origRrn, origAuth, origDateTime, balance,
                dupReason, baseAmountMinor, tipAmountMinor, originalAmountMinor, tipAdjusted ? "1" : "0", completedIccHex,
                hostTime, hostDate, requestNii, requestTid, requestMid, requestTpdu,
        };
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < f.length; i++) {
            if (i > 0) {
                b.append(SEP);
            }
            b.append(f[i] == null ? "" : f[i].replace(SEP, " "));
        }
        return b.toString();
    }

    public static TxnRecord parse(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] p = line.split("\\|", -1);
        if (p.length < FIELD_COUNT) {
            return null;
        }
        TxnRecord r = new TxnRecord();
        int i = 0;
        r.type = p[i++];
        r.mti = p[i++];
        r.proc = p[i++];
        r.amountMinor = p[i++];
        r.stan = p[i++];
        r.time = p[i++];
        r.date = p[i++];
        r.pan = p[i++];
        r.track2 = p[i++];
        r.expiry = p[i++];
        r.entryMode = p[i++];
        r.invoice = p[i++];
        r.rrn = p[i++];
        r.authCode = p[i++];
        r.iccHex = p[i++];
        r.approved = "1".equals(p[i++]);
        r.pinUsed = "1".equals(p[i++]);
        r.voided = "1".equals(p[i++]);
        if (p.length > i) {
            try {
                r.tenure = Integer.parseInt(p[i]);
            } catch (Exception e) {
                r.tenure = 0;
            }
            i++;
        }
        if (p.length > i) {
            r.installmentMinor = p[i++];
        }
        if (p.length > i) {
            r.emiProduct = p[i++];
        }
        // Receipt snapshot. Every one of these is optional: a batch written
        // before the snapshot existed simply runs out of fields here, and the
        // reprint falls back to what it can re-derive.
        r.cardholder = at(p, i++);
        r.tsi = at(p, i++);
        r.cvm = at(p, i++);
        r.appLabel = at(p, i++);
        r.issuer = at(p, i++);
        r.respCode = at(p, i++);
        r.onUs = "1".equals(at(p, i++));
        r.hostOnline = "1".equals(at(p, i++));
        r.issuerAuth = "1".equals(at(p, i++));
        r.scriptRun = "1".equals(at(p, i++));
        r.origInvoice = at(p, i++);
        r.origRrn = at(p, i++);
        r.origAuth = at(p, i++);
        r.origDateTime = at(p, i++);
        r.balance = at(p, i++);
        r.dupReason = at(p, i++);
        r.baseAmountMinor = at(p, i++);
        r.tipAmountMinor = at(p, i++);
        r.originalAmountMinor = at(p, i++);
        r.tipAdjusted = "1".equals(at(p, i++));
        r.completedIccHex = at(p, i++);
        r.hostTime = at(p, i++); r.hostDate = at(p, i++);
        r.requestNii = at(p, i++); r.requestTid = at(p, i++);
        r.requestMid = at(p, i++); r.requestTpdu = at(p, i);
        return r;
    }

    /** A field, or "" when this line was written by a build that lacked it. */
    private static String at(String[] p, int i) {
        return i >= 0 && i < p.length ? p[i] : "";
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "%s %s proc=%s amt=%s stan=%s inv=%s rrn=%s auth=%s %s%s",
                type, mti, proc, amountMinor, stan, invoice, rrn, authCode,
                approved ? "APPROVED" : "DECLINED", voided ? " VOIDED" : "");
    }
}

package com.hjq.demo.ans8583;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * What the reports menu needs but the open batch does not hold.
 *
 * <p>{@link BatchStore} keeps only approved transactions, and it is cleared the
 * moment a settlement is accepted. Two of the PAX reports need more than
 * that:</p>
 *
 * <ul>
 *   <li><b>Last Settlement</b> - the totals of the batch that was just closed,
 *       which have to be snapshotted before the journal is wiped, otherwise
 *       there is nothing left to reprint.</li>
 *   <li><b>Error TRXN Report</b> - the transactions that did <em>not</em>
 *       succeed. These never entered the batch at all, so without recording
 *       them here a declined sale leaves no trace beyond its slip.</li>
 * </ul>
 */
public final class ReportStore {

    private static final String TAG = "ReportStore";
    private static final String PREF = "report_store";
    private static final String K_LAST_SETTLE = "last_settlement";
    private static final String K_FAILED = "failed_txns";
    private static final String LINE_SEP = "\n";
    private static final String FIELD_SEP = "|";

    /** Enough history to investigate a session without unbounded growth. */
    private static final int MAX_FAILED = 100;

    private final SharedPreferences sp;
    private static ReportStore instance;

    private ReportStore(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized ReportStore get(Context c) {
        if (instance == null) {
            instance = new ReportStore(c);
        }
        return instance;
    }

    // ------------------------------------------------------ last settlement

    /** The totals of a closed batch, kept so they can be reprinted. */
    public static final class SettleSnapshot {
        public String batchNo = "";
        public String dateTime = "";
        public int saleCount, voidCount, refundCount;
        public long saleAmt, voidAmt, refundAmt;
        public String rrn = "";
        public String stan = "";

        public int netCount() {
            return saleCount + refundCount;
        }

        public long netAmt() {
            return saleAmt - refundAmt;
        }

        String serialize() {
            return join(batchNo, dateTime, String.valueOf(saleCount), String.valueOf(saleAmt),
                    String.valueOf(voidCount), String.valueOf(voidAmt),
                    String.valueOf(refundCount), String.valueOf(refundAmt), rrn, stan);
        }

        static SettleSnapshot parse(String line) {
            if (line == null || line.isEmpty()) {
                return null;
            }
            String[] p = line.split("\\|", -1);
            if (p.length < 10) {
                return null;
            }
            SettleSnapshot s = new SettleSnapshot();
            s.batchNo = p[0];
            s.dateTime = p[1];
            s.saleCount = num(p[2]);
            s.saleAmt = lnum(p[3]);
            s.voidCount = num(p[4]);
            s.voidAmt = lnum(p[5]);
            s.refundCount = num(p[6]);
            s.refundAmt = lnum(p[7]);
            s.rrn = p[8];
            s.stan = p[9];
            return s;
        }
    }

    /** Called from {@code closeBatch}, before the journal is cleared. */
    public synchronized void saveSettlement(SettleSnapshot s) {
        if (s == null) {
            return;
        }
        sp.edit().putString(K_LAST_SETTLE, s.serialize()).apply();
        Log.i(TAG, "last settlement stored: batch " + s.batchNo
                + " " + s.netCount() + " txn(s)");
    }

    public SettleSnapshot lastSettlement() {
        return SettleSnapshot.parse(sp.getString(K_LAST_SETTLE, ""));
    }

    // ----------------------------------------------------- failed txns

    /** A transaction that did not succeed, for the error report. */
    public static final class FailedTxn {
        public String dateTime = "";
        public String type = "";
        public String pan = "";
        public String amountMinor = "0";
        public String stan = "";
        public String responseCode = "";
        public String reason = "";
        /**
         * The invoice the transaction would have carried, and how the card was
         * read.
         *
         * <p>Added so the Error Transaction Report can be laid out like the
         * Detail Report - same columns, same block. Without them a failed
         * transaction could not be looked up beside the successful ones, which
         * is exactly what someone reads an error report to do.</p>
         */
        public String invoice = "";
        public String entryMode = "";
        public String expiry = "";

        public long amount() {
            return lnum(amountMinor);
        }

        String serialize() {
            return join(dateTime, type, pan, amountMinor, stan, responseCode, reason,
                    invoice, entryMode, expiry);
        }

        static FailedTxn parse(String line) {
            if (line == null || line.isEmpty()) {
                return null;
            }
            String[] p = line.split("\\|", -1);
            if (p.length < 7) {
                return null;
            }
            FailedTxn f = new FailedTxn();
            f.dateTime = p[0];
            f.type = p[1];
            f.pan = p[2];
            f.amountMinor = p[3];
            f.stan = p[4];
            f.responseCode = p[5];
            f.reason = p[6];
            // Optional tail: entries written before the Error Transaction
            // Report needed them simply run out of fields here.
            f.invoice = p.length > 7 ? p[7] : "";
            f.entryMode = p.length > 8 ? p[8] : "";
            f.expiry = p.length > 9 ? p[9] : "";
            return f;
        }
    }

    public synchronized void addFailed(FailedTxn f) {
        if (f == null) {
            return;
        }
        List<FailedTxn> all = failed();
        all.add(f);
        while (all.size() > MAX_FAILED) {
            all.remove(0);
        }
        StringBuilder b = new StringBuilder();
        for (FailedTxn x : all) {
            if (b.length() > 0) {
                b.append(LINE_SEP);
            }
            b.append(x.serialize());
        }
        sp.edit().putString(K_FAILED, b.toString()).apply();
        Log.i(TAG, "failed txn recorded: " + f.type + " " + f.responseCode + " " + f.reason);
    }

    public List<FailedTxn> failed() {
        List<FailedTxn> out = new ArrayList<>();
        String raw = sp.getString(K_FAILED, "");
        if (raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split(LINE_SEP)) {
            FailedTxn f = FailedTxn.parse(line);
            if (f != null) {
                out.add(f);
            }
        }
        return out;
    }

    public synchronized void clearFailed() {
        sp.edit().remove(K_FAILED).commit();
        Log.i(TAG, "failed transaction list cleared");
    }

    // ---------------------------------------------------------------- utils

    private static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (b.length() > 0) {
                b.append(FIELD_SEP);
            }
            b.append(p == null ? "" : p.replace(FIELD_SEP, " "));
        }
        return b.toString();
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static long lnum(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}

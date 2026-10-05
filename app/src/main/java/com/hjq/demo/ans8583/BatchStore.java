package com.hjq.demo.ans8583;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The open batch, plus the single pending reversal slot.
 *
 * <p>Approved online financial transactions are appended here so that the
 * settlement request can carry real reconciliation totals in DE63
 * (spec 6.28.1), and so a VOID can find and cancel the original entry.</p>
 *
 * <p>The reversal slot holds the transaction that was sent but never got a
 * valid response. Spec 7.35 requires the terminal to keep sending the reversal
 * "persistently until a valid response to the reversal is received", so the
 * slot survives a restart and is retried before the next transaction.</p>
 */
public final class BatchStore {

    private static final String TAG = "BatchStore";
    private static final String PREF = "batch_store";
    private static final String K_TXNS = "txns";
    private static final String K_REVERSAL = "pending_reversal";
    private static final String K_REV_TRIES = "pending_reversal_tries";
    private static final String K_TIPS = "pending_tip_adjustments";
    private static final String K_TIP_ROUTE = "tip_adjustment_route";
    private static final String K_TIP_HISTORY = "tip_adjustment_history";
    private static final String LINE_SEP = "\n";

    /** Give up auto-retrying after this many attempts and surface it to the operator. */
    public static final int MAX_REVERSAL_TRIES = 5;

    private final SharedPreferences sp;
    private volatile boolean tipStorageFault;
    private static BatchStore instance;

    private BatchStore(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized BatchStore get(Context c) {
        if (instance == null) {
            instance = new BatchStore(c);
        }
        return instance;
    }

    // ----------------------------------------------------------- batch entries

    public synchronized void add(TxnRecord r) {
        if (r == null) {
            return;
        }
        String all = sp.getString(K_TXNS, "");
        all = all.isEmpty() ? r.serialize() : all + LINE_SEP + r.serialize();
        sp.edit().putString(K_TXNS, all).apply();
        Log.i(TAG, "batch add: " + r);
    }

    /** Commit the chip-approved record and remove its recovery slot together. */
    public synchronized void completeApproved(TxnRecord record) {
        TxnRecord pending = pendingReversal();
        if (record == null || pending == null
                || !record.invoice.equals(pending.invoice) || !record.stan.equals(pending.stan)) {
            throw new IllegalStateException("Transaction recovery record changed before chip completion");
        }
        String all = sp.getString(K_TXNS, "");
        all = all.isEmpty() ? record.serialize() : all + LINE_SEP + record.serialize();
        if (!sp.edit().putString(K_TXNS, all).remove(K_REVERSAL).remove(K_REV_TRIES).commit()) {
            throw new IllegalStateException("Could not save completed transaction");
        }
    }

    public synchronized List<TxnRecord> all() {
        List<TxnRecord> out = new ArrayList<>();
        String raw = sp.getString(K_TXNS, "");
        if (raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split(LINE_SEP)) {
            TxnRecord r = TxnRecord.parse(line);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    public int count() {
        return all().size();
    }

    /**
     * Replace the whole journal. Used by the selective clears in the admin
     * panel, which keep some entries and drop others.
     */
    public synchronized void replaceAll(List<TxnRecord> keep) {
        if (hasPendingTips()) throw new IllegalStateException("Upload pending Tip Adjust transactions before clearing the batch");
        StringBuilder b = new StringBuilder();
        for (TxnRecord r : keep) {
            if (b.length() > 0) {
                b.append(LINE_SEP);
            }
            b.append(r.serialize());
        }
        sp.edit().putString(K_TXNS, b.toString()).apply();
        Log.i(TAG, "batch replaced, " + keep.size() + " entr(ies) kept");
    }

    /**
     * Clear the batch after a successful settlement: every transaction and the
     * Tip Adjust history that belonged to it.
     */
    public synchronized void clear() {
        if (hasPendingTips()) throw new IllegalStateException("Tip Adjust upload is still pending");
        if (!sp.edit().remove(K_TXNS).remove(K_TIP_HISTORY).commit()) {
            Log.e(TAG, "batch clear could not be written to storage");
        }
        Log.i(TAG, "batch cleared");
    }

    /**
     * Mark the entry with this invoice number as voided. Per spec 6.28.1 rule 9
     * a void removes both the amount and the count from the totals.
     *
     * @return true when a matching entry was found
     */
    public synchronized boolean markVoided(String invoice) {
        if (invoice == null || invoice.isEmpty()) {
            return false;
        }
        List<TxnRecord> list = all();
        boolean hit = false;
        StringBuilder b = new StringBuilder();
        for (TxnRecord r : list) {
            if (!r.voided && invoice.equals(r.invoice)) {
                r.voided = true;
                hit = true;
            }
            if (b.length() > 0) {
                b.append(LINE_SEP);
            }
            b.append(r.serialize());
        }
        if (hit) {
            sp.edit().putString(K_TXNS, b.toString()).apply();
        }
        return hit;
    }

    /**
     * Write the receipt snapshot of {@code src} onto the stored entry with the
     * same invoice number.
     *
     * <p>The record is journalled before the request goes out, so it is in the
     * batch by the time the slip is composed - and the slip is where the
     * cardholder name, TSI and CVM verdict first exist. Without this the
     * batch would hold a transaction the reprint could only approximate.</p>
     *
     * <p>Only the snapshot fields are copied. Everything the settlement totals
     * are built from stays exactly as it was written, so a bug here can cost a
     * reprint some detail but cannot move money.</p>
     *
     * @return true when a matching entry was found
     */
    public synchronized boolean updateReceipt(TxnRecord src) {
        if (src == null || src.invoice == null || src.invoice.isEmpty()) {
            return false;
        }
        List<TxnRecord> list = all();
        boolean hit = false;
        StringBuilder b = new StringBuilder();
        for (TxnRecord r : list) {
            if (!hit && src.invoice.equals(r.invoice) && src.stan.equals(r.stan)) {
                r.cardholder = src.cardholder;
                r.tsi = src.tsi;
                r.cvm = src.cvm;
                r.appLabel = src.appLabel;
                r.issuer = src.issuer;
                r.respCode = src.respCode;
                r.onUs = src.onUs;
                r.hostOnline = src.hostOnline;
                r.issuerAuth = src.issuerAuth;
                r.scriptRun = src.scriptRun;
                r.origInvoice = src.origInvoice;
                r.origRrn = src.origRrn;
                r.origAuth = src.origAuth;
                r.origDateTime = src.origDateTime;
                hit = true;
            }
            if (b.length() > 0) {
                b.append(LINE_SEP);
            }
            b.append(r.serialize());
        }
        if (hit) {
            sp.edit().putString(K_TXNS, b.toString()).apply();
        }
        return hit;
    }

    /**
     * Drop an entry from the batch entirely.
     *
     * <p>Used when the host approved but the chip then declined at the second
     * Generate AC. The transaction is being reversed, so it must not sit in the
     * batch waiting to be settled - unlike a void, there is nothing to report,
     * it never happened.</p>
     *
     * @return true when an entry was removed
     */
    public synchronized boolean remove(String invoice) {
        if (invoice == null || invoice.isEmpty()) {
            return false;
        }
        List<TxnRecord> list = all();
        boolean hit = false;
        StringBuilder b = new StringBuilder();
        for (TxnRecord r : list) {
            if (!hit && invoice.equals(r.invoice)) {
                hit = true;
                continue;                       // drop it
            }
            if (b.length() > 0) {
                b.append(LINE_SEP);
            }
            b.append(r.serialize());
        }
        if (hit) {
            sp.edit().putString(K_TXNS, b.toString()).apply();
            Log.i(TAG, "batch entry " + invoice + " removed");
        }
        return hit;
    }

    /** Find an approved, un-voided entry by invoice number - used to build a VOID. */
    public TxnRecord findByInvoice(String invoice) {
        if (invoice == null || invoice.isEmpty()) {
            return null;
        }
        for (TxnRecord r : all()) {
            if (r.approved && !r.voided && invoice.equals(r.invoice)) {
                return r;
            }
        }
        return null;
    }

    public synchronized boolean hasPendingTips() { return !sp.getString(K_TIPS, "").isEmpty(); }

    public synchronized List<TxnRecord> pendingTips() {
        List<TxnRecord> rows = new ArrayList<>();
        String raw = sp.getString(K_TIPS, "");
        if (raw.isEmpty()) return rows;
        for (String line : raw.split(LINE_SEP)) {
            TxnRecord r = TxnRecord.parse(line);
            if (r == null || !"ADJUST".equals(r.type)) throw new IllegalStateException("Tip queue requires recovery");
            rows.add(r);
        }
        return rows;
    }

    public synchronized String tipRoute() { return sp.getString(K_TIP_ROUTE, ""); }
    public boolean tipStorageFault() { return tipStorageFault; }

    public synchronized DbblQr.Session qrSession() { return DbblQr.Session.parse(sp.getString("qr_session", "")); }
    public synchronized void saveQrSession(DbblQr.Session session) {
        if (tipStorageFault || !commitTipState(sp.edit().putString("qr_session", session.serialize())))
            throw new IllegalStateException("Could not save QR payment; check terminal storage");
    }
    public synchronized void completeQr(DbblQr.Session session) {
        DbblQr.Session old = findQrSession(session.reference);
        if (old == null) throw new IllegalStateException("QR payment reference changed");
        List<TxnRecord> rows = qrHistory();
        boolean found = false;
        for (TxnRecord row : rows) if (row.invoice.equals(session.record.invoice) && row.stan.equals(session.record.stan)) found = true;
        if (!found) rows.add(session.record);
        DbblQr.Session active = qrSession();
        SharedPreferences.Editor edit = sp.edit().putString("qr_confirmed", join(rows));
        if (active != null && active.reference.equals(session.reference)) edit.putString("qr_session", session.serialize());
        else edit.putString("qr_pending", pendingQrWithout(session.reference));
        if (tipStorageFault || !commitTipState(edit))
            throw new IllegalStateException("Payment confirmed by host but could not be saved; check storage and retry status");
    }
    /** QR uses a separate acquiring route; keep its journal out of card settlement. */
    public synchronized List<TxnRecord> qrHistory() {
        List<TxnRecord> rows = new ArrayList<>();
        String raw = sp.getString("qr_confirmed", "");
        for (String line : raw.split("\n")) { TxnRecord record = TxnRecord.parse(line); if (record != null) rows.add(record); }
        return rows;
    }
    public synchronized List<DbblQr.Session> pendingQrHistory() {
        List<DbblQr.Session> rows = new ArrayList<>();
        String raw = sp.getString("qr_pending", "");
        if (!raw.isEmpty()) for (String entry : raw.split("\n\n")) rows.add(DbblQr.Session.parse(entry));
        return rows;
    }
    public synchronized DbblQr.Session findQrSession(String reference) {
        DbblQr.Session active = qrSession();
        if (active != null && active.reference.equals(reference)) return active;
        for (DbblQr.Session entry : pendingQrHistory()) if (entry.reference.equals(reference)) return entry;
        return null;
    }
    private String pendingQrWithout(String reference) {
        StringBuilder rows = new StringBuilder();
        for (DbblQr.Session entry : pendingQrHistory()) {
            if (entry.reference.equals(reference)) continue;
            if (rows.length() > 0) rows.append("\n\n");
            rows.append(entry.serialize());
        }
        return rows.toString();
    }
    /** Clear the displayed session without losing a mobile payment's audit reference. */
    public synchronized void dismissQr(String reference) {
        DbblQr.Session active = qrSession();
        if (active == null || !active.reference.equals(reference)) return;
        SharedPreferences.Editor edit = sp.edit().remove("qr_session");
        if (!active.record.approved) {
            String pending = pendingQrWithout(reference);
            edit.putString("qr_pending", (pending.isEmpty() ? "" : pending + "\n\n") + active.serialize());
        }
        if (!commitTipState(edit)) throw new IllegalStateException("Could not close QR payment; check terminal storage");
    }
    public synchronized void finishQr() {
        DbblQr.Session active = qrSession();
        if (active != null) dismissQr(active.reference);
    }

    private boolean commitTipState(SharedPreferences.Editor next) {
        String[] keys = {K_TXNS, K_TIPS, K_TIP_ROUTE, K_TIP_HISTORY, "qr_session", "qr_confirmed", "qr_pending"};
        String[] before = new String[keys.length];
        for (int i = 0; i < keys.length; i++) before[i] = sp.getString(keys[i], null);
        if (next.commit()) return true;
        // Android updates its memory cache even if the disk commit fails.
        // Restore that cache and stop new business until storage is checked/restarted.
        tipStorageFault = true;
        SharedPreferences.Editor rollback = sp.edit();
        for (int i = 0; i < keys.length; i++) {
            if (before[i] == null) rollback.remove(keys[i]); else rollback.putString(keys[i], before[i]);
        }
        rollback.commit();
        return false;
    }

    /** Update the one sale and persist its advice in a single durable commit. */
    synchronized TxnRecord queueTip(String invoice, long tip, TerminalConfig tc, String route) {
        if (tipStorageFault) throw new IllegalStateException("Storage failure: check storage and restart before continuing");
        if (!tc.tipAdjustEnabled()) throw new IllegalArgumentException("Tip Adjust is disabled");
        if (hasPendingReversal()) throw new IllegalStateException("Complete reversal recovery first");
        if (hasPendingTips() && !route.equals(tipRoute())) throw new IllegalStateException("Restore the original host parameters first");
        List<TxnRecord> rows = all();
        TxnRecord sale = null;
        for (TxnRecord r : rows) if (invoice.equals(r.invoice) && r.approved && !r.voided && "SALE".equals(r.type)) { sale = r; break; }
        if (sale == null) throw new IllegalArgumentException("Approved sale not found in the open batch");
        if (sale.tipAdjusted) throw new IllegalArgumentException("This invoice already has a Tip Adjust");
        if (sale.tip() > 0) throw new IllegalArgumentException("Sale already has a tip; Tip Adjust is not allowed");
        if (sale.rrn.isEmpty() || sale.authCode.isEmpty()) throw new IllegalArgumentException("Sale is missing its host reference");
        if ((sale.entryMode.startsWith("03") || sale.entryMode.startsWith("84"))) throw new IllegalArgumentException("Tip Adjust is unavailable for QR sales");
        long total = TipAmounts.total(sale.baseAmount(), tip, tc.tipPercentage());
        TxnRecord advice = TxnRecord.parse(sale.serialize());
        if (!sale.completedIccHex.isEmpty()) advice.iccHex = sale.completedIccHex;
        advice.type = "ADJUST"; advice.mti = "0220"; advice.proc = "020000";
        advice.originalAmountMinor = sale.amountMinor;
        advice.baseAmountMinor = String.valueOf(sale.baseAmount());
        advice.tipAmountMinor = String.valueOf(tip); advice.amountMinor = String.valueOf(total);
        advice.stan = tc.nextStan(); advice.tipAdjusted = true; advice.hostOnline = false;
        // A stored advice needs PAN, expiry and original EMV data, never PIN or track data.
        if (advice.pan.isEmpty()) advice.pan = OnlineHost.panFromTrack2(advice.track2);
        if (advice.pan.isEmpty()) throw new IllegalArgumentException("Sale is missing its card reference");
        advice.track2 = "";
        sale.baseAmountMinor = advice.baseAmountMinor; sale.tipAmountMinor = advice.tipAmountMinor;
        sale.originalAmountMinor = advice.originalAmountMinor; sale.amountMinor = advice.amountMinor;
        sale.tipAdjusted = true;
        List<TxnRecord> queue = pendingTips(); queue.add(advice);
        String history = sp.getString(K_TIP_HISTORY, "");
        history = history.isEmpty() ? advice.serialize() : history + LINE_SEP + advice.serialize();
        if (!commitTipState(sp.edit().putString(K_TXNS, join(rows)).putString(K_TIPS, join(queue))
                .putString(K_TIP_HISTORY, history).putString(K_TIP_ROUTE, route)))
            throw new IllegalStateException("Could not save Tip Adjust; not approved");
        return advice;
    }

    synchronized void acknowledgeTip(TxnRecord advice) {
        List<TxnRecord> queue = pendingTips();
        if (queue.isEmpty() || !queue.get(0).stan.equals(advice.stan) || !queue.get(0).invoice.equals(advice.invoice))
            throw new IllegalStateException("Tip queue changed during upload");
        queue.remove(0);
        SharedPreferences.Editor editor = sp.edit().putString(K_TIPS, join(queue));
        List<TxnRecord> history = tipHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            TxnRecord r = history.get(i);
            if (r.invoice.equals(advice.invoice) && r.stan.equals(advice.stan) && !r.hostOnline) {
                r.hostOnline = true; break;
            }
        }
        editor.putString(K_TIP_HISTORY, join(history));
        if (queue.isEmpty()) editor.remove(K_TIP_ROUTE);
        if (!commitTipState(editor)) throw new IllegalStateException("Could not save Tip Adjust acknowledgement; check storage and restart");
    }

    public synchronized List<TxnRecord> tipHistory() {
        List<TxnRecord> rows = new ArrayList<>();
        for (String line : sp.getString(K_TIP_HISTORY, "").split(LINE_SEP)) {
            TxnRecord row = TxnRecord.parse(line);
            if (row != null) rows.add(row);
        }
        return rows;
    }

    private static String join(List<TxnRecord> rows) {
        StringBuilder b = new StringBuilder();
        for (TxnRecord row : rows) { if (b.length() > 0) b.append(LINE_SEP); b.append(row.serialize()); }
        return b.toString();
    }

    // ---------------------------------------------------------------- totals

    /**
     * The open batch reduced to counts and amounts.
     *
     * <p>Two views of the same batch: the four capture/debit buckets that go
     * to the host in DE63 (spec 6.28.1), and the SALE / VOID / REFUND rows the
     * settlement slip shows the merchant.</p>
     */
    public static final class Totals {

        // ---- slip view
        public int saleCount, refundCount, voidCount;
        public long saleAmt, refundAmt, voidAmt;
        /**
         * EMI sales, shown as their own row on the settlement slip.
         *
         * <p>They are also included in {@link #saleCount} / {@link #saleAmt},
         * because to the acquirer an EMI sale is a sale - PAX add the ZIP and
         * Want2Buy totals into the sale figures they send with the settlement
         * ({@code saleTotalAmt + zipTotalAmt + want2BuyTotalAmt}) while showing
         * them as separate lines on the slip. The slip subtracts to avoid
         * double counting.</p>
         */
        public int emiCount;
        public long emiAmt;

        // ---- DE63 view (spec 6.28.1)
        public int capSaleCount, capRefundCount, debSaleCount, debRefundCount;
        public long capSaleAmt, capRefundAmt, debSaleAmt, debRefundAmt;

        /** Ordinary sales, i.e. everything in saleCount that is not EMI. */
        public int plainSaleCount() {
            return saleCount - emiCount;
        }

        public long plainSaleAmt() {
            return saleAmt - emiAmt;
        }

        /** Number of entries the host is being asked to reconcile. */
        public int netCount() {
            return saleCount + refundCount;
        }

        /** Net value of the batch: sales less refunds, in minor units. */
        public long netAmt() {
            return saleAmt - refundAmt;
        }

        /**
         * True when there is nothing to settle. PAX refuses to settle a zero
         * batch rather than sending empty totals, and so do we.
         */
        public boolean isZero() {
            return saleCount == 0 && refundCount == 0 && voidCount == 0;
        }

        /**
         * DE63 reconciliation totals, ASCII, spec 6.28.1.
         *
         * <pre>
         *   Captured Sales Count   an 3      Sales Amount    an 12
         *   Refund Count           an 3      Refund Amount   an 12
         *   Debit Sales Count      an 3      Sales Amount    an 12
         *   Refund Count           an 3      Refund Amount   an 12
         * </pre>
         *
         * <p>That is the 60-byte mandatory core. The authorize and cash-advance
         * groups are compatibility fields (notes 12 and 13) that the T7 family
         * does not keep, so they are omitted.</p>
         */
        public String de63() {
            StringBuilder b = new StringBuilder(60);
            b.append(n3(capSaleCount)).append(n12(capSaleAmt));
            b.append(n3(capRefundCount)).append(n12(capRefundAmt));
            b.append(n3(debSaleCount)).append(n12(debSaleAmt));
            b.append(n3(debRefundCount)).append(n12(debRefundAmt));
            return b.toString();
        }
    }

    /**
     * Reduce the open batch to totals, applying the spec 6.28.1 rules:
     *
     * <ul>
     *   <li>rules 4-7: sales are the entries that move funds to the merchant,
     *       refunds the ones that move funds back to the cardholder,</li>
     *   <li>rule 9: a voided entry loses both its amount and its count, so it
     *       drops out of the host totals entirely - it only survives as the
     *       VOID line on the slip,</li>
     *   <li>rule 10: a zero-value entry is a void and is never counted,</li>
     *   <li>declined entries were never captured, so they never count.</li>
     * </ul>
     *
     * <p>The capture/debit split follows whether a PIN block was sent. Confirm
     * with DBBL whether they instead expect the split to follow the card
     * table's capture/debit issuer flags.</p>
     */
    public Totals totals() {
        Totals t = new Totals();
        for (TxnRecord r : all()) {
            if (!r.approved || r.amount() == 0) {
                continue;
            }
            if (r.isVoid()) {
                // The void advice itself: shown on the slip, never in DE63.
                t.voidCount++;
                t.voidAmt += r.amount();
                continue;
            }
            if (r.voided) {
                continue;               // rule 9 - cancelled by a later void
            }
            boolean debit = r.pinUsed;
            if (r.isRefund()) {
                t.refundCount++;
                t.refundAmt += r.amount();
                if (debit) {
                    t.debRefundCount++;
                    t.debRefundAmt += r.amount();
                } else {
                    t.capRefundCount++;
                    t.capRefundAmt += r.amount();
                }
            } else {
                t.saleCount++;
                t.saleAmt += r.amount();
                if (r.isEmi()) {
                    t.emiCount++;
                    t.emiAmt += r.amount();
                }
                if (debit) {
                    t.debSaleCount++;
                    t.debSaleAmt += r.amount();
                } else {
                    t.capSaleCount++;
                    t.capSaleAmt += r.amount();
                }
            }
        }
        return t;
    }

    /**
     * DE63 for the settlement request. Mandatory on the 0500 (spec 7.39) and
     * on the settlement trailer (spec 7.40).
     *
     * @return the field content as ASCII, ready for {@code Iso8583.ascii(...)}
     */
    public String totals63() {
        return totals().de63();
    }

    /** Human-readable version of the totals, for the operator prompt. */
    public String totalsSummary() {
        Totals t = totals();
        return String.format(Locale.US,
                "SALE    %3d   TK %s%nVOID    %3d   TK %s%nREFUND  %3d   TK %s%n"
                        + "-----------------------%nTOTAL   %3d   TK %s",
                t.saleCount, major(t.saleAmt),
                t.voidCount, major(t.voidAmt),
                t.refundCount, major(t.refundAmt),
                t.netCount(), major(t.netAmt()));
    }

    private static String n3(int v) {
        return String.format(Locale.US, "%03d", Math.max(0, Math.min(999, v)));
    }

    private static String n12(long v) {
        return String.format(Locale.US, "%012d", Math.max(0L, v));
    }

    private static String major(long minor) {
        return String.format(Locale.US, "%d.%02d", minor / 100, Math.abs(minor % 100));
    }

    // -------------------------------------------------------------- reversal

    /**
     * Remember a transaction that has been sent but not yet acknowledged.
     * Called immediately before the socket write, so a power cut mid-exchange
     * still leaves a reversal to send.
     */
    public synchronized void setPendingReversal(TxnRecord r) {
        if (r == null) {
            sp.edit().remove(K_REVERSAL).remove(K_REV_TRIES).apply();
            return;
        }
        if (!sp.edit().putString(K_REVERSAL, r.serialize()).putInt(K_REV_TRIES, 0).commit()) {
            throw new IllegalStateException("Could not save transaction recovery record");
        }
        Log.i(TAG, "pending reversal armed: " + r);
    }

    /** Clear the slot once a valid response arrived, or the reversal was accepted. */
    public synchronized void clearPendingReversal() {
        if (sp.contains(K_REVERSAL)) {
            Log.i(TAG, "pending reversal cleared");
        }
        sp.edit().remove(K_REVERSAL).remove(K_REV_TRIES).apply();
    }

    public TxnRecord pendingReversal() {
        return TxnRecord.parse(sp.getString(K_REVERSAL, ""));
    }

    public boolean hasPendingReversal() {
        return pendingReversal() != null;
    }

    public int reversalTries() {
        return sp.getInt(K_REV_TRIES, 0);
    }

    public synchronized int bumpReversalTries() {
        int n = reversalTries() + 1;
        sp.edit().putInt(K_REV_TRIES, n).apply();
        return n;
    }
}

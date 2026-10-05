package com.hjq.demo.ui.activity;

import android.graphics.Bitmap;
import android.os.RemoteException;
import android.view.View;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.gyf.immersionbar.ImmersionBar;
import com.hjq.demo.R;
import com.hjq.demo.ans8583.BatchStore;
import com.hjq.demo.ans8583.ReportStore;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.ans8583.TxnRecord;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.ui.view.KeyBoardView;
import com.hjq.demo.utils.CardMask;
import com.hjq.demo.utils.CardRangeTable;
import com.hjq.demo.utils.DetailReport;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.PrintUtil;
import com.hjq.demo.utils.Slip;
import com.hjq.demo.utils.TxnSlip;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reports, matching the PAX {@code BatchReportMenuActivity} item for item:
 *
 * <ul>
 *   <li><b>Last Transaction</b> - merchant or customer copy, as PAX's
 *       {@code PrintLastMenuActivity} splits it;</li>
 *   <li><b>Reprint</b> - any transaction in the open batch, by invoice;</li>
 *   <li><b>Detail Report</b> - every transaction in the batch;</li>
 *   <li><b>Card Issuer Report</b> - totals grouped by card scheme;</li>
 *   <li><b>Last Settlement</b> - the totals of the batch that was last closed;</li>
 *   <li><b>Error Transaction Report</b> - every transaction that did not
 *       succeed, in the Detail Report layout. They never entered the batch,
 *       so they appear on no other report and settlement does not count
 *       them.</li>
 * </ul>
 *
 * <p>PAX also lists a Preauth Report behind their pre-auth flag. This terminal
 * does not implement pre-auth, so an empty menu entry would be worse than no
 * entry - it is left out until pre-auth exists.</p>
 */
public final class ReportsActivity extends AppActivity implements View.OnClickListener {

    private static final int PAGE_MENU = 0;
    private static final int PAGE_COPY = 1;
    private static final int PAGE_REPRINT = 2;

    private static final int INVOICE_LEN = 6;

    private ViewFlipper flipper;
    private TextView status, invoiceView, invoiceHint, copySummary;
    private IPrinterBinderService printer;

    private final StringBuilder digits = new StringBuilder();

    @Override
    protected int getLayoutId() {
        return R.layout.activity_reports;
    }

    @Override
    protected void initView() {
        // Load the slip typeface while the screen is being built, not on the
        // printer thread with the paper already moving.
        Slip.useFont(getApplicationContext());

        // AppActivity runs an immersive, edge-to-edge status bar and only
        // pushes content down when the layout carries a TitleBar. This one has
        // none, so the first row sat underneath the clock and the battery.
        // Measured rather than a fixed dp, because the status bar is not the
        // same height on a device with a notch.
        View root = findViewById(R.id.report_root);
        if (root != null) {
            root.setPadding(root.getPaddingLeft(),
                    root.getPaddingTop() + ImmersionBar.getStatusBarHeight(this),
                    root.getPaddingRight(), root.getPaddingBottom());
        }

        flipper = findViewById(R.id.report_flipper);
        status = findViewById(R.id.rep_status);
        invoiceView = findViewById(R.id.rep_invoice);
        invoiceHint = findViewById(R.id.rep_invoice_hint);
        copySummary = findViewById(R.id.rep_copy_summary);

        bind(R.id.rep_qr, R.id.rep_last_txn, R.id.rep_reprint, R.id.rep_detail, R.id.rep_issuer,
                R.id.rep_last_settle, R.id.rep_error, R.id.rep_emi,
                R.id.rep_back_copy,
                R.id.rep_back_reprint, R.id.rep_merchant_copy, R.id.rep_customer_copy);

        KeyBoardView kb = findViewById(R.id.rep_keyboard);
        if (kb != null) {
            kb.setIKetBoardListener(new KeyBoardView.IKeyBoardListener() {
                @Override
                public void getNumber(String s) {
                    if (digits.length() < INVOICE_LEN) {
                        digits.append(s);
                    }
                    showInvoice();
                }

                @Override
                public void clear() {
                    digits.setLength(0);
                    showInvoice();
                }

                @Override
                public void cancel() {
                    if (digits.length() > 0) {
                        digits.deleteCharAt(digits.length() - 1);
                    }
                    showInvoice();
                }

                @Override
                public void confirm() {
                    reprintByInvoice();
                }
            });
        }
    }

    @Override
    protected void initData() {
        refreshCounts();
        show(PAGE_MENU);
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdk) {
        try {
            printer = IPrinterBinderService.Stub.asInterface(
                    sdk.getService(ServiceID.SERVICE_ID_PRINTER));
        } catch (Exception e) {
            say("Printer not available: " + e.getMessage());
        }
    }

    private void bind(int... ids) {
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) {
                v.setOnClickListener(this);
            }
        }
    }

    /**
     * Put the count on each row, so the merchant can see there is something to
     * print before pressing it.
     */
    private void refreshCounts() {
        List<TxnRecord> batch = BatchStore.get(this).all();
        setValue(R.id.rep_detail_value, DetailReport.totalCount(batch) + " transaction(s) in batch "
                + TerminalConfig.get(this).batchNo());

        TxnRecord last = lastTxn();
        setValue(R.id.rep_last_txn_value, last == null ? "Nothing to reprint"
                : last.type + "  INV " + last.invoice + "  " + cur() + " " + Slip.money(last.amount()));

        ReportStore.SettleSnapshot s = ReportStore.get(this).lastSettlement();
        setValue(R.id.rep_last_settle_value, s == null ? "No settlement yet"
                : "Batch " + s.batchNo + "  " + s.dateTime);

        int emi = 0;
        for (TxnRecord r : batch) {
            if (r.approved && r.isEmi() && !r.isVoid()) {
                emi++;
            }
        }
        setValue(R.id.rep_emi_value, emi == 0 ? "No EMI transactions"
                : emi + " EMI transaction(s) in batch");

        int errs = ReportStore.get(this).failed().size();
        setValue(R.id.rep_error_value, errs == 0 ? "No failed transactions"
                : errs + " failed transaction(s)");
    }

    private void setValue(int id, String text) {
        TextView v = findViewById(id);
        if (v != null) {
            v.setText(text);
        }
    }

    // ---------------------------------------------------------------- clicks

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.rep_qr) {
            startActivity(new android.content.Intent(this, QrReportsActivity.class));
        } else if (id == R.id.rep_last_txn) {
            if (lastTxn() == null) {
                say("No transaction to reprint");
                return;
            }
            showCopySummary();
            show(PAGE_COPY);
        } else if (id == R.id.rep_reprint) {
            digits.setLength(0);
            showInvoice();
            show(PAGE_REPRINT);
        } else if (id == R.id.rep_detail) {
            printDetailReport();
        } else if (id == R.id.rep_issuer) {
            printIssuerReport();
        } else if (id == R.id.rep_emi) {
            printEmiReport();
        } else if (id == R.id.rep_last_settle) {
            printLastSettlement();

        } else if (id == R.id.rep_error) {
            printErrorReport();
        } else if (id == R.id.rep_back_copy || id == R.id.rep_back_reprint) {
            show(PAGE_MENU);
        } else if (id == R.id.rep_merchant_copy) {
            reprint(lastTxn(), "MERCHANT COPY");
        } else if (id == R.id.rep_customer_copy) {
            reprint(lastTxn(), "CUSTOMER COPY");
        }
    }

    @Override
    public void onBackPressed() {
        if (flipper != null && flipper.getDisplayedChild() != PAGE_MENU) {
            show(PAGE_MENU);
            return;
        }
        super.onBackPressed();
    }

    private void show(int page) {
        if (flipper != null) {
            flipper.setDisplayedChild(page);
        }
        if (page == PAGE_MENU) {
            refreshCounts();
        }
    }

    // ------------------------------------------------------- last / reprint

    private TxnRecord lastTxn() {
        List<TxnRecord> all = BatchStore.get(this).all();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    private void showCopySummary() {
        TxnRecord r = lastTxn();
        if (copySummary == null || r == null) {
            return;
        }
        copySummary.setText("TYPE     " + r.type
                + "\nINVOICE  " + r.invoice
                + "\nCARD     " + masked(r)
                + "\nAMOUNT   " + cur() + " " + Slip.money(r.amount())
                + "\nAPPROVAL " + (r.authCode.isEmpty() ? "-" : r.authCode)
                + "\nRREF     " + (r.rrn.isEmpty() ? "-" : r.rrn));
    }

    private void showInvoice() {
        if (invoiceView == null) {
            return;
        }
        // Show exactly what will be searched for - the lookup left-pads with
        // zeros, so typing "5" finds invoice 000005.
        invoiceView.setText(digits.length() == 0 ? "------" : pad(digits.toString()));
        if (invoiceHint != null) {
            invoiceHint.setText(BatchStore.get(this).all().size()
                    + " transaction(s) in batch " + TerminalConfig.get(this).batchNo());
        }
    }

    private void reprintByInvoice() {
        if (digits.length() == 0) {
            say("Enter an invoice number");
            return;
        }
        String invoice = pad(digits.toString());
        TxnRecord found = null;
        for (TxnRecord r : BatchStore.get(this).all()) {
            if (invoice.equals(r.invoice)) {
                found = r;
                break;
            }
        }
        if (found == null) {
            say("Invoice " + invoice + " is not in the open batch");
            return;
        }
        reprint(found, "REPRINT");
    }

    /**
     * Reprint one transaction.
     *
     * <p>The same builder the original went through, with the reprint flag
     * set - which adds one line at the foot and changes nothing above it. This
     * used to compose its own, shorter slip, and every difference between the
     * two had to be found by someone holding the two pieces of paper next to
     * each other: no merchant address, the real expiry where the original
     * printed {@code **}{@code /}{@code **}, an APPROVED banner the original
     * had dropped, and no EMV block, signature line or cardholder agreement at
     * all.</p>
     */
    private void reprint(TxnRecord r, String copyLabel) {
        if (r == null) {
            say("Nothing to reprint");
            return;
        }
        List<Slip.Line> o = TxnSlip.body(this, TxnSlip.of(this, r), copyLabel, true);
        // The APP VERSION footer every other report carries. A reprint that
        // cannot say which build produced it is the one you most want it on.
        tail(o);
        printSlip(o, copyLabel + " printed for invoice " + r.invoice);
    }

    // ------------------------------------------------------------- reports

    /**
     * The transaction detail report, in the reference DBBL/NexusPay layout.
     *
     * <p>Five lines per transaction, each one a single fixed-width string
     * rather than a left/right pair. The renderer shrinks any line too wide to
     * fit, one line at a time, so a report built from pairs prints with every
     * row at a slightly different size and the columns stepping in and out -
     * which is exactly what made the old one unreadable.</p>
     */
    private void printDetailReport() {
        List<TxnRecord> batch = BatchStore.get(this).all();
        if (batch.isEmpty()) {
            say("No transactions in the batch");
            return;
        }
        List<Slip.Line> o = header("DETAIL REPORT");
        detailBody(o, batch);
        tail(o);
        printSlip(o, "Detail report printed - " + DetailReport.totalCount(batch) + " transaction(s)");
    }

    /** Legend, one block per transaction, total, end line - shared by both detail reports. */
    private void detailBody(List<Slip.Line> o, List<TxnRecord> rows) {
        for (String l : DetailReport.legendLines()) {
            o.add(Slip.text(l));
        }
        o.add(Slip.rule());
        for (TxnRecord r : rows) {
            for (String l : DetailReport.blockLines(this, r)) {
                o.add(Slip.text(l));
            }
            o.add(Slip.blank());
        }
        o.add(Slip.rule());
        o.add(new Slip.Line(DetailReport.row("TOTAL   " + DetailReport.count(DetailReport.totalCount(rows)),
                cur() + " " + Slip.money(DetailReport.net(rows))), "",
                Slip.SMALL, Slip.BOLD));
        o.add(Slip.rule());
        o.add(Slip.blank());
        o.add(Slip.centre(DetailReport.END, Slip.SMALL));
    }

    /**
     * Totals grouped by card scheme, the way PAX's
     * {@code ReceiptCardIssuerReport} does it. The grouping key is the card
     * range name, so TAKAPAY and NEXUSPAY appear as themselves.
     */
    private void printIssuerReport() {
        List<TxnRecord> batch = BatchStore.get(this).all();
        if (batch.isEmpty()) {
            say("No transactions in the batch");
            return;
        }
        Map<String, long[]> byIssuer = new LinkedHashMap<>();
        for (TxnRecord r : batch) {
            if (!r.approved || r.amount() == 0) {
                continue;
            }
            String name = CardRangeTable.nameFor(this, panOf(r));
            if (name == null || name.isEmpty()) {
                name = "OTHERS";
            }
            long[] cell = byIssuer.get(name);
            if (cell == null) {
                cell = new long[4];              // saleCount, saleAmt, voidCount, voidAmt
                byIssuer.put(name, cell);
            }
            if (r.isVoid() || r.voided) {
                cell[2]++;
                cell[3] += r.amount();
            } else {
                cell[0]++;
                cell[1] += r.amount();
            }
        }
        if (byIssuer.isEmpty()) {
            say("No approved transactions to report");
            return;
        }

        List<Slip.Line> o = header("CARD ISSUER REPORT");
        long grandCount = 0;
        long grandAmt = 0;
        for (Map.Entry<String, long[]> e : byIssuer.entrySet()) {
            long[] c = e.getValue();
            o.add(Slip.centre(e.getKey(), Slip.SMALL));
            o.add(Slip.rule());
            o.add(new Slip.Line(Slip.row3("TRANSACTION", "COUNT", "AMOUNT"), "",
                    Slip.SMALL, Slip.BOLD));
            o.add(new Slip.Line(Slip.row3("SALE", Slip.count3((int) c[0]), Slip.money(c[1])),
                    "", Slip.SMALL, 0));
            o.add(new Slip.Line(Slip.row3("VOID", Slip.count3((int) c[2]), Slip.money(-c[3])),
                    "", Slip.SMALL, 0));
            o.add(new Slip.Line(Slip.row3("SUBTOTAL", Slip.count3((int) c[0]), Slip.money(c[1])),
                    "", Slip.SMALL, Slip.BOLD));
            o.add(Slip.blank());
            grandCount += c[0];
            grandAmt += c[1];
        }
        o.add(Slip.rule());
        o.add(new Slip.Line(Slip.row3("ALL CARDS", Slip.count3((int) grandCount),
                Slip.money(grandAmt)), "", Slip.SMALL, Slip.BOLD));
        o.add(Slip.rule());
        tail(o);
        printSlip(o, "Card issuer report printed - " + byIssuer.size() + " scheme(s)");
    }

    /**
     * EMI transactions in the batch, grouped by term.
     *
     * <p>Shows what the merchant is owed and what each cardholder pays monthly,
     * which is the thing an EMI report exists to answer.</p>
     */
    private void printEmiReport() {
        List<TxnRecord> batch = BatchStore.get(this).all();
        java.util.Map<Integer, long[]> byTenure = new java.util.TreeMap<>();
        int total = 0;
        long totalAmt = 0;
        for (TxnRecord r : batch) {
            if (!r.approved || !r.isEmi() || r.isVoid() || r.voided) {
                continue;
            }
            long[] cell = byTenure.get(r.tenure);
            if (cell == null) {
                cell = new long[3];               // count, amount, instalment total
                byTenure.put(r.tenure, cell);
            }
            cell[0]++;
            cell[1] += r.amount();
            cell[2] += r.installment();
            total++;
            totalAmt += r.amount();
        }
        if (byTenure.isEmpty()) {
            say("No EMI transactions in the batch");
            return;
        }

        List<Slip.Line> o = header("EMI REPORT");
        o.add(new Slip.Line(Slip.row3("TENURE", "COUNT", "AMOUNT"), "", Slip.SMALL, Slip.BOLD));
        o.add(Slip.rule());
        for (java.util.Map.Entry<Integer, long[]> e : byTenure.entrySet()) {
            long[] c = e.getValue();
            o.add(new Slip.Line(Slip.row3(e.getKey() + " MONTHS",
                    Slip.count3((int) c[0]), Slip.money(c[1])), "", Slip.SMALL, Slip.BOLD));
            o.add(Slip.text("  monthly total " + cur() + " " + Slip.money(c[2])));
        }
        o.add(Slip.rule());
        o.add(new Slip.Line(Slip.row3("TOTAL", Slip.count3(total), Slip.money(totalAmt)),
                "", Slip.SMALL, Slip.BOLD));
        o.add(Slip.rule());
        o.add(Slip.blank());

        // Then the individual plans, so each can be matched to its slip.
        o.add(new Slip.Line(Slip.row3("INVOICE", "TERM", "PER MONTH"), "",
                Slip.SMALL, Slip.BOLD));
        o.add(Slip.rule());
        for (TxnRecord r : batch) {
            if (!r.approved || !r.isEmi() || r.isVoid() || r.voided) {
                continue;
            }
            o.add(new Slip.Line(Slip.row3(r.invoice, r.tenure + "m",
                    Slip.money(r.installment())), "", Slip.SMALL, 0));
            o.add(Slip.text("  " + masked(r) + "   " + cur() + " " + Slip.money(r.amount())));
        }
        o.add(Slip.rule());
        tail(o);
        printSlip(o, "EMI report printed - " + total + " transaction(s)");
    }

    private void printLastSettlement() {
        ReportStore.SettleSnapshot s = ReportStore.get(this).lastSettlement();
        if (s == null) {
            say("No settlement has been done on this terminal yet");
            return;
        }
        List<Slip.Line> o = header("LAST SETTLEMENT");
        o.add(Slip.pair("BATCH NO. " + s.batchNo, "HOST: DBBL"));
        o.add(Slip.text("SETTLED: " + s.dateTime));
        if (!s.rrn.isEmpty()) {
            o.add(Slip.pair("STAN: " + s.stan, "RREF: " + s.rrn));
        }
        o.add(Slip.blank());
        o.add(new Slip.Line(Slip.row3("TRANSACTION", "COUNT", "AMOUNT"), "",
                Slip.SMALL, Slip.BOLD));
        o.add(Slip.rule());
        o.add(new Slip.Line(Slip.row3("SALE", Slip.count3(s.saleCount), Slip.money(s.saleAmt)),
                "", Slip.SMALL, 0));
        o.add(new Slip.Line(Slip.row3("VOID", Slip.count3(s.voidCount), Slip.money(-s.voidAmt)),
                "", Slip.SMALL, 0));
        o.add(new Slip.Line(Slip.row3("REFUND", Slip.count3(s.refundCount),
                Slip.money(-s.refundAmt)), "", Slip.SMALL, 0));
        o.add(Slip.rule());
        o.add(new Slip.Line(Slip.row3("TOTAL", Slip.count3(s.netCount()), Slip.money(s.netAmt())),
                "", Slip.SMALL, Slip.BOLD));
        o.add(Slip.rule());
        o.add(Slip.blank());
        o.add(Slip.centre("*** REPRINT ***", Slip.SMALL));
        tail(o);
        printSlip(o, "Last settlement reprinted - batch " + s.batchNo);
    }

    /**
     * Every transaction that did not succeed, in the Detail Report's layout.
     *
     * <p>These never entered the batch - only approved transactions do - so
     * they appear on no other report, and settlement does not count them. This
     * is the one place they exist, which is why it is worth reading them in the
     * same shape as the successful ones: the question is almost always "the
     * customer says they were charged, were they?", and that is answered by
     * putting the two reports side by side.</p>
     */
    private void printErrorReport() {
        List<ReportStore.FailedTxn> failed = ReportStore.get(this).failed();
        if (failed.isEmpty()) {
            say("No failed transactions recorded");
            return;
        }
        List<Slip.Line> o = header("ERROR TRANSACTION REPORT");
        for (String l : DetailReport.errorLegendLines()) {
            o.add(Slip.text(l));
        }
        o.add(Slip.rule());
        long total = 0;
        for (ReportStore.FailedTxn f : failed) {
            for (String l : DetailReport.failedBlockLines(this, f)) {
                o.add(Slip.text(l));
            }
            o.add(Slip.blank());
            total += f.amount();
        }
        o.add(Slip.rule());
        // Same total row as the Detail Report. The old "None of these were
        // charged" line is gone: a no-response failure CAN have been charged
        // until its reversal is acknowledged, so the report must not say
        // otherwise.
        o.add(new Slip.Line(DetailReport.row("TOTAL   " + DetailReport.count(failed.size()),
                cur() + " " + Slip.money(total)), "", Slip.SMALL, Slip.BOLD));
        o.add(Slip.rule());
        o.add(Slip.blank());
        o.add(Slip.centre(DetailReport.END_ERRORS, Slip.SMALL));
        tail(o);
        printSlip(o, "Error transaction report printed - " + failed.size() + " entr(ies)");
    }

    // --------------------------------------------------------------- pieces

    /**
     * Logo block, merchant name and address lines 1 to 4.
     *
     * <p>One method because there were two: the reports built theirs here and
     * the reprint built its own, which printed the name and no address at all.
     * A reprint is meant to be the same document as the slip it copies, and it
     * was missing the merchant's address every time.</p>
     */
    private void letterhead(List<Slip.Line> o) {
        TxnSlip.letterhead(o, TerminalConfig.get(this));
    }

    private List<Slip.Line> header(String title) {
        List<Slip.Line> o = Slip.list();
        // The same block the sale slip prints - letterhead, DATE/TIME, MID/TID,
        // batch - so a report and a receipt off this terminal look like they
        // came off the same terminal. This used to spell the labels its own way
        // and put the date under the title rather than above it.
        TxnSlip.header(this, o);
        o.add(Slip.centre(title, Slip.MID));
        o.add(Slip.rule());
        return o;
    }

    private void tail(List<Slip.Line> o) {
        o.add(Slip.blank());
        o.add(Slip.centre("APP VERSION: " + com.hjq.demo.BuildConfig.VERSION_NAME, Slip.SMALL));
        o.add(Slip.blank());
        o.add(Slip.blank());
    }


    /** The terminal's currency symbol, from the DE49 code it already sends. */
    private String cur() {
        return TerminalConfig.get(this).currencySymbol();
    }

    private String masked(TxnRecord r) {
        return CardMask.mask(panOf(r), TerminalConfig.get(this).cardMask());
    }

    private static String panOf(TxnRecord r) {
        if (r == null) {
            return "";
        }
        if (!r.pan.isEmpty()) {
            return r.pan;
        }
        int i = r.track2 == null ? -1 : r.track2.indexOf('=');
        return i > 0 ? r.track2.substring(0, i) : (r.track2 == null ? "" : r.track2);
    }

    private static String pad(String v) {
        StringBuilder b = new StringBuilder(v == null ? "" : v.trim());
        while (b.length() < INVOICE_LEN) {
            b.insert(0, '0');
        }
        return b.toString();
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    // -------------------------------------------------------------- printing

    private com.hjq.demo.ui.dialog.ReceiptPreviewDialog receiptPreview;
    private boolean preparingReceipt;

    @Override
    protected void onDestroy() {
        if (receiptPreview != null) receiptPreview.dismiss();
        super.onDestroy();
    }

    private void printSlip(final List<Slip.Line> lines, final String note) {
        if (preparingReceipt || (receiptPreview != null && receiptPreview.isShowing())) return;
        preparingReceipt = true;
        new Thread(() -> {
            try {
                Slip.useFont(getApplicationContext());
                TerminalConfig tc = TerminalConfig.get(getApplicationContext());
                Bitmap logo = tc.printBankLogo() || tc.printMerchantLogo()
                        ? Slip.bankLogo(getApplicationContext(), Slip.LOGO_W) : null;
                Bitmap bitmap = Slip.toBitmap(lines, logo);
                runOnUiThread(() -> {
                    preparingReceipt = false;
                    if (isFinishing() || isDestroyed()) return;
                    receiptPreview = new com.hjq.demo.ui.dialog.ReceiptPreviewDialog(
                            this, note, bitmap, printer, () -> say(note));
                    receiptPreview.show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    preparingReceipt = false;
                    say("Receipt could not be prepared: " + e.getMessage());
                });
            }
        }, "ReportReceipt").start();
    }
    private void say(String s) {
        if (status != null) {
            status.setText(s);
        }
    }

    private void sayOnUi(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                say(s);
            }
        });
    }
}

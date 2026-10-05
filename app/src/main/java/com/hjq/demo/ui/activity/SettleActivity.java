package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hjq.demo.R;
import com.hjq.demo.ans8583.BatchStore;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.app.AppActivity;
import com.szanfu.sdk.api.ISdkServiceManager;

import java.util.Locale;

/**
 * SETTLEMENT review screen, the same shape as PAX's {@code SettleActivity}:
 * the merchant / terminal / batch block, then the totals table, then a single
 * SETTLE button.
 *
 * <p>A settlement closes the batch and cannot be undone, so it is the one
 * operation that must never happen by accident. This screen shows the merchant
 * exactly what is about to be reconciled - the same
 * {@link BatchStore.Totals} object that will produce DE63 (spec 6.28.1) - and
 * sends nothing until SETTLE is pressed.</p>
 *
 * <p>On confirmation the work is handed to {@link FindCardActivity}, which owns
 * the printer binder and drives the 0500 exchange.</p>
 */
public final class SettleActivity extends AppActivity implements View.OnClickListener {

    private static final int COLOR_LABEL = 0xFF555555;
    private static final int COLOR_VALUE = 0xFF111111;
    private static final int COLOR_RULE = 0xFFD9D9D9;
    private static final int COLOR_HEAVY_RULE = 0xFFB9C2DA;

    /** Column weights copied from PAX's fragment_trans_total. */
    private static final float W_TYPE = 1f, W_COUNT = 1f, W_TK = 0.4f, W_AMOUNT = 0.9f;

    private LinearLayout infoRows;
    private LinearLayout totalRows;
    private TextView notice;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_settle;
    }

    /**
     * Settlement does not close itself.
     *
     * <p>A batch upload is a conversation with the host that can run for
     * minutes without anything on screen changing. Closing the screen under it
     * would leave the terminal and the acquirer disagreeing about whether the
     * batch was cut.</p>
     */
    @Override
    protected boolean idleTimeoutEnabled() {
        return false;
    }

    @Override
    protected void initView() {
        infoRows = findViewById(R.id.settle_info_rows);
        totalRows = findViewById(R.id.settle_total_rows);
        notice = findViewById(R.id.settle_notice);

        Button confirm = findViewById(R.id.settle_confirm);
        Button cancel = findViewById(R.id.settle_cancel);
        if (confirm != null) {
            confirm.setOnClickListener(this);
        }
        if (cancel != null) {
            cancel.setOnClickListener(this);
        }
    }

    @Override
    protected void initData() {
        BatchStore store = BatchStore.get(this);
        TerminalConfig tc = TerminalConfig.get(this);
        BatchStore.Totals t = store.totals();

        // An empty batch has nothing to reconcile. PAX refuses rather than
        // sending zero totals the host has never seen, and so do we.
        if (t.isZero()) {
            toast("No transactions in batch " + tc.batchNo() + " to settle");
            finish();
            return;
        }

        buildInfo(tc);
        buildTotals(t);
        buildNotice(store, tc);
    }

    // ------------------------------------------------------------ info block

    private void buildInfo(TerminalConfig tc) {
        if (infoRows == null) {
            return;
        }
        infoRows.removeAllViews();
        String merchant = tc.hostMerchantName();
        addInfo("Merchant Name", merchant.isEmpty() ? "-" : merchant);
        addInfo("Merchant ID", tc.mid());
        addInfo("Terminal ID", tc.tid());
        addInfo("Batch NO.", tc.batchNo());
        int days = tc.daysSinceSettle();
        addInfo("Last Settled", days < 0 ? "never" : days + " day(s) ago");
    }

    private void addInfo(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(5), 0, dp(5));

        row.addView(cell(label, COLOR_LABEL, Gravity.START, 1f, false));
        row.addView(cell(value, COLOR_VALUE, Gravity.END, 1.3f, true));
        infoRows.addView(row);
    }

    // --------------------------------------------------------- totals table

    private void buildTotals(BatchStore.Totals t) {
        if (totalRows == null) {
            return;
        }
        totalRows.removeAllViews();

        addTotalHeader();
        addRule(COLOR_HEAVY_RULE, 3);

        // Refunds and voids move money back, so they are shown negative - the
        // same convention as the printed settlement report.
        addTotalRow("SALE", t.saleCount, t.saleAmt, false);
        addRule(COLOR_RULE, 1);
        addTotalRow("VOID", t.voidCount, -t.voidAmt, false);
        addRule(COLOR_RULE, 1);
        addTotalRow("REFUND", t.refundCount, -t.refundAmt, false);
        addRule(COLOR_HEAVY_RULE, 3);
        addTotalRow("TOTAL", t.netCount(), t.netAmt(), true);
        addRule(COLOR_HEAVY_RULE, 3);
    }

    private void addTotalHeader() {
        LinearLayout row = newTableRow();
        row.addView(cell("TRANS TYPE", COLOR_VALUE, Gravity.START, W_TYPE, true));
        row.addView(cell("TIMES", COLOR_VALUE, Gravity.CENTER, W_COUNT, true));
        row.addView(cell("", COLOR_VALUE, Gravity.CENTER, W_TK, true));
        row.addView(cell("AMOUNT", COLOR_VALUE, Gravity.END, W_AMOUNT, true));
        totalRows.addView(row);
    }

    private void addTotalRow(String label, int count, long minorAmt, boolean bold) {
        LinearLayout row = newTableRow();
        row.addView(cell(label, COLOR_VALUE, Gravity.START, W_TYPE, bold));
        row.addView(cell(count3(count), COLOR_VALUE, Gravity.CENTER, W_COUNT, bold));
        row.addView(cell("TK", COLOR_LABEL, Gravity.CENTER, W_TK, bold));
        row.addView(cell(money(minorAmt), COLOR_VALUE, Gravity.END, W_AMOUNT, bold));
        totalRows.addView(row);
    }

    private LinearLayout newTableRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));
        return row;
    }

    private TextView cell(String text, int color, int gravity, float weight, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(color);
        v.setTextSize(14);
        v.setGravity(gravity | Gravity.CENTER_VERTICAL);
        v.getPaint().setFakeBoldText(bold);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        return v;
    }

    private void addRule(int color, int heightPx) {
        View sep = new View(this);
        sep.setBackgroundColor(color);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        totalRows.addView(sep);
    }

    // ---------------------------------------------------------------- notice

    /**
     * Tell the merchant up front about anything that will change what happens
     * when they press SETTLE, rather than surprising them mid-exchange.
     */
    private void buildNotice(BatchStore store, TerminalConfig tc) {
        if (notice == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        if (store.hasPendingReversal()) {
            b.append("A reversal is still outstanding. It will be sent first,")
                    .append(" and settlement will stop if it does not clear.\n");
        }
        if (tc.settleDetailReport()) {
            b.append("The transaction detail report will print before the")
                    .append(" settlement slip.\n");
        }
        if (!tc.hasHost()) {
            b.append("No host is configured - set the IP and port in the")
                    .append(" admin panel.\n");
        }
        b.append("Settling closes batch ").append(tc.batchNo()).append(". This cannot be undone.");

        notice.setText(b.toString().trim());
        notice.setTextColor(store.hasPendingReversal() || !tc.hasHost()
                ? 0xFFB00020 : COLOR_LABEL);
        notice.setVisibility(View.VISIBLE);
    }

    // --------------------------------------------------------------- actions

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.settle_confirm) {
            Intent i = new Intent(this, FindCardActivity.class);
            i.putExtra(FindCardActivity.EXTRA_TRANS_TYPE, "SETTLEMENT");
            startActivity(i);
            finish();
        } else if (id == R.id.settle_cancel) {
            // Nothing has been sent, so backing out costs nothing.
            finish();
        }
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        // Nothing to bind: this screen only reads the batch journal. The
        // printer and PIN pad are acquired by FindCardActivity, which is where
        // the settlement is actually sent and the slip printed.
    }

    // --------------------------------------------------------------- helpers

    private static String count3(int v) {
        return String.format(Locale.US, "%03d", Math.max(0, v));
    }

    private static String money(long minor) {
        long a = Math.abs(minor);
        return (minor < 0 ? "-" : "")
                + String.format(Locale.US, "%d.%02d", a / 100, a % 100);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}

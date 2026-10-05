package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.ans8583.*;
import com.hjq.demo.utils.*;
import com.hjq.demo.ui.dialog.ReceiptPreviewDialog;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import java.util.*;

/** Searchable full-screen QR ledger with independent confirmed and unresolved totals. */
public final class QrReportsActivity extends AppActivity {
    private final List<DbblQr.Session> visible = new ArrayList<>();
    private List<TxnRecord> paid = new ArrayList<>();
    private List<DbblQr.Session> pending = new ArrayList<>();
    private boolean showPending, preparing;
    private EditText search;
    private TextView summary;
    private Button paidTab, pendingTab, print;
    private BaseAdapter adapter;
    private IPrinterBinderService printer;
    private ReceiptPreviewDialog preview;
    @Override protected int getLayoutId() { return R.layout.activity_qr_reports; }
    @Override protected void initData() { }
    @Override protected void initView() {
        search = findViewById(R.id.qr_report_search); summary = findViewById(R.id.qr_report_summary);
        paidTab = findViewById(R.id.qr_report_paid); pendingTab = findViewById(R.id.qr_report_pending);
        print = findViewById(R.id.qr_report_print);
        paidTab.setOnClickListener(v -> { showPending = false; update(); });
        pendingTab.setOnClickListener(v -> { showPending = true; update(); });
        findViewById(R.id.qr_report_back).setOnClickListener(v -> finish());
        print.setOnClickListener(v -> printReport());
        ListView list = findViewById(R.id.qr_report_list);
        list.setEmptyView(findViewById(R.id.qr_report_empty));
        adapter = new BaseAdapter() {
            public int getCount() { return visible.size(); }
            public Object getItem(int position) { return visible.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position, View convert, ViewGroup parent) {
                LinearLayout row = new LinearLayout(QrReportsActivity.this);
                row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(16), dp(14), dp(16), dp(14));
                row.setBackgroundColor(Color.WHITE);
                TxnRecord r = visible.get(position).record;
                TextView title = new TextView(QrReportsActivity.this);
                title.setText("#" + r.invoice + "     TK " + TipAmounts.major(r.amount()));
                title.setTextSize(18); title.setTextColor(Color.rgb(20,43,76)); title.setTypeface(null, Typeface.BOLD);
                row.addView(title);
                TextView detail = new TextView(QrReportsActivity.this);
                detail.setText((r.approved ? "PAID" : "UNCONFIRMED") + "  •  " + QrSlip.date(r) + "  " + QrSlip.time(r)
                        + "\n" + (r.approved ? "View / reprint receipt" : "Check payment with bank"));
                detail.setTextSize(13); detail.setPadding(0, dp(5), 0, 0);
                detail.setTextColor(r.approved ? Color.rgb(18,111,78) : Color.rgb(146,88,14));
                row.addView(detail); return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (preparing || preview != null) return;
            DbblQr.Session selected = visible.get(position);
            Intent intent = new Intent(this, QrSaleActivity.class).putExtra("qr_historical", true);
            if (selected.record.approved) intent.putExtra("qr_approved", selected.record.serialize());
            else intent.putExtra("qr_reference", selected.reference);
            startActivity(intent);
        });
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { update(); resetIdleTimeout(); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        reload();
    }
    @Override protected void onResume() { super.onResume(); if (adapter != null) reload(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void reload() {
        paid = BatchStore.get(this).qrHistory(); pending = BatchStore.get(this).pendingQrHistory();
        Collections.reverse(paid); Collections.reverse(pending); update();
    }
    private void update() {
        visible.clear(); String query = search.getText().toString().trim();
        long total = 0; for (TxnRecord r : paid) total += r.amount();
        summary.setText("CONFIRMED TOTAL  TK " + TipAmounts.major(total)
                + "\n" + paid.size() + " paid  •  " + pending.size() + " unconfirmed");
        paidTab.setText("PAID (" + paid.size() + ")"); pendingTab.setText("UNCONFIRMED (" + pending.size() + ")");
        paidTab.setAlpha(showPending ? 0.55f : 1f); pendingTab.setAlpha(showPending ? 1f : 0.55f);
        if (showPending) { for (DbblQr.Session s : pending) if (s.record.invoice.contains(query)) visible.add(s); }
        else { for (TxnRecord r : paid) if (r.invoice.contains(query)) visible.add(new DbblQr.Session(r, "", "", "")); }
        adapter.notifyDataSetChanged();
    }
    private void printReport() {
        if (preparing || preview != null) return;
        preparing = true; print.setEnabled(false); stopIdleTimeout();
        List<Slip.Line> lines = QrSlip.report(this, new ArrayList<>(paid), new ArrayList<>(pending));
        new Thread(() -> {
            try {
                Slip.useFont(getApplicationContext());
                android.graphics.Bitmap bitmap = Slip.toBitmap(lines, Slip.bankLogo(getApplicationContext(), Slip.LOGO_W));
                runOnUiThread(() -> {
                    preparing = false; print.setEnabled(true);
                    if (isFinishing() || isDestroyed()) return;
                    preview = new ReceiptPreviewDialog(this, "QR SALE DETAIL REPORT", bitmap, printer,
                            () -> { preview = null; resetIdleTimeout(); });
                    preview.show();
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> { preparing = false; print.setEnabled(true); resetIdleTimeout(); toast("Report unavailable: " + e.getMessage()); });
            }
        }, "QrReportPrinter").start();
    }
    @Override protected boolean idleTimeoutEnabled() { return !preparing && preview == null; }
    @Override public void onServiceConnected(ISdkServiceManager sdk) {
        try { printer = IPrinterBinderService.Stub.asInterface(sdk.getService(ServiceID.SERVICE_ID_PRINTER)); }
        catch (Exception e) { toast("Printer unavailable"); }
    }
    @Override protected void onDestroy() { if (preview != null) preview.dismiss(); super.onDestroy(); }
}

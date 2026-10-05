package com.hjq.demo.ui.activity;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.hjq.demo.R;
import com.hjq.demo.model.AFConstant;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.ans8583.*;
import com.hjq.demo.utils.*;
import com.hjq.demo.ui.dialog.ReceiptPreviewDialog;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import java.util.List;

/** Amount-only tip entry; adjustment never opens a reader or contacts the host. */
public final class TipActivity extends AppActivity {
    public static final String BASE_AMOUNT = "tip_base_minor", TIP_AMOUNT = "tip_minor", ADJUST = "tip_adjust";
    private TextView title, badge, summary, baseAmountView, maxBadgeView, totalAmountView, currencyPrefix;
    private View baseRow, maxBadgeRow;
    private EditText input;
    private MinorAmountInput moneyInput;
    private Button next;
    private long base;
    private TxnRecord original, approved;
    private boolean adjust, saving;
    private IPrinterBinderService printer;
    private ReceiptPreviewDialog preview;
    private Bundle restored;

    @Override protected void onCreate(Bundle state) { restored = state; super.onCreate(state); }
    @Override protected int getLayoutId() { return R.layout.activity_tip; }

    @Override protected void initView() {
        adjust = getIntent().getBooleanExtra(ADJUST, false);
        base = getIntent().getLongExtra(BASE_AMOUNT, 0);

        title = findViewById(R.id.tip_title);
        badge = findViewById(R.id.tip_badge);
        summary = findViewById(R.id.tip_summary);
        input = findViewById(R.id.tip_input);
        next = findViewById(R.id.tip_continue);
        baseAmountView = findViewById(R.id.tip_base_amount);
        maxBadgeView = findViewById(R.id.tip_max_badge);
        totalAmountView = findViewById(R.id.tip_total_amount);
        baseRow = findViewById(R.id.tip_base_row);
        maxBadgeRow = findViewById(R.id.tip_max_badge_row);
        currencyPrefix = findViewById(R.id.tip_currency_prefix);

        title.setText(adjust ? "TIP ADJUST" : "SALE WITH TIP");
        if (badge != null) {
            badge.setText(adjust ? "ADJUST" : "SALE + TIP");
        }

        if (restored != null) {
            original = TxnRecord.parse(restored.getString("original"));
            approved = TxnRecord.parse(restored.getString("approved"));
            if (original != null) base = original.baseAmount();
        }

        next.setOnClickListener(v -> confirm());
        findViewById(R.id.tip_cancel).setOnClickListener(v -> { if (!saving) finish(); });
        input.setOnEditorActionListener((v, action, event) -> {
            if (event == null || event.getAction() == android.view.KeyEvent.ACTION_DOWN) confirm();
            return true;
        });
        moneyInput = new MinorAmountInput(input, () -> { updateTotal(); resetIdleTimeout(); });

        display();
        autoShowKeyboard();
    }

    private void autoShowKeyboard() {
        if (input == null || input.getVisibility() != View.VISIBLE) return;
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.requestFocus();
        input.postDelayed(() -> {
            if (isFinishing() || isDestroyed() || input.getVisibility() != View.VISIBLE || approved != null || saving) return;
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150L);
    }

    @Override protected void onResume() {
        super.onResume();
        autoShowKeyboard();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && approved == null && !saving) autoShowKeyboard();
    }
    @Override protected void initData() { }

    private void display() {
        if (approved != null) {
            summary.setText("APPROVED OFFLINE\nInvoice " + approved.invoice + "\nTip TK " + TipAmounts.major(approved.tip())
                    + "\nTotal TK " + TipAmounts.major(approved.amount()) + "\nSaved for upload before the next transaction.");
            input.setVisibility(View.GONE);
            if (currencyPrefix != null) currencyPrefix.setVisibility(View.GONE);
            if (baseRow != null) baseRow.setVisibility(View.GONE);
            if (maxBadgeRow != null) maxBadgeRow.setVisibility(View.GONE);
            if (totalAmountView != null) totalAmountView.setText("TK " + TipAmounts.major(approved.amount()));
            next.setText("RECEIPT");
            return;
        }
        boolean invoice = adjust && original == null;
        moneyInput.setEnabled(!invoice);
        input.setHint(invoice ? "Sale invoice" : "0.00");
        if (!invoice && (input.getText().length() == 0 || !input.getText().toString().contains("."))) {
            input.setText("0.00");
            input.setSelection(4);
        }
        input.setVisibility(View.VISIBLE);

        if (currencyPrefix != null) {
            currencyPrefix.setVisibility(invoice ? View.GONE : View.VISIBLE);
        }
        if (baseRow != null) {
            baseRow.setVisibility(invoice ? View.GONE : View.VISIBLE);
        }
        if (maxBadgeRow != null) {
            maxBadgeRow.setVisibility(invoice ? View.GONE : View.VISIBLE);
        }

        int maxPercent = TerminalConfig.get(this).tipPercentage();
        long maxTip = (base * maxPercent) / 100;

        if (baseAmountView != null) {
            baseAmountView.setText("TK " + TipAmounts.major(base));
        }
        if (maxBadgeView != null) {
            maxBadgeView.setText("Max Tip (" + maxPercent + "%): TK " + TipAmounts.major(maxTip));
        }

        if (invoice) {
            summary.setText("Enter the sale invoice number from the open batch.");
            if (totalAmountView != null) totalAmountView.setText("---");
            next.setText("FIND SALE");
        } else {
            summary.setText((adjust ? "Invoice " + original.invoice + " | " : "")
                    + "Base TK " + TipAmounts.major(base)
                    + " | Max tip " + maxPercent + "% (TK " + TipAmounts.major(maxTip) + ")");
            next.setText(adjust ? "APPROVE OFFLINE" : "CONTINUE TO CARD");
        }
        updateTotal();
    }

    private void updateTotal() {
        if (approved != null || (adjust && original == null)) return;
        String entered = input.getText().toString().trim();
        long tip = 0;
        int maxPercent = TerminalConfig.get(this).tipPercentage();
        long maxTip = (base * maxPercent) / 100;
        try {
            tip = entered.isEmpty() ? 0 : TipAmounts.parse(entered);
            long total = base + tip;
            if (totalAmountView != null) {
                totalAmountView.setText("TK " + TipAmounts.major(total));
            }
        } catch (RuntimeException e) {
            if (totalAmountView != null) {
                totalAmountView.setText(e.getMessage());
            }
        }
        summary.setText((adjust ? "Invoice " + original.invoice + "\n" : "")
                + "Base TK " + TipAmounts.major(base)
                + "\nMax tip " + maxPercent + "% (TK " + TipAmounts.major(maxTip) + ")");
    }

    private void showTipLimitDialog(int maxPercent) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_tip_limit);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        TextView msg = dialog.findViewById(R.id.tv_tip_limit_message);
        if (msg != null) {
            msg.setText("Only " + maxPercent + "% of the Sale amount is supported");
        }
        Button confirm = dialog.findViewById(R.id.btn_tip_limit_confirm);
        if (confirm != null) {
            confirm.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void confirm() {
        if (saving || (preview != null && preview.isShowing())) return;
        if (approved != null) { showReceipt(false); return; }
        TerminalConfig tc = TerminalConfig.get(this);
        if (adjust && !tc.tipAdjustEnabled()) { toast("Tip Adjust is disabled"); return; }
        if (adjust && original == null) {
            String invoice = input.getText().toString().trim();
            if (!invoice.matches("[0-9]{1,6}")) { input.setError("Enter a sale invoice (up to 6 digits)"); return; }
            invoice = String.format(java.util.Locale.US, "%06d", Integer.parseInt(invoice));
            TxnRecord r = BatchStore.get(this).findByInvoice(invoice);
            if (r == null || !"SALE".equals(r.type) || r.tipAdjusted) {
                input.setError("No eligible sale, or this invoice was already adjusted"); return;
            }
            if (r.tip() > 0) {
                input.setError("Sale already includes a tip. Tip adjust not allowed."); return;
            }
            original = r; base = r.baseAmount(); input.setText(""); display(); autoShowKeyboard(); return;
        }
        String text = input.getText().toString().trim();
        final long tip;
        try {
            tip = text.isEmpty() ? 0 : TipAmounts.parse(text);
        } catch (RuntimeException e) {
            input.setError(e.getMessage());
            return;
        }

        int maxPercent = tc.tipPercentage();
        if (tip * 100 > base * maxPercent) {
            showTipLimitDialog(maxPercent);
            return;
        }

        final long total = base + tip;
        if (!adjust) {
            saving = true;
            Intent intent = new Intent(this, FindCardActivity.class);
            intent.putExtra(AFConstant.CURRENT_TRANS_AMOUNT, TipAmounts.major(total));
            intent.putExtra(FindCardActivity.EXTRA_TRANS_TYPE, "SALE");
            intent.putExtra(BASE_AMOUNT, base); intent.putExtra(TIP_AMOUNT, tip);
            startActivity(intent); finish(); return;
        }
        saving = true; next.setEnabled(false);
        new Thread(() -> {
            try {
                TxnRecord saved = OnlineHost.queueTipAdjustment(getApplicationContext(), original.invoice, tip);
                runOnUiThread(() -> {
                    approved = saved; saving = false;
                    if (isFinishing() || isDestroyed()) return;
                    next.setEnabled(true); display(); showReceipt(false);
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> { saving = false; if (!isFinishing()) { next.setEnabled(true); input.setError(e.getMessage()); } });
            }
        }, "SaveTipAdjust").start();
    }

    private void showReceipt(boolean customer) {
        if (saving) return;
        saving = true;
        new Thread(() -> {
            try {
                Slip.useFont(getApplicationContext());
                TxnSlip.Data data = TxnSlip.of(getApplicationContext(), approved);
                data.title = "TIP ADJUST";
                List<Slip.Line> lines = TxnSlip.body(getApplicationContext(), data,
                        customer ? "CUSTOMER COPY" : "MERCHANT COPY", false);
                lines.add(Slip.centre("APPROVED OFFLINE", Slip.MID));
                lines.add(Slip.centre("QUEUED FOR HOST UPLOAD", Slip.SMALL));
                android.graphics.Bitmap bitmap = Slip.toBitmap(lines, null);
                runOnUiThread(() -> {
                    saving = false; if (isFinishing() || isDestroyed()) return;
                    preview = new ReceiptPreviewDialog(this, "TIP ADJUST", bitmap, printer, () -> {
                        if (customer) { finish(); return; }
                        boolean customerEnabled = com.hjq.demo.ans8583.TerminalConfig.get(getApplicationContext()).customerCopyEnabled();
                        if (customerEnabled) {
                            new android.app.AlertDialog.Builder(this).setTitle("Print Customer Copy?")
                                    .setMessage("Print a customer copy?").setPositiveButton("YES", (d, w) -> showReceipt(true))
                                    .setNegativeButton("NO", (d, w) -> finish()).setCancelable(false).show();
                        } else {
                            finish();
                        }
                    }, true); preview.show();
                });
            } catch (RuntimeException e) { runOnUiThread(() -> { saving = false; toast("Tip saved. Receipt unavailable: " + e.getMessage()); }); }
        }, "TipReceipt").start();
    }

    @Override public void onServiceConnected(ISdkServiceManager sdk) {
        try { printer = IPrinterBinderService.Stub.asInterface(sdk.getService(ServiceID.SERVICE_ID_PRINTER)); }
        catch (Exception ignored) { }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (original != null) out.putString("original", original.serialize());
        if (approved != null) out.putString("approved", approved.serialize());
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() { if (!saving) super.onBackPressed(); }
    @Override public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        // A focused EditText already consumes the key-down digit; do not append it again on key-up.
        if (input != null && input.hasFocus()) return super.onKeyUp(keyCode, event);
        if (input != null && input.getVisibility() == View.VISIBLE && approved == null) {
            if (keyCode >= android.view.KeyEvent.KEYCODE_0 && keyCode <= android.view.KeyEvent.KEYCODE_9) {
                int digit = keyCode - android.view.KeyEvent.KEYCODE_0;
                input.append(String.valueOf(digit));
                return true;
            }
            if (keyCode >= android.view.KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= android.view.KeyEvent.KEYCODE_NUMPAD_9) {
                int digit = keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0;
                input.append(String.valueOf(digit));
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DEL || keyCode == android.view.KeyEvent.KEYCODE_CLEAR) {
                int len = input.getText().length();
                if (len > 0) {
                    input.getText().delete(len - 1, len);
                }
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
                confirm();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }
    @Override protected void onDestroy() { if (preview != null) preview.dismiss(); super.onDestroy(); }
}

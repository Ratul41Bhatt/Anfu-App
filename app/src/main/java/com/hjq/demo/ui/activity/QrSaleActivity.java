package com.hjq.demo.ui.activity;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.ans8583.*;
import com.hjq.demo.utils.*;
import com.hjq.demo.ui.dialog.ReceiptPreviewDialog;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.pinpad.IPinPadBinderService;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.util.*;

/** Merchant-presented QR. Confirmation always queries the bank, never approves locally. */
public final class QrSaleActivity extends AppActivity {
    private EditText input;
    private TextView amount, status, invoice;
    private ImageView code;
    private Button confirm, home, debug;
    private View progress;
    private volatile boolean busy, closing;
    private android.os.Bundle restored;
    private android.app.AlertDialog auxiliary;
    private boolean historical;
    private DbblQr.Session session;
    private OnlineHost.HostResult last;
    private IPinPadBinderService pinpad;
    private IPrinterBinderService printer;
    private ReceiptPreviewDialog preview;

    @Override protected void onCreate(android.os.Bundle state) { restored = state; super.onCreate(state); }
    @Override protected int getLayoutId() { return R.layout.activity_qr_sale; }
    @Override protected void initView() {
        input = findViewById(R.id.qr_amount_input); amount = findViewById(R.id.qr_amount);
        status = findViewById(R.id.qr_status); invoice = findViewById(R.id.qr_invoice);
        code = findViewById(R.id.qr_code); confirm = findViewById(R.id.qr_confirm);
        home = findViewById(R.id.qr_home); debug = findViewById(R.id.qr_debug);
        progress = findViewById(R.id.qr_progress);
        ((TextView) findViewById(R.id.qr_merchant)).setText(TerminalConfig.get(this).merchantName());
        confirm.setOnClickListener(v -> proceed()); home.setOnClickListener(v -> leave());
        debug.setOnClickListener(v -> { if (session == null) history(); else receipt(false, true); });
        new MinorAmountInput(input, () -> {
            if (!closing && input.hasFocus()) resetIdleTimeout();
        }).setEnabled(true);
        input.setText("0.00");
        input.setSelection(4);
        input.setOnEditorActionListener((v, action, event) -> {
            if (event == null || event.getAction() == android.view.KeyEvent.ACTION_DOWN) proceed();
            return true;
        });
        try {
            BatchStore batch = BatchStore.get(this);
            String reference = restored == null ? getIntent().getStringExtra("qr_reference") : restored.getString("qr_reference", "");
            if (reference == null) reference = "";
            historical = restored != null ? restored.getBoolean("qr_historical", false) : getIntent().getBooleanExtra("qr_historical", false);
            session = reference.isEmpty() ? null : batch.findQrSession(reference);
            if (session == null && restored != null && restored.containsKey("qr_approved"))
                session = new DbblQr.Session(TxnRecord.parse(restored.getString("qr_approved")), "", "", "");
            if (session == null && getIntent().hasExtra("qr_approved"))
                session = new DbblQr.Session(TxnRecord.parse(getIntent().getStringExtra("qr_approved")), "", "", "");
            if (session == null) batch.finishQr(); // retire a previous visit; opening starts with a new amount
            display();
        }
        catch (RuntimeException e) { status.setText(e.getMessage()); confirm.setEnabled(false); }
    }
    @Override protected void initData() { }

    @Override protected void onResume() {
        super.onResume();
        if (session == null) showAmountKeyboard();
    }

    private void display() {
        if (session == null) {
            input.setVisibility(View.VISIBLE); code.setImageDrawable(null); code.setVisibility(View.GONE);
            debug.setText("QR REPORTS"); debug.setVisibility(View.VISIBLE); showAmountKeyboard(); return;
        }
        debug.setText("VIEW PACKET TRACE"); debug.setVisibility(View.GONE);
        input.setVisibility(View.GONE);
        amount.setText("TK " + TipAmounts.major(session.record.amount()));
        invoice.setText("Invoice " + session.record.invoice);
        if (session.record.approved) {
            code.setVisibility(View.GONE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            status.setText("Payment confirmed\nApproval " + session.record.authCode + "\nReference " + session.record.rrn);
            status.setTextColor(Color.rgb(18, 111, 78));
            confirm.setText("RECEIPT PREVIEW"); home.setText("DONE");
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            code.setVisibility(historical ? View.GONE : View.VISIBLE);
            try {
                if (!historical) {
                Map<EncodeHintType,Object> hints = new EnumMap<>(EncodeHintType.class);
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8"); hints.put(EncodeHintType.MARGIN, 4);
                BitMatrix matrix = new MultiFormatWriter().encode(session.payload, BarcodeFormat.QR_CODE, 780, 780, hints);
                int[] pixels = new int[780 * 780];
                for (int y = 0; y < 780; y++) for (int x = 0; x < 780; x++)
                    pixels[y * 780 + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                code.setImageBitmap(Bitmap.createBitmap(pixels, 780, 780, Bitmap.Config.RGB_565));
                }
            } catch (Exception e) { status.setText("Could not display QR: " + e.getMessage()); return; }
            status.setText(historical ? "Payment not confirmed. Check its status with the bank." : "Scan with NexusPay, then confirm payment.");
            confirm.setText(historical ? "CHECK PAYMENT STATUS" : "CONFIRM PAYMENT"); home.setText("EXIT");
        }
    }
    private void showAmountKeyboard() {
        if (input == null || session != null || closing) return;
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.requestFocus();
        input.postDelayed(() -> {
            if (session != null || closing || isFinishing() || isDestroyed()) return;
            input.requestFocus();
            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 150L);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && session == null) showAmountKeyboard();
    }
    @Override public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        // A focused EditText already consumes the key-down digit; do not append it again on key-up.
        if (input != null && input.hasFocus()) return super.onKeyUp(keyCode, event);
        if (session == null && input != null && input.getVisibility() == View.VISIBLE) {
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
                proceed();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }
    private void history() {
        if (busy || closing) return;
        startActivity(new android.content.Intent(this, QrReportsActivity.class));
    }
    private void trackDialogInteraction(android.app.Dialog dialog) {
        android.view.Window window = dialog.getWindow();
        if (window == null) return;
        window.setCallback(new androidx.appcompat.view.WindowCallbackWrapper(window.getCallback()) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) onUserInteraction();
                return super.dispatchTouchEvent(event);
            }
            @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) onUserInteraction();
                return super.dispatchKeyEvent(event);
            }
        });
    }
    private void busy(boolean value) {
        busy = value;
        if (value) stopIdleTimeout(); else resetIdleTimeout();
        confirm.setEnabled(!value); debug.setEnabled(!value); input.setEnabled(!value);
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
    }
    private void proceed() {
        if (closing || busy || (preview != null && preview.isShowing())) return;
        if (session != null && session.record.approved) { receipt(false, false); return; }
        final long entered;
        try {
            entered = session == null ? TipAmounts.parse(input.getText().toString().trim()) : session.record.amount();
            if (entered <= 0) throw new IllegalArgumentException("Enter an amount greater than zero");
            if (session == null) {
                TerminalConfig tc = TerminalConfig.get(this);
                DbblQr.validateSettings(tc.qrNii(), tc.qrTpdu(), tc.qrMcc(), tc.qrCity(), tc.qrMerchant());
            }
        } catch (RuntimeException e) { status.setText(e.getMessage() + "\nQR settings are in Parameters > QR Sale."); return; }
        final boolean creating = session == null;
        final DbblQr.Session selected = session;
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
        busy(true); status.setText(creating ? "Preparing your QR…" : "Checking payment with the bank…");
        new Thread(() -> {
            try {
                DbblQr.Session current;
                OnlineHost.HostResult result = null;
                if (creating) current = OnlineHost.createQrSale(getApplicationContext(), entered, () -> !closing);
                else {
                    result = OnlineHost.checkQrPayment(getApplicationContext(), pinpad, selected.reference);
                    current = result.approved && result.record != null
                            ? new DbblQr.Session(result.record, selected.reference, selected.payload, selected.route)
                            : selected;
                }
                final OnlineHost.HostResult response = result;
                runOnUiThread(() -> {
                    if (closing || isFinishing() || isDestroyed()) {
                        if (creating) {
                            try { BatchStore.get(getApplicationContext()).dismissQr(current.reference); }
                            catch (RuntimeException e) { android.util.Log.e("QrSale", "Could not retire closed QR", e); }
                        }
                        return;
                    }
                    busy(false); session = current; last = response; display();
                    if (response != null && !response.approved) {
                        status.setText(response.displayMessage() + (response.error.isEmpty() ? "" : "\n" + response.error));
                        confirm.setText("CHECK PAYMENT AGAIN");
                    }
                    debug.setVisibility(last != null && TerminalConfig.get(this).printPacketTrace() ? View.VISIBLE : View.GONE);
                    if (response != null && response.approved) receipt(false, false);
                    else if (response != null && TerminalConfig.get(this).printPacketTrace()) receipt(false, true);
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { busy(false); status.setText(e.getMessage()); } });
            }
        }, "QrSaleHost").start();
    }

    private void receipt(boolean customer, boolean traceOnly) {
        if (closing || busy || (preview != null && preview.isShowing())) return;
        busy(true);
        new Thread(() -> {
            try {
                Slip.useFont(getApplicationContext());
                List<Slip.Line> lines = new ArrayList<>();
                if (!customer && last != null && TerminalConfig.get(this).printPacketTrace()) {
                    for (String line : last.packetTrace().split("\n")) {
                        for (int start = 0; start < line.length(); start += Slip.W)
                            lines.add(Slip.text(line.substring(start, Math.min(start + Slip.W, line.length()))));
                    }
                }
                if (traceOnly && last != null) {
                    lines.add(Slip.text("QR CHECK RESULT"));
                    lines.add(Slip.text(last.displayMessage()));
                    if (!last.error.isEmpty()) lines.add(Slip.text(last.error));
                }
                if (!traceOnly && session != null && session.record.approved)
                    lines.addAll(QrSlip.receipt(getApplicationContext(), session.record,
                            customer ? "CUSTOMER COPY" : "MERCHANT COPY", historical));
                Bitmap bitmap = Slip.toBitmap(lines, Slip.bankLogo(getApplicationContext(), Slip.LOGO_W));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy(false); stopIdleTimeout();
                    preview = new ReceiptPreviewDialog(this, traceOnly ? "QR PACKET TRACE" : "QR SALE", bitmap, printer, () -> {
                        preview = null; resetIdleTimeout();
                        if (!traceOnly && customer) { returnHome(); return; }
                        if (!traceOnly && !customer) {
                            boolean isSuccessful = session != null && session.record != null && session.record.approved;
                            boolean customerEnabled = com.hjq.demo.ans8583.TerminalConfig.get(getApplicationContext()).customerCopyEnabled();
                            if (isSuccessful && customerEnabled) {
                                auxiliary = new android.app.AlertDialog.Builder(this)
                                        .setTitle("Print Customer Copy?").setMessage("Print a customer copy?")
                                        .setPositiveButton("YES", (d, w) -> receipt(true, false))
                                        .setNegativeButton("NO", (d, w) -> returnHome()).setCancelable(false).create();
                                auxiliary.show(); trackDialogInteraction(auxiliary);
                            } else {
                                returnHome();
                            }
                        }
                    }, !traceOnly); preview.show(); trackDialogInteraction(preview);
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { busy(false); toast("Receipt unavailable: " + e.getMessage()); } });
            }
        }, "QrReceipt").start();
    }
    private void returnHome() {
        startActivity(new android.content.Intent(this, PayActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    private void leave() { finish(); }
    @Override protected void onIdleTimeout() { finish(); }
    @Override protected boolean idleTimeoutEnabled() { return !closing && !busy && (preview == null || !preview.isShowing()); }
    @Override protected int idleTimeoutSeconds() { return TerminalConfig.get(this).qrTimeoutSec(); }
    @Override protected void resetIdleTimeout() {
        super.resetIdleTimeout();
        TextView timer = findViewById(R.id.qr_timer);
        if (timer != null) timer.setText("Idle " + idleTimeoutSeconds() + "s");
    }
    @Override public void finish() {
        if (closing) return;
        closing = true; stopIdleTimeout();
        try {
            if (!historical) {
                if (session != null) BatchStore.get(this).dismissQr(session.reference);
                else BatchStore.get(this).finishQr();
            }
        } catch (RuntimeException e) {
            closing = false;
            if (status != null) status.setText(e.getMessage());
            resetIdleTimeout(); return;
        }
        if (code != null) { code.setImageDrawable(null); code.setVisibility(View.GONE); }
        if (input != null) input.setText("");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (preview != null) preview.dismiss();
        if (auxiliary != null) auxiliary.dismiss();
        super.finish();
    }
    @Override protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && !isFinishing()) finish();
    }
    @Override protected void onSaveInstanceState(android.os.Bundle out) {
        if (session != null) {
            out.putString("qr_reference", session.reference);
            out.putBoolean("qr_historical", historical);
            if (session.record.approved) out.putString("qr_approved", session.record.serialize());
        }
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() { leave(); }
    @Override public void onServiceConnected(ISdkServiceManager sdk) {
        try {
            pinpad = IPinPadBinderService.Stub.asInterface(sdk.getService(ServiceID.SERVICE_ID_PINPAD));
            printer = IPrinterBinderService.Stub.asInterface(sdk.getService(ServiceID.SERVICE_ID_PRINTER));
        } catch (Exception e) { toast("Terminal services unavailable"); }
    }
    @Override protected void onDestroy() { if (preview != null) preview.dismiss(); if (auxiliary != null) auxiliary.dismiss(); super.onDestroy(); }
}

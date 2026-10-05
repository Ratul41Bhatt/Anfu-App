package com.hjq.demo;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Bitmap;
import com.hjq.demo.ans8583.*;
import com.hjq.demo.ui.activity.*;
import java.io.File;
import java.io.FileOutputStream;

/** Emulator-only visual/lifecycle regression. All data stays in the emulator app sandbox. */
public final class QrPreviewInstrumentation extends Instrumentation {
    private boolean moneyOnly;
    private boolean kioskOnly;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args); moneyOnly = args != null && "true".equals(args.getString("moneyOnly"));
        kioskOnly = args != null && "true".equals(args.getString("kioskOnly")); start();
    }
    private Activity open(Class<? extends Activity> type, String ref) {
        Intent intent = new Intent(getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (ref != null) intent.putExtra("qr_reference", ref);
        Activity activity = startActivitySync(intent); waitForIdleSync(); return activity;
    }
    private void screenshot(String name) throws Exception {
        Thread.sleep(5000); waitForIdleSync();
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new IllegalStateException("No screenshot");
        try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getFilesDir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!android.os.Build.MODEL.contains("sdk_gphone") && !android.os.Build.FINGERPRINT.contains("generic"))
                throw new IllegalStateException("Synthetic data tests are restricted to the emulator");
            if (kioskOnly) {
                kioskChecks();
                result.putString("stream", "Managed kiosk blocks Home and Recents; authenticated Settings path unlocks and return relocks.\n");
                finish(Activity.RESULT_OK, result); return;
            }
            TerminalConfig tc = TerminalConfig.get(getTargetContext());
            tc.setTid("12345678"); tc.setMid("123456789012345");
            tc.setQrSettings("009", "6000050000", "5411");
            tc.setQrTimeoutSec(60);
            if (moneyOnly) {
                moneyEntryChecks(tc);
                result.putString("stream", "QR automatic keyboard, cent entry/deletion, Tip and invoice-to-Tip Adjust entry passed.\n");
                finish(Activity.RESULT_OK, result); return;
            }
            com.hjq.demo.utils.Slip.useFont(getTargetContext());
            TxnRecord sample = new TxnRecord(); sample.approved = true; sample.entryMode = "840";
            sample.amountMinor = "12000"; sample.invoice = "000123"; sample.stan = "000456";
            sample.date = "0929"; sample.time = "092530"; sample.rrn = "626412345678"; sample.authCode = "123456";
            Bitmap receipt = com.hjq.demo.utils.Slip.toBitmap(com.hjq.demo.utils.QrSlip.receipt(
                    getTargetContext(), sample, "MERCHANT COPY", false),
                    com.hjq.demo.utils.Slip.bankLogo(getTargetContext(), com.hjq.demo.utils.Slip.LOGO_W));
            try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getFilesDir(), "qr-receipt.png"))) {
                receipt.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Activity entry = open(QrSaleActivity.class, null); screenshot("qr-entry");
            runOnMainSync(entry::finish); waitForIdleSync();
            DbblQr.Session session = OnlineHost.createQrSale(getTargetContext(), 12000);
            Activity payment = open(QrSaleActivity.class, session.reference); screenshot("qr-payment");
            runOnMainSync(payment::finish); waitForIdleSync();
            if (BatchStore.get(getTargetContext()).qrSession() != null) throw new AssertionError("QR survived exit");
            for (int i = 0; i < 4; i++) {
                DbblQr.Session s = OnlineHost.createQrSale(getTargetContext(), 5000 + i * 100);
                if (i % 2 == 0) {
                    s.record.approved = true; s.record.hostOnline = true;
                    s.record.rrn = "123456789012"; s.record.authCode = "123456";
                    BatchStore.get(getTargetContext()).completeQr(s);
                }
                BatchStore.get(getTargetContext()).dismissQr(s.reference);
            }
            Activity report = open(QrReportsActivity.class, null); screenshot("qr-reports-paid");
            runOnMainSync(() -> report.findViewById(R.id.qr_report_pending).performClick());
            screenshot("qr-reports-pending");
            runOnMainSync(report::finish); waitForIdleSync();
            tc.setQrTimeoutSec(10);
            Activity timeout = open(QrSaleActivity.class, null);
            Thread.sleep(11500); waitForIdleSync();
            if (!timeout.isFinishing() && !timeout.isDestroyed()) throw new AssertionError("QR idle timeout did not close");
            tc.setQrTimeoutSec(60);
            result.putString("stream", "QR screens captured; exit clears active QR; dedicated idle timeout passed.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void type(String value) { sendStringSync(value); waitForIdleSync(); }
    private void kioskChecks() throws Exception {
        Activity admin = open(AdminActivity.class, null); Thread.sleep(1200); waitForIdleSync();
        android.app.ActivityManager manager = (android.app.ActivityManager) getTargetContext().getSystemService(android.content.Context.ACTIVITY_SERVICE);
        if (manager.getLockTaskModeState() != android.app.ActivityManager.LOCK_TASK_MODE_LOCKED)
            throw new AssertionError("Not in managed kiosk: " + com.hjq.demo.utils.TerminalKiosk.status());
        try (android.os.ParcelFileDescriptor fd = getUiAutomation().executeShellCommand("input keyevent 3")) { Thread.sleep(700); }
        try (android.os.ParcelFileDescriptor fd = getUiAutomation().executeShellCommand("input keyevent 187")) { Thread.sleep(700); }
        runOnMainSync(() -> { if (!admin.hasWindowFocus()) throw new AssertionError("Home/Recents escaped POS"); });
        screenshot("managed-kiosk");
        runOnMainSync(() -> com.hjq.demo.utils.TerminalLockdown.openSettings(admin));
        Thread.sleep(1800); waitForIdleSync();
        if (manager.getLockTaskModeState() != android.app.ActivityManager.LOCK_TASK_MODE_NONE)
            throw new AssertionError("Administrator could not unlock Settings");
        android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        if (root == null || !"com.android.settings".contentEquals(root.getPackageName()))
            throw new AssertionError("Settings did not open");
        Activity returned = open(AdminActivity.class, null); Thread.sleep(1200); waitForIdleSync();
        if (manager.getLockTaskModeState() != android.app.ActivityManager.LOCK_TASK_MODE_LOCKED)
            throw new AssertionError("Return did not relock POS");
        runOnMainSync(() -> { com.hjq.demo.utils.TerminalKiosk.leaveForSettings(returned); returned.finish(); });
    }
    private void expect(Activity activity, int id, String value) {
        runOnMainSync(() -> {
            String actual = ((android.widget.TextView) activity.findViewById(id)).getText().toString();
            if (!value.equals(actual)) throw new AssertionError(value + " != " + actual);
        });
    }
    private void moneyEntryChecks(TerminalConfig tc) throws Exception {
        Activity qr = open(QrSaleActivity.class, null);
        Thread.sleep(1200);
        runOnMainSync(() -> {
            android.view.WindowInsets insets = qr.getWindow().getDecorView().getRootWindowInsets();
            if (android.os.Build.VERSION.SDK_INT >= 30 && !insets.isVisible(android.view.WindowInsets.Type.ime()))
                throw new AssertionError("QR keyboard did not open automatically");
        });
        type("1234"); expect(qr, R.id.qr_amount_input, "12.34");
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DEL); waitForIdleSync();
        expect(qr, R.id.qr_amount_input, "1.23");
        screenshot("qr-money-keyboard"); runOnMainSync(qr::finish); waitForIdleSync();

        Intent saleTip = new Intent(getTargetContext(), TipActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(TipActivity.BASE_AMOUNT, 10000L);
        Activity tip = startActivitySync(saleTip); Thread.sleep(700); waitForIdleSync();
        type("2500"); expect(tip, R.id.tip_input, "25.00"); expect(tip, R.id.tip_total_amount, "TK 125.00");
        screenshot("tip-money-keyboard"); runOnMainSync(tip::finish); waitForIdleSync();

        tc.setTipSettings(true, true, 50);
        TxnRecord original = new TxnRecord(); original.type = "SALE"; original.approved = true;
        original.amountMinor = "10000"; original.baseAmountMinor = "10000";
        original.invoice = "998811"; original.stan = "998811";
        BatchStore.get(getTargetContext()).add(original);
        Intent adjust = new Intent(getTargetContext(), TipActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(TipActivity.ADJUST, true);
        Activity tipAdjust = startActivitySync(adjust); Thread.sleep(700); waitForIdleSync();
        type("998811"); expect(tipAdjust, R.id.tip_input, "998811");
        runOnMainSync(() -> tipAdjust.findViewById(R.id.tip_continue).performClick()); waitForIdleSync();
        expect(tipAdjust, R.id.tip_input, "0.00"); type("1250");
        expect(tipAdjust, R.id.tip_input, "12.50"); expect(tipAdjust, R.id.tip_total_amount, "TK 112.50");
        screenshot("tip-adjust-money-keyboard"); runOnMainSync(tipAdjust::finish); waitForIdleSync();

        Activity admin = open(AdminActivity.class, null);
        runOnMainSync(() -> {
            admin.findViewById(R.id.btn_opt_param).performClick();
            ((android.widget.EditText) admin.findViewById(R.id.et_qr_timeout)).setText("85");
            ((android.widget.EditText) admin.findViewById(R.id.et_qr_nii)).setText("");
            admin.findViewById(R.id.btn_save_qr).performClick();
        });
        if (tc.qrTimeoutSec() != 85 || !tc.qrNiiOverride().isEmpty()) throw new AssertionError("QR Save button failed");
        runOnMainSync(admin::finish); waitForIdleSync();
        Activity reopened = open(AdminActivity.class, null);
        expect(reopened, R.id.et_qr_timeout, "85"); expect(reopened, R.id.et_qr_nii, "");
        runOnMainSync(reopened::finish); waitForIdleSync();
        tc.setQrTimeoutSec(60);
    }
}

package com.hjq.demo.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hjq.demo.utils.PrintUtil;
import com.szanfu.sdk.api.error.ErrorCode;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;

/** Shows the exact printer bitmap and advances only after a printer result or explicit skip. */
public final class ReceiptPreviewDialog extends Dialog {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Activity activity;
    private final Bitmap bitmap;
    private final IPrinterBinderService printer;
    private final Runnable onDone;
    private ScrollView scroll;
    private long printStartedAt;
    private long expectedPrintMs;
    private String printIssue = "";
    // The SDK exposes only a final callback, not paper position. Use the last
    // successful job's observed duration for animation, never to decide success.
    private final Runnable scrollFrame = new Runnable() {
        @Override public void run() {
            if (!printing || !isShowing()) return;
            long elapsed = android.os.SystemClock.elapsedRealtime() - printStartedAt;
            int range = scrollRange();
            if (range > 0 && expectedPrintMs > 0) {
                float progress = Math.min(0.96f, Math.max(0f, elapsed / (float) expectedPrintMs));
                scroll.scrollTo(0, Math.round(range * progress));
            }
            main.postDelayed(this, 16);
        }
    };
    private TextView status;
    private boolean printing;
    private int attempt;
    private boolean continued;
    private final boolean autoContinue;

    public ReceiptPreviewDialog(Activity activity, String title, Bitmap bitmap,
                                IPrinterBinderService printer, Runnable onDone) {
        this(activity, title, bitmap, printer, onDone, true);
    }

    public ReceiptPreviewDialog(Activity activity, String title, Bitmap bitmap,
                                IPrinterBinderService printer, Runnable onDone, boolean autoContinue) {
        super(activity);
        this.autoContinue = autoContinue;
        this.activity = activity;
        this.bitmap = bitmap;
        this.printer = printer;
        this.onDone = onDone;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(238, 242, 247));
        int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextSize(17);
        heading.setTextColor(Color.rgb(20, 43, 78));
        heading.setGravity(Gravity.CENTER);
        root.addView(heading);

        status = new TextView(activity);
        status.setTextSize(13);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, pad, 0, pad);
        root.addView(status);
        scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(ScrollView.OVER_SCROLL_NEVER);
        ImageView receipt = new ImageView(activity);
        receipt.setBackgroundColor(Color.WHITE);
        receipt.setAdjustViewBounds(true);
        receipt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        receipt.setContentDescription("Receipt preview");
        receipt.setImageBitmap(bitmap);
        LinearLayout paper = new LinearLayout(activity);
        paper.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        // Ensure the slip paper font size and print preview font size match physically.
        // 58mm thermal paper has ~48mm (1.88976 inches) printable width (384 printer dots at ~203.2 DPI).
        android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        float xdpi = dm.xdpi;
        if (xdpi < 100 || xdpi > 600 || Float.isNaN(xdpi)) {
            xdpi = dm.densityDpi > 0 ? dm.densityDpi : 203.2f;
        }
        int physicalPaperWidthPx = Math.round((48.0f / 25.4f) * xdpi);
        int availableWidth = dm.widthPixels - 2 * pad;
        int previewWidth = bitmap == null ? availableWidth : Math.min(physicalPaperWidthPx, availableWidth);
        int previewHeight = bitmap == null ? 0
                : Math.round(bitmap.getHeight() * (previewWidth / (float) bitmap.getWidth()));
        paper.addView(receipt, new LinearLayout.LayoutParams(previewWidth, previewHeight));
        scroll.addView(paper, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        setOnShowListener(d -> {
            getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            if (printer == null) {
                printIssue = "Receipt not printed: printer unavailable. Reprint from Reports.";
                status.setText(printIssue);
                main.postDelayed(this::advance, 1500);
            } else {
                print();
            }
        });
    }

    private void print() {
        if (printing || printer == null || bitmap == null) return;
        printing = true;
        printIssue = "";
        scroll.scrollTo(0, 0);
        final int currentAttempt = ++attempt;
        status.setText("Printing... Please wait");
        // A long slip goes out as several short jobs (see SlipPrinter), so the
        // timeout grows with the number of jobs.
        final int jobs = Math.max(1, com.hjq.demo.utils.SlipPrinter.jobCount(bitmap));
        main.postDelayed(() -> {
            if (isShowing() && printing && attempt == currentAttempt) {
                status.setText("Printer timeout. Check paper.");
                printIssue = "Printer completion unconfirmed.";
                main.removeCallbacks(scrollFrame);
                advance();
            }
        }, 25000L + (jobs - 1) * 8000L);
        new Thread(() -> {
            try {
                main.post(() -> startScroll(currentAttempt));
                com.hjq.demo.utils.SlipPrinter.print(printer, bitmap,
                        (success, message) -> main.post(() -> finishPrint(currentAttempt,
                                success, message)));
            } catch (Exception e) {
                main.post(() -> finishPrint(currentAttempt, false, e.getMessage()));
            }
        }, "ReceiptPrinter").start();
    }

    private void finishPrint(int currentAttempt, boolean success, String message) {
        if (!isShowing() || currentAttempt != attempt || !printing) return;
        printing = false;
        main.removeCallbacksAndMessages(null);
        if (success) {
            long elapsed = android.os.SystemClock.elapsedRealtime() - printStartedAt;
            if (printStartedAt > 0 && elapsed > 300 && elapsed < 45000 && bitmap != null) {
                activity.getSharedPreferences("receipt_animation", Context.MODE_PRIVATE).edit()
                        .putFloat("ms_per_row", elapsed / (float) bitmap.getHeight()).apply();
            }
            scroll.smoothScrollTo(0, scrollRange());
            status.setText("Printing complete");
            main.postDelayed(this::advance, 600);
        } else {
            printIssue = "Receipt not printed: " + (message == null ? "Printer error" : message)
                    + ". Reprint from Reports.";
            status.setText("Print failed: " + (message == null ? "Printer error" : message));
            android.widget.Toast.makeText(activity, "Receipt not printed. Reprint from Reports.",
                    android.widget.Toast.LENGTH_LONG).show();
            main.postDelayed(this::advance, 1500);
        }
    }

    public String printIssue() { return printIssue; }

    private int scrollRange() {
        return scroll.getChildCount() == 0 ? 0
                : Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
    }

    private void startScroll(int currentAttempt) {
        if (!printing || !isShowing() || currentAttempt != attempt) return;
        printStartedAt = android.os.SystemClock.elapsedRealtime();
        float msPerRow = activity.getSharedPreferences("receipt_animation", Context.MODE_PRIVATE)
                .getFloat("ms_per_row", 2f);
        expectedPrintMs = Math.max(800, Math.min(40000, (long) (bitmap.getHeight() * msPerRow)));
        main.post(scrollFrame);
    }

    private void advance() {
        if (continued || !isShowing()) return;
        continued = true;
        dismiss();
        if (onDone != null && !activity.isFinishing() && !activity.isDestroyed()) onDone.run();
    }

    @Override
    public void dismiss() {
        main.removeCallbacksAndMessages(null);
        ++attempt;
        super.dismiss();
    }
}

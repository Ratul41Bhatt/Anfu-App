package com.hjq.demo.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;

import com.szanfu.sdk.api.error.ErrorCode;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;

/**
 * Prints a slip as ONE printer job, smoothly, without losing part of it.
 *
 * <p>Field report 05-10: with Debug ON the pre-auth slip printed in full; with
 * Debug OFF only about half came out. 2.0.15 split the slip into short jobs,
 * which printed everything but stopped and restarted the paper between jobs.</p>
 *
 * <p>The printer log of the 767-row slip shows the first ~360 rows (about
 * 17 KB) taken in at once and the rest only at paper speed. A job longer than
 * that loses about that much, and Debug ON hides it because the packet trace
 * is printed first - the part that went missing was trace, not slip.</p>
 *
 * <p>So the slip now goes out as a single job with a blank leader of
 * {@link #LEADER_ROWS} white rows in front of it, which takes the place the
 * packet trace took with Debug ON. One job, one continuous feed.</p>
 */
public final class SlipPrinter {

    private static final String TAG = "SlipPrinter";

    /**
     * White rows sent ahead of the slip. 2.0.16 used 420; field report: they
     * all came out as blank paper, so nothing at the head is being dropped.
     * No leader - the slip prints from its first line.
     */
    public static final int LEADER_ROWS = 0;

    /**
     * Two text lines of extra white paper under every slip, so the last printed
     * line clears the tear bar instead of being torn through.
     */
    public static final int TRAILER_ROWS = 2 * Slip.lineHeightOf(Slip.SMALL);

    public interface Listener {
        /** Called once: true when the job printed. */
        void onDone(boolean success, String message);
    }

    private SlipPrinter() { }

    /** Always one job now; kept for the preview's timeout arithmetic. */
    public static int jobCount(Bitmap bmp) {
        return bmp == null ? 0 : 1;
    }

    public static void print(final IPrinterBinderService printer, final Bitmap bmp,
                             final Listener listener) {
        if (printer == null || bmp == null) {
            listener.onDone(false, "Printer unavailable");
            return;
        }
        try {
            Bitmap job = withLeader(bmp);
            byte[] data = PrintUtil.getBitmapData(job);
            Log.i(TAG, "printing " + bmp.getHeight() + " rows + " + TRAILER_ROWS
                    + " blank rows under it, as one job");
            printer.printBitmap(0, data, PrintUtil.getPaddingBitWidth(job.getWidth()),
                    job.getHeight(), new IPrinterCallback.Stub() {
                        @Override
                        public void onResult(int code, String message, int subCode) {
                            if (code != ErrorCode.SUCCESS) {
                                Log.e(TAG, "print failed: " + code + " " + message);
                            }
                            listener.onDone(code == ErrorCode.SUCCESS, message);
                        }
                    });
        } catch (Throwable t) {
            Log.e(TAG, "print could not be sent", t);
            listener.onDone(false, t.getMessage());
        }
    }

    /** The slip with {@link #LEADER_ROWS} white rows above it. */
    private static Bitmap withLeader(Bitmap bmp) {
        if (LEADER_ROWS <= 0 && TRAILER_ROWS <= 0) {
            return bmp;
        }
        int w = Math.min(bmp.getWidth(), PrintUtil.MAX_BIT_WIDTH);
        Bitmap out = Bitmap.createBitmap(w, bmp.getHeight() + LEADER_ROWS + TRAILER_ROWS,
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        c.drawBitmap(bmp, 0, LEADER_ROWS, null);
        return out;
    }
}

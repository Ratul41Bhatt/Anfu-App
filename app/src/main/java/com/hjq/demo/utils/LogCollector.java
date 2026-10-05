package com.hjq.demo.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Capture the app's own log to a file on the terminal, with no PC involved.
 *
 * <p>An unprivileged Android app can read its own logcat output by running the
 * {@code logcat} binary - the system filters the result to the calling app's
 * UID. That is enough to collect everything this application writes, which is
 * what a UAT engineer at the bank needs when there is no Android Studio to
 * hand.</p>
 *
 * <p>Files land in {@code Android/data/&lt;package&gt;/files/logs/} on the
 * device's shared storage, so they can be copied off over USB without root or
 * any developer tooling.</p>
 */
public final class LogCollector {

    /** Only these tags are kept - the rest is Android noise. */
    private static final String[] TAGS = {
            "OnlineHost", "LogonData", "Iso8583", "Emv55", "BatchStore",
            "KeyManager", "FindCardActivity", "AdminActivity", "PayActivity",
            "LogUtils",
    };

    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    private LogCollector() { }

    /** Where the captures are written. */
    public static File logDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), "logs");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public static final class Result {
        public boolean ok;
        public File file;
        public int lines;
        public String error = "";
        public String tail = "";     // last few interesting lines, for the screen
    }

    /**
     * Dump the log buffer to a timestamped file.
     *
     * @param maxLines cap on how many matching lines to keep, newest last
     */
    public static Result capture(Context ctx, int maxLines) {
        Result r = new Result();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time"});
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            List<String> kept = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null) {
                if (matches(line)) {
                    kept.add(line);
                    if (kept.size() > maxLines) {
                        kept.remove(0);
                    }
                }
            }
            in.close();

            r.file = new File(logDir(ctx), "log_" + STAMP.format(new Date()) + ".txt");
            OutputStreamWriter out =
                    new OutputStreamWriter(new FileOutputStream(r.file), "UTF-8");
            out.write("=== app log capture " + new Date() + " ===\n");
            for (String s : kept) {
                out.write(s);
                out.write('\n');
            }
            out.flush();
            out.close();

            r.lines = kept.size();
            r.ok = true;
            r.tail = tail(kept, 12);
            return r;
        } catch (Exception e) {
            r.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            return r;
        }
    }

    /**
     * The lines that matter for a key or PIN problem, for printing on a slip.
     * Full captures run to thousands of lines and are not worth paper.
     */
    public static String keyAndPinSummary(Context ctx) {
        StringBuilder b = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time"});
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            List<String> hits = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null) {
                if (isInteresting(line)) {
                    hits.add(strip(line));
                    if (hits.size() > 40) {
                        hits.remove(0);
                    }
                }
            }
            in.close();
            for (String s : hits) {
                b.append(s).append('\n');
            }
        } catch (Exception e) {
            b.append("log read failed: ").append(e.getMessage()).append('\n');
        }
        return b.length() == 0 ? "(nothing recorded yet - run a logon or a sale)\n" : b.toString();
    }

    /** Delete captures older than the newest few, so storage does not fill. */
    public static void prune(Context ctx, int keep) {
        File[] files = logDir(ctx).listFiles();
        if (files == null || files.length <= keep) {
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keep; i < files.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    // --------------------------------------------------------------- helpers

    private static boolean matches(String line) {
        for (String t : TAGS) {
            if (line.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /** Lines that name a key, a PIN block, or a host verdict. */
    private static boolean isInteresting(String line) {
        return line.contains("posWriteWorkingKey")
                || line.contains("dalPosWriteKey")
                || line.contains("working key")
                || line.contains("PIN block")
                || line.contains("onGetPinBlock")
                || line.contains("TMK")
                || line.contains("KCV")
                || line.contains("DE62")
                || line.contains("showPinPad")
                || line.contains("GAC1 ODA")
                || line.contains("ONLINE RESULT")
                || line.contains("posMasterKey");
    }

    /** Drop the date/pid preamble so a line fits the receipt width. */
    private static String strip(String line) {
        int i = line.indexOf(": ");
        return i > 0 && i < 60 ? line.substring(i + 2) : line;
    }

    private static String tail(List<String> lines, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = Math.max(0, lines.size() - n); i < lines.size(); i++) {
            b.append(strip(lines.get(i))).append('\n');
        }
        return b.toString();
    }
}

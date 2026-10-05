package com.hjq.demo.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Slip layout and rendering, shared by every screen that prints.
 *
 * <p>This used to live inside {@code FindCardActivity} as private methods,
 * which meant the reports menu had no way to print anything. It is the same
 * model and the same renderer, lifted out unchanged: a list of {@link Line},
 * turned into either a monochrome bitmap for the printer or plain text for the
 * on-screen result.</p>
 */
public final class Slip {

    /** Characters per line at the small font - the table columns rely on it. */
    public static final int W = 32;

    /**
     * How wide a logo prints, in pixels.
     *
     * <p>The head is 384 dots across and that is the hard limit - a logo cannot
     * be made larger than the paper. Callers used to pass 340 by hand in four
     * places; this is the full width, which is as big as it goes.</p>
     */
    public static final int LOGO_W = 384;
    public static final String SEP = "--------------------------------";

    public static final int BOLD = 1;
    public static final int CENTER = 2;
    public static final int RULE = 4;
    public static final int BLANK = 8;
    public static final int LOGO = 16;

    /**
     * Text sizes, chosen against the reference Move2500 slip.
     *
     * <p>They used to be 40 / 30 / 25 and the slip came out more than twice
     * the length of the terminal it is meant to match - the same transaction
     * ran 1399 pixels against the reference's 656. Very little of that was
     * extra content; it was thirty-odd lines each a third taller than they
     * needed to be. Paper costs money and a merchant who has to fold a receipt
     * twice reads it once.</p>
     */
    public static final int BIG = 30;
    public static final int MID = 24;
    public static final int SMALL = 21;

    /**
     * The merchant name in the letterhead.
     *
     * <p>Two points above {@link #MID}. It is the line a merchant sorting a
     * day's paper looks for first, and at MID it sat at the same weight as the
     * rows below it. Its own constant rather than a bumped MID, so widening it
     * again does not silently enlarge every other MID line on the slip.</p>
     */
    public static final int MERCHANT = 26;

    /**
     * Line spacing as a multiple of the text size.
     *
     * <p>Roboto reports generous vertical metrics - ascent plus descent is
     * about 1.24 times the em - which is right for a page and wasteful on a
     * 384-pixel roll. Spacing from the size rather than from the metrics, with
     * a factor tuned to the reference slip, keeps descenders clear without
     * paying for the font's page leading on every line.</p>
     */
    private static final float LINE_FACTOR = 1.16f;

    /** A deliberate blank line. Was 14, which is nearly a full line of text. */
    private static final int BLANK_H = 8;

    /**
     * Column separators inside a line's left text.
     *
     * <p>The slip font is proportional, so a row cannot be aligned by padding
     * it with spaces - which is what {@link #row3} used to do. Sixteen spaces
     * are not the width of sixteen digits in Roboto, so every table came out
     * with its columns leaning. A row that carries one of these characters is
     * split and each part is drawn at a measured position instead.</p>
     *
     * <p>Two of them because there are two kinds of middle column.
     * {@code COL_R} right-aligns it - counts and quantities read as numbers,
     * so their digits must line up on the right. {@code COL_L} left-aligns it,
     * which is what the currency marker in an amount block needs: "TK:" has to
     * start at the same x under AMOUNT, TIP and TOTAL however wide those
     * words are.</p>
     */
    public static final char COL_R = '\u001F';
    public static final char COL_L = '\u001E';

    /** Where the middle column sits, as a fraction of the printable width. */
    private static final float MID_COL_RIGHT = 0.66f;
    private static final float MID_COL_LEFT = 0.38f;

    private Slip() { }

    /** One printed line: left text, optional right-aligned text, size, flags. */
    public static final class Line {
        public final String left;
        public final String right;
        public final int size;
        public final int flags;

        public Line(String left, String right, int size, int flags) {
            this.left = left == null ? "" : left;
            this.right = right == null ? "" : right;
            this.size = size;
            this.flags = flags;
        }
    }

    // -------------------------------------------------------------- builders

    public static Line text(String s) {
        return new Line(s, "", SMALL, 0);
    }

    public static Line bold(String s) {
        return new Line(s, "", SMALL, BOLD);
    }

    public static Line centre(String s, int size) {
        return new Line(s, "", size, BOLD | CENTER);
    }

    public static Line pair(String l, String r) {
        return new Line(l, r, SMALL, 0);
    }

    public static Line blank() {
        return new Line("", "", 0, BLANK);
    }

    public static Line rule() {
        return new Line(SEP, "", SMALL, RULE);
    }

    /**
     * One table row: label left, middle and amount in measured columns.
     *
     * <p>Was a {@code %-13s%6s%13s} format string. That works in a monospace
     * font and only in a monospace font - once the slip moved to Roboto the
     * padding stopped matching the glyph widths and every table leaned. The
     * parts are kept separate here and placed by measurement at print time,
     * so the columns line up whatever the font does.</p>
     */
    public static String row3(String label, String middle, String amount) {
        return nz(label) + COL_R + nz(middle) + COL_R + nz(amount);
    }

    /**
     * An amount row: "AMOUNT", the currency marker, then the figure.
     *
     * <p>The marker is left-aligned so it starts at the same place on every
     * row of the block, and the figure is right-aligned so the decimal points
     * line up. That is how the reference slip prints its AMOUNT / TIP / TOTAL
     * block, and it is the one part of a receipt people actually read down.</p>
     */
    public static String amountRow(String label, String marker, String value) {
        return nz(label) + COL_L + nz(marker) + COL_L + nz(value);
    }

    /** The three parts of a column row, or null when this is an ordinary line. */
    private static String[] columns(String s) {
        if (s == null) {
            return null;
        }
        char sep = s.indexOf(COL_R) >= 0 ? COL_R : (s.indexOf(COL_L) >= 0 ? COL_L : 0);
        if (sep == 0) {
            return null;
        }
        String[] parts = s.split(String.valueOf(sep), -1);
        if (parts.length != 3) {
            return null;
        }
        return new String[] {parts[0], parts[1], parts[2], String.valueOf(sep)};
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static String count3(int v) {
        return String.format(Locale.US, "%03d", Math.max(0, v));
    }

    /** Minor units to a printable amount, with the sign kept. */
    public static String money(long minor) {
        long a = Math.abs(minor);
        return (minor < 0 ? "-" : "") + String.format(Locale.US, "%d.%02d", a / 100, a % 100);
    }

    // ------------------------------------------------------------- rendering

    /** Plain text for the on-screen result page. */
    public static String toText(List<Line> lines, String logoText) {
        StringBuilder s = new StringBuilder();
        for (Line ln : lines) {
            if ((ln.flags & LOGO) != 0) {
                s.append(centreText(logoText)).append('\n');
            } else if ((ln.flags & RULE) != 0) {
                s.append(SEP).append('\n');
            } else if ((ln.flags & BLANK) != 0) {
                s.append('\n');
            } else if (columns(ln.left) != null) {
                // The on-screen preview is monospace, so here the padded form
                // is the right one.
                String[] col = columns(ln.left);
                if (col[1].isEmpty()) {
                    s.append(leftRight(col[0], col[2])).append('\n');
                } else {
                    s.append(String.format(Locale.US, "%-13s%6s%13s",
                            trim(col[0], 13), col[1], col[2])).append('\n');
                }
            } else if ((ln.flags & CENTER) != 0) {
                s.append(centreText(ln.left)).append('\n');
            } else if (!ln.right.isEmpty()) {
                s.append(leftRight(ln.left, ln.right)).append('\n');
            } else {
                s.append(ln.left).append('\n');
            }
        }
        return s.toString();
    }

    public static String centreText(String t) {
        if (t == null) {
            t = "";
        }
        if (t.length() >= W) {
            return t;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < (W - t.length()) / 2; i++) {
            b.append(' ');
        }
        return b.append(t).toString();
    }

    public static String leftRight(String l, String r) {
        if (l == null) {
            l = "";
        }
        if (r == null) {
            r = "";
        }
        int gap = W - l.length() - r.length();
        StringBuilder b = new StringBuilder(l);
        if (gap < 1) {
            b.append(' ');
        } else {
            for (int i = 0; i < gap; i++) {
                b.append(' ');
            }
        }
        return b.append(r).toString();
    }

    /**
     * Paint for every line.
     *
     * <p>Anti-aliasing is off deliberately. {@code PrintUtil.getBitmapData}
     * prints a pixel only when its blue channel is below 128; anti-aliased
     * glyph edges are mid-grey, land above that and are dropped, so each
     * character reaches the paper thinner than it was drawn. With it off every
     * drawn pixel is pure black and survives the threshold intact.</p>
     *
     * <p><b>No synthetic bolding.</b> This used to set {@code fakeBoldText} and
     * then draw {@code FILL_AND_STROKE} with a stroke on top, which was three
     * different ways of making the same glyph heavier. That was written for a
     * thin monospace face; the slip font is Roboto <em>SemiBold</em>, already
     * carrying its weight in the outlines. Stacking the other two on top of it
     * filled the counters - the holes in a, e, o, 8 - and on thermal paper,
     * which spreads ink of its own, the result came out as blobs. Weight
     * belongs in the typeface.</p>
     */
    public static TextPaint paint(int size) {
        TextPaint p = new TextPaint();
        p.setAntiAlias(false);
        p.setColor(Color.BLACK);
        p.setTextSize(size);
        p.setTypeface(face());
        p.setStyle(Paint.Style.FILL);
        // Only when the shipped font is missing and the fallback is a plain
        // monospace, which does need the help.
        if (sFace == null) {
            p.setFakeBoldText(true);
        }
        return p;
    }

    /**
     * A rule that spans the paper.
     *
     * <p>{@link #SEP} is 32 dashes, which was the full width when every glyph
     * was one monospace cell wide. In a proportional face a dash is narrower
     * than a digit, so the same 32 characters stop well short and the rule
     * reads as a stray dotted line rather than a divider. Count how many
     * actually fit instead.</p>
     */
    public static String rule(TextPaint p, int contentW) {
        return fillRule(p, contentW);
    }

    private static String fillRule(TextPaint p, int contentW) {
        float dash = p.measureText("-");
        int n = dash <= 0 ? W : (int) (contentW / dash);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append('-');
        }
        return b.toString();
    }

    /** The typeface every slip prints in. Loads the asset if it has not yet. */
    public static Typeface typeface(Context ctx) {
        useFont(ctx);
        return face();
    }

    /**
     * Draw a three-column row at measured positions.
     *
     * <p>Left hard left, right hard right, and the middle either right-aligned
     * at {@link #MID_COL_RIGHT} or left-aligned at {@link #MID_COL_LEFT}
     * depending on which separator built the row - see {@link #COL_R}.</p>
     */
    private static void drawColumns(Canvas c, String[] col, TextPaint p,
                                    int pad, int contentW, float baseline) {
        c.drawText(col[0], pad, baseline, p);
        if (!col[2].isEmpty()) {
            c.drawText(col[2], pad + contentW - p.measureText(col[2]), baseline, p);
        }
        if (col[1].isEmpty()) {
            return;
        }
        boolean rightAligned = String.valueOf(COL_R).equals(col[3]);
        float x = rightAligned
                ? pad + contentW * MID_COL_RIGHT - p.measureText(col[1])
                : pad + contentW * MID_COL_LEFT;
        // Never let the middle run into the left column.
        float floor = pad + p.measureText(col[0]) + 8;
        c.drawText(col[1], Math.max(floor, x), baseline, p);
    }

    /** The slip typeface, loaded once. */
    private static Typeface sFace;
    /** Set when the asset has already been looked for and was not there. */
    private static boolean sFaceTried;

    /** The asset the slip font is shipped as. */
    public static final String FONT_ASSET = "slip_font.ttf";

    /**
     * Load the slip typeface.
     *
     * <p>Call before printing. Cheap after the first time - the result is
     * cached, including the failure, so a build shipped without the asset does
     * not go looking for it on every line of every receipt.</p>
     */
    public static synchronized void useFont(Context ctx) {
        if (sFaceTried || ctx == null) {
            return;
        }
        sFaceTried = true;
        try {
            sFace = Typeface.createFromAsset(ctx.getAssets(), FONT_ASSET);
            LogUtils.i("Slip: printing in " + FONT_ASSET);
        } catch (Exception e) {
            // Not fatal. A missing font costs the slip its house style; a crash
            // in the printer thread costs the merchant the transaction.
            sFace = null;
            LogUtils.e("Slip: " + FONT_ASSET + " not loadable (" + e.getMessage()
                    + "), falling back to the built-in monospace");
        }
    }

    private static Typeface face() {
        return sFace != null ? sFace : Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
    }

    private static int lineHeight(int size) {
        return Math.round(size * LINE_FACTOR);
    }

    /** Line spacing for a size, so the other renderer can match it exactly. */
    public static int lineHeightOf(int size) {
        return lineHeight(size == 0 ? SMALL : size);
    }

    /** The height of a blank line, shared for the same reason. */
    public static final int BLANK_HEIGHT = BLANK_H;

    /** Shrink a line that would overflow, so nothing is silently clipped. */
    private static int fit(Line ln, int contentW) {
        int base = ln.size == 0 ? SMALL : ln.size;
        TextPaint p = paint(base);
        String[] col = columns(ln.left);
        float need;
        if (col != null) {
            need = p.measureText(col[0]) + p.measureText(col[1]) + p.measureText(col[2]) + 20;
        } else if (ln.right.isEmpty()) {
            need = p.measureText(ln.left);
        } else {
            need = p.measureText(ln.left) + p.measureText(ln.right) + 14;
        }
        if (need <= contentW) {
            return base;
        }
        return Math.max(13, (int) Math.floor(base * (contentW / need)));
    }

    /**
     * Render to a monochrome-friendly bitmap.
     *
     * @param logo optional bitmap drawn centred wherever a {@link #LOGO} line
     *             appears; null simply skips it
     */
    public static Bitmap toBitmap(List<Line> lines, Bitmap logo) {
        final int width = 384;
        final int pad = 10;
        // Feed margins. The print head sits above the tear bar, so without a
        // lead-in the first line comes out under the cutter and without a
        // lead-out the last line is torn through.
        // Blank paper before and after the slip.
        //
        // These were 64 and 96, then cut to 24 and 48 while the slip was being
        // shortened - too far. The print head sits above the tear bar, so the
        // lead-out has to cover that gap before the merchant tears: at 48 the
        // last line was coming off inside the cut. Back to a lead-out that
        // clears it, with the lead-in kept small because the roll already
        // advances a little when the head engages.
        final int leadIn = 32;
        final int leadOut = 120;
        final int contentW = width - pad * 2;

        int total = pad;
        for (Line ln : lines) {
            if ((ln.flags & LOGO) != 0) {
                total += (logo != null ? logo.getHeight() : 0) + 12;
            } else if ((ln.flags & BLANK) != 0) {
                total += BLANK_H;
            } else {
                total += lineHeight(fit(ln, contentW));
            }
        }
        total += pad + leadIn + leadOut;
        if (total < 120) {
            total = 120;
        }

        Bitmap bmp = Bitmap.createBitmap(width, total, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        int y = pad + leadIn;
        for (Line ln : lines) {
            if ((ln.flags & LOGO) != 0) {
                if (logo != null) {
                    c.drawBitmap(logo, (width - logo.getWidth()) / 2f, y, null);
                    y += logo.getHeight() + 12;
                }
                continue;
            }
            if ((ln.flags & BLANK) != 0) {
                y += BLANK_H;
                continue;
            }
            int size = fit(ln, contentW);
            TextPaint p = paint(size);
            Paint.FontMetrics fm = p.getFontMetrics();
            float baseline = y - fm.ascent;
            String[] col = columns(ln.left);
            if ((ln.flags & RULE) != 0) {
                c.drawText(fillRule(p, contentW), pad, baseline, p);
            } else if (col != null) {
                drawColumns(c, col, p, pad, contentW, baseline);
            } else if ((ln.flags & CENTER) != 0) {
                c.drawText(ln.left, (width - p.measureText(ln.left)) / 2f, baseline, p);
            } else if (!ln.right.isEmpty()) {
                c.drawText(ln.left, pad, baseline, p);
                c.drawText(ln.right, width - pad - p.measureText(ln.right), baseline, p);
            } else {
                c.drawText(ln.left, pad, baseline, p);
            }
            y += lineHeight(size);
        }
        return bmp;
    }

    /**
     * The bank logo, scaled to fit, or null when the terminal has no asset.
     *
     * <p>Lives here so the sale slip and the reports load the same image the
     * same way. They did not: FindCardActivity loaded it from assets with a
     * drawable fallback, while ReportsActivity passed null to
     * {@link #toBitmap} and so silently printed no logo at all, despite
     * emitting a LOGO line for it.</p>
     */
    public static Bitmap bankLogo(Context ctx, int maxWidth) {
        return scaled(loadAsset(ctx, "bank_logo_full.jpg"), maxWidth);
    }

    /** Bank logo at half width, for the side-by-side pair. */
    public static Bitmap bankLogoHalf(Context ctx, int maxWidth) {
        Bitmap b = loadAsset(ctx, "bank_logo_half.jpg");
        return scaled(b == null ? loadAsset(ctx, "bank_logo_full.jpg") : b, maxWidth);
    }

    /** Merchant logo at half width. Null unless the site supplied the asset. */
    public static Bitmap merchantLogoHalf(Context ctx, int maxWidth) {
        return scaled(loadAsset(ctx, "merchant_logo_half.jpg"), maxWidth);
    }

    private static Bitmap loadAsset(Context ctx, String name) {
        if (ctx == null) {
            return null;
        }
        try {
            java.io.InputStream is = ctx.getAssets().open(name);
            Bitmap b = BitmapFactory.decodeStream(is);
            is.close();
            return b;
        } catch (Exception ignore) {
            // Asset not shipped on this build - print without it.
            return null;
        }
    }

    /**
     * Scale a logo to the target width, up or down.
     *
     * <p>It used to be {@code Math.min(target, src.getWidth())}, which meant an
     * asset narrower than the target printed at its own size and no bigger -
     * so a small logo file printed small however much room the paper had, and
     * nothing on screen explained why.</p>
     *
     * <p>Height follows the width: the aspect ratio is the artwork's, and
     * stretching a bank's logo to fill space is not this code's decision to
     * make.</p>
     */
    private static Bitmap scaled(Bitmap src, int targetWidth) {
        if (src == null || src.getWidth() <= 0) {
            return null;
        }
        int w = Math.max(1, targetWidth);
        int h = Math.round(src.getHeight() * (w / (float) src.getWidth()));
        if (h <= 0) {
            return null;
        }
        return Bitmap.createScaledBitmap(src, w, h, true);
    }

    public static List<Line> list() {
        return new ArrayList<>();
    }
}

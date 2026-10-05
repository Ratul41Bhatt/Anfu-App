package com.hjq.demo.utils;

import android.content.Context;

import com.hjq.demo.ans8583.TerminalConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The BIN ranges this terminal accepts, and the scheme name each one prints.
 *
 * <p>The PAX reference validates every card against {@code card_range.json}
 * before doing anything with it - {@code SearchCardPresenter} and each
 * transaction class call {@code findCardRange(pan)} and end with
 * {@code ERR_CARD_UNSUPPORTED} / "Unsupported Card" when nothing matches. This
 * is the same table and the same rule.</p>
 *
 * <p>Two sources, in order:</p>
 * <ol>
 *   <li>the table edited in the admin panel, held in
 *       {@link TerminalConfig#cardRangesJson()};</li>
 *   <li>the shipped {@code assets/card_range.json}.</li>
 * </ol>
 * Clearing the first restores the second, so an edit can always be undone.
 *
 * <p>Ranges carry the name that goes on the receipt, which is how TAKAPAY and
 * NEXUSPAY cards print as themselves rather than being guessed at from the
 * first digit of the PAN.</p>
 */
public final class CardRangeTable {

    private static final String ASSET = "card_range.json";

    /** The table stores 12-digit bounds; every PAN is compared at that width. */
    public static final int COMPARE_LEN = 12;

    private static String assetCache;

    private CardRangeTable() { }

    /** One row of the table. */
    public static final class Range {
        public String name = "";
        public String low = "";
        public String high = "";
        public boolean saleAllowed = true;
        public boolean voidAllowed = true;
        public boolean refundAllowed = true;
        public boolean manualPanEntry = true;
        /**
         * True when this range is the acquirer's own paper - a DBBL card on a
         * DBBL terminal.
         *
         * <p>Defaults to false, which is the safe answer: a range nobody has
         * marked is somebody else's card, and calling an off-us card on-us on
         * the receipt would misstate who carries the transaction.</p>
         */
        public boolean onUs = false;

        public JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("panRangeLow", low);
            o.put("panRangeHigh", high);
            o.put("saleAllowed", saleAllowed);
            o.put("voidAllowed", voidAllowed);
            o.put("refundAllowed", refundAllowed);
            o.put("manualPanEntry", manualPanEntry);
            o.put("onUs", onUs);
            return o;
        }

        static Range from(JSONObject o) {
            Range r = new Range();
            r.name = o.optString("name", "");
            r.low = o.optString("panRangeLow", "");
            r.high = o.optString("panRangeHigh", "");
            r.saleAllowed = o.optBoolean("saleAllowed", true);
            r.voidAllowed = o.optBoolean("voidAllowed", true);
            r.refundAllowed = o.optBoolean("refundAllowed", true);
            r.manualPanEntry = o.optBoolean("manualPanEntry", true);
            r.onUs = o.optBoolean("onUs", false);
            return r;
        }

        @Override
        public String toString() {
            return name + "  " + low + " - " + high + (onUs ? "  ON-US" : "");
        }
    }

    // ------------------------------------------------------------ the table

    /** Every range, edited copy first, shipped asset otherwise. */
    public static List<Range> all(Context ctx) {
        List<Range> out = new ArrayList<>();
        String json = TerminalConfig.get(ctx).cardRangesJson();
        boolean edited = json != null && !json.trim().isEmpty();
        try {
            JSONArray arr = new JSONArray(edited ? json : readAsset(ctx));
            for (int i = 0; i < arr.length(); i++) {
                out.add(Range.from(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            LogUtils.e("CardRangeTable: cannot read the "
                    + (edited ? "edited table" : ASSET) + ": " + e.getMessage());
        }
        return out;
    }

    /** True when the operator has edited the table away from the shipped one. */
    public static boolean isEdited(Context ctx) {
        String j = TerminalConfig.get(ctx).cardRangesJson();
        return j != null && !j.trim().isEmpty();
    }

    /** Persist an edited table. */
    public static void save(Context ctx, List<Range> ranges) {
        try {
            JSONArray arr = new JSONArray();
            for (Range r : ranges) {
                arr.put(r.toJson());
            }
            TerminalConfig.get(ctx).setCardRangesJson(arr.toString());
            LogUtils.i("CardRangeTable: saved " + ranges.size() + " ranges");
        } catch (Exception e) {
            LogUtils.e("CardRangeTable.save: " + e.getMessage());
        }
    }

    /** Drop the edits and go back to the shipped table. */
    public static void restoreDefaults(Context ctx) {
        TerminalConfig.get(ctx).clearCardRanges();
        LogUtils.i("CardRangeTable: restored the shipped table");
    }

    // -------------------------------------------------------- the EMI table

    /**
     * The EMI card ranges, as entered in the admin panel.
     *
     * <p>The same {@link Range} row, kept in its own list. A card has to be in
     * the ordinary table to be acquired at all; it has to be in this one as
     * well before it can be sold on instalments. The bank issues the two BIN
     * lists separately and they do not line up - plenty of cards this terminal
     * takes have no EMI programme behind them.</p>
     *
     * <p>No shipped asset: an operator who has not been given the EMI list has
     * an empty table, and an empty table is not a table of nothing - see
     * {@link #emiAllowed}.</p>
     */
    public static List<Range> allEmi(Context ctx) {
        List<Range> out = new ArrayList<>();
        String json = TerminalConfig.get(ctx).emiCardRangesJson();
        if (json == null || json.trim().isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Range.from(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            LogUtils.e("CardRangeTable: cannot read the EMI table: " + e.getMessage());
        }
        return out;
    }

    /** Persist the EMI table. */
    public static void saveEmi(Context ctx, List<Range> ranges) {
        try {
            JSONArray arr = new JSONArray();
            for (Range r : ranges) {
                arr.put(r.toJson());
            }
            TerminalConfig.get(ctx).setEmiCardRangesJson(arr.toString());
            LogUtils.i("CardRangeTable: saved " + ranges.size() + " EMI range(s)");
        } catch (Exception e) {
            LogUtils.e("CardRangeTable.saveEmi: " + e.getMessage());
        }
    }

    /** Empty the EMI table, which turns the EMI range check off again. */
    public static void clearEmi(Context ctx) {
        TerminalConfig.get(ctx).clearEmiCardRanges();
        LogUtils.i("CardRangeTable: EMI table cleared");
    }

    /** True once the operator has entered at least one EMI range. */
    public static boolean emiConfigured(Context ctx) {
        return !allEmi(ctx).isEmpty();
    }

    /** The EMI range covering this PAN, or null. */
    public static Range findEmi(Context ctx, String pan) {
        String probe = probe(pan);
        if (probe == null) {
            return null;
        }
        for (Range r : allEmi(ctx)) {
            if (r.low.length() == COMPARE_LEN && r.high.length() == COMPARE_LEN
                    && probe.compareTo(r.low) >= 0 && probe.compareTo(r.high) <= 0) {
                return r;
            }
        }
        return null;
    }

    /**
     * Whether this card may be sold on instalments.
     *
     * <p>Fails CLOSED. A card has to be inside a range in the EMI table, and an
     * empty table matches nothing - so a terminal that has not been given the
     * EMI BIN list offers no EMI at all.</p>
     *
     * <p>This started out failing open, on the reasoning that an unconfigured
     * table should not stop every EMI sale the day it shipped. That was the
     * wrong trade. EMI is a credit product the bank sells on specific BINs; a
     * terminal quietly writing instalment sales against cards that carry no
     * programme is a settlement problem, not an inconvenience. "No table" now
     * means "no EMI", which is the safe reading and the one the bank asked
     * for.</p>
     */
    public static boolean emiAllowed(Context ctx, String pan) {
        return findEmi(ctx, pan) != null;
    }

    // ------------------------------------------------------------- lookups

    /**
     * Find the range covering this PAN.
     *
     * @return the matching range, or null when nothing covers it - the
     *         "Unsupported Card" case
     */
    public static Range find(Context ctx, String pan) {
        String probe = probe(pan);
        if (probe == null) {
            return null;
        }
        List<Range> ranges = all(ctx);
        if (ranges.isEmpty()) {
            // Fail OPEN. An unreadable or empty table must not stop every card
            // at the terminal - the host is still the authority.
            LogUtils.e("CardRangeTable: table empty, allowing the card through");
            Range r = new Range();
            r.name = "";
            return r;
        }
        for (Range r : ranges) {
            if (r.low.length() == COMPARE_LEN && r.high.length() == COMPARE_LEN
                    && probe.compareTo(r.low) >= 0 && probe.compareTo(r.high) <= 0) {
                return r;
            }
        }
        return null;
    }

    /** True when this card is acquired here at all. */
    public static boolean isSupported(Context ctx, String pan) {
        return find(ctx, pan) != null;
    }

    /**
     * True when the card was issued by the acquiring bank itself.
     *
     * <p>DBBL supplied the BIN list; the ranges carrying it are marked {@code
     * onUs} in {@code card_range.json} and sit ahead of the broad scheme
     * ranges, so 444515 resolves to the on-us VISA entry and 4111 to the
     * general VISA one. Both still print as VISA - being DBBL's own card
     * changes who settles it, not what scheme it belongs to.</p>
     *
     * <p>A card outside every range answers false rather than throwing: an
     * unknown card is not the bank's own, and the receipt has to say
     * something.</p>
     */
    public static boolean isOnUs(Context ctx, String pan) {
        Range r = find(ctx, pan);
        return r != null && r.onUs;
    }

    /** "ON-US" or "OFF-US", the words the receipt prints. */
    public static String onUsLabel(Context ctx, String pan) {
        return isOnUs(ctx, pan) ? "ON-US" : "OFF-US";
    }

    /**
     * The name to print on the receipt - TAKAPAY, NEXUSPAY, VISA, MASTER and so
     * on, exactly as the range is labelled.
     *
     * @return the range name, or a best-effort scheme guess when the card is
     *         outside every range
     */
    public static String nameFor(Context ctx, String pan) {
        Range r = find(ctx, pan);
        if (r != null && !r.name.isEmpty()) {
            return r.name;
        }
        return schemeOf(pan);
    }

    /**
     * Scheme guessed from the leading digits, used only to name a card the
     * table does not cover so the refusal message can be specific. Never used
     * to authorise anything.
     */
    public static String schemeOf(String pan) {
        String d = pan == null ? "" : pan.replaceAll("[^0-9]", "");
        if (d.length() < 2) {
            return "";
        }
        String two = d.substring(0, 2);
        if ("34".equals(two) || "37".equals(two)) {
            return "AMERICAN EXPRESS";
        }
        if ("35".equals(two)) {
            return "JCB";
        }
        if (d.charAt(0) == '4') {
            return "VISA";
        }
        if (two.compareTo("51") >= 0 && two.compareTo("55") <= 0) {
            return "MASTERCARD";
        }
        return "";
    }

    /**
     * A typed bound widened to the {@link #COMPARE_LEN} key the table compares.
     *
     * <p>The bounds are 12 characters because that is the width every PAN is
     * reduced to before comparing - see {@link #probe(String)} - not because
     * cards are 12 digits. Requiring the operator to type exactly 12 made that
     * look like a card-length rule and turned "the BIN is 100002" into a
     * counting exercise.</p>
     *
     * <p>A short low bound pads with zeros and a short high bound with nines,
     * so 100002 to 100002 means the whole 100002xxxxxx block - which is what
     * someone entering a BIN means. Longer input is truncated: past 12 digits
     * the extra digits play no part in the comparison anyway.</p>
     *
     * @param high true to pad with '9' (an upper bound), false with '0'
     */
    public static String widenBound(String s, boolean high) {
        String d = s == null ? "" : s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder(d);
        while (b.length() < COMPARE_LEN) {
            b.append(high ? '9' : '0');
        }
        return b.substring(0, COMPARE_LEN);
    }

    /** The PAN reduced to the width the bounds are written in. */
    private static String probe(String pan) {
        String digits = pan == null ? "" : pan.replaceAll("[^0-9]", "");
        if (digits.length() < 6) {
            return null;
        }
        StringBuilder key = new StringBuilder(digits);
        while (key.length() < COMPARE_LEN) {
            key.append('0');
        }
        return key.substring(0, COMPARE_LEN);
    }

    private static String readAsset(Context ctx) throws Exception {
        if (assetCache != null) {
            return assetCache;
        }
        InputStream in = ctx.getAssets().open(ASSET);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            assetCache = new String(bos.toByteArray(), "UTF-8");
            return assetCache;
        } finally {
            try {
                in.close();
            } catch (Exception ignore) {
                // nothing useful to do
            }
        }
    }
}

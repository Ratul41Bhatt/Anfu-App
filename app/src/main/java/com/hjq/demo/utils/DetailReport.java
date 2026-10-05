package com.hjq.demo.utils;

import android.content.Context;

import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.ans8583.TxnRecord;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The transaction detail report, as measured column rows.
 *
 * <p>Every line is one row with its columns kept apart, rather than a
 * left/right pair. That is not a style choice: the bitmap renderer shrinks any
 * line too wide to fit, independently of its neighbours, so a report built from
 * pairs comes out with each row at a slightly different size and the columns
 * visibly stepping in and out. Measuring the row as a whole fixes that.</p>
 *
 * <p>These rows used to be built by padding with spaces to {@link Slip#W}
 * characters, which is correct in a monospace font and only in one. The slip
 * font is now Roboto; the padding is gone and the renderer places each column
 * by measurement.</p>
 *
 * <p>Returns plain strings because the app still has two unrelated line models -
 * {@code FindCardActivity.RLine} for the slips it prints itself, {@code
 * Slip.Line} for the reports menu. Both need this report and neither can use
 * the other's type, so the composition lives here and each caller wraps the
 * finished lines in whatever it draws with.</p>
 *
 * <p>Layout follows the reference DBBL/NexusPay detail report: five lines per
 * transaction, so the card number, expiry, invoice, approval code, amount,
 * timestamp and entry mode each get the room they need instead of three of them
 * being crushed into thirds of a 32-character line.</p>
 */
public final class DetailReport {

    /** Printed after the last transaction, as on the reference slip. */
    public static final String END = "END OF DETAIL REPORT PRINTING";

    private DetailReport() {
    }

    /**
     * One line: label left, value hard right, padded to exactly {@link Slip#W}.
     *
     * <p>When the two would collide the label is truncated, never the value.
     * The value is the figure someone is checking - an amount, an approval
     * code - and half an amount is worse than a shortened label.</p>
     */
    public static String row(String left, String right) {
        // A measured two-column row, not a space-padded one.
        //
        // The padding worked while the slip was monospace: count the
        // characters, insert the difference, done. In Roboto a space is not the
        // width of a digit, so the same arithmetic left every value in a
        // slightly different place and the App Information block came out with
        // its right-hand column wandering. The renderer measures instead - see
        // Slip.COL_R - and puts the label hard left and the value hard right,
        // shrinking the whole line if the two would meet.
        return Slip.row3(left, "", right);
    }

    /**
     * The column legend heading the list.
     *
     * <p>Four lines, because the block below is four label/value pairs plus the
     * card type. Without it there is no telling an invoice number from an
     * approval code - both are six digits.</p>
     */
    public static List<String> legendLines() {
        List<String> o = new ArrayList<>();
        o.add(row("CARD NUMBER", "EXP DATE"));
        o.add(row("INVOICE NUM", "APPROVAL CODE"));
        o.add(row("TRANSACTION", "AMOUNT"));
        o.add(row("DATE", "TIME"));
        return o;
    }

    /** One transaction: five fixed-width lines, six when it is an EMI. */
    public static List<String> blockLines(Context ctx, TxnRecord r) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        List<String> o = new ArrayList<>();
        String pan = panOf(r);

        // Grouped in fours. A 16-digit run with asterisks through the middle is
        // unreadable held against the card it is meant to identify.
        o.add(row(CardMask.maskGrouped(pan, tc.cardMask()), "**/**"));

        o.add(row(blank(r.invoice), blank(r.authCode)));

        // Keep the original sale positive; its separate VOID row cancels it.
        // Neither record contributes to the active batch totals below.
        long amt = r.isVoid() || r.isRefund() ? -r.amount() : r.amount();
        o.add(row(label(r), tc.currencySymbol() + " " + Slip.money(amt)));

        // The EMI plan gets its own line rather than sharing the amount row.
        // "VOID INSTAPAY : 3 MONTHS" plus an amount is 37 characters on a
        // 32-character roll, so sharing meant truncating it to "VOID INSTAPAY :
        // 3 M" - which loses the term, the one thing the row was carrying.
        String plan = plan(r);
        if (!plan.isEmpty()) {
            o.add(row("  " + plan, ""));
        }

        o.add(row("DATE: " + date(r), "TIME: " + time(r)));

        o.add(row("CARDTYPE: " + CardRangeTable.nameFor(ctx, pan), entryLabel(r.entryMode)));
        return o;
    }

    /** Printed after the last entry of the error report. */
    public static final String END_ERRORS = "END OF ERROR REPORT PRINTING";

    /**
     * The column legend for the error report.
     *
     * <p>The Detail Report's four rows exactly, with one word changed: the
     * approval code column is the response code here, because that is the
     * whole difference between a sale that went through and one that did
     * not. Same rows, same order, so the two reports read the same way.</p>
     */
    public static List<String> errorLegendLines() {
        List<String> o = new ArrayList<>();
        o.add(row("CARD NUMBER", "EXP DATE"));
        o.add(row("INVOICE NUM", "RESPONSE CODE"));
        o.add(row("TRANSACTION", "AMOUNT"));
        o.add(row("DATE", "TIME"));
        return o;
    }

    /**
     * One failed transaction, in exactly the Detail Report's block.
     *
     * <pre>
     *   **** **** **** 1234            **&#47;**
     *   000123                            91
     *   SALE                       TK 850.00
     *     ISSUER NOT AVAILABLE                  (where the detail report puts an EMI plan)
     *   DATE: 01-10-2026      TIME: 12:39:52
     *   CARDTYPE: NEXUSPAY                CHIP
     * </pre>
     *
     * <p>Line for line the same as {@link #blockLines}: card and expiry,
     * invoice and code, transaction and amount, date and time, card type and
     * entry mode. The reason sits on the indented line the detail report uses
     * for an EMI plan, so the extra information does not change the shape.
     * Someone reading this report is usually holding a detail report too,
     * working out which attempt became which sale.</p>
     */
    public static List<String> failedBlockLines(Context ctx,
                                                com.hjq.demo.ans8583.ReportStore.FailedTxn f) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        List<String> o = new ArrayList<>();
        String pan = f.pan == null ? "" : f.pan;

        o.add(row(pan.isEmpty() ? "-" : CardMask.maskGrouped(pan, tc.cardMask()), "**/**"));
        o.add(row(blank(f.invoice), blank(f.responseCode)));
        o.add(row(failedLabel(f.type), tc.currencySymbol() + " " + Slip.money(f.amount())));

        String why = f.reason == null ? "" : f.reason.trim();
        if (why.isEmpty()) {
            why = com.hjq.demo.ans8583.ResponseCode.label(f.responseCode);
        }
        if (why != null && !why.trim().isEmpty()) {
            // At most two lines, so one long host message cannot turn a block
            // into a paragraph.
            List<String> lines = wrap("  " + why.trim().toUpperCase(Locale.US), Slip.W);
            for (int i = 0; i < lines.size() && i < 2; i++) {
                o.add(row(lines.get(i), ""));
            }
        }

        String[] dt = splitDateTime(f.dateTime);
        o.add(row("DATE: " + dt[0], "TIME: " + dt[1]));
        o.add(row("CARDTYPE: " + CardRangeTable.nameFor(ctx, pan), entryLabel(f.entryMode)));
        return o;
    }

    /** "EMI_SALE" prints as "EMI SALE", the way the Detail Report names it. */
    private static String failedLabel(String type) {
        String t = type == null ? "" : type.trim();
        return t.isEmpty() ? "-" : t.replace('_', ' ').toUpperCase(Locale.US);
    }

    /** Break a line at spaces so a long reason reads instead of being cut. */
    private static List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        String rest = s == null ? "" : s;
        while (rest.length() > width) {
            int cut = rest.lastIndexOf(' ', width);
            if (cut <= 2) {
                cut = width;
            }
            out.add(rest.substring(0, cut));
            rest = "  " + rest.substring(cut).trim();
        }
        if (!rest.trim().isEmpty()) {
            out.add(rest);
        }
        return out;
    }

    /** "yyyy-MM-dd HH:mm:ss" into the two columns the block prints. */
    private static String[] splitDateTime(String s) {
        String v = s == null ? "" : s.trim();
        int sp = v.indexOf(' ');
        if (sp < 0) {
            return new String[] {v.isEmpty() ? "-" : v, "-"};
        }
        String d = v.substring(0, sp);
        // The report prints dd-MM-yyyy like every other document here.
        if (d.length() == 10 && d.charAt(4) == '-') {
            d = d.substring(8) + "-" + d.substring(5, 7) + "-" + d.substring(0, 4);
        }
        return new String[] {d, v.substring(sp + 1)};
    }

    /**
     * What the transaction is called in the list.
     *
     * <p>An EMI names its plan and term - "INSTAPAY : 6 MONTHS" - because on an
     * instalment sale those are the terms the cardholder agreed to, and a row
     * saying only "EMI_SALE" tells the merchant nothing they can check against
     * a statement. A void of one reads "VOID INSTAPAY : 3 MONTHS".</p>
     */
    public static String label(TxnRecord r) {
        String base;
        if (r.isEmi()) {
            base = r.isVoid() ? "VOID EMI SALE" : "EMI SALE";
        } else {
            base = r.type == null ? "" : r.type;
        }
        return base;
    }

    /**
     * The instalment plan and term, or "" for an ordinary transaction.
     *
     * <p>"INSTAPAY : 6 MONTHS" - on an instalment sale these are the terms the
     * cardholder agreed to, and a row saying only "EMI SALE" tells the merchant
     * nothing they can check against a statement.</p>
     */
    public static String plan(TxnRecord r) {
        if (r == null || !r.isEmi()) {
            return "";
        }
        String p = r.emiProduct == null ? "" : r.emiProduct.trim();
        if (p.isEmpty() && r.tenure <= 0) {
            return "";
        }
        if (p.isEmpty()) {
            p = "EMI";
        }
        return p + (r.tenure > 0 ? " : " + r.tenure + " MONTHS" : "");
    }

    private static String blank(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }

    /** DE13 is MMDD with no year, so the current year is the best available. */
    private static String date(TxnRecord r) {
        String d = r.date == null ? "" : r.date.replaceAll("[^0-9]", "");
        if (d.length() < 4) {
            return "-";
        }
        return d.substring(2, 4) + "-" + d.substring(0, 2) + "-"
                + Calendar.getInstance().get(Calendar.YEAR);
    }

    /** DE12 is hhmmss. */
    private static String time(TxnRecord r) {
        String t = r.time == null ? "" : r.time.replaceAll("[^0-9]", "");
        if (t.length() < 6) {
            return "-";
        }
        return t.substring(0, 2) + ":" + t.substring(2, 4) + ":" + t.substring(4, 6);
    }

    /** How the card was read, from DE22. Blank when the field was not sent. */
    public static String entryLabel(String de22) {
        String d = de22 == null ? "" : de22.trim().replaceAll("[^0-9]", "");
        if (d.isEmpty()) {
            return "";
        }
        // DE22 is three digits - PAN entry mode in the first two, PIN entry
        // capability in the third - but it is stored here as the field was
        // built, which drops the leading zero: a chip sale is held as "51",
        // not "051". Reading the first two characters of that gives "51" and
        // matches nothing, which is why the entry mode had stopped printing on
        // the slip at all. Pad first, then read.
        while (d.length() < 3) {
            d = "0" + d;
        }
        switch (d.substring(0, 2)) {
            case "01": return "MANUAL";
            case "02": return "SWIPE";
            case "84":
            case "03": return "QR";
            case "05": return "CHIP";
            case "07":
            case "90":
            case "91": return "NFC";
            case "80":
            case "08": return "FALLBACK";
            default:   return "";
        }
    }

    /** The PAN, falling back to the track 2 image when DE2 was not sent. */
    public static String panOf(TxnRecord r) {
        if (r == null) {
            return "";
        }
        if (r.pan != null && !r.pan.isEmpty()) {
            return r.pan;
        }
        String t = r.track2 == null ? "" : r.track2;
        int sep = t.indexOf('=');
        if (sep < 0) {
            sep = t.indexOf('D');
        }
        return sep > 0 ? t.substring(0, sep) : t;
    }

    /** Match settlement eligibility: neither side of a void contributes. */
    private static boolean includedInTotal(TxnRecord r) {
        return r.approved && !r.isVoid() && !r.voided && r.amount() != 0;
    }

    /** Count approved, non-voided transactions, excluding VOID records. */
    public static int totalCount(List<TxnRecord> batch) {
        int count = 0;
        for (TxnRecord r : batch) {
            if (includedInTotal(r)) {
                count++;
            }
        }
        return count;
    }

    /** Active batch value in minor units: sales less refunds, excluding voids. */
    public static long net(List<TxnRecord> batch) {
        long net = 0;
        for (TxnRecord r : batch) {
            if (includedInTotal(r)) {
                net += r.isRefund() ? -r.amount() : r.amount();
            }
        }
        return net;
    }

    /** "007" - counts print three digits wide on this report. */
    public static String count(int n) {
        return String.format(Locale.US, "%03d", n);
    }
}

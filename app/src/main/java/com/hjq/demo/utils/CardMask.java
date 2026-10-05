package com.hjq.demo.utils;

/**
 * Card number masking, following the PAX reference.
 *
 * <p>PAX masks with a regular expression that replaces each matched digit with
 * a star:</p>
 *
 * <pre>
 *   public static String maskCardNo(String cardNo, String pattern) {
 *       return cardNo.replaceAll(pattern, "*");
 *   }
 *   public static String maskCardNo(String cardNo) {
 *       return maskCardNo(cardNo, "(?&lt;=\\d{6})\\d(?=\\d{4})");
 *   }
 * </pre>
 *
 * <p>Their default keeps the first six and last four digits and stars the
 * middle - which is also what PCI DSS permits to be displayed. That is
 * {@link #MIDDLE} here, and the default.</p>
 */
public final class CardMask {

    /** First 6 and last 4 shown: {@code 446617******5908}. PAX default, PCI safe. */
    public static final int MIDDLE = 0;
    /** Last 4 only: {@code ************5908}. */
    public static final int LAST4 = 1;
    /** First 6 only: {@code 446617**********}. */
    public static final int FIRST6 = 2;
    /** No masking - the full PAN. Test use only. */
    public static final int NONE = 3;

    /** PAX's own pattern: mask every digit after the first 6 and before the last 4. */
    private static final String P_MIDDLE = "(?<=\\d{6})\\d(?=\\d{4})";
    private static final String P_LAST4 = "\\d(?=\\d{4})";
    private static final String P_FIRST6 = "(?<=\\d{6})\\d";

    private CardMask() { }

    public static String label(int mode) {
        switch (mode) {
            case LAST4:  return "LAST 4";
            case FIRST6: return "FIRST 6";
            case NONE:   return "NONE (unmasked)";
            default:     return "MIDDLE (first 6, last 4)";
        }
    }

    /** Parse an operator-entered mode; anything unrecognised falls back to MIDDLE. */
    public static int parse(String s) {
        if (s == null) {
            return MIDDLE;
        }
        String v = s.trim().toUpperCase(java.util.Locale.US);
        if (v.startsWith("1") || v.startsWith("LAST")) {
            return LAST4;
        }
        if (v.startsWith("2") || v.startsWith("FIRST")) {
            return FIRST6;
        }
        if (v.startsWith("3") || v.startsWith("NONE") || v.startsWith("OFF")) {
            return NONE;
        }
        return MIDDLE;
    }

    /**
     * Mask a card number for display.
     *
     * <p>Anything that is not a digit - spaces, an '=' separator, track data -
     * is left alone, so a track image or an already formatted number can be
     * passed in safely.</p>
     */
    public static String mask(String cardNo, int mode) {
        if (cardNo == null || cardNo.isEmpty()) {
            return "";
        }
        String digits = cardNo.replaceAll("[^0-9]", "");
        if (digits.length() < 10) {
            // Too short to mask meaningfully without hiding everything.
            return digits.isEmpty() ? "" : digits.replaceAll("\\d", "*");
        }
        switch (mode) {
            case NONE:
                return digits;
            case LAST4:
                return digits.replaceAll(P_LAST4, "*");
            case FIRST6:
                return digits.replaceAll(P_FIRST6, "*");
            case MIDDLE:
            default:
                return digits.replaceAll(P_MIDDLE, "*");
        }
    }

    /** Masked and grouped in fours, for a confirmation prompt: {@code 4466 17** **** 5908}. */
    public static String maskGrouped(String cardNo, int mode) {
        String m = mask(cardNo, mode);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < m.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                b.append(' ');
            }
            b.append(m.charAt(i));
        }
        return b.toString();
    }

    // ------------------------------------------------------------- card type

    /**
     * True when the PAN is a Mastercard.
     *
     * <p>Ranges per Mastercard's own BIN allocation: 51-55, and the 2-series
     * 222100-272099 added in 2017.</p>
     */
    /**
     * Mastercard card ranges: 51-55 and the 2-series 222100-272099.
     *
     * <p>No longer used to gate DE14 - the reference terminal and PAX both send
     * the expiry for every brand. Kept for card-brand labelling on the slip.</p>
     */
    public static boolean isMastercard(String pan) {
        String d = pan == null ? "" : pan.replaceAll("[^0-9]", "");
        if (d.length() < 6) {
            return false;
        }
        int two = Integer.parseInt(d.substring(0, 2));
        if (two >= 51 && two <= 55) {
            return true;
        }
        int six = Integer.parseInt(d.substring(0, 6));
        return six >= 222100 && six <= 272099;
    }
}

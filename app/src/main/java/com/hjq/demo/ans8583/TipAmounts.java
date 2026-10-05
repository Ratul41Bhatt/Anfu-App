package com.hjq.demo.ans8583;

import java.math.BigDecimal;
import java.util.Locale;

/** Exact minor-unit arithmetic shared by entry, storage and packet builders. */
public final class TipAmounts {
    private TipAmounts() { }
    public static long parse(String major) {
        String value = major == null ? "" : major.trim();
        if (!value.matches("(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})"))
            throw new IllegalArgumentException("Enter an amount with up to two decimal places");
        try { return new BigDecimal(value).movePointRight(2).longValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Amount is too large"); }
    }
    public static long total(long base, long tip, int percent) {
        if (base <= 0 || tip < 0 || percent < 0 || percent > 100)
            throw new IllegalArgumentException("Invalid sale or tip amount");
        if (BigDecimal.valueOf(tip).multiply(BigDecimal.valueOf(100))
                .compareTo(BigDecimal.valueOf(base).multiply(BigDecimal.valueOf(percent))) > 0)
            throw new IllegalArgumentException("Tip must be at most " + percent + "% of the base amount");
        return total(base, tip);
    }
    public static long total(long base, long tip) {
        if (base <= 0 || tip < 0) throw new IllegalArgumentException("Invalid sale or tip amount");
        long total = Math.addExact(base, tip);
        if (total > 999999999999L) throw new IllegalArgumentException("Total exceeds the transaction limit");
        return total;
    }
    public static String field(long minor) { return String.format(Locale.US, "%012d", minor); }
    public static String major(long minor) { return String.format(Locale.US, "%d.%02d", minor / 100, minor % 100); }
}

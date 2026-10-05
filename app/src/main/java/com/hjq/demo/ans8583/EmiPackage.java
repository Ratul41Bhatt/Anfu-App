package com.hjq.demo.ans8583;

import java.util.Locale;

/**
 * One EMI product, as DBBL define it.
 *
 * <p>Keyed in from the admin panel. The fields mirror what the PAX reference
 * receives from its host, because they are what the transaction needs:</p>
 *
 * <pre>
 *   product code      identifies the EMI product   -> DE48 tag E01
 *   rate              interest, as a number        -> the instalment maths
 *   interest index    the rate the host applies    -> DE48 tag E03
 *   description       shown to the cardholder
 *   tenure            months                       -> DE48 tag E02
 * </pre>
 */
public final class EmiPackage {

    public String productCode = "";
    public String productWording = "";
    public String interestIndex = "";
    public String interestWording = "";
    public int tenure;

    public EmiPackage() { }

    /**
     * The interest rate, taken from the product wording.
     *
     * <p>Stored as text so the admin field stays simple. PAX multiply by the
     * same value as a double. Anything unparseable means no interest rather
     * than a crash - an EMI plan that cannot compute is worse than one at
     * zero percent, which is at least visibly wrong.</p>
     */
    public double rate() {
        try {
            return Double.parseDouble(productWording.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0d;
        }
    }

    /**
     * Monthly instalment for an amount, using PAX's formula:
     *
     * <pre>
     *   round( (amount / tenure) * (1 + rate * (tenure / 12)) )
     * </pre>
     *
     * @param amountMinor the transaction amount in minor units
     * @return the instalment in minor units
     */
    public long installment(long amountMinor) {
        if (tenure <= 0) {
            return amountMinor;
        }
        double perMonth = (double) amountMinor / tenure;
        double withInterest = perMonth * (1 + rate() * (tenure / 12.0));
        return Math.round(withInterest);
    }

    /** Total the cardholder pays across the term. */
    public long totalPayable(long amountMinor) {
        return installment(amountMinor) * Math.max(1, tenure);
    }

    public String label() {
        return tenure + " MONTHS"
                + (rate() > 0 ? String.format(Locale.US, "  @ %.2f%%", rate() * 100) : "");
    }

    // -------------------------------------------------------- serialisation

    public String serialize() {
        return productCode + "|" + productWording + "|" + interestIndex
                + "|" + interestWording + "|" + tenure;
    }

    public static EmiPackage parse(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] p = line.split("\\|", -1);
        if (p.length < 5) {
            return null;
        }
        EmiPackage e = new EmiPackage();
        e.productCode = p[0];
        e.productWording = p[1];
        e.interestIndex = p[2];
        e.interestWording = p[3];
        try {
            e.tenure = Integer.parseInt(p[4].trim());
        } catch (Exception ex) {
            return null;
        }
        return e;
    }

    @Override
    public String toString() {
        return productCode + " " + tenure + "m " + interestWording;
    }
}

package com.hjq.demo.ans8583;

import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;

public class CardExpiryTest {

    private Calendar createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1); // Calendar.MONTH is 0-based
        cal.set(Calendar.DAY_OF_MONTH, day);
        return cal;
    }

    @Test
    public void testExpiredPastYear() {
        Calendar now = createDate(2026, 9, 29);
        Assert.assertTrue(TerminalConfig.isCardExpired("2512", now));
        Assert.assertTrue(TerminalConfig.isCardExpired("2401", now));
        Assert.assertTrue(TerminalConfig.isCardExpired("25/12", now));
    }

    @Test
    public void testExpiredPastMonthSameYear() {
        Calendar now = createDate(2026, 9, 29);
        Assert.assertTrue(TerminalConfig.isCardExpired("2608", now));
        Assert.assertTrue(TerminalConfig.isCardExpired("2601", now));
        Assert.assertTrue(TerminalConfig.isCardExpired("26-08", now));
    }

    @Test
    public void testCurrentMonthWithoutDayIsValidUntilEndOfMonth() {
        Calendar now = createDate(2026, 9, 29);
        // Expiring in 2609 is valid through the entire month of September 2026
        Assert.assertFalse(TerminalConfig.isCardExpired("2609", now));
    }

    @Test
    public void testCurrentMonthWithDay() {
        Calendar now = createDate(2026, 9, 29);
        // Day 25 has passed on Sept 29
        Assert.assertTrue(TerminalConfig.isCardExpired("260925", now));
        // Day 28 has passed on Sept 29
        Assert.assertTrue(TerminalConfig.isCardExpired("260928", now));
        // Day 30 has not passed yet
        Assert.assertFalse(TerminalConfig.isCardExpired("260930", now));
    }

    @Test
    public void testFutureMonthSameYear() {
        Calendar now = createDate(2026, 9, 29);
        Assert.assertFalse(TerminalConfig.isCardExpired("2610", now));
        Assert.assertFalse(TerminalConfig.isCardExpired("2612", now));
    }

    @Test
    public void testFutureYears() {
        Calendar now = createDate(2026, 9, 29);
        Assert.assertFalse(TerminalConfig.isCardExpired("2701", now));
        Assert.assertFalse(TerminalConfig.isCardExpired("2806", now));
        Assert.assertFalse(TerminalConfig.isCardExpired("3012", now));
    }

    @Test
    public void testInvalidInputsReturnFalse() {
        Calendar now = createDate(2026, 9, 29);
        Assert.assertFalse(TerminalConfig.isCardExpired(null, now));
        Assert.assertFalse(TerminalConfig.isCardExpired("", now));
        Assert.assertFalse(TerminalConfig.isCardExpired("123", now));
        Assert.assertFalse(TerminalConfig.isCardExpired("invalid", now));
        Assert.assertFalse(TerminalConfig.isCardExpired("2613", now)); // Invalid month 13
        Assert.assertFalse(TerminalConfig.isCardExpired("2600", now)); // Invalid month 00
    }
}

package com.hjq.demo.ans8583;
import org.junit.Test;
import static org.junit.Assert.*;

public class TipAmountsTest {
    @Test public void entryRejectsAmbiguousOrNegativeValuesRatherThanChangingThem() {
        assertEquals(2000, TipAmounts.parse("20.00"));
        assertEquals(5, TipAmounts.parse(".05"));
        assertThrows(IllegalArgumentException.class, () -> TipAmounts.parse("1.2.3"));
        assertThrows(IllegalArgumentException.class, () -> TipAmounts.parse("-20"));
        assertThrows(IllegalArgumentException.class, () -> TipAmounts.parse("1.999"));
    }
    @Test public void percentageBoundaryUsesMinorUnitsWithoutRoundingUp() {
        assertEquals(15000, TipAmounts.total(10000, 5000, 50));
        assertEquals(151, TipAmounts.total(101, 50, 50));
        assertThrows(IllegalArgumentException.class, () -> TipAmounts.total(101, 51, 50));
    }
    @Test public void invalidAndOverflowAmountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TipAmounts.total(0, 1, 50));
        assertThrows(IllegalArgumentException.class, () -> TipAmounts.total(100, -1, 50));
        assertThrows(IllegalArgumentException.class, () -> TipAmounts.total(999999999999L, 1, 100));
        assertEquals(100, TipAmounts.total(100, 0, 0));
    }
    @Test public void oldRecordsAndNewRecordsRetainFinancialAmounts() {
        TxnRecord old = new TxnRecord(); old.amountMinor = "10000";
        // Explicit pre-tip record: the first 37 columns belong to earlier releases.
        String legacy = String.join("|", java.util.Arrays.copyOf(old.serialize().split("\\|", -1), 37));
        assertEquals(10000, TxnRecord.parse(legacy).baseAmount());
        old.baseAmountMinor = "10000"; old.tipAmountMinor = "2000"; old.amountMinor = "12000";
        old.originalAmountMinor = "10000"; old.tipAdjusted = true;
        TxnRecord copy = TxnRecord.parse(old.serialize());
        assertEquals(10000, copy.baseAmount()); assertEquals(2000, copy.tip());
        assertTrue(copy.tipAdjusted); assertEquals("10000", copy.originalAmountMinor);
    }
}

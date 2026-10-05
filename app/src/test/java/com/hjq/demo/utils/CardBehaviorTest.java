package com.hjq.demo.utils;

import org.junit.Test;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class CardBehaviorTest {
    @Test public void sdkChipErrorsCountEvenWithoutSlotEvidence() {
        for (int code : new int[]{-2005, -2006, -2007, -2011, CardSearchType.IC_CARD})
            assertTrue("code=" + code, ChipFallbackPolicy.failedChipAttempt(code, false));
    }
    @Test public void onlyObservedInsertionsMakeTimeoutsOrGenericFailuresCount() {
        for (int code : new int[]{-2003, -2009}) {
            assertFalse(ChipFallbackPolicy.failedChipAttempt(code, false));
            assertTrue(ChipFallbackPolicy.failedChipAttempt(code, true));
        }
    }
    @Test public void rfStripeCancellationAndBusyNeverCountAsChipFailure() {
        for (int code : new int[]{1, 4, -2012, -2001, -2002, -2004, -2008, Integer.MIN_VALUE})
            assertFalse("code=" + code, ChipFallbackPolicy.failedChipAttempt(code, true));
    }
    @Test public void stableInsertionOpensOnceUntilRemovalAndReinsertion() {
        CardPresenceTracker t = new CardPresenceTracker(300);
        assertNull(t.sample(false, 0));
        assertNull(t.sample(true, 100));
        assertEquals(Boolean.TRUE, t.sample(true, 400));
        assertNull(t.sample(true, 900));
        assertNull(t.sample(false, 1000));
        assertEquals(Boolean.FALSE, t.sample(false, 1300));
        assertNull(t.sample(true, 1400));
        assertEquals(Boolean.TRUE, t.sample(true, 1700));
    }
    @Test public void sensorFlickerDoesNotOpenOrDismissKeypad() {
        CardPresenceTracker t = new CardPresenceTracker(300);
        assertNull(t.sample(true, 0));
        assertNull(t.sample(false, 200));
        assertNull(t.sample(true, 300));
        assertEquals(Boolean.TRUE, t.sample(true, 600));
        assertNull(t.sample(false, 700));
        assertNull(t.sample(true, 800));
        assertNull(t.sample(true, 1200));
    }
    @Test public void homeSensorDoesNotCallSdkDuringTransaction() throws Exception {
        CardReaderAccess.beginTransaction();
        try {
            FutureTask<Boolean> read = new FutureTask<>(() -> CardReaderAccess.observe(() -> {
                throw new AssertionError("Home sensor entered active card session");
            }));
            new Thread(read).start();
            assertNull(read.get(2, TimeUnit.SECONDS));
        } finally { CardReaderAccess.endTransaction(); }
        assertEquals(Boolean.TRUE, CardReaderAccess.observe(() -> true));
    }
}

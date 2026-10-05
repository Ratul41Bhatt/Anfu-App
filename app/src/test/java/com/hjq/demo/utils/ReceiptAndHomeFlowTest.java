package com.hjq.demo.utils;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReceiptAndHomeFlowTest {
    @Test public void noCustomerCopyCompletesImmediatelyOnce() {
        ReceiptFlow flow = new ReceiptFlow();
        assertTrue(flow.start()); assertFalse(flow.start());
        assertTrue(flow.merchantDone(true)); assertFalse(flow.merchantDone(true));
        assertTrue(flow.chooseCustomerCopy(false)); assertFalse(flow.chooseCustomerCopy(true));
        assertEquals(ReceiptFlow.Stage.COMPLETE, flow.stage());
    }
    @Test public void customerCopyNeedsOnlyItsOwnCompletion() {
        ReceiptFlow flow = new ReceiptFlow(); flow.start(); flow.merchantDone(true);
        assertFalse(flow.customerDone()); assertTrue(flow.chooseCustomerCopy(true));
        assertTrue(flow.customerDone()); assertFalse(flow.customerDone());
        assertEquals(ReceiptFlow.Stage.COMPLETE, flow.stage());
    }
    @Test public void oneCopyCompletesWithoutLegacyPrinterEvent() {
        ReceiptFlow flow = new ReceiptFlow(); flow.start(); flow.merchantDone(false);
        assertEquals(ReceiptFlow.Stage.COMPLETE, flow.stage());
    }
    @Test public void completedSaleCardDoesNotOpenNewAmountUntilStableRemovalAndReinsert() {
        CardPresenceTracker tracker = new CardPresenceTracker(300, true);
        assertNull(tracker.sample(true, 0)); assertNull(tracker.sample(true, 900));
        assertNull(tracker.sample(false, 1000)); assertNull(tracker.sample(true, 1100));
        assertTrue(tracker.waitingForRemoval());
        assertNull(tracker.sample(false, 1500)); assertNull(tracker.sample(false, 1800));
        assertFalse(tracker.waitingForRemoval());
        assertNull(tracker.sample(true, 1900)); assertEquals(Boolean.TRUE, tracker.sample(true, 2200));
        assertNull(tracker.sample(true, 2600));
    }
    @Test public void incompleteChipReadRetriesButGac2OrHostDeclineNeverFallsBack() {
        assertTrue(ChipFallbackPolicy.retryIncompleteKernelRead(0, CardSearchType.IC_CARD, false, false));
        assertFalse(ChipFallbackPolicy.retryIncompleteKernelRead(1, CardSearchType.IC_CARD, false, false));
        assertFalse(ChipFallbackPolicy.retryIncompleteKernelRead(0, CardSearchType.IC_CARD, true, false));
        assertFalse(ChipFallbackPolicy.retryIncompleteKernelRead(0, CardSearchType.RF_CARD, false, false));
        assertFalse(ChipFallbackPolicy.retryIncompleteKernelRead(0, CardSearchType.IC_CARD, false, true));
    }
}

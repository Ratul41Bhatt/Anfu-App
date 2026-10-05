package com.hjq.demo.ans8583;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReversalTraceTest {
    private OnlineHost.HostResult sale() {
        OnlineHost.HostResult sale = new OnlineHost.HostResult();
        sale.requestDump = "MTI: 0200";
        sale.responseDump = "MTI: 0210";
        sale.responseCode = "00";
        sale.approved = true;
        return sale;
    }

    @Test public void receiptRetainsBothExchangesInOrder() {
        OnlineHost.HostResult sale = sale();
        OnlineHost.HostResult reversal = new OnlineHost.HostResult();
        reversal.requestDump = "MTI: 0400";
        reversal.responseDump = "MTI: 0410";
        reversal.online = true;
        reversal.responseCode = "00";
        sale.attachReversal(reversal);
        String trace = sale.packetTrace();
        assertTrue(trace.indexOf("MTI: 0200") < trace.indexOf("MTI: 0210"));
        assertTrue(trace.indexOf("MTI: 0210") < trace.indexOf("MTI: 0400"));
        assertTrue(trace.indexOf("MTI: 0400") < trace.indexOf("MTI: 0410"));
        assertTrue(sale.reversalAcknowledged);
        assertTrue(sale.approved); // host verdict is preserved for chip-decline handling
        assertEquals("00", sale.responseCode);
    }

    @Test public void failedSendDoesNotPretendAReversalWasTransmitted() {
        OnlineHost.HostResult sale = sale();
        OnlineHost.HostResult reversal = new OnlineHost.HostResult();
        reversal.requestDump = "MTI: 0400";
        reversal.error = "Connection refused";
        sale.attachReversal(reversal);
        assertFalse(sale.reversalAcknowledged);
        assertTrue(sale.packetTrace().contains("0400 NOT SENT"));
        assertTrue(sale.packetTrace().contains("Connection refused"));
        assertFalse(sale.packetTrace().contains("MTI: 0410"));
    }

    @Test public void timeoutRetainsAttemptAndPendingOutcome() {
        OnlineHost.HostResult sale = sale();
        sale.approved = false;
        sale.needsReversal = true;
        OnlineHost.HostResult reversal = new OnlineHost.HostResult();
        reversal.requestSent = true;
        reversal.requestDump = "MTI: 0400";
        sale.attachReversal(reversal);
        assertTrue(sale.packetTrace().contains("0400 WRITE ATTEMPTED - ACK PENDING"));
        assertFalse(sale.approved);
        assertTrue(sale.needsReversal);
    }

    @Test public void untrustedReversalApprovalStaysPending() {
        OnlineHost.HostResult sale = sale();
        OnlineHost.HostResult reversal = new OnlineHost.HostResult();
        reversal.online = true;
        reversal.responseCode = "00";
        reversal.needsReversal = true;
        sale.attachReversal(reversal);
        assertFalse(sale.reversalAcknowledged);
    }

    @Test public void normalCompletionDoesNotInventReversalPackets() {
        OnlineHost.HostResult sale = sale();
        sale.reversalDecision = "TRANSACTION COMPLETED - NO REVERSAL REQUIRED";
        assertFalse(sale.packetTrace().contains("0400"));
        assertTrue(sale.packetTrace().contains("NO REVERSAL REQUIRED"));
    }
}

package com.hjq.demo.ans8583;

import org.junit.Test;
import static org.junit.Assert.*;

public class TransactionPolicyTest {
    @Test public void matchesResponsesAcrossTransactionTypes() {
        assertEquals("", TransactionPolicy.responseFault("0200", "0210", "000123", "000123", "00"));
        assertEquals("", TransactionPolicy.responseFault("0400", "0410", "000123", "000123", "00"));
        assertEquals("", TransactionPolicy.responseFault("0800", "0810", "000123", "000123", "00"));
        assertEquals("", TransactionPolicy.responseFault("0220", "0230", "000123", "000123", "05"));
    }

    @Test public void unrelatedApprovalCannotApproveTheRequest() {
        assertFalse(TransactionPolicy.responseFault("0200", "0410", "000123", "000123", "00").isEmpty());
        assertFalse(TransactionPolicy.responseFault("0200", "0210", "000123", "000124", "00").isEmpty());
        assertFalse(TransactionPolicy.responseFault("0200", "0210", "000123", "", "00").isEmpty());
    }

    @Test public void rejectsMissingOrWrongHostResponseFormat() {
        for (String code : new String[]{null, "", "0", "000", "**"}) {
            assertFalse(TransactionPolicy.responseFault("0200", "0210", "000123", "000123", code).isEmpty());
        }
    }

    @Test public void failedReversalMustRemainQueued() {
        assertTrue(TransactionPolicy.reversalAccepted(true, "00"));
        assertFalse(TransactionPolicy.reversalAccepted(true, "05"));
        assertFalse(TransactionPolicy.reversalAccepted(true, "96"));
        assertFalse(TransactionPolicy.reversalAccepted(false, "00"));
        assertFalse(TransactionPolicy.reversalAccepted(true, "000"));
    }

    @Test public void chipCardsRequireInsertionBeforeFallback() {
        assertTrue(TransactionPolicy.chipServiceCode("4000000000000002=2912201123"));
        assertTrue(TransactionPolicy.chipServiceCode("4000000000000002D2912601123"));
        assertTrue(TransactionPolicy.chipServiceCode("4000000000000002d2912221123"));
        assertFalse(TransactionPolicy.chipServiceCode("4000000000000002=2912101123"));
        assertFalse(TransactionPolicy.chipServiceCode("4000000000000002=2912000123"));
        assertFalse(TransactionPolicy.chipServiceCode("4000000000000002=29122"));
        assertFalse(TransactionPolicy.chipServiceCode(null));
    }
}

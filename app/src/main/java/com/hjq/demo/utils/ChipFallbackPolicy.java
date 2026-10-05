package com.hjq.demo.utils;

/** Classifies completed SDK results, not elapsed time while a search is running. */
public final class ChipFallbackPolicy {
    private ChipFallbackPolicy() { }

    public static boolean retryIncompleteKernelRead(int stage, int cardType, boolean hostStarted, boolean aborted) {
        return stage == 0 && cardType == CardSearchType.IC_CARD && !hostStarted && !aborted;
    }

    public static boolean failedChipAttempt(int result, boolean insertionObserved) {
        switch (result) {
            case CardSearchType.IC_CARD: // Search succeeded but the subsequent chip read failed.
            case -2005: // CARD_ERROR_IC_CARD_DETECT
            case -2006: // CARD_ERROR_IC_CARD_POWER_ON
            case -2007: // CARD_ERROR_IC_CARD_RESET
            case -2011: // CARD_ERROR_FIND_CARD_RECERSE_IC_CARD
                return true;
            case -2003: // Timeout only counts when a chip insertion was actually observed.
            case -2009: // Generic find failure must also have insertion evidence.
                return insertionObserved;
            default:
                return false; // Busy, cancellation, RF, stripe and service errors are not chip strikes.
        }
    }
}

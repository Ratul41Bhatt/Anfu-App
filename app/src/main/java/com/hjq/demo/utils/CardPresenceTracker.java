package com.hjq.demo.utils;

/** Debounces the slot sensor and reports each insertion/removal once. */
public final class CardPresenceTracker {
    private final long stableMs;
    private boolean stable;
    private Boolean candidate;
    private long since;
    private boolean waitingForRemoval;

    public CardPresenceTracker(long stableMs) { this(stableMs, false); }

    public CardPresenceTracker(long stableMs, boolean waitingForRemoval) {
        this.stableMs = stableMs;
        this.waitingForRemoval = waitingForRemoval;
    }

    public boolean waitingForRemoval() { return waitingForRemoval; }

    /** Returns TRUE for insertion, FALSE for removal, or null for no change. */
    public Boolean sample(boolean present, long now) {
        if (candidate == null || candidate != present) {
            candidate = present;
            since = now;
        }
        // Returning from a transaction with its card still inserted is not a new sale.
        if (waitingForRemoval) {
            if (!present && now - since >= stableMs) waitingForRemoval = false;
            return null;
        }
        if (stable != present && now - since >= stableMs) {
            stable = present;
            return present;
        }
        return null;
    }
}

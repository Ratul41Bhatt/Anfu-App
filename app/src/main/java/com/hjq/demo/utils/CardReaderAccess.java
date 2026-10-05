package com.hjq.demo.utils;

import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One owner of the card reader at a time, and a record of who that owner is.
 *
 * <p>The lock is process wide. That is what made the "stuck on PRESENT CARD
 * until the app is killed" fault so total: a transaction thread that never
 * came back from the card service kept this lock for ever, every later card
 * screen waited on it, and only killing the process let go of it.</p>
 *
 * <p>So the owner is now recorded - which screen took the reader, when, and
 * whether that screen has since closed. A reader still held by a screen that
 * has gone is <em>orphaned</em>; {@link ReaderRecovery} uses
 * {@link #orphanedForMs()} to decide when the only way out is the one the
 * operator was taking by hand.</p>
 */
public final class CardReaderAccess {
    private static final ReentrantLock LOCK = new ReentrantLock(true);
    private static final AtomicInteger SESSIONS = new AtomicInteger(0);

    /** The idle home-screen search. */
    public static final int HOME_SESSION = -1;

    private static volatile int sOwnerSession;
    private static volatile String sOwnerWhat = "";
    private static volatile long sAcquiredAt;
    private static volatile long sOwnerEndedAt;

    private CardReaderAccess() { }

    public interface Sensor { boolean read() throws Exception; }

    /** A new id for one card screen. */
    public static int newSession() {
        return SESSIONS.incrementAndGet();
    }

    public static boolean beginTransaction(long timeoutMs) {
        return beginTransaction(timeoutMs, 0, "");
    }

    public static boolean beginTransaction(long timeoutMs, int session, String what) {
        try {
            if (!LOCK.tryLock(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (LOCK.getHoldCount() == 1) {
            sOwnerSession = session;
            sOwnerWhat = what == null ? "" : what;
            sAcquiredAt = SystemClock.uptimeMillis();
            sOwnerEndedAt = 0L;
        }
        return true;
    }

    public static void beginTransaction() {
        if (!beginTransaction(3000)) {
            throw new IllegalStateException("Card reader is still in use");
        }
    }

    public static void endTransaction() {
        if (LOCK.isHeldByCurrentThread()) {
            if (LOCK.getHoldCount() == 1) {
                sOwnerSession = 0;
                sOwnerWhat = "";
                sAcquiredAt = 0L;
                sOwnerEndedAt = 0L;
            }
            LOCK.unlock();
        }
    }

    /**
     * The screen that owns {@code session} has closed.
     *
     * <p>If its thread still holds the reader, the clock starts on how long
     * the reader has been held by nobody who can let it go.</p>
     */
    public static void sessionEnded(int session) {
        if (session != 0 && LOCK.isLocked() && sOwnerSession == session
                && sOwnerEndedAt == 0L) {
            sOwnerEndedAt = SystemClock.uptimeMillis();
        }
    }

    /** 0 when the reader is free or its owner is still on screen. */
    public static long orphanedForMs() {
        long ended = sOwnerEndedAt;
        if (ended == 0L || !LOCK.isLocked()) {
            return 0L;
        }
        return SystemClock.uptimeMillis() - ended;
    }

    /** The session currently holding the reader, 0 if none. */
    public static int holderSession() {
        return LOCK.isLocked() ? sOwnerSession : 0;
    }

    public static String describeHolder() {
        if (!LOCK.isLocked()) {
            return "free";
        }
        long now = SystemClock.uptimeMillis();
        return "session " + sOwnerSession + " (" + sOwnerWhat + ") held "
                + (sAcquiredAt == 0L ? "?" : String.valueOf(now - sAcquiredAt)) + " ms"
                + (sOwnerEndedAt == 0L ? "" : ", screen closed "
                + (now - sOwnerEndedAt) + " ms ago");
    }

    public static Boolean observe(Sensor sensor) throws Exception {
        if (!LOCK.tryLock(500, java.util.concurrent.TimeUnit.MILLISECONDS)) return null;
        try { return sensor.read(); }
        finally {
            if (LOCK.isHeldByCurrentThread()) {
                LOCK.unlock();
            }
        }
    }
}

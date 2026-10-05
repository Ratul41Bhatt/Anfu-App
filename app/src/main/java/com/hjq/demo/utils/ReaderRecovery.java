package com.hjq.demo.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The last way out of a card service that has stopped answering.
 *
 * <p>The card service keeps one EMV session per client. When a call into it
 * never returns - the kernel left waiting for a PIN that is never going to be
 * entered, say - that session stays busy, every later {@code
 * openFindCardForEMV} is refused or never answers, and the terminal sits on
 * PRESENT CARD. The service only lets go when the client process dies: that is
 * why killing the app from Recents and starting it again always fixed it.</p>
 *
 * <p>This does the same thing, on its own, and only when there is no doubt:
 * the reader has been held for {@link #ORPHAN_LIMIT_MS} by a card screen that
 * has already closed. Nothing that is still on screen is ever interrupted, and
 * a sale that was already sent is protected the usual way - its reversal is on
 * disk before the request goes out and is sent on the next start.</p>
 */
public final class ReaderRecovery {
    private static final String TAG = "ReaderRecovery";

    /**
     * How long a closed screen may keep the reader before the process is
     * restarted. Longer than the PIN pad's own exit (its SDK clock lands five
     * seconds after ours), shorter than an operator's patience.
     */
    public static final long ORPHAN_LIMIT_MS = 12000L;

    private static final AtomicBoolean RESTARTING = new AtomicBoolean(false);

    private ReaderRecovery() { }

    /** Restart if the reader is orphaned. @return true if a restart is under way. */
    public static boolean recoverIfWedged(Context context, String where) {
        if (RESTARTING.get()) {
            return true;
        }
        long orphan = CardReaderAccess.orphanedForMs();
        if (orphan < ORPHAN_LIMIT_MS) {
            return false;
        }
        restart(context, where + ": card reader still held by a closed screen - "
                + CardReaderAccess.describeHolder());
        return true;
    }

    public static boolean isRestarting() {
        return RESTARTING.get();
    }

    /**
     * Start a fresh process for the app and end this one.
     *
     * <p>Done from a tiny activity in a separate process, so the restart does
     * not depend on this process still being alive to launch anything.</p>
     */
    public static void restart(Context context, String why) {
        if (!RESTARTING.compareAndSet(false, true)) {
            return;
        }
        Log.e(TAG, "RESTARTING THE APP TO FREE THE CARD READER. " + why);
        try {
            LogUtils.e("ReaderRecovery: restarting the app to free the card reader - " + why);
        } catch (Throwable ignore) {
            // logging must never stop the recovery
        }
        Context app = context.getApplicationContext();
        try {
            Intent reborn = new Intent(app, ReaderRebornActivity.class);
            reborn.putExtra(ReaderRebornActivity.EXTRA_MAIN_PID, Process.myPid());
            reborn.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            if (context instanceof Activity) {
                ((Activity) context).startActivity(reborn);
            } else {
                app.startActivity(reborn);
            }
        } catch (Throwable e) {
            Log.e(TAG, "could not start the restart helper - ending the process anyway", e);
        }
        // The helper kills this process itself. This is the backstop in case it
        // never gets the chance.
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException ignore) {
                    // fall through
                }
                Process.killProcess(Process.myPid());
            }
        }, "ReaderRecoveryBackstop").start();
    }
}

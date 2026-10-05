package com.hjq.demo.ans8583;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * Removes copies of settled batches.
 *
 * <p>Settled batches are no longer kept: after a settlement every transaction
 * of that batch is erased. A build installed for a short time did keep copies
 * under {@code files/settled_batches}; this deletes them, and is called on
 * every settlement so none can linger.</p>
 */
public final class SettledBatchStore {

    private static final String DIR = "settled_batches";

    private SettledBatchStore() {
    }

    public static void purgeAll(Context ctx) {
        try {
            File dir = new File(ctx.getApplicationContext().getFilesDir(), DIR);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
            //noinspection ResultOfMethodCallIgnored
            dir.delete();
        } catch (Exception e) {
            Log.e("SettledBatchStore", "could not remove old settled batch copies", e);
        }
    }
}

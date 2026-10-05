package com.hjq.demo.utils;

import java.util.concurrent.Executor;

/** Serializes blocking reader work and keeps only the most recent requested retry. */
public final class CardSearchQueue {
    private final Executor executor;
    private Runnable pending;
    private boolean running;
    private int generation;

    public CardSearchQueue(Executor executor) {
        this.executor = executor;
    }

    public synchronized int generation() {
        return generation;
    }

    public synchronized boolean isCurrent(int token) {
        return token == generation;
    }

    /** Invalidate results and discard retries without pretending a blocked call has ended. */
    public synchronized void cancel() {
        generation++;
        pending = null;
    }

    public synchronized void clearPending() {
        pending = null;
    }

    public synchronized void submit(int token, Runnable task) {
        if (token != generation) return;
        pending = task;
        if (running) return;
        running = true;
        try {
            executor.execute(this::drain);
        } catch (RuntimeException e) {
            running = false;
            pending = null;
            throw e;
        }
    }

    private void drain() {
        while (true) {
            Runnable task;
            synchronized (this) {
                task = pending;
                pending = null;
                if (task == null) {
                    running = false;
                    return;
                }
            }
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                synchronized (this) {
                    running = false;
                    pending = null;
                }
                throw e;
            }
        }
    }
}

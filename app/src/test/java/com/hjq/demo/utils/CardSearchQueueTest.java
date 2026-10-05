package com.hjq.demo.utils;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CardSearchQueueTest {
    @Test public void retriesWaitForBlockedSearchAndOnlyLatestRuns() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        List<String> calls = new ArrayList<>();
        CardSearchQueue queue = new CardSearchQueue(pool);
        try {
            queue.submit(queue.generation(), () -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                calls.add("search");
                entered.countDown();
                await(release);
                calls.add("result handled");
                active.decrementAndGet();
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            queue.submit(queue.generation(), () -> calls.add("obsolete retry"));
            queue.submit(queue.generation(), () -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                calls.add("latest retry");
                active.decrementAndGet();
                finished.countDown();
            });
            assertEquals(1, active.get());
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(Arrays.asList("search", "result handled", "latest retry"), calls);
            assertEquals(1, maximum.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test public void resultCanQueueRetryWithoutRecursionOrAnotherWorker() {
        List<Runnable> workers = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        CardSearchQueue queue = new CardSearchQueue(workers::add);
        queue.submit(queue.generation(), () -> {
            calls.add("timeout");
            queue.submit(queue.generation(), () -> calls.add("retry"));
            calls.add("returned");
        });
        workers.get(0).run();
        assertEquals(1, workers.size());
        assertEquals(Arrays.asList("timeout", "returned", "retry"), calls);
    }

    @Test public void cancellationDropsQueuedWorkAndInvalidatesLateResult() {
        List<Runnable> workers = new ArrayList<>();
        AtomicInteger reads = new AtomicInteger();
        CardSearchQueue queue = new CardSearchQueue(workers::add);
        int old = queue.generation();
        queue.submit(old, reads::incrementAndGet);
        queue.cancel();
        queue.submit(old, reads::incrementAndGet);
        workers.get(0).run();
        assertEquals(0, reads.get());
        assertFalse(queue.isCurrent(old));
        queue.submit(queue.generation(), reads::incrementAndGet);
        workers.get(1).run();
        assertEquals(1, reads.get());
    }

    @Test public void resumedScreenStillWaitsForPreviousSdkCallToReturn() {
        List<Runnable> workers = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        CardSearchQueue queue = new CardSearchQueue(workers::add);
        int old = queue.generation();
        queue.submit(old, () -> {
            queue.cancel();
            queue.submit(queue.generation(), () -> calls.add("new screen"));
            if (queue.isCurrent(old)) calls.add("stale success");
            calls.add("old returned");
        });
        workers.get(0).run();
        assertEquals(1, workers.size());
        assertEquals(Arrays.asList("old returned", "new screen"), calls);
    }

    @Test public void successfulReadConsumesQueuedDuplicateStart() {
        List<Runnable> workers = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        CardSearchQueue queue = new CardSearchQueue(workers::add);
        queue.submit(queue.generation(), () -> {
            queue.submit(queue.generation(), () -> calls.add("duplicate"));
            queue.clearPending();
            calls.add("process card");
        });
        workers.get(0).run();
        assertEquals(Arrays.asList("process card"), calls);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("Worker did not release");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}

package com.verdant.salon_ecomm.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

// Runs N calls at the same instant (all threads wait on one start gate) and collects each outcome, so a
// test can assert "exactly one succeeded, the rest failed with X" instead of hoping for a race.
// Keep N below the Hikari pool size (10), or threads wait for a connection instead of racing.
public final class Concurrently {

    private Concurrently() {
    }

    public record Outcome<T>(T value, Throwable error) {
        public boolean succeeded() {
            return error == null;
        }
    }

    public static <T> List<Outcome<T>> run(int threads, IntFunction<Callable<T>> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<T> call = task.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();

            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> f : futures) {
                try {
                    outcomes.add(new Outcome<>(f.get(60, TimeUnit.SECONDS), null));
                } catch (java.util.concurrent.ExecutionException e) {
                    outcomes.add(new Outcome<>(null, e.getCause()));
                } catch (java.util.concurrent.TimeoutException e) {
                    outcomes.add(new Outcome<>(null, e));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    public static <T> long successes(List<Outcome<T>> outcomes) {
        return outcomes.stream().filter(Outcome::succeeded).count();
    }
}

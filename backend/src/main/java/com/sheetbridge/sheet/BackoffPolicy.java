package com.sheetbridge.sheet;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Equal-jitter exponential backoff for Sheets API quota and transient failures.
 * delay = temp/2 + random(0, temp/2) where temp = min(cap, base * 2^attempt)
 */
public final class BackoffPolicy {

    private final long baseMillis;
    private final long maxMillis;
    private final Sleeper sleeper;
    private final RandomLong random;

    public BackoffPolicy(long baseMillis, long maxMillis) {
        this(baseMillis, maxMillis, Thread::sleep, bound -> ThreadLocalRandom.current().nextLong(bound));
    }

    public BackoffPolicy(long baseMillis, long maxMillis, Sleeper sleeper, RandomLong random) {
        if (baseMillis <= 0 || maxMillis < baseMillis) {
            throw new IllegalArgumentException("Backoff bounds are invalid");
        }
        this.baseMillis = baseMillis;
        this.maxMillis = maxMillis;
        this.sleeper = sleeper;
        this.random = random;
    }

    public long delayMillis(int attempt) {
        int cappedAttempt = Math.min(Math.max(attempt, 0), 16);
        long exponential = baseMillis * (1L << cappedAttempt);
        long temp = Math.min(maxMillis, exponential);
        long half = Math.max(1L, temp / 2);
        return half + random.nextLong(half + 1);
    }

    public Duration delay(int attempt) {
        return Duration.ofMillis(delayMillis(attempt));
    }

    public <T> T execute(Supplier<T> action, Predicate<Exception> retryable, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        Exception last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (Exception ex) {
                last = ex;
                if (attempt == maxAttempts - 1 || !retryable.test(ex)) {
                    break;
                }
                try {
                    sleeper.sleep(delayMillis(attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SheetsQuotaException("Retry interrupted", ie);
                }
            }
        }
        if (last instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw new SheetsQuotaException("Retry exhausted", last);
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    @FunctionalInterface
    public interface RandomLong {
        long nextLong(long bound);
    }
}

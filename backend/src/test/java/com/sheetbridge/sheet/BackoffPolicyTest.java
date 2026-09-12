package com.sheetbridge.sheet;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackoffPolicyTest {

    @Test
    void equalJitterStaysInsideHalfToFullExponentialWindow() {
        BackoffPolicy policy = new BackoffPolicy(100, 8000, millis -> {
        }, bound -> bound - 1);
        long delay0 = policy.delayMillis(0);
        assertTrue(delay0 >= 50 && delay0 <= 100, "delay=" + delay0);
        long delay3 = policy.delayMillis(3);
        assertTrue(delay3 >= 400 && delay3 <= 800, "delay=" + delay3);
    }

    @Test
    void delayIsCappedAtMax() {
        BackoffPolicy policy = new BackoffPolicy(100, 500, millis -> {
        }, bound -> bound - 1);
        long delay = policy.delayMillis(10);
        assertTrue(delay <= 500, "delay=" + delay);
    }

    @Test
    void retriesRetryableFailuresThenSucceeds() {
        List<Long> sleeps = new ArrayList<>();
        BackoffPolicy policy = new BackoffPolicy(10, 100, sleeps::add, bound -> 0);
        AtomicInteger attempts = new AtomicInteger();
        String result = policy.execute(() -> {
            if (attempts.getAndIncrement() < 2) {
                throw new SheetsQuotaException("Sheets HTTP 429: quota");
            }
            return "ok";
        }, ex -> ex instanceof SheetsQuotaException, 4);
        assertEquals("ok", result);
        assertEquals(2, sleeps.size());
        assertEquals(3, attempts.get());
    }

    @Test
    void nonRetryableFailuresFailFast() {
        BackoffPolicy policy = new BackoffPolicy(10, 100, millis -> {
        }, bound -> 0);
        assertThrows(SheetsQuotaException.class, () -> policy.execute(() -> {
            throw new SheetsQuotaException("Sheets HTTP 400: bad range");
        }, ex -> ex.getMessage() != null && ex.getMessage().contains("429"), 5));
    }
}

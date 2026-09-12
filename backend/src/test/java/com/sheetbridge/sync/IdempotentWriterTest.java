package com.sheetbridge.sync;

import com.sheetbridge.Fixtures;
import com.sheetbridge.domain.AuditSource;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.WriteDirection;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.MappingRepository;
import com.sheetbridge.repo.WriteReceiptRepository;
import com.sheetbridge.sheet.LocalSpreadsheetGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class IdempotentWriterTest {

    @Autowired
    IdempotentWriter writer;
    @Autowired
    MappingRepository mappings;
    @Autowired
    DbRowRepository dbRows;
    @Autowired
    WriteReceiptRepository receipts;
    @Autowired
    LocalSpreadsheetGateway gateway;

    @Test
    void retryOfSameRowAndRevisionDoesNotDuplicate() {
        Mapping mapping = Fixtures.mapping(mappings);
        Map<String, String> payload = Fixtures.payload("SKU-R", "4", "1.00", "N", "once");
        WriteResult first = writer.writeToDb(mapping, "SKU-R", payload, 3, false, "eng", AuditSource.SYNC, null, null);
        WriteResult second = writer.writeToDb(mapping, "SKU-R", payload, 3, false, "eng", AuditSource.SYNC, null, null);
        assertEquals(WriteResult.APPLIED, first);
        assertEquals(WriteResult.SKIPPED_DUPLICATE, second);
        assertEquals(1, receipts.countByMappingIdAndRowKeyAndDirection(mapping.getId(), "SKU-R", WriteDirection.TO_DB));
        assertEquals(1, dbRows.findByMappingIdOrderByRowKeyAsc(mapping.getId()).size());
    }

    @Test
    void concurrentRetriesShareASingleReceipt() throws Exception {
        Mapping mapping = Fixtures.mapping(mappings);
        Map<String, String> payload = Fixtures.payload("SKU-C", "9", "2.00", "N", "race");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger applied = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        WriteResult result = writer.writeToDb(
                                mapping, "SKU-C", payload, 5, false, "eng", AuditSource.SYNC, null, null);
                        if (result != WriteResult.SKIPPED_DUPLICATE) {
                            applied.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, receipts.countByMappingIdAndRowKeyAndDirection(mapping.getId(), "SKU-C", WriteDirection.TO_DB));
        assertEquals(1, dbRows.findByMappingIdOrderByRowKeyAsc(mapping.getId()).size());
        assertTrue(applied.get() >= 1);
    }

    @Test
    void sheetWriteIsIdempotentOnRevision() {
        Mapping mapping = Fixtures.mapping(mappings);
        Map<String, String> payload = Fixtures.payload("SKU-S", "1", "3.00", "N", "sheet");
        WriteResult first = writer.writeToSheet(
                mapping, "SKU-S", payload, 2, false, "ops", AuditSource.SYNC, null, null, gateway);
        WriteResult second = writer.writeToSheet(
                mapping, "SKU-S", payload, 2, false, "ops", AuditSource.SYNC, null, null, gateway);
        assertEquals(WriteResult.APPLIED, first);
        assertEquals(WriteResult.SKIPPED_DUPLICATE, second);
        assertEquals(1, receipts.countByMappingIdAndRowKeyAndDirection(mapping.getId(), "SKU-S", WriteDirection.TO_SHEET));
    }
}

package com.sheetbridge.sync;

import com.sheetbridge.Fixtures;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.SyncRun;
import com.sheetbridge.domain.SyncStatus;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.MappingRepository;
import com.sheetbridge.repo.SnapshotRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import com.sheetbridge.repo.SyncRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ResumableSyncTest {

    @Autowired
    SyncOrchestrator orchestrator;
    @Autowired
    MappingRepository mappings;
    @Autowired
    SpreadsheetRowRepository sheetRows;
    @Autowired
    DbRowRepository dbRows;
    @Autowired
    SnapshotRepository snapshots;
    @Autowired
    SyncRunRepository runs;

    @Test
    void interruptedRunResumesFromCheckpointWithoutDuplicatingWrites() {
        Mapping mapping = Fixtures.mapping(mappings);
        for (int i = 0; i < 9; i++) {
            String sku = "SKU-%02d".formatted(i);
            Map<String, String> base = Fixtures.payload(sku, "10", "1.00", "N", "");
            Map<String, String> sheet = Fixtures.payload(sku, String.valueOf(10 + i), "1.00", "N", "");
            Fixtures.snapshot(snapshots, mapping.getId(), sku, base, 1);
            Fixtures.sheet(sheetRows, mapping.getId(), sku, sheet, 2);
            Fixtures.db(dbRows, mapping.getId(), sku, base, 1);
        }

        SyncRun interrupted = orchestrator.start(mapping.getId(), "TEST", "tester", 3);
        assertEquals(SyncStatus.INTERRUPTED, interrupted.getStatus());
        assertNotNull(interrupted.getCheckpointJson());

        SyncRun resumed = orchestrator.resume(interrupted.getId(), "tester");
        assertEquals(SyncStatus.COMPLETED, resumed.getStatus());

        for (int i = 0; i < 9; i++) {
            String sku = "SKU-%02d".formatted(i);
            var sheet = sheetRows.findByMappingIdAndRowKey(mapping.getId(), sku).orElseThrow();
            var db = dbRows.findByMappingIdAndRowKey(mapping.getId(), sku).orElseThrow();
            assertEquals(Payloads.parse(sheet.getPayloadJson()), Payloads.parse(db.getPayloadJson()));
        }
        SyncRun stored = runs.findById(interrupted.getId()).orElseThrow();
        assertEquals(SyncStatus.COMPLETED, stored.getStatus());
        assertEquals(2, stored.getAttempt());
    }
}

package com.sheetbridge.sync;

import com.sheetbridge.Fixtures;
import com.sheetbridge.domain.ConflictStatus;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.ResolutionChoice;
import com.sheetbridge.domain.SyncStatus;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.repo.ConflictRepository;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.MappingRepository;
import com.sheetbridge.repo.SnapshotRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import com.sheetbridge.repo.WriteReceiptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Injects {@value #SCENARIOS} concurrent-edit scenarios over {@value #RUNS} independent
 * sync runs and asserts zero lost updates and zero duplicated writes.
 */
@SpringBootTest
class ConcurrentEditHarnessTest {

    static final int SCENARIOS = 50;
    static final int RUNS = 20;

    @Autowired
    SyncOrchestrator orchestrator;
    @Autowired
    ConflictResolutionService resolution;
    @Autowired
    MappingRepository mappings;
    @Autowired
    SpreadsheetRowRepository sheetRows;
    @Autowired
    DbRowRepository dbRows;
    @Autowired
    SnapshotRepository snapshots;
    @Autowired
    ConflictRepository conflicts;
    @Autowired
    WriteReceiptRepository receipts;

    @Test
    void fiftyScenariosOverTwentyRuns_zeroLostOrDuplicatedWrites() {
        int lost = 0;
        int duplicated = 0;
        for (int run = 0; run < RUNS; run++) {
            Mapping mapping = Fixtures.mapping(mappings);
            for (int i = 0; i < SCENARIOS; i++) {
                seedScenario(mapping, run, i);
            }

            var first = orchestrator.start(mapping.getId(), "HARNESS", "harness");
            assertEquals(SyncStatus.COMPLETED, first.getStatus());

            conflicts.findByMappingIdAndStatusOrderByCreatedAtAsc(mapping.getId(), ConflictStatus.OPEN)
                    .forEach(conflict -> {
                        int index = Integer.parseInt(conflict.getRowKey().split("-")[2]);
                        ResolutionChoice choice = index % 2 == 0 ? ResolutionChoice.SHEET : ResolutionChoice.DB;
                        resolution.resolve(conflict.getId(), choice, null, null, "harness");
                    });

            long receiptsAfterFirst = receipts.count();
            var second = orchestrator.start(mapping.getId(), "HARNESS-RETRY", "harness");
            assertEquals(SyncStatus.COMPLETED, second.getStatus());
            assertEquals(receiptsAfterFirst, receipts.count(), "retried sync duplicated writes on run " + run);

            lost += countLost(mapping, run);
            duplicated += countDuplicated(mapping);
        }
        assertEquals(0, lost, "lost writes across " + SCENARIOS + " scenarios × " + RUNS + " runs");
        assertEquals(0, duplicated, "duplicated writes across " + SCENARIOS + " scenarios × " + RUNS + " runs");
    }

    private void seedScenario(Mapping mapping, int run, int i) {
        String sku = sku(run, i);
        Map<String, String> base = Fixtures.payload(sku, "10", "5.00", "N", "base");
        switch (i % 8) {
            case 0 -> {
                Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), sku, base);
                overwriteSheet(mapping, sku, Fixtures.payload(sku, "3", "5.00", "N", "base"), 2);
            }
            case 1 -> {
                Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), sku, base);
                overwriteDb(mapping, sku, Fixtures.payload(sku, "10", "8.00", "N", "base"), 2);
            }
            case 2 -> {
                Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), sku, base);
                overwriteSheet(mapping, sku, Fixtures.payload(sku, "4", "5.00", "N", "base"), 2);
                overwriteDb(mapping, sku, Fixtures.payload(sku, "10", "9.00", "N", "base"), 2);
            }
            case 3 -> {
                Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), sku, base);
                overwriteSheet(mapping, sku, Fixtures.payload(sku, "1", "5.00", "N", "base"), 2);
                overwriteDb(mapping, sku, Fixtures.payload(sku, "2", "5.00", "N", "base"), 2);
            }
            case 4 -> {
                Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), sku, base);
                overwriteSheet(mapping, sku, Fixtures.payload(sku, "6", "5.00", "N", "retry"), 2);
            }
            case 5 -> Fixtures.sheet(sheetRows, mapping.getId(), sku,
                    Fixtures.payload(sku, "1", "1.00", "N", "new-sheet"), 1);
            case 6 -> Fixtures.db(dbRows, mapping.getId(), sku,
                    Fixtures.payload(sku, "1", "1.00", "N", "new-db"), 1);
            default -> {
                Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), sku, base);
                Map<String, String> same = Fixtures.payload(sku, "10", "5.00", "N", "both");
                overwriteSheet(mapping, sku, same, 2);
                overwriteDb(mapping, sku, same, 2);
            }
        }
    }

    private int countLost(Mapping mapping, int run) {
        int lost = 0;
        for (int i = 0; i < SCENARIOS; i++) {
            String sku = sku(run, i);
            Map<String, String> sheet = sheetRows.findByMappingIdAndRowKey(mapping.getId(), sku)
                    .filter(row -> !row.isDeleted())
                    .map(row -> Payloads.parse(row.getPayloadJson()))
                    .orElse(null);
            Map<String, String> db = dbRows.findByMappingIdAndRowKey(mapping.getId(), sku)
                    .filter(row -> !row.isDeleted())
                    .map(row -> Payloads.parse(row.getPayloadJson()))
                    .orElse(null);
            if (sheet == null || db == null || !sheet.equals(db)) {
                lost++;
                continue;
            }
            switch (i % 8) {
                case 0, 4 -> {
                    if (!"3".equals(sheet.get("quantity")) && !"6".equals(sheet.get("quantity"))) {
                        lost++;
                    }
                }
                case 1 -> {
                    if (!"8.00".equals(sheet.get("price"))) {
                        lost++;
                    }
                }
                case 2 -> {
                    if (!"4".equals(sheet.get("quantity")) || !"9.00".equals(sheet.get("price"))) {
                        lost++;
                    }
                }
                case 3 -> {
                    String expected = i % 2 == 0 ? "1" : "2";
                    if (!expected.equals(sheet.get("quantity"))) {
                        lost++;
                    }
                }
                case 5 -> {
                    if (!"new-sheet".equals(sheet.get("notes"))) {
                        lost++;
                    }
                }
                case 6 -> {
                    if (!"new-db".equals(sheet.get("notes"))) {
                        lost++;
                    }
                }
                default -> {
                    if (!"both".equals(sheet.get("notes"))) {
                        lost++;
                    }
                }
            }
        }
        return lost;
    }

    private int countDuplicated(Mapping mapping) {
        List<String> dbKeys = dbRows.findByMappingIdOrderByRowKeyAsc(mapping.getId()).stream()
                .map(row -> row.getRowKey())
                .toList();
        List<String> sheetKeys = sheetRows.findByMappingIdOrderByRowKeyAsc(mapping.getId()).stream()
                .map(row -> row.getRowKey())
                .toList();
        Set<String> uniqueDb = new HashSet<>(dbKeys);
        Set<String> uniqueSheet = new HashSet<>(sheetKeys);
        int dup = 0;
        if (uniqueDb.size() != dbKeys.size()) {
            dup += dbKeys.size() - uniqueDb.size();
        }
        if (uniqueSheet.size() != sheetKeys.size()) {
            dup += sheetKeys.size() - uniqueSheet.size();
        }
        return dup;
    }

    private void overwriteSheet(Mapping mapping, String sku, Map<String, String> payload, long revision) {
        sheetRows.findByMappingIdAndRowKey(mapping.getId(), sku).ifPresent(row -> {
            row.setPayloadJson(Payloads.stringify(payload));
            row.setRevision(revision);
            sheetRows.save(row);
        });
    }

    private void overwriteDb(Mapping mapping, String sku, Map<String, String> payload, long revision) {
        dbRows.findByMappingIdAndRowKey(mapping.getId(), sku).ifPresent(row -> {
            row.setPayloadJson(Payloads.stringify(payload));
            row.setRevision(revision);
            dbRows.save(row);
        });
    }

    private static String sku(int run, int i) {
        return "R%02d-SC-%02d".formatted(run, i);
    }
}

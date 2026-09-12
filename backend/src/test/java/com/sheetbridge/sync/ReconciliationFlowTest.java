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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ReconciliationFlowTest {

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

    @Test
    void classifiesOneSidedAutoMergeAndConflictThenResolutionConverges() {
        Mapping mapping = Fixtures.mapping(mappings);
        Map<String, String> baseA = Fixtures.payload("A", "10", "5.00", "N", "");
        Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), "A", baseA);
        sheetRows.findByMappingIdAndRowKey(mapping.getId(), "A").ifPresent(row -> {
            row.setPayloadJson(Payloads.stringify(Fixtures.payload("A", "4", "5.00", "N", "")));
            row.setRevision(2);
            sheetRows.save(row);
        });

        Map<String, String> baseB = Fixtures.payload("B", "10", "5.00", "N", "");
        Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), "B", baseB);
        dbRows.findByMappingIdAndRowKey(mapping.getId(), "B").ifPresent(row -> {
            row.setPayloadJson(Payloads.stringify(Fixtures.payload("B", "10", "9.00", "N", "")));
            row.setRevision(2);
            dbRows.save(row);
        });

        Map<String, String> baseC = Fixtures.payload("C", "10", "5.00", "N", "old");
        Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), "C", baseC);
        sheetRows.findByMappingIdAndRowKey(mapping.getId(), "C").ifPresent(row -> {
            row.setPayloadJson(Payloads.stringify(Fixtures.payload("C", "8", "5.00", "N", "old")));
            row.setRevision(2);
            sheetRows.save(row);
        });
        dbRows.findByMappingIdAndRowKey(mapping.getId(), "C").ifPresent(row -> {
            row.setPayloadJson(Payloads.stringify(Fixtures.payload("C", "10", "5.00", "N", "eng")));
            row.setRevision(2);
            dbRows.save(row);
        });

        Map<String, String> baseD = Fixtures.payload("D", "10", "5.00", "N", "");
        Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), "D", baseD);
        sheetRows.findByMappingIdAndRowKey(mapping.getId(), "D").ifPresent(row -> {
            row.setPayloadJson(Payloads.stringify(Fixtures.payload("D", "1", "5.00", "N", "")));
            row.setRevision(2);
            sheetRows.save(row);
        });
        dbRows.findByMappingIdAndRowKey(mapping.getId(), "D").ifPresent(row -> {
            row.setPayloadJson(Payloads.stringify(Fixtures.payload("D", "2", "5.00", "N", "")));
            row.setRevision(2);
            dbRows.save(row);
        });

        var run = orchestrator.start(mapping.getId(), "TEST", "tester");
        assertEquals(SyncStatus.COMPLETED, run.getStatus());
        assertTrue(run.getStatsJson().contains("\"sheetOnly\":1"));
        assertTrue(run.getStatsJson().contains("\"dbOnly\":1"));
        assertTrue(run.getStatsJson().contains("\"autoMerged\":1"));
        assertTrue(run.getStatsJson().contains("\"conflicts\":1"));

        assertEquals("4", Payloads.parse(dbRows.findByMappingIdAndRowKey(mapping.getId(), "A").orElseThrow().getPayloadJson()).get("quantity"));
        assertEquals("9.00", Payloads.parse(sheetRows.findByMappingIdAndRowKey(mapping.getId(), "B").orElseThrow().getPayloadJson()).get("price"));
        Map<String, String> mergedC = Payloads.parse(dbRows.findByMappingIdAndRowKey(mapping.getId(), "C").orElseThrow().getPayloadJson());
        assertEquals("8", mergedC.get("quantity"));
        assertEquals("eng", mergedC.get("notes"));

        var open = conflicts.findByMappingIdAndStatusOrderByCreatedAtAsc(mapping.getId(), ConflictStatus.OPEN);
        assertEquals(1, open.size());
        assertEquals("D", open.getFirst().getRowKey());

        resolution.resolve(open.getFirst().getId(), ResolutionChoice.SHEET, null, null, "maya.ops");
        Map<String, String> resolved = Payloads.parse(
                dbRows.findByMappingIdAndRowKey(mapping.getId(), "D").orElseThrow().getPayloadJson());
        assertEquals("1", resolved.get("quantity"));
        assertEquals(resolved, Payloads.parse(
                sheetRows.findByMappingIdAndRowKey(mapping.getId(), "D").orElseThrow().getPayloadJson()));
        assertEquals(ConflictStatus.RESOLVED, conflicts.findById(open.getFirst().getId()).orElseThrow().getStatus());
    }

    @Test
    void resolvingTwiceIsIdempotent() {
        Mapping mapping = Fixtures.mapping(mappings);
        Map<String, String> base = Fixtures.payload("Z", "10", "5.00", "N", "");
        Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), "Z", base);
        sheetRows.findByMappingIdAndRowKey(mapping.getId(), "Z").ifPresent(row -> {
            row.setPayloadJson(Payloads.stringify(Fixtures.payload("Z", "1", "5.00", "N", "")));
            row.setRevision(2);
            sheetRows.save(row);
        });
        dbRows.findByMappingIdAndRowKey(mapping.getId(), "Z").ifPresent(row -> {
            row.setPayloadJson(Payloads.stringify(Fixtures.payload("Z", "2", "5.00", "N", "")));
            row.setRevision(2);
            dbRows.save(row);
        });
        orchestrator.start(mapping.getId(), "TEST", "tester");
        var conflict = conflicts.findByMappingIdAndStatusOrderByCreatedAtAsc(mapping.getId(), ConflictStatus.OPEN).getFirst();
        var first = resolution.resolve(conflict.getId(), ResolutionChoice.DB, null, null, "eng");
        var second = resolution.resolve(conflict.getId(), ResolutionChoice.SHEET, null, null, "ops");
        assertEquals(first.getResolvedPayloadJson(), second.getResolvedPayloadJson());
        assertEquals(first.getResolvedBy(), second.getResolvedBy());
        assertEquals(ResolutionChoice.DB, second.getResolutionChoice());
    }
}

package com.sheetbridge.audit;

import com.sheetbridge.Fixtures;
import com.sheetbridge.domain.AuditSource;
import com.sheetbridge.domain.ConflictStatus;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.ResolutionChoice;
import com.sheetbridge.repo.AuditEntryRepository;
import com.sheetbridge.repo.ConflictRepository;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.MappingRepository;
import com.sheetbridge.repo.SnapshotRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import com.sheetbridge.sync.ConflictResolutionService;
import com.sheetbridge.sync.RowEditService;
import com.sheetbridge.sync.SyncOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AuditTrailTest {

    @Autowired
    RowEditService edits;
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
    AuditEntryRepository audits;
    @Autowired
    ConflictRepository conflicts;

    @Test
    void everyFieldChangeTracesToActorTimeAndSource() {
        Mapping mapping = Fixtures.mapping(mappings);
        Map<String, String> base = Fixtures.payload("AUD-1", "10", "5.00", "N", "");
        Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), "AUD-1", base);

        edits.editSheet(mapping.getId(), "AUD-1",
                Fixtures.payload("AUD-1", "4", "5.00", "N", ""), false, "maya.ops");
        edits.editDb(mapping.getId(), "AUD-1",
                Fixtures.payload("AUD-1", "10", "7.00", "N", ""), false, "ravi.eng");

        var sheetEdits = audits.findByMappingIdAndRowKeyAndFieldNameOrderByCreatedAtDesc(
                mapping.getId(), "AUD-1", "quantity");
        assertEquals(1, sheetEdits.size());
        assertEquals(AuditSource.SHEET, sheetEdits.getFirst().getSource());
        assertEquals("maya.ops", sheetEdits.getFirst().getActor());
        assertEquals("10", sheetEdits.getFirst().getOldValue());
        assertEquals("4", sheetEdits.getFirst().getNewValue());

        orchestrator.start(mapping.getId(), "TEST", "system");
        var open = conflicts.findByMappingIdAndStatusOrderByCreatedAtAsc(mapping.getId(), ConflictStatus.OPEN);
        assertTrue(open.isEmpty());

        var mergedQty = audits.findByMappingIdAndRowKeyAndFieldNameOrderByCreatedAtDesc(
                mapping.getId(), "AUD-1", "quantity");
        assertTrue(mergedQty.stream().anyMatch(e -> e.getSource() == AuditSource.AUTO_MERGE
                || e.getSource() == AuditSource.SYNC
                || e.getSource() == AuditSource.SHEET));
        assertTrue(mergedQty.stream().allMatch(e -> e.getCreatedAt() != null && e.getActor() != null));
    }

    @Test
    void resolutionIsAttributedToThePersonWhoPickedASide() {
        Mapping mapping = Fixtures.mapping(mappings);
        Map<String, String> base = Fixtures.payload("AUD-2", "10", "5.00", "N", "");
        Fixtures.aligned(snapshots, sheetRows, dbRows, mapping.getId(), "AUD-2", base);
        edits.editSheet(mapping.getId(), "AUD-2",
                Fixtures.payload("AUD-2", "1", "5.00", "N", ""), false, "maya.ops");
        edits.editDb(mapping.getId(), "AUD-2",
                Fixtures.payload("AUD-2", "2", "5.00", "N", ""), false, "ravi.eng");
        orchestrator.start(mapping.getId(), "TEST", "system");
        var conflict = conflicts.findByMappingIdAndStatusOrderByCreatedAtAsc(mapping.getId(), ConflictStatus.OPEN).getFirst();
        resolution.resolve(conflict.getId(), ResolutionChoice.CUSTOM, null,
                Map.of("sku", "SHEET", "quantity", "SHEET", "price", "DB", "warehouse", "SHEET", "notes", "SHEET"),
                "alex.ops");
        var qty = audits.findByMappingIdAndRowKeyAndFieldNameOrderByCreatedAtDesc(
                mapping.getId(), "AUD-2", "quantity");
        assertTrue(qty.stream().anyMatch(e -> e.getSource() == AuditSource.USER_RESOLUTION
                && "alex.ops".equals(e.getActor())));
    }
}

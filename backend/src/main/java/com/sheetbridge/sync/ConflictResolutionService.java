package com.sheetbridge.sync;

import com.sheetbridge.domain.AuditSource;
import com.sheetbridge.domain.Conflict;
import com.sheetbridge.domain.ConflictStatus;
import com.sheetbridge.domain.DbRow;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.ResolutionChoice;
import com.sheetbridge.domain.SpreadsheetRow;
import com.sheetbridge.error.ApiException;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.repo.ConflictRepository;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.MappingRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import com.sheetbridge.sheet.SpreadsheetGatewayResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConflictResolutionService {

    private final ConflictRepository conflicts;
    private final MappingRepository mappings;
    private final SpreadsheetRowRepository sheetRows;
    private final DbRowRepository dbRows;
    private final IdempotentWriter writer;
    private final SnapshotService snapshots;
    private final SpreadsheetGatewayResolver gateways;

    public ConflictResolutionService(
            ConflictRepository conflicts,
            MappingRepository mappings,
            SpreadsheetRowRepository sheetRows,
            DbRowRepository dbRows,
            IdempotentWriter writer,
            SnapshotService snapshots,
            SpreadsheetGatewayResolver gateways
    ) {
        this.conflicts = conflicts;
        this.mappings = mappings;
        this.sheetRows = sheetRows;
        this.dbRows = dbRows;
        this.writer = writer;
        this.snapshots = snapshots;
        this.gateways = gateways;
    }

    @Transactional
    public Conflict resolve(
            UUID conflictId,
            ResolutionChoice choice,
            Map<String, String> customPayload,
            Map<String, String> fieldPicks,
            String actor
    ) {
        Conflict conflict = conflicts.findById(conflictId).orElseThrow(() -> ApiException.notFound("Conflict not found"));
        if (conflict.getStatus() == ConflictStatus.RESOLVED) {
            return conflict;
        }
        Mapping mapping = mappings.findById(conflict.getMappingId())
                .orElseThrow(() -> ApiException.notFound("Mapping not found"));
        List<String> columns = Payloads.parseList(mapping.getColumnsJson());
        Map<String, String> sheet = Payloads.parse(conflict.getSheetPayloadJson());
        Map<String, String> db = Payloads.parse(conflict.getDbPayloadJson());
        Map<String, String> snapshot = conflict.getSnapshotPayloadJson() == null
                ? Map.of()
                : Payloads.parse(conflict.getSnapshotPayloadJson());

        Map<String, String> resolved = switch (choice) {
            case SHEET -> Payloads.project(sheet, columns);
            case DB -> Payloads.project(db, columns);
            case CUSTOM -> {
                if (fieldPicks != null && !fieldPicks.isEmpty()) {
                    yield Payloads.mergePicks(snapshot, sheet, db, columns, fieldPicks);
                }
                if (customPayload == null || customPayload.isEmpty()) {
                    throw ApiException.badRequest("CUSTOM resolution requires fieldPicks or customPayload");
                }
                yield Payloads.project(customPayload, columns);
            }
        };
        resolved.put(mapping.getRowKeyColumn(), conflict.getRowKey());

        long sheetRev = sheetRows.findByMappingIdAndRowKey(mapping.getId(), conflict.getRowKey())
                .map(SpreadsheetRow::getRevision).orElse(1L);
        long dbRev = dbRows.findByMappingIdAndRowKey(mapping.getId(), conflict.getRowKey())
                .map(DbRow::getRevision).orElse(1L);
        long revision = Math.max(sheetRev, dbRev) + 1;

        var gateway = gateways.resolve(mapping);
        writer.writeToDb(mapping, conflict.getRowKey(), resolved, revision, false,
                actor, AuditSource.USER_RESOLUTION, conflict.getSyncRunId(), conflict.getId());
        writer.writeToSheet(mapping, conflict.getRowKey(), resolved, revision, false,
                actor, AuditSource.USER_RESOLUTION, conflict.getSyncRunId(), conflict.getId(), gateway);
        snapshots.upsert(mapping.getId(), conflict.getRowKey(), resolved, revision, false);

        conflict.setStatus(ConflictStatus.RESOLVED);
        conflict.setResolutionChoice(choice);
        conflict.setResolvedPayloadJson(Payloads.stringify(resolved));
        conflict.setResolvedBy(actor);
        conflict.setResolvedAt(Instant.now());
        return conflicts.save(conflict);
    }
}

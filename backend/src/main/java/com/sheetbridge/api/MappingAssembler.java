package com.sheetbridge.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheetbridge.api.dto.Dtos;
import com.sheetbridge.domain.AuditEntry;
import com.sheetbridge.domain.Conflict;
import com.sheetbridge.domain.ConflictStatus;
import com.sheetbridge.domain.DbRow;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.Snapshot;
import com.sheetbridge.domain.SpreadsheetRow;
import com.sheetbridge.domain.SyncRun;
import com.sheetbridge.reconcile.FieldDecision;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.reconcile.RowMergeResult;
import com.sheetbridge.repo.ConflictRepository;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.SnapshotRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import com.sheetbridge.repo.SyncRunRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MappingAssembler {

    private final SpreadsheetRowRepository sheetRows;
    private final DbRowRepository dbRows;
    private final SnapshotRepository snapshots;
    private final ConflictRepository conflicts;
    private final SyncRunRepository runs;
    private final ObjectMapper mapper;

    public MappingAssembler(
            SpreadsheetRowRepository sheetRows,
            DbRowRepository dbRows,
            SnapshotRepository snapshots,
            ConflictRepository conflicts,
            SyncRunRepository runs,
            ObjectMapper mapper
    ) {
        this.sheetRows = sheetRows;
        this.dbRows = dbRows;
        this.snapshots = snapshots;
        this.conflicts = conflicts;
        this.runs = runs;
        this.mapper = mapper;
    }

    public Dtos.MappingResponse toResponse(Mapping mapping) {
        var last = runs.findFirstByMappingIdOrderByCreatedAtDesc(mapping.getId());
        return new Dtos.MappingResponse(
                mapping.getId(),
                mapping.getName(),
                mapping.getSpreadsheetId(),
                mapping.getSheetName(),
                mapping.getRowKeyColumn(),
                Payloads.parseList(mapping.getColumnsJson()),
                mapping.getSourceType(),
                mapping.getCreatedAt(),
                mapping.getUpdatedAt(),
                sheetRows.countByMappingIdAndDeletedFalse(mapping.getId()),
                dbRows.countByMappingIdAndDeletedFalse(mapping.getId()),
                conflicts.countByMappingIdAndStatus(mapping.getId(), ConflictStatus.OPEN),
                last.map(SyncRun::getStatus).orElse(null),
                last.map(run -> run.getFinishedAt() != null ? run.getFinishedAt() : run.getStartedAt()).orElse(null)
        );
    }

    public Dtos.RowResponse toSheet(SpreadsheetRow row) {
        return new Dtos.RowResponse(
                row.getRowKey(),
                Payloads.parse(row.getPayloadJson()),
                row.getRevision(),
                row.isDeleted(),
                row.getUpdatedAt(),
                "SHEET"
        );
    }

    public Dtos.RowResponse toDb(DbRow row) {
        return new Dtos.RowResponse(
                row.getRowKey(),
                Payloads.parse(row.getPayloadJson()),
                row.getRevision(),
                row.isDeleted(),
                row.getUpdatedAt(),
                "DB"
        );
    }

    public Dtos.PreviewRow toPreview(UUID mappingId, RowMergeResult result) {
        SpreadsheetRow sheet = sheetRows.findByMappingIdAndRowKey(mappingId, result.rowKey()).orElse(null);
        DbRow db = dbRows.findByMappingIdAndRowKey(mappingId, result.rowKey()).orElse(null);
        Snapshot snap = snapshots.findByMappingIdAndRowKey(mappingId, result.rowKey()).orElse(null);
        return new Dtos.PreviewRow(
                result.rowKey(),
                result.outcome(),
                sheet == null || sheet.isDeleted() ? Map.of() : Payloads.parse(sheet.getPayloadJson()),
                db == null || db.isDeleted() ? Map.of() : Payloads.parse(db.getPayloadJson()),
                snap == null || snap.isDeleted() ? Map.of() : Payloads.parse(snap.getPayloadJson()),
                result.mergedPayload(),
                result.fields(),
                result.conflictingFields(),
                sheet == null ? 0 : sheet.getRevision(),
                db == null ? 0 : db.getRevision()
        );
    }

    public Dtos.SyncRunResponse toRun(SyncRun run) {
        return new Dtos.SyncRunResponse(
                run.getId(),
                run.getMappingId(),
                run.getStatus(),
                run.getTriggerSource(),
                run.getActor(),
                readMap(run.getCheckpointJson()),
                readMap(run.getStatsJson()),
                run.getErrorMessage(),
                run.getAttempt(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt()
        );
    }

    public Dtos.ConflictResponse toConflict(Conflict conflict) {
        return new Dtos.ConflictResponse(
                conflict.getId(),
                conflict.getMappingId(),
                conflict.getSyncRunId(),
                conflict.getRowKey(),
                Payloads.parse(conflict.getSheetPayloadJson()),
                Payloads.parse(conflict.getDbPayloadJson()),
                conflict.getSnapshotPayloadJson() == null ? Map.of() : Payloads.parse(conflict.getSnapshotPayloadJson()),
                Payloads.parseList(conflict.getConflictingFieldsJson()),
                readFields(conflict.getFieldsJson()),
                conflict.getStatus(),
                conflict.getResolutionChoice(),
                conflict.getResolvedPayloadJson() == null ? null : Payloads.parse(conflict.getResolvedPayloadJson()),
                conflict.getResolvedBy(),
                conflict.getResolvedAt(),
                conflict.getCreatedAt()
        );
    }

    public Dtos.AuditResponse toAudit(AuditEntry entry) {
        return new Dtos.AuditResponse(
                entry.getId(),
                entry.getMappingId(),
                entry.getRowKey(),
                entry.getFieldName(),
                entry.getOldValue(),
                entry.getNewValue(),
                entry.getSource().name(),
                entry.getActor(),
                entry.getSyncRunId(),
                entry.getConflictId(),
                entry.getRevision(),
                entry.getCreatedAt()
        );
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private List<FieldDecision> readFields(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}

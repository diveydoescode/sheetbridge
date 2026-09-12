package com.sheetbridge.sync;

import com.sheetbridge.audit.AuditService;
import com.sheetbridge.domain.AuditSource;
import com.sheetbridge.domain.ConflictStatus;
import com.sheetbridge.domain.DbRow;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.SpreadsheetRow;
import com.sheetbridge.error.ApiException;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.repo.ConflictRepository;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.MappingRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RowEditService {

    private final MappingRepository mappings;
    private final SpreadsheetRowRepository sheetRows;
    private final DbRowRepository dbRows;
    private final ConflictRepository conflicts;
    private final AuditService audit;

    public RowEditService(
            MappingRepository mappings,
            SpreadsheetRowRepository sheetRows,
            DbRowRepository dbRows,
            ConflictRepository conflicts,
            AuditService audit
    ) {
        this.mappings = mappings;
        this.sheetRows = sheetRows;
        this.dbRows = dbRows;
        this.conflicts = conflicts;
        this.audit = audit;
    }

    @Transactional
    public SpreadsheetRow editSheet(UUID mappingId, String rowKey, Map<String, String> payload, boolean deleted, String actor) {
        Mapping mapping = requireMapping(mappingId);
        rejectIfOpenConflict(mappingId, rowKey);
        List<String> columns = Payloads.parseList(mapping.getColumnsJson());
        Map<String, String> projected = Payloads.project(payload, columns);
        projected.put(mapping.getRowKeyColumn(), rowKey);

        SpreadsheetRow row = sheetRows.findByMappingIdAndRowKey(mappingId, rowKey).orElseGet(() -> {
            SpreadsheetRow created = new SpreadsheetRow();
            created.setId(UUID.randomUUID());
            created.setMappingId(mappingId);
            created.setRowKey(rowKey);
            created.setRevision(0);
            created.setPayloadJson(Payloads.stringify(Map.of()));
            return created;
        });
        Map<String, String> before = row.isDeleted() ? Map.of() : Payloads.parse(row.getPayloadJson());
        long revision = row.getRevision() + 1;
        row.setPayloadJson(Payloads.stringify(deleted ? Map.of(mapping.getRowKeyColumn(), rowKey) : projected));
        row.setDeleted(deleted);
        row.setRevision(revision);
        row.setUpdatedAt(Instant.now());
        sheetRows.save(row);
        audit.recordDiff(mappingId, rowKey, before, deleted ? Map.of() : projected, columns,
                AuditSource.SHEET, actor, revision, null, null);
        return row;
    }

    @Transactional
    public DbRow editDb(UUID mappingId, String rowKey, Map<String, String> payload, boolean deleted, String actor) {
        Mapping mapping = requireMapping(mappingId);
        rejectIfOpenConflict(mappingId, rowKey);
        List<String> columns = Payloads.parseList(mapping.getColumnsJson());
        Map<String, String> projected = Payloads.project(payload, columns);
        projected.put(mapping.getRowKeyColumn(), rowKey);

        DbRow row = dbRows.findByMappingIdAndRowKey(mappingId, rowKey).orElseGet(() -> {
            DbRow created = new DbRow();
            created.setId(UUID.randomUUID());
            created.setMappingId(mappingId);
            created.setRowKey(rowKey);
            created.setRevision(0);
            created.setPayloadJson(Payloads.stringify(Map.of()));
            return created;
        });
        Map<String, String> before = row.isDeleted() ? Map.of() : Payloads.parse(row.getPayloadJson());
        long revision = row.getRevision() + 1;
        row.setPayloadJson(Payloads.stringify(deleted ? Map.of(mapping.getRowKeyColumn(), rowKey) : projected));
        row.setDeleted(deleted);
        row.setRevision(revision);
        row.setUpdatedAt(Instant.now());
        dbRows.save(row);
        audit.recordDiff(mappingId, rowKey, before, deleted ? Map.of() : projected, columns,
                AuditSource.DB, actor, revision, null, null);
        return row;
    }

    private Mapping requireMapping(UUID mappingId) {
        return mappings.findById(mappingId).orElseThrow(() -> ApiException.notFound("Mapping not found"));
    }

    private void rejectIfOpenConflict(UUID mappingId, String rowKey) {
        conflicts.findByMappingIdAndRowKeyAndStatus(mappingId, rowKey, ConflictStatus.OPEN).ifPresent(c -> {
            throw ApiException.conflict("Row " + rowKey + " has an open conflict; resolve it before editing");
        });
    }
}

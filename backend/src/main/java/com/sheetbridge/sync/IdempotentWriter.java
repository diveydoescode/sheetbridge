package com.sheetbridge.sync;

import com.sheetbridge.audit.AuditService;
import com.sheetbridge.domain.AuditSource;
import com.sheetbridge.domain.DbRow;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.WriteDirection;
import com.sheetbridge.domain.WriteReceipt;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import com.sheetbridge.repo.WriteReceiptRepository;
import com.sheetbridge.sheet.SpreadsheetGateway;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes are keyed on (mapping, row key, revision, direction). A retried sync
 * never inserts a second copy; a crash after the receipt but before the body
 * is repaired on the next attempt.
 */
@Service
public class IdempotentWriter {

    private final WriteReceiptRepository receipts;
    private final DbRowRepository dbRows;
    private final SpreadsheetRowRepository sheetRows;
    private final AuditService audit;

    public IdempotentWriter(
            WriteReceiptRepository receipts,
            DbRowRepository dbRows,
            SpreadsheetRowRepository sheetRows,
            AuditService audit
    ) {
        this.receipts = receipts;
        this.dbRows = dbRows;
        this.sheetRows = sheetRows;
        this.audit = audit;
    }

    @Transactional
    public WriteResult writeToDb(
            Mapping mapping,
            String rowKey,
            Map<String, String> payload,
            long revision,
            boolean delete,
            String actor,
            AuditSource source,
            UUID syncRunId,
            UUID conflictId
    ) {
        return write(
                mapping,
                rowKey,
                payload,
                revision,
                delete,
                WriteDirection.TO_DB,
                actor,
                source,
                syncRunId,
                conflictId,
                null
        );
    }

    @Transactional
    public WriteResult writeToSheet(
            Mapping mapping,
            String rowKey,
            Map<String, String> payload,
            long revision,
            boolean delete,
            String actor,
            AuditSource source,
            UUID syncRunId,
            UUID conflictId,
            SpreadsheetGateway gateway
    ) {
        return write(
                mapping,
                rowKey,
                payload,
                revision,
                delete,
                WriteDirection.TO_SHEET,
                actor,
                source,
                syncRunId,
                conflictId,
                gateway
        );
    }

    private WriteResult write(
            Mapping mapping,
            String rowKey,
            Map<String, String> payload,
            long revision,
            boolean delete,
            WriteDirection direction,
            String actor,
            AuditSource source,
            UUID syncRunId,
            UUID conflictId,
            SpreadsheetGateway gateway
    ) {
        UUID mappingId = mapping.getId();
        String hash = Payloads.hash(payload == null ? Map.of() : payload);
        Optional<WriteReceipt> existing = receipts.findByMappingIdAndRowKeyAndRevisionAndDirection(
                mappingId, rowKey, revision, direction);

        boolean destinationMatches = destinationAlreadyApplied(mapping, rowKey, payload, revision, delete, direction);
        if (existing.isPresent() && destinationMatches) {
            return WriteResult.SKIPPED_DUPLICATE;
        }

        if (existing.isEmpty()) {
            try {
                WriteReceipt receipt = new WriteReceipt();
                receipt.setId(UUID.randomUUID());
                receipt.setMappingId(mappingId);
                receipt.setRowKey(rowKey);
                receipt.setRevision(revision);
                receipt.setDirection(direction);
                receipt.setPayloadHash(hash);
                receipt.setCreatedAt(Instant.now());
                receipts.saveAndFlush(receipt);
            } catch (DataIntegrityViolationException ex) {
                if (destinationAlreadyApplied(mapping, rowKey, payload, revision, delete, direction)) {
                    return WriteResult.SKIPPED_DUPLICATE;
                }
            }
        }

        Map<String, String> before = currentPayload(mapping, rowKey, direction);
        applyDestination(mapping, rowKey, payload, revision, delete, direction, gateway);
        List<String> columns = Payloads.parseList(mapping.getColumnsJson());
        Map<String, String> after = delete ? Map.of() : payload;
        audit.recordDiff(mappingId, rowKey, before, after, columns, source, actor, revision, syncRunId, conflictId);
        return existing.isPresent() ? WriteResult.REPAIRED : WriteResult.APPLIED;
    }

    private boolean destinationAlreadyApplied(
            Mapping mapping,
            String rowKey,
            Map<String, String> payload,
            long revision,
            boolean delete,
            WriteDirection direction
    ) {
        if (direction == WriteDirection.TO_DB) {
            return dbRows.findByMappingIdAndRowKey(mapping.getId(), rowKey)
                    .map(row -> row.getRevision() >= revision && row.isDeleted() == delete
                            && (delete || Payloads.hash(Payloads.parse(row.getPayloadJson())).equals(Payloads.hash(payload))))
                    .orElse(delete);
        }
        return sheetRows.findByMappingIdAndRowKey(mapping.getId(), rowKey)
                .map(row -> row.getRevision() >= revision && row.isDeleted() == delete
                        && (delete || Payloads.hash(Payloads.parse(row.getPayloadJson())).equals(Payloads.hash(payload))))
                .orElse(delete);
    }

    private Map<String, String> currentPayload(Mapping mapping, String rowKey, WriteDirection direction) {
        if (direction == WriteDirection.TO_DB) {
            return dbRows.findByMappingIdAndRowKey(mapping.getId(), rowKey)
                    .filter(row -> !row.isDeleted())
                    .map(row -> Payloads.parse(row.getPayloadJson()))
                    .orElse(Map.of());
        }
        return sheetRows.findByMappingIdAndRowKey(mapping.getId(), rowKey)
                .filter(row -> !row.isDeleted())
                .map(row -> Payloads.parse(row.getPayloadJson()))
                .orElse(Map.of());
    }

    private void applyDestination(
            Mapping mapping,
            String rowKey,
            Map<String, String> payload,
            long revision,
            boolean delete,
            WriteDirection direction,
            SpreadsheetGateway gateway
    ) {
        if (direction == WriteDirection.TO_SHEET) {
            if (delete) {
                gateway.deleteRow(mapping, rowKey, revision);
            } else {
                gateway.upsertRow(mapping, rowKey, payload, revision);
            }
            return;
        }
        DbRow row = dbRows.findByMappingIdAndRowKey(mapping.getId(), rowKey).orElseGet(() -> {
            DbRow created = new DbRow();
            created.setId(UUID.randomUUID());
            created.setMappingId(mapping.getId());
            created.setRowKey(rowKey);
            created.setRevision(0);
            return created;
        });
        row.setRevision(Math.max(revision, row.getRevision()));
        row.setDeleted(delete);
        row.setPayloadJson(Payloads.stringify(delete ? Map.of(mapping.getRowKeyColumn(), rowKey) : payload));
        row.setUpdatedAt(Instant.now());
        dbRows.save(row);
    }
}

package com.sheetbridge.audit;

import com.sheetbridge.domain.AuditEntry;
import com.sheetbridge.domain.AuditSource;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.repo.AuditEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEntryRepository audits;

    public AuditService(AuditEntryRepository audits) {
        this.audits = audits;
    }

    @Transactional
    public int recordDiff(
            UUID mappingId,
            String rowKey,
            Map<String, String> before,
            Map<String, String> after,
            List<String> columns,
            AuditSource source,
            String actor,
            long revision,
            UUID syncRunId,
            UUID conflictId
    ) {
        int written = 0;
        Map<String, String> oldMap = before == null ? Map.of() : before;
        Map<String, String> newMap = after == null ? Map.of() : after;
        for (String column : columns) {
            String oldVal = Payloads.nv(oldMap.get(column));
            String newVal = Payloads.nv(newMap.get(column));
            if (oldVal.equals(newVal)) {
                continue;
            }
            written += append(mappingId, rowKey, column, oldVal, newVal, source, actor, revision, syncRunId, conflictId);
        }
        return written;
    }

    @Transactional
    public int append(
            UUID mappingId,
            String rowKey,
            String field,
            String oldValue,
            String newValue,
            AuditSource source,
            String actor,
            long revision,
            UUID syncRunId,
            UUID conflictId
    ) {
        if (audits.countByMappingIdAndRowKeyAndRevisionAndFieldNameAndSource(
                mappingId, rowKey, revision, field, source) > 0) {
            return 0;
        }
        AuditEntry entry = new AuditEntry();
        entry.setId(UUID.randomUUID());
        entry.setMappingId(mappingId);
        entry.setRowKey(rowKey);
        entry.setFieldName(field);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        entry.setSource(source);
        entry.setActor(actor);
        entry.setRevision(revision);
        entry.setSyncRunId(syncRunId);
        entry.setConflictId(conflictId);
        entry.setCreatedAt(Instant.now());
        audits.save(entry);
        return 1;
    }

    public List<AuditEntry> query(UUID mappingId, String rowKey, String field) {
        if (rowKey != null && !rowKey.isBlank() && field != null && !field.isBlank()) {
            return audits.findByMappingIdAndRowKeyAndFieldNameOrderByCreatedAtDesc(mappingId, rowKey, field);
        }
        if (rowKey != null && !rowKey.isBlank()) {
            return audits.findByMappingIdAndRowKeyOrderByCreatedAtDesc(mappingId, rowKey);
        }
        if (field != null && !field.isBlank()) {
            return audits.findByMappingIdAndFieldNameOrderByCreatedAtDesc(mappingId, field);
        }
        return audits.findByMappingIdOrderByCreatedAtDesc(mappingId);
    }
}

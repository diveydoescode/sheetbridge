package com.sheetbridge.sheet;

import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.SpreadsheetRow;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class LocalSpreadsheetGateway implements SpreadsheetGateway {

    private final SpreadsheetRowRepository rows;

    public LocalSpreadsheetGateway(SpreadsheetRowRepository rows) {
        this.rows = rows;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, LinkedHashMap<String, String>> readRows(Mapping mapping) {
        Map<String, LinkedHashMap<String, String>> result = new LinkedHashMap<>();
        rows.findByMappingIdOrderByRowKeyAsc(mapping.getId()).forEach(row -> {
            if (!row.isDeleted()) {
                result.put(row.getRowKey(), new LinkedHashMap<>(Payloads.parse(row.getPayloadJson())));
            }
        });
        return result;
    }

    @Override
    @Transactional
    public void upsertRow(Mapping mapping, String rowKey, Map<String, String> payload, long revision) {
        SpreadsheetRow row = rows.findByMappingIdAndRowKey(mapping.getId(), rowKey)
                .orElseGet(() -> {
                    SpreadsheetRow created = new SpreadsheetRow();
                    created.setId(UUID.randomUUID());
                    created.setMappingId(mapping.getId());
                    created.setRowKey(rowKey);
                    created.setRevision(0);
                    return created;
                });
        row.setPayloadJson(Payloads.stringify(payload));
        row.setRevision(Math.max(revision, row.getRevision()));
        row.setDeleted(false);
        row.setUpdatedAt(Instant.now());
        rows.save(row);
    }

    @Override
    @Transactional
    public void deleteRow(Mapping mapping, String rowKey, long revision) {
        rows.findByMappingIdAndRowKey(mapping.getId(), rowKey).ifPresent(row -> {
            row.setDeleted(true);
            row.setRevision(Math.max(revision, row.getRevision()));
            row.setPayloadJson(Payloads.stringify(Map.of(mapping.getRowKeyColumn(), rowKey)));
            row.setUpdatedAt(Instant.now());
            rows.save(row);
        });
    }
}

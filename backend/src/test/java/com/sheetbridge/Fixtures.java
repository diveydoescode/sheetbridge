package com.sheetbridge;

import com.sheetbridge.domain.DbRow;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.Snapshot;
import com.sheetbridge.domain.SourceType;
import com.sheetbridge.domain.SpreadsheetRow;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.MappingRepository;
import com.sheetbridge.repo.SnapshotRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Fixtures {

    public static final List<String> COLUMNS = List.of("sku", "quantity", "price", "warehouse", "notes");

    private Fixtures() {
    }

    public static Mapping mapping(MappingRepository mappings) {
        Mapping mapping = new Mapping();
        mapping.setId(UUID.randomUUID());
        mapping.setName("test-" + mapping.getId().toString().substring(0, 8));
        mapping.setSpreadsheetId("local:test");
        mapping.setSheetName("Sheet1");
        mapping.setRowKeyColumn("sku");
        mapping.setColumnsJson(Payloads.stringifyList(COLUMNS));
        mapping.setSourceType(SourceType.LOCAL);
        return mappings.save(mapping);
    }

    public static Map<String, String> payload(String sku, String qty, String price, String warehouse, String notes) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("sku", sku);
        map.put("quantity", qty);
        map.put("price", price);
        map.put("warehouse", warehouse);
        map.put("notes", notes);
        return map;
    }

    public static void snapshot(
            SnapshotRepository repo, UUID mappingId, String sku, Map<String, String> payload, long revision
    ) {
        Snapshot snapshot = new Snapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setMappingId(mappingId);
        snapshot.setRowKey(sku);
        snapshot.setPayloadJson(Payloads.stringify(payload));
        snapshot.setRevision(revision);
        snapshot.setDeleted(false);
        snapshot.setCapturedAt(Instant.now());
        repo.save(snapshot);
    }

    public static SpreadsheetRow sheet(
            SpreadsheetRowRepository repo, UUID mappingId, String sku, Map<String, String> payload, long revision
    ) {
        SpreadsheetRow row = new SpreadsheetRow();
        row.setId(UUID.randomUUID());
        row.setMappingId(mappingId);
        row.setRowKey(sku);
        row.setPayloadJson(Payloads.stringify(payload));
        row.setRevision(revision);
        row.setDeleted(false);
        row.setUpdatedAt(Instant.now());
        return repo.save(row);
    }

    public static DbRow db(
            DbRowRepository repo, UUID mappingId, String sku, Map<String, String> payload, long revision
    ) {
        DbRow row = new DbRow();
        row.setId(UUID.randomUUID());
        row.setMappingId(mappingId);
        row.setRowKey(sku);
        row.setPayloadJson(Payloads.stringify(payload));
        row.setRevision(revision);
        row.setDeleted(false);
        row.setUpdatedAt(Instant.now());
        return repo.save(row);
    }

    public static void aligned(
            SnapshotRepository snapshots,
            SpreadsheetRowRepository sheetRows,
            DbRowRepository dbRows,
            UUID mappingId,
            String sku,
            Map<String, String> payload
    ) {
        snapshot(snapshots, mappingId, sku, payload, 1);
        sheet(sheetRows, mappingId, sku, payload, 1);
        db(dbRows, mappingId, sku, payload, 1);
    }
}

package com.sheetbridge.config;

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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "sheetbridge", name = "seed", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    static final List<String> COLUMNS = List.of(
            "sku", "product_name", "quantity", "unit_price", "warehouse", "status", "notes"
    );

    private final MappingRepository mappings;
    private final SpreadsheetRowRepository sheetRows;
    private final DbRowRepository dbRows;
    private final SnapshotRepository snapshots;

    public DemoDataSeeder(
            MappingRepository mappings,
            SpreadsheetRowRepository sheetRows,
            DbRowRepository dbRows,
            SnapshotRepository snapshots
    ) {
        this.mappings = mappings;
        this.sheetRows = sheetRows;
        this.dbRows = dbRows;
        this.snapshots = snapshots;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mappings.count() > 0) {
            return;
        }
        Mapping mapping = new Mapping();
        mapping.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        mapping.setName("North Warehouse — SKU ledger");
        mapping.setSpreadsheetId("local:north-warehouse");
        mapping.setSheetName("Inventory");
        mapping.setRowKeyColumn("sku");
        mapping.setColumnsJson(Payloads.stringifyList(COLUMNS));
        mapping.setSourceType(SourceType.LOCAL);
        mappings.save(mapping);

        seed(mapping, "CB-100", "AeroPress Original", "42", "39.95", "NORTH-A", "IN_STOCK", "bin A-12",
                null, null);
        seed(mapping, "CB-110", "AeroPress Go", "18", "32.50", "NORTH-A", "IN_STOCK", "",
                Map.of("status", "LOW", "notes", "ops: weekend pop-up drained stock"),
                Map.of("status", "BACKORDER"));
        seed(mapping, "ML-200", "Baratza Encore", "18", "169.00", "NORTH-B", "IN_STOCK", "grinder wall",
                Map.of("quantity", "14", "notes", "cycle count 3/9"),
                Map.of("quantity", "21"));
        seed(mapping, "ML-210", "Baratza Sette 270", "7", "379.00", "NORTH-B", "LOW", "",
                Map.of("notes", "waiting on burr kit"),
                Map.of("unit_price", "359.00"));
        seed(mapping, "KT-300", "Fellow Stagg EKG", "11", "195.00", "NORTH-A", "IN_STOCK", "",
                Map.of("quantity", "9"),
                null);
        seed(mapping, "KT-310", "Fellow Ode Brew Grinder", "5", "345.00", "NORTH-B", "LOW", "demo unit on floor",
                null,
                Map.of("warehouse", "NORTH-C"));
        seed(mapping, "FL-400", "Chemex 6-cup", "24", "47.50", "NORTH-A", "IN_STOCK", "",
                Map.of("status", "LOW"),
                null);
        seed(mapping, "FL-410", "Hario V60-02", "60", "24.00", "NORTH-A", "IN_STOCK", "reorder at 20",
                null, null);
        seed(mapping, "FL-420", "Kalita Wave 185", "33", "29.00", "NORTH-A", "IN_STOCK", "",
                null, null);
        seed(mapping, "AC-500", "Acaia Lunar Scale", "4", "259.00", "NORTH-C", "LOW", "locked cabinet",
                null,
                Map.of("warehouse", "NORTH-A", "notes", "moved for espresso bar"));
        seed(mapping, "AC-510", "Airscape Canister 64oz", "40", "35.00", "NORTH-A", "IN_STOCK", "",
                Map.of("quantity", "38"),
                Map.of("unit_price", "32.00"));
        seed(mapping, "CB-120", "AeroPress filters (350)", "90", "8.50", "NORTH-A", "IN_STOCK", "",
                null, null);
        seed(mapping, "ML-220", "1Zpresso J-Max", "6", "219.00", "NORTH-B", "LOW", "",
                Map.of("status", "IN_STOCK", "quantity", "8"),
                Map.of("status", "BACKORDER", "quantity", "3"));

        insertSheetOnly(mapping, "AC-520", row("AC-520", "Fellow Atmos Canister", "12", "40.00", "NORTH-A", "IN_STOCK", "ops added from receiving"));
        insertDbOnly(mapping, "KT-330", row("KT-330", "Fellow Clara French Press", "8", "68.00", "NORTH-C", "IN_STOCK", "eng SKU from PIM import"));
    }

    private void seed(
            Mapping mapping,
            String sku,
            String name,
            String qty,
            String price,
            String warehouse,
            String status,
            String notes,
            Map<String, String> sheetEdits,
            Map<String, String> dbEdits
    ) {
        Map<String, String> base = row(sku, name, qty, price, warehouse, status, notes);
        writeSnapshot(mapping, sku, base, 1);
        Map<String, String> sheet = overlay(base, sheetEdits);
        Map<String, String> db = overlay(base, dbEdits);
        writeSheet(mapping, sku, sheet, sheetEdits == null ? 1 : 2);
        writeDb(mapping, sku, db, dbEdits == null ? 1 : 2);
    }

    private void insertSheetOnly(Mapping mapping, String sku, Map<String, String> payload) {
        writeSheet(mapping, sku, payload, 1);
    }

    private void insertDbOnly(Mapping mapping, String sku, Map<String, String> payload) {
        writeDb(mapping, sku, payload, 1);
    }

    private void writeSnapshot(Mapping mapping, String sku, Map<String, String> payload, long revision) {
        Snapshot snapshot = new Snapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setMappingId(mapping.getId());
        snapshot.setRowKey(sku);
        snapshot.setPayloadJson(Payloads.stringify(payload));
        snapshot.setRevision(revision);
        snapshot.setDeleted(false);
        snapshot.setCapturedAt(Instant.parse("2026-03-02T15:00:00Z"));
        snapshots.save(snapshot);
    }

    private void writeSheet(Mapping mapping, String sku, Map<String, String> payload, long revision) {
        SpreadsheetRow row = new SpreadsheetRow();
        row.setId(UUID.randomUUID());
        row.setMappingId(mapping.getId());
        row.setRowKey(sku);
        row.setPayloadJson(Payloads.stringify(payload));
        row.setRevision(revision);
        row.setDeleted(false);
        row.setUpdatedAt(Instant.now());
        sheetRows.save(row);
    }

    private void writeDb(Mapping mapping, String sku, Map<String, String> payload, long revision) {
        DbRow row = new DbRow();
        row.setId(UUID.randomUUID());
        row.setMappingId(mapping.getId());
        row.setRowKey(sku);
        row.setPayloadJson(Payloads.stringify(payload));
        row.setRevision(revision);
        row.setDeleted(false);
        row.setUpdatedAt(Instant.now());
        dbRows.save(row);
    }

    private static Map<String, String> row(
            String sku, String name, String qty, String price, String warehouse, String status, String notes
    ) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("sku", sku);
        payload.put("product_name", name);
        payload.put("quantity", qty);
        payload.put("unit_price", price);
        payload.put("warehouse", warehouse);
        payload.put("status", status);
        payload.put("notes", notes);
        return payload;
    }

    private static Map<String, String> overlay(Map<String, String> base, Map<String, String> edits) {
        Map<String, String> copy = new LinkedHashMap<>(base);
        if (edits != null) {
            copy.putAll(edits);
        }
        return copy;
    }
}

package com.sheetbridge.sync;

import com.sheetbridge.domain.DbRow;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.Snapshot;
import com.sheetbridge.domain.SpreadsheetRow;
import com.sheetbridge.error.ApiException;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.reconcile.RowMergeResult;
import com.sheetbridge.reconcile.ThreeWayMerger;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.MappingRepository;
import com.sheetbridge.repo.SnapshotRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class PreviewService {

    private final MappingRepository mappings;
    private final SpreadsheetRowRepository sheetRows;
    private final DbRowRepository dbRows;
    private final SnapshotRepository snapshots;

    public PreviewService(
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

    @Transactional(readOnly = true)
    public List<RowMergeResult> preview(UUID mappingId) {
        Mapping mapping = mappings.findById(mappingId).orElseThrow(() -> ApiException.notFound("Mapping not found"));
        List<String> columns = Payloads.parseList(mapping.getColumnsJson());
        Map<String, SpreadsheetRow> sheet = new LinkedHashMap<>();
        sheetRows.findByMappingIdOrderByRowKeyAsc(mappingId)
                .forEach(row -> sheet.put(row.getRowKey(), row));
        Map<String, DbRow> db = new LinkedHashMap<>();
        dbRows.findByMappingIdOrderByRowKeyAsc(mappingId)
                .forEach(row -> db.put(row.getRowKey(), row));
        Map<String, Snapshot> snap = new LinkedHashMap<>();
        snapshots.findByMappingIdOrderByRowKeyAsc(mappingId)
                .forEach(row -> snap.put(row.getRowKey(), row));

        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(sheet.keySet());
        keys.addAll(db.keySet());
        keys.addAll(snap.keySet());

        List<RowMergeResult> results = new ArrayList<>();
        for (String key : keys) {
            SpreadsheetRow s = sheet.get(key);
            DbRow d = db.get(key);
            Snapshot n = snap.get(key);
            Map<String, String> sheetPayload = (s == null || s.isDeleted()) ? null : Payloads.parse(s.getPayloadJson());
            Map<String, String> dbPayload = (d == null || d.isDeleted()) ? null : Payloads.parse(d.getPayloadJson());
            Map<String, String> snapPayload = (n == null || n.isDeleted()) ? null : Payloads.parse(n.getPayloadJson());
            results.add(ThreeWayMerger.merge(key, mapping.getRowKeyColumn(), columns, snapPayload, sheetPayload, dbPayload));
        }
        return results;
    }
}

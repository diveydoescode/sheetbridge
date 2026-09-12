package com.sheetbridge.reconcile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classifies a row by three-way merge against the last-synced snapshot.
 * Same-field divergence on both sides is a conflict; disjoint field edits auto-merge.
 */
public final class ThreeWayMerger {

    private ThreeWayMerger() {
    }

    public static RowMergeResult merge(
            String rowKey,
            String rowKeyColumn,
            List<String> columns,
            Map<String, String> snapshot,
            Map<String, String> sheet,
            Map<String, String> db
    ) {
        boolean inSnap = snapshot != null;
        boolean inSheet = sheet != null;
        boolean inDb = db != null;

        if (!inSnap && !inSheet && !inDb) {
            return new RowMergeResult(rowKey, RowOutcome.BOTH_DELETED, Map.of(), List.of(), List.of());
        }

        if (!inSnap) {
            return mergeInsert(rowKey, rowKeyColumn, columns, sheet, db);
        }
        if (!inSheet || !inDb) {
            return mergeDeletion(rowKey, rowKeyColumn, columns, snapshot, sheet, db);
        }
        return mergePresent(rowKey, rowKeyColumn, columns, snapshot, sheet, db);
    }

    private static RowMergeResult mergeInsert(
            String rowKey,
            String rowKeyColumn,
            List<String> columns,
            Map<String, String> sheet,
            Map<String, String> db
    ) {
        if (sheet != null && db == null) {
            Map<String, String> payload = Payloads.project(sheet, columns);
            return new RowMergeResult(
                    rowKey,
                    RowOutcome.SHEET_INSERT,
                    payload,
                    fieldDecisionsForInsert(columns, rowKeyColumn, payload, true),
                    List.of()
            );
        }
        if (sheet == null && db != null) {
            Map<String, String> payload = Payloads.project(db, columns);
            return new RowMergeResult(
                    rowKey,
                    RowOutcome.DB_INSERT,
                    payload,
                    fieldDecisionsForInsert(columns, rowKeyColumn, payload, false),
                    List.of()
            );
        }
        if (Payloads.equal(sheet, db, columns)) {
            Map<String, String> payload = Payloads.project(sheet, columns);
            return new RowMergeResult(rowKey, RowOutcome.CLEAN, payload, List.of(), List.of());
        }
        List<String> conflicting = new ArrayList<>();
        List<FieldDecision> fields = new ArrayList<>();
        for (String column : columns) {
            if (column.equals(rowKeyColumn)) {
                continue;
            }
            String sheetVal = Payloads.nv(sheet.get(column));
            String dbVal = Payloads.nv(db.get(column));
            FieldStatus status = sheetVal.equals(dbVal) ? FieldStatus.BOTH_SAME : FieldStatus.CONFLICT;
            if (status == FieldStatus.CONFLICT) {
                conflicting.add(column);
            }
            fields.add(new FieldDecision(column, "", sheetVal, dbVal, sheetVal.equals(dbVal) ? sheetVal : null, status));
        }
        return new RowMergeResult(rowKey, RowOutcome.BOTH_INSERT_CONFLICT, null, List.copyOf(fields), List.copyOf(conflicting));
    }

    private static RowMergeResult mergeDeletion(
            String rowKey,
            String rowKeyColumn,
            List<String> columns,
            Map<String, String> snapshot,
            Map<String, String> sheet,
            Map<String, String> db
    ) {
        if (sheet == null && db == null) {
            return new RowMergeResult(rowKey, RowOutcome.BOTH_DELETED, Map.of(), List.of(), List.of());
        }
        if (sheet == null) {
            if (Payloads.equal(db, snapshot, columns)) {
                return new RowMergeResult(
                        rowKey,
                        RowOutcome.SHEET_DELETE,
                        Map.of(),
                        List.of(new FieldDecision(rowKeyColumn, rowKey, "", rowKey, "", FieldStatus.DELETED)),
                        List.of()
                );
            }
            return deleteConflict(rowKey, columns, rowKeyColumn, snapshot, null, db);
        }
        if (Payloads.equal(sheet, snapshot, columns)) {
            return new RowMergeResult(
                    rowKey,
                    RowOutcome.DB_DELETE,
                    Map.of(),
                    List.of(new FieldDecision(rowKeyColumn, rowKey, rowKey, "", "", FieldStatus.DELETED)),
                    List.of()
            );
        }
        return deleteConflict(rowKey, columns, rowKeyColumn, snapshot, sheet, null);
    }

    private static RowMergeResult deleteConflict(
            String rowKey,
            List<String> columns,
            String rowKeyColumn,
            Map<String, String> snapshot,
            Map<String, String> sheet,
            Map<String, String> db
    ) {
        List<FieldDecision> fields = new ArrayList<>();
        List<String> conflicting = new ArrayList<>();
        conflicting.add(rowKeyColumn);
        for (String column : columns) {
            String snapVal = Payloads.nv(snapshot.get(column));
            String sheetVal = sheet == null ? "" : Payloads.nv(sheet.get(column));
            String dbVal = db == null ? "" : Payloads.nv(db.get(column));
            fields.add(new FieldDecision(column, snapVal, sheetVal, dbVal, null, FieldStatus.CONFLICT));
        }
        return new RowMergeResult(rowKey, RowOutcome.DELETE_CONFLICT, null, List.copyOf(fields), List.copyOf(conflicting));
    }

    private static RowMergeResult mergePresent(
            String rowKey,
            String rowKeyColumn,
            List<String> columns,
            Map<String, String> snapshot,
            Map<String, String> sheet,
            Map<String, String> db
    ) {
        List<FieldDecision> fields = new ArrayList<>();
        List<String> conflicting = new ArrayList<>();
        Map<String, String> merged = new LinkedHashMap<>();
        boolean sheetChanged = false;
        boolean dbChanged = false;

        for (String column : columns) {
            String snapVal = Payloads.nv(snapshot.get(column));
            String sheetVal = Payloads.nv(sheet.get(column));
            String dbVal = Payloads.nv(db.get(column));

            if (column.equals(rowKeyColumn)) {
                merged.put(column, rowKey);
                fields.add(new FieldDecision(column, rowKey, rowKey, rowKey, rowKey, FieldStatus.UNCHANGED));
                continue;
            }

            if (sheetVal.equals(dbVal)) {
                merged.put(column, sheetVal);
                FieldStatus status = sheetVal.equals(snapVal) ? FieldStatus.UNCHANGED : FieldStatus.BOTH_SAME;
                fields.add(new FieldDecision(column, snapVal, sheetVal, dbVal, sheetVal, status));
            } else if (sheetVal.equals(snapVal)) {
                merged.put(column, dbVal);
                dbChanged = true;
                fields.add(new FieldDecision(column, snapVal, sheetVal, dbVal, dbVal, FieldStatus.DB));
            } else if (dbVal.equals(snapVal)) {
                merged.put(column, sheetVal);
                sheetChanged = true;
                fields.add(new FieldDecision(column, snapVal, sheetVal, dbVal, sheetVal, FieldStatus.SHEET));
            } else {
                conflicting.add(column);
                fields.add(new FieldDecision(column, snapVal, sheetVal, dbVal, null, FieldStatus.CONFLICT));
            }
        }

        if (!conflicting.isEmpty()) {
            return new RowMergeResult(rowKey, RowOutcome.CONFLICT, null, List.copyOf(fields), List.copyOf(conflicting));
        }
        if (Payloads.equal(sheet, db, columns)) {
            return new RowMergeResult(rowKey, RowOutcome.CLEAN, merged, List.copyOf(fields), List.of());
        }
        if (sheetChanged && dbChanged) {
            return new RowMergeResult(rowKey, RowOutcome.AUTO_MERGED, merged, List.copyOf(fields), List.of());
        }
        if (sheetChanged) {
            return new RowMergeResult(rowKey, RowOutcome.SHEET_ONLY, merged, List.copyOf(fields), List.of());
        }
        return new RowMergeResult(rowKey, RowOutcome.DB_ONLY, merged, List.copyOf(fields), List.of());
    }

    private static List<FieldDecision> fieldDecisionsForInsert(
            List<String> columns,
            String rowKeyColumn,
            Map<String, String> payload,
            boolean fromSheet
    ) {
        List<FieldDecision> fields = new ArrayList<>();
        for (String column : columns) {
            if (column.equals(rowKeyColumn)) {
                continue;
            }
            String value = Payloads.nv(payload.get(column));
            String sheetVal = fromSheet ? value : "";
            String dbVal = fromSheet ? "" : value;
            fields.add(new FieldDecision(column, "", sheetVal, dbVal, value, FieldStatus.INSERTED));
        }
        return List.copyOf(fields);
    }
}

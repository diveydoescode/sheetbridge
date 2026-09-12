package com.sheetbridge.reconcile;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeWayMergerTest {

    private static final List<String> COLS = List.of("sku", "quantity", "price", "notes");

    @Test
    void identicalRowsAreClean() {
        Map<String, String> row = row("SKU-1", "10", "5.00", "ok");
        RowMergeResult result = ThreeWayMerger.merge("SKU-1", "sku", COLS, row, row, row);
        assertEquals(RowOutcome.CLEAN, result.outcome());
        assertEquals("10", result.mergedPayload().get("quantity"));
    }

    @Test
    void sheetOnlyChangeAppliesSheetValue() {
        Map<String, String> snap = row("SKU-1", "10", "5.00", "ok");
        Map<String, String> sheet = row("SKU-1", "7", "5.00", "ok");
        RowMergeResult result = ThreeWayMerger.merge("SKU-1", "sku", COLS, snap, sheet, snap);
        assertEquals(RowOutcome.SHEET_ONLY, result.outcome());
        assertEquals("7", result.mergedPayload().get("quantity"));
        assertTrue(result.writesToDb());
    }

    @Test
    void dbOnlyChangeAppliesDbValue() {
        Map<String, String> snap = row("SKU-1", "10", "5.00", "ok");
        Map<String, String> db = row("SKU-1", "10", "6.50", "ok");
        RowMergeResult result = ThreeWayMerger.merge("SKU-1", "sku", COLS, snap, snap, db);
        assertEquals(RowOutcome.DB_ONLY, result.outcome());
        assertEquals("6.50", result.mergedPayload().get("price"));
        assertTrue(result.writesToSheet());
    }

    @Test
    void disjointFieldEditsAutoMerge() {
        Map<String, String> snap = row("SKU-1", "10", "5.00", "ok");
        Map<String, String> sheet = row("SKU-1", "7", "5.00", "ok");
        Map<String, String> db = row("SKU-1", "10", "6.50", "ok");
        RowMergeResult result = ThreeWayMerger.merge("SKU-1", "sku", COLS, snap, sheet, db);
        assertEquals(RowOutcome.AUTO_MERGED, result.outcome());
        assertEquals("7", result.mergedPayload().get("quantity"));
        assertEquals("6.50", result.mergedPayload().get("price"));
        assertTrue(result.conflictingFields().isEmpty());
    }

    @Test
    void sameFieldDivergesIntoConflict() {
        Map<String, String> snap = row("SKU-1", "10", "5.00", "ok");
        Map<String, String> sheet = row("SKU-1", "7", "5.00", "ok");
        Map<String, String> db = row("SKU-1", "12", "5.00", "ok");
        RowMergeResult result = ThreeWayMerger.merge("SKU-1", "sku", COLS, snap, sheet, db);
        assertEquals(RowOutcome.CONFLICT, result.outcome());
        assertEquals(List.of("quantity"), result.conflictingFields());
        assertNull(result.mergedPayload());
        assertTrue(result.isConflict());
    }

    @Test
    void bothSidesChangingSameFieldToSameValueIsClean() {
        Map<String, String> snap = row("SKU-1", "10", "5.00", "ok");
        Map<String, String> both = row("SKU-1", "8", "5.00", "ok");
        RowMergeResult result = ThreeWayMerger.merge("SKU-1", "sku", COLS, snap, both, both);
        assertEquals(RowOutcome.CLEAN, result.outcome());
        assertEquals("8", result.mergedPayload().get("quantity"));
    }

    @Test
    void sheetInsertWhenAbsentFromSnapshotAndDb() {
        Map<String, String> sheet = row("SKU-9", "1", "2.00", "new");
        RowMergeResult result = ThreeWayMerger.merge("SKU-9", "sku", COLS, null, sheet, null);
        assertEquals(RowOutcome.SHEET_INSERT, result.outcome());
        assertEquals("1", result.mergedPayload().get("quantity"));
    }

    @Test
    void dbInsertWhenAbsentFromSnapshotAndSheet() {
        Map<String, String> db = row("SKU-9", "1", "2.00", "new");
        RowMergeResult result = ThreeWayMerger.merge("SKU-9", "sku", COLS, null, null, db);
        assertEquals(RowOutcome.DB_INSERT, result.outcome());
    }

    @Test
    void bothInsertsWithDifferentPayloadsConflict() {
        Map<String, String> sheet = row("SKU-9", "1", "2.00", "ops");
        Map<String, String> db = row("SKU-9", "4", "2.00", "eng");
        RowMergeResult result = ThreeWayMerger.merge("SKU-9", "sku", COLS, null, sheet, db);
        assertEquals(RowOutcome.BOTH_INSERT_CONFLICT, result.outcome());
        assertTrue(result.conflictingFields().contains("quantity"));
        assertTrue(result.conflictingFields().contains("notes"));
    }

    @Test
    void sheetDeleteWhenDbMatchesSnapshot() {
        Map<String, String> snap = row("SKU-1", "10", "5.00", "ok");
        RowMergeResult result = ThreeWayMerger.merge("SKU-1", "sku", COLS, snap, null, snap);
        assertEquals(RowOutcome.SHEET_DELETE, result.outcome());
        assertTrue(result.isDeleteOnDb());
    }

    @Test
    void deleteConflictsWhenOtherSideAlsoEdited() {
        Map<String, String> snap = row("SKU-1", "10", "5.00", "ok");
        Map<String, String> db = row("SKU-1", "3", "5.00", "ok");
        RowMergeResult result = ThreeWayMerger.merge("SKU-1", "sku", COLS, snap, null, db);
        assertEquals(RowOutcome.DELETE_CONFLICT, result.outcome());
        assertTrue(result.isConflict());
    }

    @Test
    void bothDeletedIsTerminal() {
        Map<String, String> snap = row("SKU-1", "10", "5.00", "ok");
        RowMergeResult result = ThreeWayMerger.merge("SKU-1", "sku", COLS, snap, null, null);
        assertEquals(RowOutcome.BOTH_DELETED, result.outcome());
    }

    private static Map<String, String> row(String sku, String qty, String price, String notes) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("sku", sku);
        map.put("quantity", qty);
        map.put("price", price);
        map.put("notes", notes);
        return map;
    }
}

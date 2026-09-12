package com.sheetbridge.reconcile;

import java.util.List;
import java.util.Map;

public record RowMergeResult(
        String rowKey,
        RowOutcome outcome,
        Map<String, String> mergedPayload,
        List<FieldDecision> fields,
        List<String> conflictingFields
) {
    public boolean isConflict() {
        return outcome == RowOutcome.CONFLICT
                || outcome == RowOutcome.BOTH_INSERT_CONFLICT
                || outcome == RowOutcome.DELETE_CONFLICT;
    }

    public boolean writesToDb() {
        return outcome == RowOutcome.SHEET_ONLY
                || outcome == RowOutcome.AUTO_MERGED
                || outcome == RowOutcome.SHEET_INSERT
                || outcome == RowOutcome.SHEET_DELETE;
    }

    public boolean writesToSheet() {
        return outcome == RowOutcome.DB_ONLY
                || outcome == RowOutcome.AUTO_MERGED
                || outcome == RowOutcome.DB_INSERT
                || outcome == RowOutcome.DB_DELETE;
    }

    public boolean isDeleteOnDb() {
        return outcome == RowOutcome.SHEET_DELETE;
    }

    public boolean isDeleteOnSheet() {
        return outcome == RowOutcome.DB_DELETE;
    }

    public boolean updatesSnapshot() {
        return !isConflict();
    }
}

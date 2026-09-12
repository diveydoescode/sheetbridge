package com.sheetbridge.reconcile;

public enum RowOutcome {
    CLEAN,
    SHEET_ONLY,
    DB_ONLY,
    AUTO_MERGED,
    CONFLICT,
    SHEET_INSERT,
    DB_INSERT,
    SHEET_DELETE,
    DB_DELETE,
    BOTH_INSERT_CONFLICT,
    DELETE_CONFLICT,
    BOTH_DELETED
}

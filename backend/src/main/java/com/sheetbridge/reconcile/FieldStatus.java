package com.sheetbridge.reconcile;

public enum FieldStatus {
    UNCHANGED,
    SHEET,
    DB,
    BOTH_SAME,
    CONFLICT,
    INSERTED,
    DELETED
}

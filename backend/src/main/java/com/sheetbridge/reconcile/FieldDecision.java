package com.sheetbridge.reconcile;

public record FieldDecision(
        String field,
        String snapshot,
        String sheet,
        String db,
        String merged,
        FieldStatus status
) {
}

package com.sheetbridge.sync;

public class SyncStats {

    private int clean;
    private int sheetOnly;
    private int dbOnly;
    private int autoMerged;
    private int conflicts;
    private int sheetInserts;
    private int dbInserts;
    private int sheetDeletes;
    private int dbDeletes;
    private int bothDeleted;
    private int processed;
    private int idempotentSkips;
    private int repaired;

    public void increment(com.sheetbridge.reconcile.RowOutcome outcome) {
        processed++;
        switch (outcome) {
            case CLEAN -> clean++;
            case SHEET_ONLY -> sheetOnly++;
            case DB_ONLY -> dbOnly++;
            case AUTO_MERGED -> autoMerged++;
            case CONFLICT, BOTH_INSERT_CONFLICT, DELETE_CONFLICT -> conflicts++;
            case SHEET_INSERT -> sheetInserts++;
            case DB_INSERT -> dbInserts++;
            case SHEET_DELETE -> sheetDeletes++;
            case DB_DELETE -> dbDeletes++;
            case BOTH_DELETED -> bothDeleted++;
        }
    }

    public int getClean() {
        return clean;
    }

    public void setClean(int clean) {
        this.clean = clean;
    }

    public int getSheetOnly() {
        return sheetOnly;
    }

    public void setSheetOnly(int sheetOnly) {
        this.sheetOnly = sheetOnly;
    }

    public int getDbOnly() {
        return dbOnly;
    }

    public void setDbOnly(int dbOnly) {
        this.dbOnly = dbOnly;
    }

    public int getAutoMerged() {
        return autoMerged;
    }

    public void setAutoMerged(int autoMerged) {
        this.autoMerged = autoMerged;
    }

    public int getConflicts() {
        return conflicts;
    }

    public void setConflicts(int conflicts) {
        this.conflicts = conflicts;
    }

    public int getSheetInserts() {
        return sheetInserts;
    }

    public void setSheetInserts(int sheetInserts) {
        this.sheetInserts = sheetInserts;
    }

    public int getDbInserts() {
        return dbInserts;
    }

    public void setDbInserts(int dbInserts) {
        this.dbInserts = dbInserts;
    }

    public int getSheetDeletes() {
        return sheetDeletes;
    }

    public void setSheetDeletes(int sheetDeletes) {
        this.sheetDeletes = sheetDeletes;
    }

    public int getDbDeletes() {
        return dbDeletes;
    }

    public void setDbDeletes(int dbDeletes) {
        this.dbDeletes = dbDeletes;
    }

    public int getBothDeleted() {
        return bothDeleted;
    }

    public void setBothDeleted(int bothDeleted) {
        this.bothDeleted = bothDeleted;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getIdempotentSkips() {
        return idempotentSkips;
    }

    public void setIdempotentSkips(int idempotentSkips) {
        this.idempotentSkips = idempotentSkips;
    }

    public int getRepaired() {
        return repaired;
    }

    public void setRepaired(int repaired) {
        this.repaired = repaired;
    }
}

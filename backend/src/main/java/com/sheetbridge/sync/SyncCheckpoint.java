package com.sheetbridge.sync;

public class SyncCheckpoint {

    private String lastRowKey = "";
    private int processed;
    private int applied;
    private int conflictsOpened;
    private int idempotentSkips;
    private int repaired;

    public String getLastRowKey() {
        return lastRowKey;
    }

    public void setLastRowKey(String lastRowKey) {
        this.lastRowKey = lastRowKey;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getApplied() {
        return applied;
    }

    public void setApplied(int applied) {
        this.applied = applied;
    }

    public int getConflictsOpened() {
        return conflictsOpened;
    }

    public void setConflictsOpened(int conflictsOpened) {
        this.conflictsOpened = conflictsOpened;
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

package com.sheetbridge.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheetbridge.config.SheetBridgeProperties;
import com.sheetbridge.domain.AuditSource;
import com.sheetbridge.domain.Conflict;
import com.sheetbridge.domain.ConflictStatus;
import com.sheetbridge.domain.DbRow;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.MappingSyncLock;
import com.sheetbridge.domain.Snapshot;
import com.sheetbridge.domain.SpreadsheetRow;
import com.sheetbridge.domain.SyncRun;
import com.sheetbridge.domain.SyncStatus;
import com.sheetbridge.error.ApiException;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.reconcile.RowMergeResult;
import com.sheetbridge.reconcile.ThreeWayMerger;
import com.sheetbridge.repo.ConflictRepository;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.MappingRepository;
import com.sheetbridge.repo.MappingSyncLockRepository;
import com.sheetbridge.repo.SnapshotRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import com.sheetbridge.repo.SyncRunRepository;
import com.sheetbridge.sheet.SpreadsheetGateway;
import com.sheetbridge.sheet.SpreadsheetGatewayResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class SyncOrchestrator {

    private final MappingRepository mappings;
    private final SyncRunRepository runs;
    private final MappingSyncLockRepository locks;
    private final SpreadsheetRowRepository sheetRows;
    private final DbRowRepository dbRows;
    private final SnapshotRepository snapshots;
    private final ConflictRepository conflicts;
    private final SpreadsheetGatewayResolver gateways;
    private final IdempotentWriter writer;
    private final SnapshotService snapshotService;
    private final ObjectMapper mapper;
    private final SheetBridgeProperties properties;
    private final TransactionTemplate tx;

    public SyncOrchestrator(
            MappingRepository mappings,
            SyncRunRepository runs,
            MappingSyncLockRepository locks,
            SpreadsheetRowRepository sheetRows,
            DbRowRepository dbRows,
            SnapshotRepository snapshots,
            ConflictRepository conflicts,
            SpreadsheetGatewayResolver gateways,
            IdempotentWriter writer,
            SnapshotService snapshotService,
            ObjectMapper mapper,
            SheetBridgeProperties properties,
            PlatformTransactionManager txManager
    ) {
        this.mappings = mappings;
        this.runs = runs;
        this.locks = locks;
        this.sheetRows = sheetRows;
        this.dbRows = dbRows;
        this.snapshots = snapshots;
        this.conflicts = conflicts;
        this.gateways = gateways;
        this.writer = writer;
        this.snapshotService = snapshotService;
        this.mapper = mapper;
        this.properties = properties;
        this.tx = new TransactionTemplate(txManager);
    }

    public SyncRun start(UUID mappingId, String trigger, String actor) {
        return start(mappingId, trigger, actor, null);
    }

    public SyncRun start(UUID mappingId, String trigger, String actor, Integer crashAfter) {
        Mapping mapping = mappings.findById(mappingId).orElseThrow(() -> ApiException.notFound("Mapping not found"));
        SyncRun run = tx.execute(status -> {
            SyncRun created = new SyncRun();
            created.setId(UUID.randomUUID());
            created.setMappingId(mappingId);
            created.setStatus(SyncStatus.PENDING);
            created.setTriggerSource(trigger);
            created.setActor(actor);
            created.setAttempt(1);
            created.setCreatedAt(Instant.now());
            created.setStatsJson(writeJson(new SyncStats()));
            created.setCheckpointJson(writeJson(new SyncCheckpoint()));
            runs.save(created);
            acquireLock(mappingId, created.getId());
            return created;
        });
        return execute(mapping, run, crashAfter);
    }

    public SyncRun resume(UUID runId, String actor) {
        return resume(runId, actor, null);
    }

    public SyncRun resume(UUID runId, String actor, Integer crashAfter) {
        SyncRun run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("Sync run not found"));
        if (run.getStatus() != SyncStatus.INTERRUPTED && run.getStatus() != SyncStatus.FAILED) {
            throw ApiException.badRequest("Only interrupted or failed runs can be resumed");
        }
        Mapping mapping = mappings.findById(run.getMappingId())
                .orElseThrow(() -> ApiException.notFound("Mapping not found"));
        tx.executeWithoutResult(status -> {
            acquireLock(mapping.getId(), run.getId());
            run.setAttempt(run.getAttempt() + 1);
            run.setActor(actor);
            run.setStatus(SyncStatus.PENDING);
            run.setErrorMessage(null);
            runs.save(run);
        });
        return execute(mapping, run, crashAfter);
    }

    private SyncRun execute(Mapping mapping, SyncRun run, Integer crashAfter) {
        SpreadsheetGateway gateway = gateways.resolve(mapping);
        SyncCheckpoint checkpoint = readCheckpoint(run);
        SyncStats stats = readStats(run);
        List<String> columns = Payloads.parseList(mapping.getColumnsJson());
        int batchSize = Math.max(1, properties.getSync().getBatchSize());

        try {
            tx.executeWithoutResult(status -> {
                run.setStatus(SyncStatus.RUNNING);
                run.setStartedAt(run.getStartedAt() == null ? Instant.now() : run.getStartedAt());
                runs.save(run);
            });

            Map<String, LinkedHashMap<String, String>> sheet = gateway.readRows(mapping);
            Map<String, Map<String, String>> db = loadDb(mapping.getId());
            Map<String, Map<String, String>> snap = loadSnapshots(mapping.getId());

            TreeSet<String> keys = new TreeSet<>();
            keys.addAll(sheet.keySet());
            keys.addAll(db.keySet());
            keys.addAll(snap.keySet());

            int processedThisPass = 0;
            for (String rowKey : keys) {
                if (!checkpoint.getLastRowKey().isBlank() && rowKey.compareTo(checkpoint.getLastRowKey()) <= 0) {
                    continue;
                }
                if (crashAfter != null && processedThisPass >= crashAfter) {
                    persistProgress(run, checkpoint, stats, SyncStatus.INTERRUPTED, "Injected crash after " + crashAfter);
                    throw new SyncInterruptedException("Sync interrupted after " + crashAfter + " rows");
                }

                SpreadsheetRow sheetEntity = sheetRows.findByMappingIdAndRowKey(mapping.getId(), rowKey).orElse(null);
                DbRow dbEntity = dbRows.findByMappingIdAndRowKey(mapping.getId(), rowKey).orElse(null);
                Map<String, String> sheetPayload = sheet.containsKey(rowKey) ? sheet.get(rowKey) : null;
                Map<String, String> dbPayload = db.get(rowKey);
                Map<String, String> snapPayload = snap.get(rowKey);

                RowMergeResult result = ThreeWayMerger.merge(
                        rowKey,
                        mapping.getRowKeyColumn(),
                        columns,
                        snapPayload,
                        sheetPayload,
                        dbPayload
                );
                apply(mapping, gateway, result, sheetEntity, dbEntity, sheetPayload, dbPayload, run, checkpoint, stats, actorOf(run));

                checkpoint.setLastRowKey(rowKey);
                checkpoint.setProcessed(checkpoint.getProcessed() + 1);
                processedThisPass++;
                if (processedThisPass % batchSize == 0) {
                    persistProgress(run, checkpoint, stats, SyncStatus.RUNNING, null);
                }
            }

            persistProgress(run, checkpoint, stats, SyncStatus.COMPLETED, null);
            return runs.findById(run.getId()).orElse(run);
        } catch (SyncInterruptedException ex) {
            return runs.findById(run.getId()).orElse(run);
        } catch (RuntimeException ex) {
            persistProgress(run, checkpoint, stats, SyncStatus.FAILED, ex.getMessage());
            throw ex;
        } finally {
            tx.executeWithoutResult(status -> locks.deleteById(mapping.getId()));
        }
    }

    private void apply(
            Mapping mapping,
            SpreadsheetGateway gateway,
            RowMergeResult result,
            SpreadsheetRow sheetEntity,
            DbRow dbEntity,
            Map<String, String> sheetPayload,
            Map<String, String> dbPayload,
            SyncRun run,
            SyncCheckpoint checkpoint,
            SyncStats stats,
            String actor
    ) {
        stats.increment(result.outcome());
        long sheetRev = sheetEntity == null ? 1 : sheetEntity.getRevision();
        long dbRev = dbEntity == null ? 1 : dbEntity.getRevision();
        long writeRev;

        switch (result.outcome()) {
            case CLEAN -> snapshotService.upsert(
                    mapping.getId(), result.rowKey(), result.mergedPayload(), Math.max(sheetRev, dbRev), false);
            case BOTH_DELETED -> snapshotService.upsert(
                    mapping.getId(), result.rowKey(), Map.of(), Math.max(sheetRev, dbRev), true);
            case SHEET_ONLY, SHEET_INSERT -> {
                writeRev = sheetRev;
                note(checkpoint, stats, writer.writeToDb(
                        mapping, result.rowKey(), result.mergedPayload(), writeRev, false,
                        actor, AuditSource.SYNC, run.getId(), null));
                snapshotService.upsert(mapping.getId(), result.rowKey(), result.mergedPayload(), writeRev, false);
            }
            case DB_ONLY, DB_INSERT -> {
                writeRev = dbRev;
                note(checkpoint, stats, writer.writeToSheet(
                        mapping, result.rowKey(), result.mergedPayload(), writeRev, false,
                        actor, AuditSource.SYNC, run.getId(), null, gateway));
                snapshotService.upsert(mapping.getId(), result.rowKey(), result.mergedPayload(), writeRev, false);
            }
            case AUTO_MERGED -> {
                writeRev = Math.max(sheetRev, dbRev) + 1;
                note(checkpoint, stats, writer.writeToDb(
                        mapping, result.rowKey(), result.mergedPayload(), writeRev, false,
                        actor, AuditSource.AUTO_MERGE, run.getId(), null));
                note(checkpoint, stats, writer.writeToSheet(
                        mapping, result.rowKey(), result.mergedPayload(), writeRev, false,
                        actor, AuditSource.AUTO_MERGE, run.getId(), null, gateway));
                snapshotService.upsert(mapping.getId(), result.rowKey(), result.mergedPayload(), writeRev, false);
            }
            case SHEET_DELETE -> {
                writeRev = sheetRev;
                note(checkpoint, stats, writer.writeToDb(
                        mapping, result.rowKey(), Map.of(), writeRev, true,
                        actor, AuditSource.SYNC, run.getId(), null));
                snapshotService.upsert(mapping.getId(), result.rowKey(), Map.of(), writeRev, true);
            }
            case DB_DELETE -> {
                writeRev = dbRev;
                note(checkpoint, stats, writer.writeToSheet(
                        mapping, result.rowKey(), Map.of(), writeRev, true,
                        actor, AuditSource.SYNC, run.getId(), null, gateway));
                snapshotService.upsert(mapping.getId(), result.rowKey(), Map.of(), writeRev, true);
            }
            case CONFLICT, BOTH_INSERT_CONFLICT, DELETE_CONFLICT ->
                    openConflict(mapping, run, result, sheetPayload, dbPayload, checkpoint);
            default -> {
            }
        }
    }

    private void openConflict(
            Mapping mapping,
            SyncRun run,
            RowMergeResult result,
            Map<String, String> sheetPayload,
            Map<String, String> dbPayload,
            SyncCheckpoint checkpoint
    ) {
        if (conflicts.findByMappingIdAndRowKeyAndStatus(mapping.getId(), result.rowKey(), ConflictStatus.OPEN).isPresent()) {
            return;
        }
        Conflict conflict = new Conflict();
        conflict.setId(UUID.randomUUID());
        conflict.setMappingId(mapping.getId());
        conflict.setSyncRunId(run.getId());
        conflict.setRowKey(result.rowKey());
        conflict.setSheetPayloadJson(Payloads.stringify(sheetPayload == null ? Map.of() : sheetPayload));
        conflict.setDbPayloadJson(Payloads.stringify(dbPayload == null ? Map.of() : dbPayload));
        snapshots.findByMappingIdAndRowKey(mapping.getId(), result.rowKey())
                .ifPresentOrElse(
                        snap -> conflict.setSnapshotPayloadJson(snap.getPayloadJson()),
                        () -> conflict.setSnapshotPayloadJson(null)
                );
        conflict.setConflictingFieldsJson(Payloads.stringifyList(result.conflictingFields()));
        conflict.setFieldsJson(writeJson(result.fields()));
        conflict.setStatus(ConflictStatus.OPEN);
        conflict.setCreatedAt(Instant.now());
        conflicts.save(conflict);
        checkpoint.setConflictsOpened(checkpoint.getConflictsOpened() + 1);
    }

    private void note(SyncCheckpoint checkpoint, SyncStats stats, WriteResult result) {
        if (result == WriteResult.SKIPPED_DUPLICATE) {
            checkpoint.setIdempotentSkips(checkpoint.getIdempotentSkips() + 1);
            stats.setIdempotentSkips(stats.getIdempotentSkips() + 1);
        } else if (result == WriteResult.REPAIRED) {
            checkpoint.setRepaired(checkpoint.getRepaired() + 1);
            stats.setRepaired(stats.getRepaired() + 1);
            checkpoint.setApplied(checkpoint.getApplied() + 1);
        } else {
            checkpoint.setApplied(checkpoint.getApplied() + 1);
        }
    }

    private void persistProgress(SyncRun run, SyncCheckpoint checkpoint, SyncStats stats, SyncStatus status, String error) {
        tx.executeWithoutResult(txStatus -> {
            run.setCheckpointJson(writeJson(checkpoint));
            run.setStatsJson(writeJson(stats));
            run.setStatus(status);
            run.setErrorMessage(error);
            if (status == SyncStatus.COMPLETED || status == SyncStatus.FAILED || status == SyncStatus.INTERRUPTED) {
                run.setFinishedAt(Instant.now());
            }
            runs.save(run);
        });
    }

    private void acquireLock(UUID mappingId, UUID runId) {
        if (locks.existsById(mappingId)) {
            throw new ApiException(HttpStatus.CONFLICT, "A sync is already running for this mapping");
        }
        try {
            MappingSyncLock lock = new MappingSyncLock();
            lock.setMappingId(mappingId);
            lock.setSyncRunId(runId);
            lock.setLockedAt(Instant.now());
            locks.saveAndFlush(lock);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "A sync is already running for this mapping");
        }
    }

    private Map<String, Map<String, String>> loadDb(UUID mappingId) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        dbRows.findByMappingIdOrderByRowKeyAsc(mappingId).forEach(row -> {
            if (!row.isDeleted()) {
                result.put(row.getRowKey(), Payloads.parse(row.getPayloadJson()));
            }
        });
        return result;
    }

    private Map<String, Map<String, String>> loadSnapshots(UUID mappingId) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        snapshots.findByMappingIdOrderByRowKeyAsc(mappingId).forEach(row -> {
            if (!row.isDeleted()) {
                result.put(row.getRowKey(), Payloads.parse(row.getPayloadJson()));
            }
        });
        return result;
    }

    private SyncCheckpoint readCheckpoint(SyncRun run) {
        if (run.getCheckpointJson() == null || run.getCheckpointJson().isBlank()) {
            return new SyncCheckpoint();
        }
        try {
            return mapper.readValue(run.getCheckpointJson(), SyncCheckpoint.class);
        } catch (JsonProcessingException e) {
            return new SyncCheckpoint();
        }
    }

    private SyncStats readStats(SyncRun run) {
        if (run.getStatsJson() == null || run.getStatsJson().isBlank()) {
            return new SyncStats();
        }
        try {
            return mapper.readValue(run.getStatsJson(), SyncStats.class);
        } catch (JsonProcessingException e) {
            return new SyncStats();
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String actorOf(SyncRun run) {
        return run.getActor() == null ? "system" : run.getActor();
    }
}

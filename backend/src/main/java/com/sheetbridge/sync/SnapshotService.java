package com.sheetbridge.sync;

import com.sheetbridge.domain.Snapshot;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.repo.SnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class SnapshotService {

    private final SnapshotRepository snapshots;

    public SnapshotService(SnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    @Transactional
    public void upsert(UUID mappingId, String rowKey, Map<String, String> payload, long revision, boolean deleted) {
        Snapshot snapshot = snapshots.findByMappingIdAndRowKey(mappingId, rowKey).orElseGet(() -> {
            Snapshot created = new Snapshot();
            created.setId(UUID.randomUUID());
            created.setMappingId(mappingId);
            created.setRowKey(rowKey);
            return created;
        });
        snapshot.setPayloadJson(Payloads.stringify(payload == null ? Map.of() : payload));
        snapshot.setRevision(revision);
        snapshot.setDeleted(deleted);
        snapshot.setCapturedAt(Instant.now());
        snapshots.save(snapshot);
    }
}

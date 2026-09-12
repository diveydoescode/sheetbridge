package com.sheetbridge.repo;

import com.sheetbridge.domain.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SnapshotRepository extends JpaRepository<Snapshot, UUID> {

    List<Snapshot> findByMappingIdOrderByRowKeyAsc(UUID mappingId);

    Optional<Snapshot> findByMappingIdAndRowKey(UUID mappingId, String rowKey);
}

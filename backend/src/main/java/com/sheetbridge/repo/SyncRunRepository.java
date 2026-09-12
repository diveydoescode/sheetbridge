package com.sheetbridge.repo;

import com.sheetbridge.domain.SyncRun;
import com.sheetbridge.domain.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    List<SyncRun> findByMappingIdOrderByCreatedAtDesc(UUID mappingId);

    List<SyncRun> findAllByOrderByCreatedAtDesc();

    Optional<SyncRun> findFirstByMappingIdAndStatusIn(UUID mappingId, Collection<SyncStatus> statuses);

    Optional<SyncRun> findFirstByMappingIdOrderByCreatedAtDesc(UUID mappingId);
}

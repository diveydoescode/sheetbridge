package com.sheetbridge.repo;

import com.sheetbridge.domain.Conflict;
import com.sheetbridge.domain.ConflictStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConflictRepository extends JpaRepository<Conflict, UUID> {

    List<Conflict> findByStatusOrderByCreatedAtAsc(ConflictStatus status);

    List<Conflict> findByMappingIdAndStatusOrderByCreatedAtAsc(UUID mappingId, ConflictStatus status);

    List<Conflict> findByMappingIdOrderByCreatedAtDesc(UUID mappingId);

    Optional<Conflict> findByMappingIdAndRowKeyAndStatus(UUID mappingId, String rowKey, ConflictStatus status);

    long countByStatus(ConflictStatus status);

    long countByMappingIdAndStatus(UUID mappingId, ConflictStatus status);
}

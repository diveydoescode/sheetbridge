package com.sheetbridge.repo;

import com.sheetbridge.domain.DbRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DbRowRepository extends JpaRepository<DbRow, UUID> {

    List<DbRow> findByMappingIdOrderByRowKeyAsc(UUID mappingId);

    Optional<DbRow> findByMappingIdAndRowKey(UUID mappingId, String rowKey);

    long countByMappingIdAndDeletedFalse(UUID mappingId);
}

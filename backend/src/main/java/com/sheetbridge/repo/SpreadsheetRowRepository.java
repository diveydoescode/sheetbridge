package com.sheetbridge.repo;

import com.sheetbridge.domain.SpreadsheetRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpreadsheetRowRepository extends JpaRepository<SpreadsheetRow, UUID> {

    List<SpreadsheetRow> findByMappingIdOrderByRowKeyAsc(UUID mappingId);

    Optional<SpreadsheetRow> findByMappingIdAndRowKey(UUID mappingId, String rowKey);

    long countByMappingIdAndDeletedFalse(UUID mappingId);
}

package com.sheetbridge.repo;

import com.sheetbridge.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    List<AuditEntry> findByMappingIdOrderByCreatedAtDesc(UUID mappingId);

    List<AuditEntry> findByMappingIdAndRowKeyOrderByCreatedAtDesc(UUID mappingId, String rowKey);

    List<AuditEntry> findByMappingIdAndFieldNameOrderByCreatedAtDesc(UUID mappingId, String fieldName);

    List<AuditEntry> findByMappingIdAndRowKeyAndFieldNameOrderByCreatedAtDesc(
            UUID mappingId, String rowKey, String fieldName);

    long countByMappingIdAndRowKeyAndRevisionAndFieldNameAndSource(
            UUID mappingId, String rowKey, long revision, String fieldName, com.sheetbridge.domain.AuditSource source);
}

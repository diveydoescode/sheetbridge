package com.sheetbridge.repo;

import com.sheetbridge.domain.WriteDirection;
import com.sheetbridge.domain.WriteReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WriteReceiptRepository extends JpaRepository<WriteReceipt, UUID> {

    Optional<WriteReceipt> findByMappingIdAndRowKeyAndRevisionAndDirection(
            UUID mappingId, String rowKey, long revision, WriteDirection direction);

    long countByMappingIdAndRowKeyAndDirection(UUID mappingId, String rowKey, WriteDirection direction);
}

package com.sheetbridge.repo;

import com.sheetbridge.domain.MappingSyncLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MappingSyncLockRepository extends JpaRepository<MappingSyncLock, UUID> {
}

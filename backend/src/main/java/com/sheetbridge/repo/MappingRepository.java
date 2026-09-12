package com.sheetbridge.repo;

import com.sheetbridge.domain.Mapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MappingRepository extends JpaRepository<Mapping, UUID> {
}

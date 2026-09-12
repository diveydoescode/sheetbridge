package com.sheetbridge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mapping_sync_locks")
public class MappingSyncLock {

    @Id
    @Column(name = "mapping_id")
    private UUID mappingId;

    @Column(name = "sync_run_id", nullable = false)
    private UUID syncRunId;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;
}

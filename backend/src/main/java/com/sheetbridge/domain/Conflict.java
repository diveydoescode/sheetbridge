package com.sheetbridge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "conflicts")
public class Conflict {

    @Id
    private UUID id;

    @Column(name = "mapping_id", nullable = false)
    private UUID mappingId;

    @Column(name = "sync_run_id", nullable = false)
    private UUID syncRunId;

    @Column(name = "row_key", nullable = false)
    private String rowKey;

    @Column(name = "sheet_payload_json", nullable = false, columnDefinition = "TEXT")
    private String sheetPayloadJson;

    @Column(name = "db_payload_json", nullable = false, columnDefinition = "TEXT")
    private String dbPayloadJson;

    @Column(name = "snapshot_payload_json", columnDefinition = "TEXT")
    private String snapshotPayloadJson;

    @Column(name = "conflicting_fields_json", nullable = false, columnDefinition = "TEXT")
    private String conflictingFieldsJson;

    @Column(name = "fields_json", nullable = false, columnDefinition = "TEXT")
    private String fieldsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConflictStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_choice")
    private ResolutionChoice resolutionChoice;

    @Column(name = "resolved_payload_json", columnDefinition = "TEXT")
    private String resolvedPayloadJson;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ConflictStatus.OPEN;
        }
    }
}

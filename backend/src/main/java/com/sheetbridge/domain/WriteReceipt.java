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
@Table(name = "write_receipts")
public class WriteReceipt {

    @Id
    private UUID id;

    @Column(name = "mapping_id", nullable = false)
    private UUID mappingId;

    @Column(name = "row_key", nullable = false)
    private String rowKey;

    @Column(nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WriteDirection direction;

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

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
    }
}

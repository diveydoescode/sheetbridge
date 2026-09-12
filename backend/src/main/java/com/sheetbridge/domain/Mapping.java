package com.sheetbridge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "mappings")
public class Mapping {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "spreadsheet_id", nullable = false)
    private String spreadsheetId;

    @Column(name = "sheet_name", nullable = false)
    private String sheetName;

    @Column(name = "row_key_column", nullable = false)
    private String rowKeyColumn;

    @Column(name = "columns_json", nullable = false, columnDefinition = "TEXT")
    private String columnsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (sourceType == null) {
            sourceType = SourceType.LOCAL;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

package com.sheetbridge.api.dto;

import com.sheetbridge.domain.ConflictStatus;
import com.sheetbridge.domain.ResolutionChoice;
import com.sheetbridge.domain.SourceType;
import com.sheetbridge.domain.SyncStatus;
import com.sheetbridge.reconcile.FieldDecision;
import com.sheetbridge.reconcile.RowOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Dtos {

    private Dtos() {
    }

    public record MappingRequest(
            @NotBlank String name,
            @NotBlank String spreadsheetId,
            @NotBlank String sheetName,
            @NotBlank String rowKeyColumn,
            @NotNull List<String> columns,
            SourceType sourceType
    ) {
    }

    public record MappingResponse(
            UUID id,
            String name,
            String spreadsheetId,
            String sheetName,
            String rowKeyColumn,
            List<String> columns,
            SourceType sourceType,
            Instant createdAt,
            Instant updatedAt,
            long sheetRows,
            long dbRows,
            long openConflicts,
            SyncStatus lastRunStatus,
            Instant lastRunAt
    ) {
    }

    public record RowWriteRequest(
            @NotNull Map<String, String> payload,
            Boolean deleted
    ) {
    }

    public record RowResponse(
            String rowKey,
            Map<String, String> payload,
            long revision,
            boolean deleted,
            Instant updatedAt,
            String side
    ) {
    }

    public record PreviewRow(
            String rowKey,
            RowOutcome outcome,
            Map<String, String> sheet,
            Map<String, String> db,
            Map<String, String> snapshot,
            Map<String, String> merged,
            List<FieldDecision> fields,
            List<String> conflictingFields,
            long sheetRevision,
            long dbRevision
    ) {
    }

    public record SyncRunResponse(
            UUID id,
            UUID mappingId,
            SyncStatus status,
            String triggerSource,
            String actor,
            Map<String, Object> checkpoint,
            Map<String, Object> stats,
            String errorMessage,
            int attempt,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt
    ) {
    }

    public record ConflictResponse(
            UUID id,
            UUID mappingId,
            UUID syncRunId,
            String rowKey,
            Map<String, String> sheet,
            Map<String, String> db,
            Map<String, String> snapshot,
            List<String> conflictingFields,
            List<FieldDecision> fields,
            ConflictStatus status,
            ResolutionChoice resolutionChoice,
            Map<String, String> resolvedPayload,
            String resolvedBy,
            Instant resolvedAt,
            Instant createdAt
    ) {
    }

    public record ResolveRequest(
            @NotNull ResolutionChoice choice,
            Map<String, String> customPayload,
            Map<String, String> fieldPicks
    ) {
    }

    public record AuditResponse(
            UUID id,
            UUID mappingId,
            String rowKey,
            String fieldName,
            String oldValue,
            String newValue,
            String source,
            String actor,
            UUID syncRunId,
            UUID conflictId,
            long revision,
            Instant createdAt
    ) {
    }

    public record HealthResponse(String status, String service) {
    }
}

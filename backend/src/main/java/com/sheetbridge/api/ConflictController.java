package com.sheetbridge.api;

import com.sheetbridge.api.dto.Dtos;
import com.sheetbridge.domain.ConflictStatus;
import com.sheetbridge.error.ApiException;
import com.sheetbridge.repo.ConflictRepository;
import com.sheetbridge.sync.ConflictResolutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conflicts")
public class ConflictController {

    private final ConflictRepository conflicts;
    private final ConflictResolutionService resolution;
    private final MappingAssembler assembler;
    private final ActorResolver actors;

    public ConflictController(
            ConflictRepository conflicts,
            ConflictResolutionService resolution,
            MappingAssembler assembler,
            ActorResolver actors
    ) {
        this.conflicts = conflicts;
        this.resolution = resolution;
        this.assembler = assembler;
        this.actors = actors;
    }

    @GetMapping
    public List<Dtos.ConflictResponse> list(
            @RequestParam(required = false) UUID mappingId,
            @RequestParam(required = false, defaultValue = "OPEN") ConflictStatus status
    ) {
        var rows = mappingId == null
                ? conflicts.findByStatusOrderByCreatedAtAsc(status)
                : conflicts.findByMappingIdAndStatusOrderByCreatedAtAsc(mappingId, status);
        return rows.stream().map(assembler::toConflict).toList();
    }

    @GetMapping("/{id}")
    public Dtos.ConflictResponse get(@PathVariable UUID id) {
        return assembler.toConflict(conflicts.findById(id).orElseThrow(() -> ApiException.notFound("Conflict not found")));
    }

    @PostMapping("/{id}/resolve")
    public Dtos.ConflictResponse resolve(
            @PathVariable UUID id,
            @Valid @RequestBody Dtos.ResolveRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor
    ) {
        return assembler.toConflict(resolution.resolve(
                id, request.choice(), request.customPayload(), request.fieldPicks(), actors.resolve(actor)));
    }
}

package com.sheetbridge.api;

import com.sheetbridge.api.dto.Dtos;
import com.sheetbridge.domain.SyncRun;
import com.sheetbridge.error.ApiException;
import com.sheetbridge.repo.SyncRunRepository;
import com.sheetbridge.sync.SyncOrchestrator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SyncController {

    private final SyncOrchestrator orchestrator;
    private final SyncRunRepository runs;
    private final MappingAssembler assembler;
    private final ActorResolver actors;

    public SyncController(
            SyncOrchestrator orchestrator,
            SyncRunRepository runs,
            MappingAssembler assembler,
            ActorResolver actors
    ) {
        this.orchestrator = orchestrator;
        this.runs = runs;
        this.assembler = assembler;
        this.actors = actors;
    }

    @PostMapping("/mappings/{mappingId}/sync")
    public Dtos.SyncRunResponse start(
            @PathVariable UUID mappingId,
            @RequestHeader(value = "X-Actor", required = false) String actor
    ) {
        SyncRun run = orchestrator.start(mappingId, "MANUAL", actors.resolve(actor));
        return assembler.toRun(run);
    }

    @GetMapping("/sync-runs")
    public List<Dtos.SyncRunResponse> list() {
        return runs.findAllByOrderByCreatedAtDesc().stream().map(assembler::toRun).toList();
    }

    @GetMapping("/sync-runs/{id}")
    public Dtos.SyncRunResponse get(@PathVariable UUID id) {
        return assembler.toRun(runs.findById(id).orElseThrow(() -> ApiException.notFound("Sync run not found")));
    }

    @PostMapping("/sync-runs/{id}/resume")
    public Dtos.SyncRunResponse resume(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Actor", required = false) String actor
    ) {
        return assembler.toRun(orchestrator.resume(id, actors.resolve(actor)));
    }
}

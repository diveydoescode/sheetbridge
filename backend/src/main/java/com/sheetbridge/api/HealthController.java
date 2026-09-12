package com.sheetbridge.api;

import com.sheetbridge.api.dto.Dtos;
import com.sheetbridge.domain.ConflictStatus;
import com.sheetbridge.repo.ConflictRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final ConflictRepository conflicts;

    public HealthController(ConflictRepository conflicts) {
        this.conflicts = conflicts;
    }

    @GetMapping("/api/health")
    public Dtos.HealthResponse health() {
        return new Dtos.HealthResponse("ok", "sheetbridge");
    }

    @GetMapping("/api/meta")
    public Map<String, Object> meta() {
        return Map.of(
                "service", "sheetbridge",
                "openConflicts", conflicts.countByStatus(ConflictStatus.OPEN)
        );
    }
}

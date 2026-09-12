package com.sheetbridge.api;

import com.sheetbridge.api.dto.Dtos;
import com.sheetbridge.audit.AuditService;
import com.sheetbridge.error.ApiException;
import com.sheetbridge.repo.MappingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService audit;
    private final MappingRepository mappings;
    private final MappingAssembler assembler;

    public AuditController(AuditService audit, MappingRepository mappings, MappingAssembler assembler) {
        this.audit = audit;
        this.mappings = mappings;
        this.assembler = assembler;
    }

    @GetMapping
    public List<Dtos.AuditResponse> query(
            @RequestParam UUID mappingId,
            @RequestParam(required = false) String rowKey,
            @RequestParam(required = false) String field
    ) {
        if (!mappings.existsById(mappingId)) {
            throw ApiException.notFound("Mapping not found");
        }
        return audit.query(mappingId, rowKey, field).stream().map(assembler::toAudit).toList();
    }
}

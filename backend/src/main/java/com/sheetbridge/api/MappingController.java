package com.sheetbridge.api;

import com.sheetbridge.api.dto.Dtos;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.SourceType;
import com.sheetbridge.error.ApiException;
import com.sheetbridge.reconcile.Payloads;
import com.sheetbridge.repo.MappingRepository;
import com.sheetbridge.sync.PreviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mappings")
public class MappingController {

    private final MappingRepository mappings;
    private final MappingAssembler assembler;
    private final PreviewService preview;

    public MappingController(MappingRepository mappings, MappingAssembler assembler, PreviewService preview) {
        this.mappings = mappings;
        this.assembler = assembler;
        this.preview = preview;
    }

    @GetMapping
    public List<Dtos.MappingResponse> list() {
        return mappings.findAll().stream().map(assembler::toResponse).toList();
    }

    @GetMapping("/{id}")
    public Dtos.MappingResponse get(@PathVariable UUID id) {
        return assembler.toResponse(require(id));
    }

    @PostMapping
    public Dtos.MappingResponse create(@Valid @RequestBody Dtos.MappingRequest request) {
        Mapping mapping = new Mapping();
        apply(mapping, request);
        return assembler.toResponse(mappings.save(mapping));
    }

    @PutMapping("/{id}")
    public Dtos.MappingResponse update(@PathVariable UUID id, @Valid @RequestBody Dtos.MappingRequest request) {
        Mapping mapping = require(id);
        apply(mapping, request);
        return assembler.toResponse(mappings.save(mapping));
    }

    @GetMapping("/{id}/preview")
    public List<Dtos.PreviewRow> preview(@PathVariable UUID id) {
        return preview.preview(id).stream()
                .map(result -> assembler.toPreview(id, result))
                .toList();
    }

    private Mapping require(UUID id) {
        return mappings.findById(id).orElseThrow(() -> ApiException.notFound("Mapping not found"));
    }

    private static void apply(Mapping mapping, Dtos.MappingRequest request) {
        mapping.setName(request.name());
        mapping.setSpreadsheetId(request.spreadsheetId());
        mapping.setSheetName(request.sheetName());
        mapping.setRowKeyColumn(request.rowKeyColumn());
        mapping.setColumnsJson(Payloads.stringifyList(request.columns()));
        mapping.setSourceType(request.sourceType() == null ? SourceType.LOCAL : request.sourceType());
    }
}

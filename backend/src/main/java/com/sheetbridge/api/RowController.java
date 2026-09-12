package com.sheetbridge.api;

import com.sheetbridge.api.dto.Dtos;
import com.sheetbridge.domain.DbRow;
import com.sheetbridge.domain.SpreadsheetRow;
import com.sheetbridge.repo.DbRowRepository;
import com.sheetbridge.repo.SpreadsheetRowRepository;
import com.sheetbridge.sync.RowEditService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mappings/{mappingId}")
public class RowController {

    private final SpreadsheetRowRepository sheetRows;
    private final DbRowRepository dbRows;
    private final RowEditService edits;
    private final MappingAssembler assembler;
    private final ActorResolver actors;

    public RowController(
            SpreadsheetRowRepository sheetRows,
            DbRowRepository dbRows,
            RowEditService edits,
            MappingAssembler assembler,
            ActorResolver actors
    ) {
        this.sheetRows = sheetRows;
        this.dbRows = dbRows;
        this.edits = edits;
        this.assembler = assembler;
        this.actors = actors;
    }

    @GetMapping("/sheet-rows")
    public List<Dtos.RowResponse> sheetRows(@PathVariable UUID mappingId) {
        return sheetRows.findByMappingIdOrderByRowKeyAsc(mappingId).stream()
                .filter(row -> !row.isDeleted())
                .map(assembler::toSheet)
                .toList();
    }

    @GetMapping("/db-rows")
    public List<Dtos.RowResponse> dbRows(@PathVariable UUID mappingId) {
        return dbRows.findByMappingIdOrderByRowKeyAsc(mappingId).stream()
                .filter(row -> !row.isDeleted())
                .map(assembler::toDb)
                .toList();
    }

    @PutMapping("/sheet-rows/{rowKey}")
    public Dtos.RowResponse editSheet(
            @PathVariable UUID mappingId,
            @PathVariable String rowKey,
            @Valid @RequestBody Dtos.RowWriteRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor
    ) {
        SpreadsheetRow row = edits.editSheet(
                mappingId, rowKey, request.payload(), Boolean.TRUE.equals(request.deleted()), actors.resolve(actor));
        return assembler.toSheet(row);
    }

    @PutMapping("/db-rows/{rowKey}")
    public Dtos.RowResponse editDb(
            @PathVariable UUID mappingId,
            @PathVariable String rowKey,
            @Valid @RequestBody Dtos.RowWriteRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor
    ) {
        DbRow row = edits.editDb(
                mappingId, rowKey, request.payload(), Boolean.TRUE.equals(request.deleted()), actors.resolve(actor));
        return assembler.toDb(row);
    }
}

CREATE TABLE mappings (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    spreadsheet_id VARCHAR(256) NOT NULL,
    sheet_name VARCHAR(128) NOT NULL,
    row_key_column VARCHAR(64) NOT NULL,
    columns_json TEXT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE spreadsheet_rows (
    id UUID PRIMARY KEY,
    mapping_id UUID NOT NULL REFERENCES mappings (id),
    row_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    revision BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (mapping_id, row_key)
);

CREATE TABLE db_rows (
    id UUID PRIMARY KEY,
    mapping_id UUID NOT NULL REFERENCES mappings (id),
    row_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    revision BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (mapping_id, row_key)
);

CREATE TABLE snapshots (
    id UUID PRIMARY KEY,
    mapping_id UUID NOT NULL REFERENCES mappings (id),
    row_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    revision BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    captured_at TIMESTAMP NOT NULL,
    UNIQUE (mapping_id, row_key)
);

CREATE TABLE sync_runs (
    id UUID PRIMARY KEY,
    mapping_id UUID NOT NULL REFERENCES mappings (id),
    status VARCHAR(32) NOT NULL,
    trigger_source VARCHAR(32) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    checkpoint_json TEXT,
    stats_json TEXT,
    error_message TEXT,
    attempt INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE mapping_sync_locks (
    mapping_id UUID PRIMARY KEY REFERENCES mappings (id),
    sync_run_id UUID NOT NULL,
    locked_at TIMESTAMP NOT NULL
);

CREATE TABLE conflicts (
    id UUID PRIMARY KEY,
    mapping_id UUID NOT NULL REFERENCES mappings (id),
    sync_run_id UUID NOT NULL REFERENCES sync_runs (id),
    row_key VARCHAR(128) NOT NULL,
    sheet_payload_json TEXT NOT NULL,
    db_payload_json TEXT NOT NULL,
    snapshot_payload_json TEXT,
    conflicting_fields_json TEXT NOT NULL,
    fields_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    resolution_choice VARCHAR(32),
    resolved_payload_json TEXT,
    resolved_by VARCHAR(128),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    mapping_id UUID NOT NULL REFERENCES mappings (id),
    row_key VARCHAR(128) NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    source VARCHAR(32) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    sync_run_id UUID,
    conflict_id UUID,
    revision BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE write_receipts (
    id UUID PRIMARY KEY,
    mapping_id UUID NOT NULL,
    row_key VARCHAR(128) NOT NULL,
    revision BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (mapping_id, row_key, revision, direction)
);

CREATE INDEX idx_sheet_rows_mapping ON spreadsheet_rows (mapping_id);
CREATE INDEX idx_db_rows_mapping ON db_rows (mapping_id);
CREATE INDEX idx_snapshots_mapping ON snapshots (mapping_id);
CREATE INDEX idx_sync_runs_mapping ON sync_runs (mapping_id, created_at);
CREATE INDEX idx_conflicts_queue ON conflicts (mapping_id, status, created_at);
CREATE INDEX idx_audit_row ON audit_log (mapping_id, row_key, created_at);
CREATE INDEX idx_audit_field ON audit_log (mapping_id, field_name, created_at);
CREATE INDEX idx_receipts_row ON write_receipts (mapping_id, row_key);

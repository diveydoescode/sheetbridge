export type SourceType = "LOCAL" | "GOOGLE_SHEETS";
export type SyncStatus = "PENDING" | "RUNNING" | "INTERRUPTED" | "COMPLETED" | "FAILED";
export type ConflictStatus = "OPEN" | "RESOLVED";
export type ResolutionChoice = "SHEET" | "DB" | "CUSTOM";
export type RowOutcome =
  | "CLEAN"
  | "SHEET_ONLY"
  | "DB_ONLY"
  | "AUTO_MERGED"
  | "CONFLICT"
  | "SHEET_INSERT"
  | "DB_INSERT"
  | "SHEET_DELETE"
  | "DB_DELETE"
  | "BOTH_INSERT_CONFLICT"
  | "DELETE_CONFLICT"
  | "BOTH_DELETED";

export type FieldStatus = "UNCHANGED" | "SHEET" | "DB" | "BOTH_SAME" | "CONFLICT" | "INSERTED" | "DELETED";

export interface Mapping {
  id: string;
  name: string;
  spreadsheetId: string;
  sheetName: string;
  rowKeyColumn: string;
  columns: string[];
  sourceType: SourceType;
  createdAt: string;
  updatedAt: string;
  sheetRows: number;
  dbRows: number;
  openConflicts: number;
  lastRunStatus: SyncStatus | null;
  lastRunAt: string | null;
}

export interface FieldDecision {
  field: string;
  snapshot: string;
  sheet: string;
  db: string;
  merged: string | null;
  status: FieldStatus;
}

export interface PreviewRow {
  rowKey: string;
  outcome: RowOutcome;
  sheet: Record<string, string>;
  db: Record<string, string>;
  snapshot: Record<string, string>;
  merged: Record<string, string> | null;
  fields: FieldDecision[];
  conflictingFields: string[];
  sheetRevision: number;
  dbRevision: number;
}

export interface RowResponse {
  rowKey: string;
  payload: Record<string, string>;
  revision: number;
  deleted: boolean;
  updatedAt: string;
  side: "SHEET" | "DB";
}

export interface SyncRun {
  id: string;
  mappingId: string;
  status: SyncStatus;
  triggerSource: string;
  actor: string;
  checkpoint: Record<string, unknown>;
  stats: Record<string, number>;
  errorMessage: string | null;
  attempt: number;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface Conflict {
  id: string;
  mappingId: string;
  syncRunId: string;
  rowKey: string;
  sheet: Record<string, string>;
  db: Record<string, string>;
  snapshot: Record<string, string>;
  conflictingFields: string[];
  fields: FieldDecision[];
  status: ConflictStatus;
  resolutionChoice: ResolutionChoice | null;
  resolvedPayload: Record<string, string> | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
  createdAt: string;
}

export interface AuditEntry {
  id: string;
  mappingId: string;
  rowKey: string;
  fieldName: string;
  oldValue: string | null;
  newValue: string | null;
  source: string;
  actor: string;
  syncRunId: string | null;
  conflictId: string | null;
  revision: number;
  createdAt: string;
}

import type { FieldDecision, FieldStatus, RowOutcome, PreviewRow } from "./types";

export interface MergeResult {
  rowKey: string;
  outcome: RowOutcome;
  mergedPayload: Record<string, string> | null;
  fields: FieldDecision[];
  conflictingFields: string[];
}

function nv(value: string | undefined | null): string {
  return value ?? "";
}

function equal(
  left: Record<string, string> | null | undefined,
  right: Record<string, string> | null | undefined,
  columns: string[],
): boolean {
  if (!left && !right) return true;
  if (!left || !right) return false;
  return columns.every((column) => nv(left[column]) === nv(right[column]));
}

function project(payload: Record<string, string>, columns: string[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (const column of columns) out[column] = nv(payload[column]);
  return out;
}

export function threeWayMerge(
  rowKey: string,
  rowKeyColumn: string,
  columns: string[],
  snapshot: Record<string, string> | null,
  sheet: Record<string, string> | null,
  db: Record<string, string> | null,
): MergeResult {
  const inSnap = snapshot !== null;
  const inSheet = sheet !== null;
  const inDb = db !== null;
  if (!inSnap && !inSheet && !inDb) {
    return { rowKey, outcome: "BOTH_DELETED", mergedPayload: {}, fields: [], conflictingFields: [] };
  }
  if (!inSnap) return mergeInsert(rowKey, rowKeyColumn, columns, sheet, db);
  if (!inSheet || !inDb) return mergeDeletion(rowKey, rowKeyColumn, columns, snapshot, sheet, db);
  return mergePresent(rowKey, rowKeyColumn, columns, snapshot, sheet, db);
}

function mergeInsert(
  rowKey: string,
  rowKeyColumn: string,
  columns: string[],
  sheet: Record<string, string> | null,
  db: Record<string, string> | null,
): MergeResult {
  if (sheet && !db) {
    const payload = project(sheet, columns);
    return {
      rowKey,
      outcome: "SHEET_INSERT",
      mergedPayload: payload,
      fields: insertFields(columns, rowKeyColumn, payload, true),
      conflictingFields: [],
    };
  }
  if (!sheet && db) {
    const payload = project(db, columns);
    return {
      rowKey,
      outcome: "DB_INSERT",
      mergedPayload: payload,
      fields: insertFields(columns, rowKeyColumn, payload, false),
      conflictingFields: [],
    };
  }
  if (sheet && db && equal(sheet, db, columns)) {
    return { rowKey, outcome: "CLEAN", mergedPayload: project(sheet, columns), fields: [], conflictingFields: [] };
  }
  const conflicting: string[] = [];
  const fields: FieldDecision[] = [];
  for (const column of columns) {
    if (column === rowKeyColumn) continue;
    const sheetVal = nv(sheet?.[column]);
    const dbVal = nv(db?.[column]);
    const status: FieldStatus = sheetVal === dbVal ? "BOTH_SAME" : "CONFLICT";
    if (status === "CONFLICT") conflicting.push(column);
    fields.push({
      field: column,
      snapshot: "",
      sheet: sheetVal,
      db: dbVal,
      merged: sheetVal === dbVal ? sheetVal : null,
      status,
    });
  }
  return { rowKey, outcome: "BOTH_INSERT_CONFLICT", mergedPayload: null, fields, conflictingFields: conflicting };
}

function mergeDeletion(
  rowKey: string,
  rowKeyColumn: string,
  columns: string[],
  snapshot: Record<string, string> | null,
  sheet: Record<string, string> | null,
  db: Record<string, string> | null,
): MergeResult {
  if (!sheet && !db) {
    return { rowKey, outcome: "BOTH_DELETED", mergedPayload: {}, fields: [], conflictingFields: [] };
  }
  if (!sheet) {
    if (equal(db, snapshot, columns)) {
      return {
        rowKey,
        outcome: "SHEET_DELETE",
        mergedPayload: {},
        fields: [{ field: rowKeyColumn, snapshot: rowKey, sheet: "", db: rowKey, merged: "", status: "DELETED" }],
        conflictingFields: [],
      };
    }
    return deleteConflict(rowKey, columns, rowKeyColumn, snapshot, null, db);
  }
  if (equal(sheet, snapshot, columns)) {
    return {
      rowKey,
      outcome: "DB_DELETE",
      mergedPayload: {},
      fields: [{ field: rowKeyColumn, snapshot: rowKey, sheet: rowKey, db: "", merged: "", status: "DELETED" }],
      conflictingFields: [],
    };
  }
  return deleteConflict(rowKey, columns, rowKeyColumn, snapshot, sheet, null);
}

function deleteConflict(
  rowKey: string,
  columns: string[],
  rowKeyColumn: string,
  snapshot: Record<string, string> | null,
  sheet: Record<string, string> | null,
  db: Record<string, string> | null,
): MergeResult {
  const fields: FieldDecision[] = columns.map((column) => ({
    field: column,
    snapshot: nv(snapshot?.[column]),
    sheet: sheet ? nv(sheet[column]) : "",
    db: db ? nv(db[column]) : "",
    merged: null,
    status: "CONFLICT" as const,
  }));
  return {
    rowKey,
    outcome: "DELETE_CONFLICT",
    mergedPayload: null,
    fields,
    conflictingFields: [rowKeyColumn],
  };
}

function mergePresent(
  rowKey: string,
  rowKeyColumn: string,
  columns: string[],
  snapshot: Record<string, string>,
  sheet: Record<string, string>,
  db: Record<string, string>,
): MergeResult {
  const fields: FieldDecision[] = [];
  const conflicting: string[] = [];
  const merged: Record<string, string> = {};
  let sheetChanged = false;
  let dbChanged = false;

  for (const column of columns) {
    const snapVal = nv(snapshot[column]);
    const sheetVal = nv(sheet[column]);
    const dbVal = nv(db[column]);
    if (column === rowKeyColumn) {
      merged[column] = rowKey;
      fields.push({ field: column, snapshot: rowKey, sheet: rowKey, db: rowKey, merged: rowKey, status: "UNCHANGED" });
      continue;
    }
    if (sheetVal === dbVal) {
      merged[column] = sheetVal;
      fields.push({
        field: column,
        snapshot: snapVal,
        sheet: sheetVal,
        db: dbVal,
        merged: sheetVal,
        status: sheetVal === snapVal ? "UNCHANGED" : "BOTH_SAME",
      });
    } else if (sheetVal === snapVal) {
      merged[column] = dbVal;
      dbChanged = true;
      fields.push({ field: column, snapshot: snapVal, sheet: sheetVal, db: dbVal, merged: dbVal, status: "DB" });
    } else if (dbVal === snapVal) {
      merged[column] = sheetVal;
      sheetChanged = true;
      fields.push({ field: column, snapshot: snapVal, sheet: sheetVal, db: dbVal, merged: sheetVal, status: "SHEET" });
    } else {
      conflicting.push(column);
      fields.push({ field: column, snapshot: snapVal, sheet: sheetVal, db: dbVal, merged: null, status: "CONFLICT" });
    }
  }

  if (conflicting.length) {
    return { rowKey, outcome: "CONFLICT", mergedPayload: null, fields, conflictingFields: conflicting };
  }
  if (equal(sheet, db, columns)) {
    return { rowKey, outcome: "CLEAN", mergedPayload: merged, fields, conflictingFields: [] };
  }
  if (sheetChanged && dbChanged) {
    return { rowKey, outcome: "AUTO_MERGED", mergedPayload: merged, fields, conflictingFields: [] };
  }
  if (sheetChanged) {
    return { rowKey, outcome: "SHEET_ONLY", mergedPayload: merged, fields, conflictingFields: [] };
  }
  return { rowKey, outcome: "DB_ONLY", mergedPayload: merged, fields, conflictingFields: [] };
}

function insertFields(
  columns: string[],
  rowKeyColumn: string,
  payload: Record<string, string>,
  fromSheet: boolean,
): FieldDecision[] {
  return columns
    .filter((column) => column !== rowKeyColumn)
    .map((column) => {
      const value = nv(payload[column]);
      return {
        field: column,
        snapshot: "",
        sheet: fromSheet ? value : "",
        db: fromSheet ? "" : value,
        merged: value,
        status: "INSERTED" as const,
      };
    });
}

export function toPreview(
  result: MergeResult,
  sheet: Record<string, string> | null,
  db: Record<string, string> | null,
  snapshot: Record<string, string> | null,
  sheetRevision: number,
  dbRevision: number,
): PreviewRow {
  return {
    rowKey: result.rowKey,
    outcome: result.outcome,
    sheet: sheet ?? {},
    db: db ?? {},
    snapshot: snapshot ?? {},
    merged: result.mergedPayload,
    fields: result.fields,
    conflictingFields: result.conflictingFields,
    sheetRevision,
    dbRevision,
  };
}

export function isConflictOutcome(outcome: RowOutcome): boolean {
  return outcome === "CONFLICT" || outcome === "BOTH_INSERT_CONFLICT" || outcome === "DELETE_CONFLICT";
}

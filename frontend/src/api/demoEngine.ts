import type {
  AuditEntry,
  Conflict,
  Mapping,
  PreviewRow,
  ResolutionChoice,
  RowResponse,
  SyncRun,
} from "../types";
import { isConflictOutcome, threeWayMerge, toPreview } from "../merge";
import { getActor } from "./actor";

const STORAGE_KEY = "sheetbridge.demo.v1";
const MAPPING_ID = "11111111-1111-1111-1111-111111111111";
const COLUMNS = ["sku", "product_name", "quantity", "unit_price", "warehouse", "status", "notes"];
const KEY = "sku";

interface SideRow {
  payload: Record<string, string>;
  revision: number;
  deleted: boolean;
  updatedAt: string;
}

interface Store {
  mapping: Mapping;
  sheet: Record<string, SideRow>;
  db: Record<string, SideRow>;
  snapshot: Record<string, SideRow>;
  conflicts: Conflict[];
  runs: SyncRun[];
  audit: AuditEntry[];
  receipts: string[];
}

function now(): string {
  return new Date().toISOString();
}

function id(): string {
  return crypto.randomUUID();
}

function row(
  sku: string,
  name: string,
  qty: string,
  price: string,
  warehouse: string,
  status: string,
  notes: string,
): Record<string, string> {
  return { sku, product_name: name, quantity: qty, unit_price: price, warehouse, status, notes };
}

function overlay(base: Record<string, string>, edits?: Record<string, string>): Record<string, string> {
  return edits ? { ...base, ...edits } : { ...base };
}

function side(payload: Record<string, string>, revision: number): SideRow {
  return { payload, revision, deleted: false, updatedAt: now() };
}

function seedStore(): Store {
  const createdAt = now();
  const mapping: Mapping = {
    id: MAPPING_ID,
    name: "North Warehouse — SKU ledger",
    spreadsheetId: "local:north-warehouse",
    sheetName: "Inventory",
    rowKeyColumn: KEY,
    columns: COLUMNS,
    sourceType: "LOCAL",
    createdAt,
    updatedAt: createdAt,
    sheetRows: 0,
    dbRows: 0,
    openConflicts: 0,
    lastRunStatus: null,
    lastRunAt: null,
  };
  const store: Store = {
    mapping,
    sheet: {},
    db: {},
    snapshot: {},
    conflicts: [],
    runs: [],
    audit: [],
    receipts: [],
  };

  const catalog: Array<{
    sku: string;
    name: string;
    qty: string;
    price: string;
    warehouse: string;
    status: string;
    notes: string;
    sheet?: Record<string, string>;
    db?: Record<string, string>;
  }> = [
    { sku: "CB-100", name: "AeroPress Original", qty: "42", price: "39.95", warehouse: "NORTH-A", status: "IN_STOCK", notes: "bin A-12" },
    { sku: "CB-110", name: "AeroPress Go", qty: "18", price: "32.50", warehouse: "NORTH-A", status: "IN_STOCK", notes: "", sheet: { status: "LOW", notes: "ops: weekend pop-up drained stock" }, db: { status: "BACKORDER" } },
    { sku: "ML-200", name: "Baratza Encore", qty: "18", price: "169.00", warehouse: "NORTH-B", status: "IN_STOCK", notes: "grinder wall", sheet: { quantity: "14", notes: "cycle count 3/9" }, db: { quantity: "21" } },
    { sku: "ML-210", name: "Baratza Sette 270", qty: "7", price: "379.00", warehouse: "NORTH-B", status: "LOW", notes: "", sheet: { notes: "waiting on burr kit" }, db: { unit_price: "359.00" } },
    { sku: "KT-300", name: "Fellow Stagg EKG", qty: "11", price: "195.00", warehouse: "NORTH-A", status: "IN_STOCK", notes: "", sheet: { quantity: "9" } },
    { sku: "KT-310", name: "Fellow Ode Brew Grinder", qty: "5", price: "345.00", warehouse: "NORTH-B", status: "LOW", notes: "demo unit on floor", db: { warehouse: "NORTH-C" } },
    { sku: "FL-400", name: "Chemex 6-cup", qty: "24", price: "47.50", warehouse: "NORTH-A", status: "IN_STOCK", notes: "", sheet: { status: "LOW" } },
    { sku: "FL-410", name: "Hario V60-02", qty: "60", price: "24.00", warehouse: "NORTH-A", status: "IN_STOCK", notes: "reorder at 20" },
    { sku: "FL-420", name: "Kalita Wave 185", qty: "33", price: "29.00", warehouse: "NORTH-A", status: "IN_STOCK", notes: "" },
    { sku: "AC-500", name: "Acaia Lunar Scale", qty: "4", price: "259.00", warehouse: "NORTH-C", status: "LOW", notes: "locked cabinet", db: { warehouse: "NORTH-A", notes: "moved for espresso bar" } },
    { sku: "AC-510", name: "Airscape Canister 64oz", qty: "40", price: "35.00", warehouse: "NORTH-A", status: "IN_STOCK", notes: "", sheet: { quantity: "38" }, db: { unit_price: "32.00" } },
    { sku: "CB-120", name: "AeroPress filters (350)", qty: "90", price: "8.50", warehouse: "NORTH-A", status: "IN_STOCK", notes: "" },
    { sku: "ML-220", name: "1Zpresso J-Max", qty: "6", price: "219.00", warehouse: "NORTH-B", status: "LOW", notes: "", sheet: { status: "IN_STOCK", quantity: "8" }, db: { status: "BACKORDER", quantity: "3" } },
  ];

  for (const item of catalog) {
    const base = row(item.sku, item.name, item.qty, item.price, item.warehouse, item.status, item.notes);
    store.snapshot[item.sku] = side(base, 1);
    store.sheet[item.sku] = side(overlay(base, item.sheet), item.sheet ? 2 : 1);
    store.db[item.sku] = side(overlay(base, item.db), item.db ? 2 : 1);
  }
  store.sheet["AC-520"] = side(row("AC-520", "Fellow Atmos Canister", "12", "40.00", "NORTH-A", "IN_STOCK", "ops added from receiving"), 1);
  store.db["KT-330"] = side(row("KT-330", "Fellow Clara French Press", "8", "68.00", "NORTH-C", "IN_STOCK", "eng SKU from PIM import"), 1);
  refreshCounts(store);
  return store;
}

function refreshCounts(store: Store): void {
  store.mapping.sheetRows = Object.values(store.sheet).filter((r) => !r.deleted).length;
  store.mapping.dbRows = Object.values(store.db).filter((r) => !r.deleted).length;
  store.mapping.openConflicts = store.conflicts.filter((c) => c.status === "OPEN").length;
  const last = store.runs[0];
  store.mapping.lastRunStatus = last?.status ?? null;
  store.mapping.lastRunAt = last?.finishedAt ?? last?.startedAt ?? null;
  store.mapping.updatedAt = now();
}

function load(): Store {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Store;
      if (parsed?.mapping?.id === MAPPING_ID) return parsed;
    }
  } catch {
    /* ignore corrupt storage */
  }
  const seeded = seedStore();
  save(seeded);
  return seeded;
}

function save(store: Store): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(store));
}

function live(row: SideRow | undefined): Record<string, string> | null {
  if (!row || row.deleted) return null;
  return row.payload;
}

function receiptKey(rowKey: string, revision: number, direction: string): string {
  return `${rowKey}:${revision}:${direction}`;
}

function recordDiff(
  store: Store,
  rowKey: string,
  before: Record<string, string> | null,
  after: Record<string, string> | null,
  source: string,
  actor: string,
  revision: number,
  syncRunId: string | null,
  conflictId: string | null,
): void {
  const oldMap = before ?? {};
  const newMap = after ?? {};
  for (const field of COLUMNS) {
    const oldValue = oldMap[field] ?? "";
    const newValue = newMap[field] ?? "";
    if (oldValue === newValue) continue;
    store.audit.unshift({
      id: id(),
      mappingId: MAPPING_ID,
      rowKey,
      fieldName: field,
      oldValue,
      newValue,
      source,
      actor,
      syncRunId,
      conflictId,
      revision,
      createdAt: now(),
    });
  }
}

function writeSide(
  store: Store,
  sideName: "sheet" | "db",
  rowKey: string,
  payload: Record<string, string>,
  revision: number,
  deleted: boolean,
  source: string,
  actor: string,
  runId: string | null,
  conflictId: string | null,
): "APPLIED" | "SKIPPED" {
  const direction = sideName === "sheet" ? "TO_SHEET" : "TO_DB";
  const key = receiptKey(rowKey, revision, direction);
  const bucket = store[sideName];
  const existing = bucket[rowKey];
  const already =
    existing &&
    existing.revision >= revision &&
    existing.deleted === deleted &&
    JSON.stringify(existing.payload) === JSON.stringify(payload);
  if (store.receipts.includes(key) && already) return "SKIPPED";
  if (!store.receipts.includes(key)) store.receipts.push(key);
  const before = live(existing);
  bucket[rowKey] = {
    payload: deleted ? { sku: rowKey } : { ...payload, sku: rowKey },
    revision: Math.max(revision, existing?.revision ?? 0),
    deleted,
    updatedAt: now(),
  };
  recordDiff(store, rowKey, before, deleted ? {} : payload, source, actor, revision, runId, conflictId);
  return "APPLIED";
}

function keys(store: Store): string[] {
  return [...new Set([...Object.keys(store.sheet), ...Object.keys(store.db), ...Object.keys(store.snapshot)])].sort();
}

function mappingView(store: Store): Mapping {
  refreshCounts(store);
  return { ...store.mapping };
}

function preview(store: Store): PreviewRow[] {
  return keys(store).map((rowKey) => {
    const sheet = live(store.sheet[rowKey]);
    const db = live(store.db[rowKey]);
    const snapshot = live(store.snapshot[rowKey]);
    const result = threeWayMerge(rowKey, KEY, COLUMNS, snapshot, sheet, db);
    return toPreview(result, sheet, db, snapshot, store.sheet[rowKey]?.revision ?? 0, store.db[rowKey]?.revision ?? 0);
  });
}

function toRow(rowKey: string, row: SideRow, sideName: "SHEET" | "DB"): RowResponse {
  return {
    rowKey,
    payload: row.payload,
    revision: row.revision,
    deleted: row.deleted,
    updatedAt: row.updatedAt,
    side: sideName,
  };
}

function openConflict(store: Store, runId: string, result: ReturnType<typeof threeWayMerge>, sheet: Record<string, string> | null, db: Record<string, string> | null): void {
  if (store.conflicts.some((c) => c.rowKey === result.rowKey && c.status === "OPEN")) return;
  store.conflicts.unshift({
    id: id(),
    mappingId: MAPPING_ID,
    syncRunId: runId,
    rowKey: result.rowKey,
    sheet: sheet ?? {},
    db: db ?? {},
    snapshot: live(store.snapshot[result.rowKey]) ?? {},
    conflictingFields: result.conflictingFields,
    fields: result.fields,
    status: "OPEN",
    resolutionChoice: null,
    resolvedPayload: null,
    resolvedBy: null,
    resolvedAt: null,
    createdAt: now(),
  });
}

function sync(store: Store, actor: string): SyncRun {
  const runId = id();
  const startedAt = now();
  const stats: Record<string, number> = {
    clean: 0,
    sheetOnly: 0,
    dbOnly: 0,
    autoMerged: 0,
    conflicts: 0,
    sheetInserts: 0,
    dbInserts: 0,
    sheetDeletes: 0,
    dbDeletes: 0,
    bothDeleted: 0,
    processed: 0,
    idempotentSkips: 0,
    repaired: 0,
  };
  let applied = 0;
  let lastRowKey = "";

  for (const rowKey of keys(store)) {
    const sheet = live(store.sheet[rowKey]);
    const db = live(store.db[rowKey]);
    const snapshot = live(store.snapshot[rowKey]);
    const result = threeWayMerge(rowKey, KEY, COLUMNS, snapshot, sheet, db);
    stats.processed += 1;
    lastRowKey = rowKey;
    const sheetRev = store.sheet[rowKey]?.revision ?? 1;
    const dbRev = store.db[rowKey]?.revision ?? 1;

    const bump = (result: "APPLIED" | "SKIPPED") => {
      if (result === "SKIPPED") stats.idempotentSkips += 1;
      else applied += 1;
    };

    switch (result.outcome) {
      case "CLEAN":
        stats.clean += 1;
        store.snapshot[rowKey] = side(result.mergedPayload ?? {}, Math.max(sheetRev, dbRev));
        break;
      case "BOTH_DELETED":
        stats.bothDeleted += 1;
        store.snapshot[rowKey] = { payload: { sku: rowKey }, revision: Math.max(sheetRev, dbRev), deleted: true, updatedAt: now() };
        break;
      case "SHEET_ONLY":
      case "SHEET_INSERT":
        stats[result.outcome === "SHEET_INSERT" ? "sheetInserts" : "sheetOnly"] += 1;
        bump(writeSide(store, "db", rowKey, result.mergedPayload ?? {}, sheetRev, false, "SYNC", actor, runId, null));
        store.snapshot[rowKey] = side(result.mergedPayload ?? {}, sheetRev);
        break;
      case "DB_ONLY":
      case "DB_INSERT":
        stats[result.outcome === "DB_INSERT" ? "dbInserts" : "dbOnly"] += 1;
        bump(writeSide(store, "sheet", rowKey, result.mergedPayload ?? {}, dbRev, false, "SYNC", actor, runId, null));
        store.snapshot[rowKey] = side(result.mergedPayload ?? {}, dbRev);
        break;
      case "AUTO_MERGED": {
        stats.autoMerged += 1;
        const writeRev = Math.max(sheetRev, dbRev) + 1;
        bump(writeSide(store, "db", rowKey, result.mergedPayload ?? {}, writeRev, false, "AUTO_MERGE", actor, runId, null));
        bump(writeSide(store, "sheet", rowKey, result.mergedPayload ?? {}, writeRev, false, "AUTO_MERGE", actor, runId, null));
        store.snapshot[rowKey] = side(result.mergedPayload ?? {}, writeRev);
        break;
      }
      case "SHEET_DELETE":
        stats.sheetDeletes += 1;
        bump(writeSide(store, "db", rowKey, {}, sheetRev, true, "SYNC", actor, runId, null));
        store.snapshot[rowKey] = { payload: { sku: rowKey }, revision: sheetRev, deleted: true, updatedAt: now() };
        break;
      case "DB_DELETE":
        stats.dbDeletes += 1;
        bump(writeSide(store, "sheet", rowKey, {}, dbRev, true, "SYNC", actor, runId, null));
        store.snapshot[rowKey] = { payload: { sku: rowKey }, revision: dbRev, deleted: true, updatedAt: now() };
        break;
      default:
        if (isConflictOutcome(result.outcome)) {
          stats.conflicts += 1;
          openConflict(store, runId, result, sheet, db);
        }
    }
  }

  const run: SyncRun = {
    id: runId,
    mappingId: MAPPING_ID,
    status: "COMPLETED",
    triggerSource: "MANUAL",
    actor,
    checkpoint: { lastRowKey, processed: stats.processed, applied, conflictsOpened: stats.conflicts, idempotentSkips: stats.idempotentSkips, repaired: 0 },
    stats,
    errorMessage: null,
    attempt: 1,
    startedAt,
    finishedAt: now(),
    createdAt: startedAt,
  };
  store.runs.unshift(run);
  refreshCounts(store);
  save(store);
  return run;
}

function edit(store: Store, sideName: "sheet" | "db", rowKey: string, payload: Record<string, string>, actor: string): RowResponse {
  if (store.conflicts.some((c) => c.rowKey === rowKey && c.status === "OPEN")) {
    throw new Error(`Row ${rowKey} has an open conflict; resolve it before editing`);
  }
  const bucket = store[sideName];
  const current = bucket[rowKey];
  const revision = (current?.revision ?? 0) + 1;
  const projected: Record<string, string> = {};
  for (const column of COLUMNS) projected[column] = payload[column] ?? "";
  projected[KEY] = rowKey;
  recordDiff(store, rowKey, live(current), projected, sideName === "sheet" ? "SHEET" : "DB", actor, revision, null, null);
  bucket[rowKey] = side(projected, revision);
  refreshCounts(store);
  save(store);
  return toRow(rowKey, bucket[rowKey], sideName === "sheet" ? "SHEET" : "DB");
}

function resolve(
  store: Store,
  conflictId: string,
  choice: ResolutionChoice,
  fieldPicks: Record<string, string> | undefined,
  customPayload: Record<string, string> | undefined,
  actor: string,
): Conflict {
  const conflict = store.conflicts.find((c) => c.id === conflictId);
  if (!conflict) throw new Error("Conflict not found");
  if (conflict.status === "RESOLVED") return conflict;

  let resolved: Record<string, string>;
  if (choice === "SHEET") resolved = { ...conflict.sheet };
  else if (choice === "DB") resolved = { ...conflict.db };
  else if (fieldPicks && Object.keys(fieldPicks).length) {
    resolved = {};
    for (const column of COLUMNS) {
      const pick = fieldPicks[column];
      if (pick === "DB") resolved[column] = conflict.db[column] ?? "";
      else if (pick === "SNAPSHOT") resolved[column] = conflict.snapshot[column] ?? "";
      else resolved[column] = conflict.sheet[column] ?? "";
    }
  } else if (customPayload) {
    resolved = { ...customPayload };
  } else {
    throw new Error("CUSTOM resolution requires fieldPicks or customPayload");
  }
  resolved[KEY] = conflict.rowKey;
  const revision = Math.max(store.sheet[conflict.rowKey]?.revision ?? 1, store.db[conflict.rowKey]?.revision ?? 1) + 1;
  writeSide(store, "db", conflict.rowKey, resolved, revision, false, "USER_RESOLUTION", actor, conflict.syncRunId, conflict.id);
  writeSide(store, "sheet", conflict.rowKey, resolved, revision, false, "USER_RESOLUTION", actor, conflict.syncRunId, conflict.id);
  store.snapshot[conflict.rowKey] = side(resolved, revision);
  conflict.status = "RESOLVED";
  conflict.resolutionChoice = choice;
  conflict.resolvedPayload = resolved;
  conflict.resolvedBy = actor;
  conflict.resolvedAt = now();
  refreshCounts(store);
  save(store);
  return conflict;
}

export function isPagesDemo(): boolean {
  if (import.meta.env.VITE_API_MODE === "live") return false;
  if (import.meta.env.VITE_API_MODE === "demo" || import.meta.env.VITE_PAGES === "true") return true;
  if (typeof window === "undefined") return false;
  const params = new URLSearchParams(window.location.search);
  if (params.get("demo") === "1") return true;
  return window.location.hostname.endsWith("github.io");
}

export function resetDemo(): void {
  localStorage.removeItem(STORAGE_KEY);
}

export const demoApi = {
  mappings: async () => [mappingView(load())],
  mapping: async () => mappingView(load()),
  preview: async () => preview(load()),
  sheetRows: async () =>
    Object.entries(load().sheet)
      .filter(([, row]) => !row.deleted)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, row]) => toRow(key, row, "SHEET")),
  dbRows: async () =>
    Object.entries(load().db)
      .filter(([, row]) => !row.deleted)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, row]) => toRow(key, row, "DB")),
  editSheet: async (_id: string, rowKey: string, payload: Record<string, string>) =>
    edit(load(), "sheet", rowKey, payload, getActor()),
  editDb: async (_id: string, rowKey: string, payload: Record<string, string>) =>
    edit(load(), "db", rowKey, payload, getActor()),
  sync: async () => sync(load(), getActor()),
  runs: async () => load().runs,
  run: async (runId: string) => {
    const run = load().runs.find((r) => r.id === runId);
    if (!run) throw new Error("Sync run not found");
    return run;
  },
  resume: async (runId: string) => {
    const run = load().runs.find((r) => r.id === runId);
    if (!run) throw new Error("Sync run not found");
    if (run.status !== "INTERRUPTED" && run.status !== "FAILED") {
      throw new Error("Only interrupted or failed runs can be resumed");
    }
    return sync(load(), getActor());
  },
  conflicts: async (_mappingId?: string, status = "OPEN") =>
    load().conflicts.filter((c) => c.status === status),
  conflict: async (conflictId: string) => {
    const conflict = load().conflicts.find((c) => c.id === conflictId);
    if (!conflict) throw new Error("Conflict not found");
    return conflict;
  },
  resolve: async (
    conflictId: string,
    choice: ResolutionChoice,
    fieldPicks?: Record<string, string>,
    customPayload?: Record<string, string>,
  ) => resolve(load(), conflictId, choice, fieldPicks, customPayload, getActor()),
  audit: async (_mappingId: string, rowKey?: string, field?: string) => {
    return load().audit.filter((entry) => {
      if (rowKey && entry.rowKey !== rowKey) return false;
      if (field && entry.fieldName !== field) return false;
      return true;
    });
  },
};

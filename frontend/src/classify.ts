import type { RowOutcome } from "./types";

export function outcomeLabel(outcome: RowOutcome): string {
  switch (outcome) {
    case "CLEAN":
      return "In sync";
    case "SHEET_ONLY":
      return "Sheet only";
    case "DB_ONLY":
      return "Database only";
    case "AUTO_MERGED":
      return "Auto-merged";
    case "CONFLICT":
      return "Conflict";
    case "SHEET_INSERT":
      return "New on sheet";
    case "DB_INSERT":
      return "New in database";
    case "SHEET_DELETE":
      return "Deleted on sheet";
    case "DB_DELETE":
      return "Deleted in database";
    case "BOTH_INSERT_CONFLICT":
      return "Insert conflict";
    case "DELETE_CONFLICT":
      return "Delete conflict";
    case "BOTH_DELETED":
      return "Deleted both sides";
  }
}

export function outcomeTone(outcome: RowOutcome): "sheet" | "db" | "conflict" | "merge" | "clean" {
  switch (outcome) {
    case "SHEET_ONLY":
    case "SHEET_INSERT":
    case "SHEET_DELETE":
      return "sheet";
    case "DB_ONLY":
    case "DB_INSERT":
    case "DB_DELETE":
      return "db";
    case "CONFLICT":
    case "BOTH_INSERT_CONFLICT":
    case "DELETE_CONFLICT":
      return "conflict";
    case "AUTO_MERGED":
      return "merge";
    default:
      return "clean";
  }
}

export function isConflictOutcome(outcome: RowOutcome): boolean {
  return outcome === "CONFLICT" || outcome === "BOTH_INSERT_CONFLICT" || outcome === "DELETE_CONFLICT";
}

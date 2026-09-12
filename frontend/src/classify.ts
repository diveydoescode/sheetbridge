import type { RowOutcome } from "./types";

/** Short label shown on a row. Names the action, not the theory. */
export function outcomeLabel(outcome: RowOutcome): string {
  switch (outcome) {
    case "CLEAN":
      return "In sync";
    case "SHEET_ONLY":
      return "Sheet → database";
    case "DB_ONLY":
      return "Database → sheet";
    case "AUTO_MERGED":
      return "Merge both";
    case "CONFLICT":
      return "Needs a choice";
    case "SHEET_INSERT":
      return "Add to database";
    case "DB_INSERT":
      return "Add to sheet";
    case "SHEET_DELETE":
      return "Remove from database";
    case "DB_DELETE":
      return "Remove from sheet";
    case "BOTH_INSERT_CONFLICT":
      return "Both added, differ";
    case "DELETE_CONFLICT":
      return "Delete vs edit";
    case "BOTH_DELETED":
      return "Gone on both";
  }
}

/** One-line hover text. Shown as a native tooltip, not a page essay. */
export function outcomeHint(outcome: RowOutcome): string {
  switch (outcome) {
    case "CLEAN":
      return "Spreadsheet and database already match.";
    case "SHEET_ONLY":
      return "Only the spreadsheet changed. Reconcile copies it into the database.";
    case "DB_ONLY":
      return "Only the database changed. Reconcile copies it into the spreadsheet.";
    case "AUTO_MERGED":
      return "Each side changed a different field. Reconcile keeps both edits.";
    case "CONFLICT":
      return "Both sides changed the same field. Open the queue and pick a value.";
    case "SHEET_INSERT":
      return "This key exists only on the spreadsheet. Reconcile inserts it into the database.";
    case "DB_INSERT":
      return "This key exists only in the database. Reconcile inserts it into the spreadsheet.";
    case "SHEET_DELETE":
      return "Removed on the spreadsheet; database still matches the last snapshot.";
    case "DB_DELETE":
      return "Removed in the database; spreadsheet still matches the last snapshot.";
    case "BOTH_INSERT_CONFLICT":
      return "Both sides created this key with different values. Pick one in the queue.";
    case "DELETE_CONFLICT":
      return "One side deleted the row, the other edited it. Pick in the queue.";
    case "BOTH_DELETED":
      return "Already gone on both sides.";
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

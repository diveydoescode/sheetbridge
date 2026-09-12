# Core logic: three-way merge

This is the heart of SheetBridge. If you rebuild only one thing well, rebuild this. The rest of the service is plumbing around these rules.

The Java implementation is `ThreeWayMerger`. The TypeScript study copy is `frontend/src/merge.ts`. They are meant to agree.

## The three documents

For one stable row key, you always have up to three maps of field to string:

- **Snapshot.** Last agreed payload. Missing means this key was never committed, or it was deleted on both sides already.
- **Sheet.** Current spreadsheet payload. Missing means the row is gone from the sheet.
- **Database.** Current database payload. Missing means the row is gone from the database.

“Missing” is not an empty string. Empty string is a cleared cell. Missing is absence of the row.

## Presence first, fields second

Before you compare quantity and price, you classify existence.

If the key is in none of the three, there is nothing to do.

If there is **no snapshot**:

- Sheet only: **SHEET_INSERT**. Insert the sheet payload into the database.
- Database only: **DB_INSERT**. Insert the database payload into the sheet.
- Both, and equal: **CLEAN**. They independently arrived at the same new row. Snapshot it.
- Both, and not equal: **BOTH_INSERT_CONFLICT**. Two systems created the same key with different bodies. A person must choose.

If there **is a snapshot** but one live side is gone:

- Sheet gone, database still equals snapshot: **SHEET_DELETE**. Ops deleted a row nobody else touched. Delete it from the database.
- Database gone, sheet still equals snapshot: **DB_DELETE**. Engineering deleted it. Delete it from the sheet.
- One side gone, the other side edited away from snapshot: **DELETE_CONFLICT**. One side deleted, the other side still has new work. Do not auto-delete.
- Both gone: **BOTH_DELETED**. Update the snapshot to deleted. No live writes.

Only when the key is present on snapshot, sheet, and database do you walk fields.

## Field rules when all three exist

For each column except the row key:

Call the snapshot value B, the sheet value S, the database value D. Treat null as empty string.

1. If S equals D: take S. If it also equals B, the field is unchanged. If not, both sides converged on the same new value. That is fine. Not a conflict.
2. If S equals B but D does not: only the database changed. Take D.
3. If D equals B but S does not: only the sheet changed. Take S.
4. If S, D, and B are all different: **this field is a conflict**.

After every field:

- Any conflicting field means the **row** is CONFLICT. Do not write. Do not update the snapshot.
- If sheet payload equals database payload overall: CLEAN. Refresh the snapshot so the next run has a new base.
- If only sheet fields changed: SHEET_ONLY. Write the merged payload to the database.
- If only database fields changed: DB_ONLY. Write the merged payload to the sheet.
- If both sides changed, but different fields, and no field conflicted: AUTO_MERGED. Write the merged payload to both sides.

That last case is the one last-write-wins cannot express. Ops changed quantity. Engineering changed price. The merged row has both.

## A walk-through with numbers

Snapshot for ML-210: quantity 7, price 379.00, notes empty.

Sheet: quantity 7, price 379.00, notes “waiting on burr kit.”
Database: quantity 7, price 359.00, notes empty.

Quantity: S = D = B. Unchanged.
Price: S = B, D is new. Take database 359.00.
Notes: D = B, S is new. Take sheet note.

No field conflicts. Both sides changed. Outcome AUTO_MERGED. Merged row is quantity 7, price 359.00, notes “waiting on burr kit.” Write that to both sides. Snapshot becomes that row.

Now ML-200.

Snapshot quantity 18.
Sheet quantity 14.
Database quantity 21.

Quantity: S, D, and B all differ. Conflict on quantity. Row is CONFLICT. Nothing is written. The queue shows 14 versus 21 versus 18.

## Why strings, not types

Spreadsheet cells are strings. “14” and 14 must not disagree because one side parsed a number. The engine compares exact cell text after mapping columns. When you rebuild, do not get clever with floats until you have the string merge right.

## What the merger returns

The merger is a pure function. It does not touch the database. It returns:

- The row key.
- The outcome enum.
- The merged payload, or null if the row is a conflict.
- Per-field decisions: snapshot value, sheet value, database value, merged value, status.
- The list of conflicting field names.

The orchestrator is the only thing allowed to apply that result. Keeping merge pure is what makes `ThreeWayMergerTest` possible without Spring.

## Outcomes you must implement

CLEAN, SHEET_ONLY, DB_ONLY, AUTO_MERGED, CONFLICT, SHEET_INSERT, DB_INSERT, SHEET_DELETE, DB_DELETE, BOTH_INSERT_CONFLICT, DELETE_CONFLICT, BOTH_DELETED.

If you skip deletes and inserts, the demo looks fine until someone removes a row. Implement presence cases in the same week as field cases.

## Tests that prove you understood it

Write these before any HTTP.

- Identical triples are CLEAN.
- Sheet-only quantity change is SHEET_ONLY and merged quantity is the sheet’s.
- Database-only price change is DB_ONLY.
- Quantity on sheet, price on database, is AUTO_MERGED with both new values.
- Quantity different on both sides is CONFLICT and merged payload is null.
- Both sides change quantity to the same number is CLEAN, not CONFLICT.
- Sheet-only new key is SHEET_INSERT.
- Both sides insert different bodies is BOTH_INSERT_CONFLICT.
- Sheet missing, database equals snapshot, is SHEET_DELETE.
- Sheet missing, database edited, is DELETE_CONFLICT.

When those pass, you have SheetBridge’s brain. Everything else applies the brain’s answers.

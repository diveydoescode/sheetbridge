# Rebuild roadmap

You start the day after tomorrow. This is the order to build SheetBridge yourself so the core logic is yours, not a repo you once watched someone generate.

Do not begin with the UI. Do not begin with Docker. Do not begin with Google Sheets. Begin with a function that classifies three maps.

The existing GitHub repo is a reference implementation. Open it when a test fails and you cannot see why. Close it when you write the next test.

## How to work

One vertical slice per phase. Each phase has a stop rule. If the stop rule is true, do not “just add the workspace grid.” Move to the next phase.

Write tests first for merge, receipts, and resume. Those are cheap and they are the product.

Use Java 21 and Spring Boot because that is the system you are learning to own. If you are faster in another language, you may port the engine, but keep the same tests and the same schema ideas.

Budget: about two hours a phase if you already know Spring, longer if you are learning JPA as you go. Eight phases. UI vibe comes after phase 8.

## Phase 0 — Set the problem in your own words

Before you open an editor, write ten lines:

- Who edits the sheet.
- Who edits the database.
- What the row key is.
- What last-write-wins would destroy in your example.
- What the snapshot is.

If you cannot write that, reread `01-problem-and-product.md`. Do not code yet.

Stop rule: you can explain ML-210 auto-merge and ML-200 conflict out loud.

## Phase 1 — Pure three-way merge

No Spring. A package of records and one class.

Implement presence cases and field cases. Return outcome, merged payload, field decisions, conflicting fields.

Tests: the list in `03-core-logic-three-way-merge.md`.

Stop rule: `mvn test` on that class is green, including inserts, deletes, auto-merge, and same-field conflict.

## Phase 2 — Schema and two live sides

Flyway V1. Tables: mappings, spreadsheet_rows, db_rows, snapshots. JSON payloads. Unique (mapping_id, row_key). Revisions.

Entities and repositories. A seeder or a test fixture that inserts the North Warehouse catalog, or a smaller catalog with the same edit kinds.

An API or a test helper that can PUT a sheet cell and PUT a database cell.

Stop rule: you can create a mapping, write different quantities on each side, and read them back. Snapshot still holds Monday’s value.

## Phase 3 — Preview classification

`PreviewService`: load three sides, union of keys, run the merger, return DTOs.

A GET endpoint is enough. curl or httpie is enough. No React.

Stop rule: preview of the seed data shows clean, sheet-only, db-only, auto-merge, conflict, sheet insert, db insert. Counts roughly match the original seeder.

## Phase 4 — Apply loop without being clever

`SyncOrchestrator` that applies SHEET_ONLY, DB_ONLY, AUTO_MERGED, inserts, and CLEAN snapshot updates. Conflicts are collected in memory first if you want, or written to a conflicts table.

Use a local gateway that is just the spreadsheet_rows table.

Stop rule: after POST sync, one-sided rows match on both sides, auto-merged rows contain both field changes, conflict rows are unchanged and listed.

## Phase 5 — Receipts and audit

`write_receipts` unique index. Writer skips or repairs. Audit append for changed fields only, with actor and source.

Test: write the same (key, revision, direction) twice. One row on the destination. One receipt. Audit did not double.

Test: eight threads, same write. Still one destination row.

Stop rule: those two tests pass.

## Phase 6 — Resume and lock

Lock table. Checkpoint JSON. Skip keys through lastRowKey. Crash-after-N for tests. Resume.

Test: nine sheet-only rows, crash after three, resume, all nine converged, attempt count is 2.

Stop rule: that test passes. Two overlapping POST syncs, one fails with conflict.

## Phase 7 — Conflict queue as data, not as UI

Conflicts table. OPEN unique-enough by mapping+key. Resolve SHEET, DB, CUSTOM field picks. Both sides written through the writer. Snapshot updated. Second resolve is a no-op.

Reject edits on OPEN keys.

Audit source USER_RESOLUTION with the resolver’s actor.

Stop rule: a disputed quantity can be resolved to sheet, both sides show that quantity, audit names the person, a second POST does not flip it to database.

## Phase 8 — Hardening the engine

Backoff policy unit tests. Optional Google gateway behind the interface. Postgres trigger for append-only audit. Concurrent harness: many scenario kinds, many runs, assert no duplicate keys and no lost one-sided edits.

Stop rule: you trust `mvn test` more than you trust a screenshot.

## Phase 9 — A disposable window

Only now, UI.

Minimum: a table of preview outcomes, a button that calls sync, a list of OPEN conflicts, a page with three columns and two buttons (Take sheet, Take database), a table of audit rows.

This can be ugly. It can be one HTML file. It can be vibe-coded. It must not contain merge rules. Merge rules stay in the backend, or in a shared function you already tested.

The current React app is a reference for what screens exist, not a skin you must reproduce.

## Phase 10 — Later, the real console

When the engine is yours:

- Side-by-side workspace with cell edits.
- Per-field picks on resolve.
- Sync run list with resume.
- Actor as real auth.
- Google Sheets credentials.
- Docker Compose on a server.

That is productization. It is not how you learn the system.

## Suggested calendar if you start day after tomorrow

These are working sessions, not calendar dates.

- Session 1: Phase 0 and 1. Merger and tests only.
- Session 2: Phase 2. Schema and two-sided edits.
- Session 3: Phase 3 and 4. Preview and first real sync.
- Session 4: Phase 5. Receipts and audit. This session feels dry and is where most naive rebuilds fail.
- Session 5: Phase 6 and 7. Resume plus resolve.
- Session 6: Phase 8. Harness. Then a crude UI if you still have energy.

If a session slips, slip the UI, never the merger tests.

## How to use the existing repo without copying it

Allowed:

- Reading `ThreeWayMerger.java` when a test disagrees with your intuition.
- Copying the warehouse seed rows as fixture data.
- Copying the outcome enum names so your brain is not fighting vocabulary.
- Running the Pages demo to see a conflict, then reproducing that SKU in a unit test.

Not allowed until Phase 9, if you want the knowledge to stick:

- Pasting SyncOrchestrator into a new project.
- Generating the whole Spring app again from a prompt and calling it “I built this.”

After Phase 8 you can compare file-by-file with `05-code-we-wrote.md` and fill gaps on purpose.

## Definition of done for your rebuild

You are done with the core, even with an ugly UI, when:

1. Three-way merge tests pass, including auto-merge and delete-conflict.
2. A retried sync does not duplicate rows or audit lines.
3. An interrupted sync resumes and converges.
4. A human resolution is attributed and idempotent.
5. You can point at a SKU and tell the story of every field from snapshot to now using only the audit log.

When that is true, vibe the UI. The UI will be wrapping something you understand.

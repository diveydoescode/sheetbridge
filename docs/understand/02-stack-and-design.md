# Stack, architecture, and why it is shaped this way

This document explains the technology choices and the shape of the system. Not line-by-line code. Why these pieces, and why this layout.

## The job the stack has to do

The engine must:

- Compare three JSON-like row payloads per key.
- Write to two stores without duplicating a retry.
- Survive a crash halfway through a thousand-row run.
- Retry Google Sheets when the API says 429.
- Keep an audit trail that cannot be quietly edited.
- Expose that work to a browser so an operations user can resolve a conflict.

That is a backend problem with a thin UI. Java and Spring Boot are the center of gravity. React is a window.

## What we chose, and why

**Java 21 and Spring Boot 3.** The reconciliation loop, idempotent writer, and sync lock are easier to test as services with transactions than as a pile of scripts. Spring Boot gives HTTP, JPA, Flyway, and a test slice without ceremony. Java 21 is current LTS. This is also the stack the original product pitch named, so the rebuild stays honest to that pitch.

**PostgreSQL in production, H2 in local tests.** Postgres is the engineering system of record in the story, and it is a real database with constraints, unique indexes, and a trigger that makes `audit_log` append-only. H2 in PostgreSQL compatibility mode lets `mvn test` run on GitHub Actions without Docker. The schema is written in SQL that both can swallow, except the trigger, which is Postgres-only.

**Flyway.** The tables are the product. They are not generated from entities and hoped for. `V1__init.sql` is the contract. If you rebuild, start with this schema, not with Hibernate auto-ddl.

**A generic JSON payload per row, not a hardcoded inventory table.** The demo is SKUs, but the engine does not know what a SKU is. A mapping has a row-key column and an ordered list of column names. Each side stores `payload_json`. That is how you reconcile any spreadsheet-shaped record without rewriting the merger.

**A spreadsheet gateway interface.** Locally, the “sheet” is a table named `spreadsheet_rows`. In production it can be Google Sheets API v4. The orchestrator talks to `SpreadsheetGateway`, not to Sheets. Tests and the demo never need a Google token.

**React, TypeScript, Vite for the console.** The conflict queue needs a side-by-side table, not a form wizard. TypeScript matches the Java DTOs closely enough that the same field names travel over JSON. Vite is the local dev server. GitHub Pages cannot run Java, so the static build also contains a TypeScript port of the merger that runs in the browser against localStorage. That port is a teaching clone of the engine, not a second product.

**JUnit 5, not a screenshot, is the proof.** Fifty concurrent-edit scenarios over twenty runs is a test, not a slogan. When you rebuild, the merger tests are the first thing you write, not the last.

**Docker Compose and GitHub Actions.** Compose is how you run Postgres plus API plus nginx for real. Actions run the test suite on every push. Pages deploys the static demo.

## The shape of the system

Think in five layers.

**Mapping.** A pairing: this spreadsheet tab, this database dataset, this row-key column, these columns. Everything else hangs off `mapping_id`.

**Two live sides plus a snapshot.** `spreadsheet_rows`, `db_rows`, `snapshots`. Same shape: row key, payload JSON, revision, deleted flag. The snapshot is not a backup. It is the base of the next three-way merge.

**Sync run.** One execution of classify-and-apply. It has a status, a checkpoint of the last row key processed, and stats: how many clean, sheet-only, conflicts, and so on. A lock table makes sure two syncs do not run on the same mapping at once.

**Conflict.** A row the merger would not apply. Payloads from sheet, database, and snapshot are frozen at detect time. Status is OPEN or RESOLVED. Resolution is SHEET, DB, or CUSTOM field picks.

**Audit log and write receipts.** Audit is append-only field history. Receipts are the idempotency key: mapping, row key, revision, direction. If that tuple exists and the destination already matches, a retry is a no-op.

HTTP is a thin shell over those five.

## Why not simpler designs

**Why not two-way merge, sheet versus database, no snapshot?** Because you cannot tell a one-sided edit from a conflict. If sheet is 14 and database is 21, did ops change it, did engineering change it, or did both? Without Monday’s 18, you are guessing. Guessing is last-write-wins in a costume.

**Why not field-level last-write-wins using updated_at?** Spreadsheet cells often do not have per-field timestamps. Even if they did, two people updating quantity two minutes apart is still a conflict, not a race to the clock.

**Why not operational transform or CRDTs?** Those shine when many peers edit continuously. Here we have two systems, batch reconciliation, and a human in the loop for real disputes. Three-way merge plus a queue is the right complexity.

**Why store JSON instead of real columns?** Because the sheet schema is the user’s schema. Adding a Notes column should not require a migration of the engine. Project onto the mapping’s column list at merge time.

**Why revisions plus receipts instead of just “update the row”?** Sync jobs retry. Networks blip. A user double-clicks Run. Without an idempotency key you insert twice or apply the same field change twice into the audit log. The unique constraint on receipts is the lock.

**Why checkpoint instead of one transaction for the whole mapping?** A Sheets API call in the middle of 10,000 rows cannot sit in an open database transaction. Batches commit. If the process dies, resume starts after the last finished key. That is how you get “resumable” instead of “start over and hope receipts save you.”

## Two runtimes, one engine idea

When you run locally with Maven, the Java orchestrator is the truth. When you open GitHub Pages, there is no JVM. The TypeScript file `merge.ts` is the same three-way rules. `demoEngine.ts` is a miniature orchestrator on localStorage.

Do not treat the Pages demo as a second architecture. Treat it as the engine explained in a language you can step through in the browser. Your rebuild should follow the Java side. The TypeScript side is a study copy.

## What you should not copy blindly when you rebuild

You do not need Tailwind, IBM Plex, or the ledger aesthetic. You do not need Google Sheets on day one. You do not need Docker on day one. You do not need GitHub Pages.

You do need: a snapshot, a pure merger, a classification apply loop, receipts, an append-only audit table, and a way for a human to pick a side. Everything else waits.

# The code we wrote

This is a map of the repository as it exists, so you know what each piece is for when you rebuild. You will not copy this tree file for file. You will know which ideas live where.

The UI files at the bottom are optional for your first week. The Java engine is not.

## Top level

- `backend/` Spring Boot 3.4, Java 21, Maven.
- `frontend/` React 18, TypeScript, Vite. Console plus a Pages-ready demo engine.
- `docker-compose.yml` Postgres, API, nginx frontend.
- `.github/workflows/ci.yml` tests the Java suite and the frontend build.
- `.github/workflows/pages.yml` builds the static demo to GitHub Pages.
- `README.md` operator docs. These Understand files are teacher docs.

## Database

`backend/src/main/resources/db/migration/V1__init.sql`

Creates mappings, spreadsheet_rows, db_rows, snapshots, sync_runs, mapping_sync_locks, conflicts, audit_log, write_receipts, and the indexes that make the queue and the receipts fast.

`V2__audit_append_only.sql` is Postgres-only. It installs a trigger that forbids update and delete on audit_log.

If you rebuild from zero, write V1 first. Entities come after.

## Java packages

Base package: `com.sheetbridge`.

### reconcile — the brain

- `ThreeWayMerger.java` pure merge. Start here.
- `RowOutcome.java` `FieldStatus.java` `FieldDecision.java` `RowMergeResult.java` the vocabulary of a result.
- `Payloads.java` JSON parse, canonical hash, column projection, field-pick merge for CUSTOM resolution.

No Spring. No HTTP. No repositories.

### domain — tables as objects

One class per table: Mapping, SpreadsheetRow, DbRow, Snapshot, SyncRun, MappingSyncLock, Conflict, AuditEntry, WriteReceipt.

Enums: SourceType, SyncStatus, ConflictStatus, ResolutionChoice, WriteDirection, AuditSource.

These are JPA entities. Keep them boring.

### repo — Spring Data

One repository per entity. The interesting queries are “open conflict by mapping and key,” “receipt by mapping key revision direction,” and “audit by mapping, key, field.”

### sync — apply loop

- `SyncOrchestrator.java` lock, read sides, sort keys, merge, apply, checkpoint, resume, crash hook.
- `IdempotentWriter.java` receipts, repair, destination write, audit diff.
- `SnapshotService.java` upsert snapshot.
- `PreviewService.java` merge without writes.
- `RowEditService.java` single-side edit, reject if conflict OPEN.
- `ConflictResolutionService.java` SHEET / DB / CUSTOM, then write both sides.
- `SyncCheckpoint.java` `SyncStats.java` `WriteResult.java` `SyncInterruptedException.java` supporting types.

This package is the product besides the merger.

### sheet — the other system of record

- `SpreadsheetGateway.java` readRows, upsertRow, deleteRow.
- `LocalSpreadsheetGateway.java` the `spreadsheet_rows` table. Use this until Google is real.
- `GoogleSheetsGateway.java` Sheets API v4, header row, row-key lookup, retries through backoff.
- `SpreadsheetGatewayResolver.java` Google if configured, else local.
- `BackoffPolicy.java` equal-jitter exponential backoff.
- `SheetsQuotaException.java`

### audit

- `AuditService.java` append field diffs. Query by mapping, key, field. Skip duplicate revision+field+source.

### api — HTTP

- `MappingController` CRUD plus `/preview`.
- `RowController` GET/PUT sheet-rows and db-rows.
- `SyncController` POST sync, GET runs, POST resume.
- `ConflictController` list, get, POST resolve.
- `AuditController` GET filter.
- `HealthController` liveness.
- `MappingAssembler` entity to DTO.
- `ActorResolver` X-Actor header.
- `dto/Dtos.java` records that match the TypeScript types.

### config and error

- `SheetBridgeApplication.java` main.
- `SheetBridgeProperties.java` batch size, backoff, Google token path, CORS.
- `WebConfig.java` CORS, including localhost any port.
- `DemoDataSeeder.java` North Warehouse catalog if the mapping table is empty.
- `ApiException` `GlobalExceptionHandler`.

### tests you should reread before rebuilding

- `ThreeWayMergerTest`
- `BackoffPolicyTest`
- `IdempotentWriterTest`
- `ResumableSyncTest`
- `ReconciliationFlowTest`
- `ConcurrentEditHarnessTest`
- `AuditTrailTest`
- `GoogleSheetsGatewayTest` parses a values JSON body.
- `HealthApiTest` smoke HTTP.

`Fixtures.java` builds mappings and aligned triples for tests. Steal the idea, not necessarily the class.

## Frontend, ranked by importance to the rebuild

Study these to understand the engine in TypeScript:

- `src/merge.ts` the same three-way rules.
- `src/merge.test.ts` four cases you can expand.
- `src/api/demoEngine.ts` miniature orchestrator, seed data, receipts, resolve, audit, localStorage.
- `src/types.ts` the DTO contract.

The console, which you will replace when you vibe the UI:

- `pages/WorkspacePage.tsx` dual sheet/database grid, preview outcomes, Run reconciliation.
- `pages/ConflictQueuePage.tsx` open conflicts.
- `pages/ResolvePage.tsx` side-by-side fields, SHEET / DB / SNAP picks.
- `pages/AuditPage.tsx` filterable log.
- `pages/RunsPage.tsx` checkpoints and resume.
- `pages/DashboardPage.tsx` mapping cards.
- `api/client.ts` live HTTP or demo engine.
- `layout/AppShell.tsx` chrome.

Do not rebuild Tailwind first. A table in the browser or even curl plus a printed JSON conflict is enough to prove the engine.

## Request map

These are the verbs the engine needs. Rebuild them in this order.

1. PUT sheet row, PUT db row. Prove two sides can diverge.
2. GET preview. Prove classification without writes.
3. POST sync. Prove apply.
4. GET conflicts, POST resolve. Prove the human path.
5. GET audit. Prove attribution.
6. POST resume. Prove checkpoints.

Everything else is comfort.

## Seed data as a spec

`DemoDataSeeder` is not fluff. It is a fixture of concurrent-edit kinds:

- Clean SKUs that nobody touched.
- Sheet-only quantity or status.
- Database-only warehouse.
- Auto-merge of notes versus price.
- Conflicts on status, on quantity, and on both quantity and status.
- A sheet insert and a database insert.

When your rebuild’s first sync produces those same outcome counts, your engine matches this one.

## What you can ignore at the start

Dockerfiles, nginx, GitHub Pages workflow, Google credential loading, H2 console, actor header polish, CSS. They are real, they are not the first week.

# SheetBridge

**Live demo:** [https://diveydoescode.github.io/sheetbridge/](https://diveydoescode.github.io/sheetbridge/)

Spreadsheet ↔ database reconciliation that refuses last-write-wins.

The GitHub Pages site runs the same three-way merge, conflict queue, and audit log in the browser (no Java process). Local / Docker still uses the Spring Boot API and PostgreSQL.

Operations teams live in a spreadsheet. Engineering lives in a database. The same record is edited on both sides, and a naïve sync silently overwrites one of them. SheetBridge classifies every row against the **last-synced snapshot** — clean, one-sided, or conflicting — by a three-way merge over a stable row key.

```
                    last-synced snapshot
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
         spreadsheet    (base)      database
              │                         │
              └──────── classify ───────┘
                    │
     ┌──────────┬───┴────┬──────────┬───────────┐
     ▼          ▼        ▼          ▼           ▼
   clean    sheet-only  db-only  auto-merge  conflict
   (agree)  → write DB  → sheet  (disjoint    → queue
                                  fields)
```

## What it does

- **Three-way merge**, not latest-writer-wins. If ops changed `quantity` and engineering changed `unit_price`, the row auto-merges. If both changed `quantity`, it is a conflict.
- **Conflict queue** (React / TypeScript): an operations user resolves a disputed row side by side. Per-field picks, or take an entire side.
- **Append-only audit log**. Every field traces back to a person, a time, and a source (`SHEET`, `DB`, `AUTO_MERGE`, `USER_RESOLUTION`, `SYNC`).
- **Idempotent writes** on `(mapping, row key, revision, direction)`. A retried run never duplicates a row. A crash after the receipt and before the body is repaired on resume.
- **Exponential backoff with equal jitter** against Google Sheets API quota (`429` / `5xx`).
- **Resumable sync runs**. Checkpoints persist after each batch; resume continues from the last row key.
- **Failure-case tests**: 50 injected concurrent-edit scenarios over 20 independent runs, asserting zero lost and zero duplicated writes. JUnit 5, Docker Compose, GitHub Actions CI.

## Stack

| Layer | Choice |
| --- | --- |
| API | Java 21, Spring Boot 3.4, Flyway |
| Store | PostgreSQL (Docker) / H2 file (local demo) |
| Spreadsheet | Local ledger for demo, Google Sheets API v4 when credentials are present |
| UI | React 18, TypeScript, Vite, Tailwind |
| Tests | JUnit 5 + MockMvc + Vitest |
| Ship | Docker Compose, GitHub Actions, GitHub Pages |

## GitHub Pages

The Pages deploy is a static build of the React app. On `*.github.io` it uses an in-browser ledger (localStorage) so you can reconcile, resolve conflicts, and read the audit log without hosting PostgreSQL. Reset the seeded warehouse from the banner.

## Quick start (no Docker)

```bash
# API — seeds the North Warehouse SKU ledger on first boot
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home)
export JAVA_HOME
mvn spring-boot:run

# UI
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

1. Open **North Warehouse — SKU ledger**.
2. Rows are already diverged from last Monday’s snapshot (ops cycle counts vs engineering price/warehouse edits).
3. **Run reconciliation**. One-sided edits apply automatically; overlapping field edits land in the **conflict queue**.
4. Resolve a row side by side. Then filter the **audit log** by SKU.

Actor identity is the `X-Actor` header (the “Acting as” control in the header). Change it to see attribution.

## Docker

```bash
docker compose up --build
```

UI at http://localhost, API at http://localhost:8080. PostgreSQL is the system of record; Flyway applies `V1` plus a PostgreSQL trigger that makes `audit_log` physically append-only.

## How classification works

For each field of each stable row key:

| Spreadsheet vs snapshot | Database vs snapshot | Result |
| --- | --- | --- |
| equal | equal | unchanged |
| changed | equal | take spreadsheet |
| equal | changed | take database |
| both changed, same value | — | already converged |
| both changed, different values | — | **conflict** on that field |

Row outcomes: `CLEAN`, `SHEET_ONLY`, `DB_ONLY`, `AUTO_MERGED`, `CONFLICT`, inserts, deletes, and delete-conflicts (one side deleted, the other edited).

Writes that apply a source revision are keyed in `write_receipts`. Retrying the same `(row key, revision, direction)` is a no-op; if the receipt exists but the body does not, the writer repairs.

## Google Sheets

Set either:

```bash
export GOOGLE_SHEETS_TOKEN=ya29....
# or
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
```

Create a mapping with `sourceType: GOOGLE_SHEETS`, `spreadsheetId`, `sheetName`, and `rowKeyColumn` matching the header row. The gateway retries `429/500/502/503` with equal-jitter exponential backoff (`base * 2^attempt`, capped, then `temp/2 + random(0, temp/2)`).

Without credentials the demo uses the local spreadsheet table — the same three-way merge, queue, and audit path.

## Tests

```bash
cd backend && mvn test
cd frontend && npm test
```

| Suite | What it proves |
| --- | --- |
| `ThreeWayMergerTest` | Classification of clean / one-sided / auto-merge / conflict / insert / delete |
| `BackoffPolicyTest` | Equal jitter bounds and retry of `429` |
| `IdempotentWriterTest` | Same `(row, revision)` does not duplicate, including 8-way concurrent retry |
| `ResumableSyncTest` | Crash after 3 rows, resume from checkpoint, both sides converge |
| `ReconciliationFlowTest` | End-to-end classify + human resolution |
| `ConcurrentEditHarnessTest` | **50 scenarios × 20 runs**, zero lost or duplicated writes |
| `AuditTrailTest` | Field changes carry actor, timestamp, and source |

## API

| Method | Path | |
| --- | --- | --- |
| `GET` | `/api/mappings` | List ledgers |
| `GET` | `/api/mappings/{id}/preview` | Classify without writing |
| `PUT` | `/api/mappings/{id}/sheet-rows/{key}` | Operations edit |
| `PUT` | `/api/mappings/{id}/db-rows/{key}` | Engineering edit |
| `POST` | `/api/mappings/{id}/sync` | Reconcile |
| `POST` | `/api/sync-runs/{id}/resume` | Continue an interrupted run |
| `GET` | `/api/conflicts?status=OPEN` | Queue |
| `POST` | `/api/conflicts/{id}/resolve` | `{ choice: SHEET \| DB \| CUSTOM, fieldPicks? }` |
| `GET` | `/api/audit?mappingId=&rowKey=&field=` | Append-only history |

## Layout

```
backend/   Spring Boot API, merge engine, sync orchestrator, JUnit
frontend/   React workspace, conflict queue, audit log
.github/workflows/ci.yml
docker-compose.yml
```

## License

MIT

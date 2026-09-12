# Sync, idempotency, conflicts, and audit

Three-way merge classifies a row. This document is what happens after that classification. This is the rest of the core logic you must rebuild. Still not the UI.

## Preview versus apply

`PreviewService` runs the merger across every key and returns outcomes without writing. The workspace screen is a preview. That is important: classification is cheap and side-effect free.

`SyncOrchestrator` is preview plus apply. It is the only path that should mutate live rows in bulk.

Never apply from the UI cell editor. Editing a cell is an ordinary write to one side, with its own revision bump and audit entries. Sync is the later classification of those writes.

## The apply loop

Sort the union of keys from sheet, database, and snapshot. Process in order. That order is what makes a checkpoint meaningful: “everything up to SKU-042 is done.”

For each key, merge, then:

- CLEAN: upsert snapshot to the current agreed payload.
- BOTH_DELETED: mark snapshot deleted.
- SHEET_ONLY or SHEET_INSERT: write merged payload to the database at the sheet’s revision. Then snapshot.
- DB_ONLY or DB_INSERT: write merged payload to the sheet at the database’s revision. Then snapshot.
- AUTO_MERGED: write both sides at max(sheet revision, database revision) + 1. Then snapshot.
- SHEET_DELETE: delete on the database. Snapshot deleted.
- DB_DELETE: delete on the sheet. Snapshot deleted.
- Any conflict outcome: open a conflict row if one is not already OPEN for that key. Do not touch the snapshot.

If you update the snapshot on a conflict, the next run will treat the disputed state as agreed. That hides the fight. Never do that.

## Revisions and write receipts

Every live row has a monotonically increasing revision. A user edit adds one. An applied sync write uses the source revision, or max+1 for a merge or a human resolution.

A **write receipt** is unique on (mapping, row key, revision, direction). Direction is TO_SHEET or TO_DB.

The writer does this:

1. If a receipt exists and the destination already has that payload at that revision, return SKIPPED. A retry will take this path.
2. If a receipt exists but the destination does not match, repair the destination. That is the crash-between-receipt-and-body case.
3. If no receipt, insert one. Unique-constraint collisions from two threads become skips or repairs.
4. Write the destination.
5. Diff old payload versus new payload and append audit rows for fields that actually changed.

That is how “retried run never duplicates” and “zero lost writes” can both be true. At-most-once is the receipt. At-least-once is the repair. Together they are exactly-once **state**, not exactly-once **attempts**.

When you rebuild, put a unique index on receipts before you write the happy path. The index is the feature.

## Locks

`mapping_sync_locks` has mapping_id as primary key. Insert to start a run. Delete in a finally block. A second Run click gets a 409 instead of a second loop fighting the first.

Do not skip this because your laptop is single-user. Double-submit is enough to corrupt a demo.

## Checkpoints and resume

The orchestrator does not wrap the whole mapping in one transaction. After every batch of keys, it persists:

- lastRowKey
- processed count
- applied count
- conflicts opened
- idempotent skips

If a test (or a real crash) stops the loop, status becomes INTERRUPTED or FAILED. Resume loads the checkpoint and skips keys less than or equal to lastRowKey.

Batch size is a setting. Tests use a small batch so resume is observable. Production can use ten or fifty.

Injected crash after N rows is a first-class test hook, not a hack. You will want the same hook when you rebuild.

## Exponential backoff with jitter

Google Sheets will throttle you. `BackoffPolicy` uses equal jitter:

temp = min(cap, base * 2^attempt)
delay = temp/2 + random(0, temp/2)

Retry 429, 500, 502, 503. Do not retry 400. The policy is a small pure object with an injected sleeper so tests do not actually sleep.

You can stub the gateway on day one and still implement backoff as a unit-tested function. Wire it to HTTP later.

## Opening a conflict

A conflict stores the three payloads as they were at detection, the list of disputed fields, and the per-field decisions. The queue is `status = OPEN`.

If an OPEN conflict already exists for that mapping and key, do not open another. Resume would otherwise duplicate the queue.

While a conflict is OPEN, cell edits on that key are rejected. The user must resolve, not keep mutating under the dispute.

## Resolving a conflict

Three choices:

- SHEET: both sides become the sheet payload.
- DB: both sides become the database payload.
- CUSTOM: either an explicit payload, or per-field picks of SHEET, DB, or SNAPSHOT.

Resolution writes both sides at max revision + 1, through the same idempotent writer, with audit source USER_RESOLUTION. Then it snapshots the resolved payload and marks the conflict RESOLVED.

Resolving twice is a no-op. Return the already-resolved row. Do not apply the second person’s opposite choice. That is how a double-click or a retried POST stays safe.

## Audit log

Every field change is a new row: mapping, row key, field, old value, new value, source, actor, revision, optional sync run id, optional conflict id, time.

Sources: SHEET, DB, AUTO_MERGE, USER_RESOLUTION, SYNC.

There is no update API and no delete API for audit. On Postgres, a trigger raises an exception if anyone tries. On H2, discipline plus tests.

The audit uniqueness check used in code is “do not append the same mapping, key, revision, field, and source twice.” That keeps a repaired write from double-logging.

If you cannot answer “who changed quantity on ML-200, from what to what, and was it ops, engineering, or a resolver?” the audit log is unfinished.

## Actor

HTTP carries `X-Actor`. The UI has an “Acting as” box that sets that header. There is no login in this version. Attribution is still required. When you rebuild for real users, replace the header with auth, but keep the actor on every audit row.

## The failure cases the tests encode

Read these as the spec, not as optional extras.

- `ThreeWayMergerTest`: classification.
- `BackoffPolicyTest`: jitter bounds and retry of 429.
- `IdempotentWriterTest`: same revision does not duplicate, including concurrent threads.
- `ResumableSyncTest`: crash after three rows, resume, both sides converge, no duplicate bodies.
- `ReconciliationFlowTest`: one-sided, auto-merge, conflict, then human resolution.
- `ConcurrentEditHarnessTest`: fifty scenarios, twenty independent runs, zero lost rows, zero duplicate keys.
- `AuditTrailTest`: actor, time, and source on field changes, including resolution.

When you rebuild, you may rename tests. You may not skip the properties they assert.

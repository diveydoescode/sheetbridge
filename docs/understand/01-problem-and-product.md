# The problem SheetBridge exists to solve

SheetBridge is a reconciliation service. It keeps a spreadsheet and a database honest with each other without letting the latest writer silently erase the other side.

This document is the story of the product. Not the code yet. The code only makes sense after the problem is sharp.

## Two systems of record that refuse to be one

In a lot of companies, operations does not live in the engineering database.

Warehouse staff, store managers, finance, and customer-support teams run the business in Google Sheets or Excel. They count inventory. They change a status to LOW. They paste a note that says the weekend pop-up drained stock. The spreadsheet is fast, visible, and they already know how to use it.

Engineering runs the same business objects in PostgreSQL. Product information management, pricing, warehouse codes, and imports from another system all land as rows in a table. A service writes `unit_price`. A PIM import inserts a new SKU. A job moves a scale from NORTH-C to NORTH-A.

Both sides are editing the same real-world thing: a SKU, an order, a customer, a location. The identifiers match. The fields overlap. The people do not.

## What goes wrong today

The usual “sync” is last-write-wins.

A job reads the sheet, writes the database. Or it reads the database, writes the sheet. Whichever run finishes last is the truth. The other side’s edit disappears. Nobody gets a ticket. Nobody is asked to choose. The overwrite is silent.

That is catastrophic for operations data, because the two sides often change **different facts for different reasons**.

Imagine SKU ML-210, a grinder.

- Last Monday both sides agreed: quantity 7, price 379.00, notes empty.
- Ops types a note: “waiting on burr kit.”
- Engineering publishes a price cut: 359.00.

A naive sync that copies the sheet onto the database throws away the new price. A naive sync that copies the database onto the sheet throws away the note. Both facts were true. The system picked a winner by clock time.

Now imagine SKU ML-200.

- Last Monday quantity was 18.
- Ops cycle-counts 14.
- Engineering’s stock system says 21.

Those are not complementary facts. They are a dispute about the same field. Last-write-wins still “resolves” it, by destroying one count. That is not a resolution. That is data loss with a timestamp.

## The real-world pattern

This is not a coffee-warehouse toy. It is the same pattern as:

- A clinic spreadsheet of patient follow-ups versus an EHR table.
- A fundraising CRM export versus a donor database.
- A store’s shelf-count sheet versus an inventory service.
- A support team’s “VIP accounts” tab versus the billing database.
- Any migration where the old tool is a spreadsheet and the new tool is a database, and both stay live for months.

The shared property is a **stable row key**. SKU, patient id, account id, order number. If you cannot name the row on both sides, you cannot reconcile. If you can, you should not overwrite.

## What “reconcile” actually means

Reconciliation is not copying. Copying has a source and a destination. Reconciliation has two live sources and a memory of the last time they agreed.

That memory is the **snapshot**.

The snapshot is not the spreadsheet. It is not the database. It is the last committed truth: the row as it stood after the previous successful sync, or after a human resolved a conflict.

Once you have three versions of a row — snapshot, sheet, database — you can ask a better question than “who saved last?”

You can ask: **who changed what, relative to the last agreement?**

- If only the sheet moved, apply the sheet to the database.
- If only the database moved, apply the database to the sheet.
- If they moved different fields, merge those fields.
- If they moved the same field to different values, stop and ask a person.
- If nobody moved, do nothing except keep the snapshot current.

That is three-way merge. Git does this for source code. SheetBridge does this for operational rows.

## What the product is, in one paragraph

SheetBridge is a service that, for each mapping of a spreadsheet tab to a database dataset, classifies every row against the last-synced snapshot. Clean rows stay quiet. One-sided edits apply automatically. Disjoint field edits auto-merge. Overlapping field edits become a conflict in a queue. An operations user resolves a disputed row side by side. Every field change is appended to an audit log with a person, a time, and a source. Writes are idempotent on row key and revision so a retried job never duplicates a row. A crashed job resumes from a checkpoint instead of starting over.

The UI is how a human sees that. The engine is the product.

## The demo story you will keep meeting

The seeded ledger is a North Warehouse SKU catalog. Columns are sku, product_name, quantity, unit_price, warehouse, status, notes. The row key is sku.

Last Monday’s snapshot is the agreement. During the week, ops counted shelves in the sheet. Engineering changed prices and warehouse codes in the database. Some SKUs only moved on one side. Some moved on both sides in different fields. Some fought over quantity or status. One SKU exists only on the sheet because receiving added it. One SKU exists only in the database because a PIM import created it.

When you hit Run reconciliation, you are not importing a file. You are classifying a week of concurrent edits against Monday’s truth.

## What this is not

It is not a general spreadsheet product. It is not a database GUI. It is not “sync Google Sheets to Postgres” as a dumb pipe. It is not last-write-wins with a prettier log.

If you take those shortcuts when you rebuild, you will have a working demo that still destroys data. The whole point of SheetBridge is to refuse that shortcut.

## The sentence to remember

Operations and engineering are both right until they touch the same field. The snapshot tells you which case you are in. A person resolves the rest. The audit log remembers who did what.

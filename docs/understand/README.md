# Understand SheetBridge

These notes exist so you can rebuild SheetBridge yourself, and so you can drop them into **NotebookLM** and generate a video that explains the product, the problem, the stack, and the core logic.

The UI in this repo is a stand-in. When you build it, vibe-code the interface later. The thing you must understand first is the **reconciliation engine**.

## How to feed NotebookLM

Create a new notebook. Upload these files **in this order**:

1. `01-problem-and-product.md`
2. `02-stack-and-design.md`
3. `03-core-logic-three-way-merge.md`
4. `04-sync-conflicts-audit.md`
5. `05-code-we-wrote.md`
6. `06-rebuild-roadmap.md`

Then ask NotebookLM for:

- A **Video Overview** of the whole notebook
- A second video focused only on `03` and `04` (the engine)
- A briefing: “Explain three-way merge like I have to implement it tomorrow without looking at the repo”

Suggested custom prompt for the video:

> Explain SheetBridge as a real operations product. Start with the spreadsheet-versus-database problem. Then teach three-way merge against a last-synced snapshot. Then cover classification, idempotent writes, resumable sync, the conflict queue, and the append-only audit log. Do not dwell on CSS or React components. End with the rebuild order a solo engineer should follow.

## What “done learning” looks like

You can rebuild the core if you can answer these without the repo open:

1. Why last-write-wins is the wrong default for a spreadsheet and a database that share a record.
2. What the snapshot is, and why merge is three-way instead of two-way.
3. How a row becomes CLEAN, SHEET_ONLY, DB_ONLY, AUTO_MERGED, or CONFLICT.
4. Why writes are keyed on row key plus revision plus direction.
5. Why a sync run checkpoints after a batch instead of one giant transaction.
6. What an operations user actually does in the conflict queue, and what the audit log must record.

If those six are solid, the UI is decoration.

## Live reference

- Code: https://github.com/diveydoescode/sheetbridge
- Static demo: https://diveydoescode.github.io/sheetbridge/

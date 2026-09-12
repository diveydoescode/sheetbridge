import { Link } from "react-router-dom";
import { OutcomePill } from "../components/StatusPill";
import type { RowOutcome } from "../types";

const rows: { outcome: RowOutcome; sheet: string; snap: string; db: string }[] = [
  { outcome: "CLEAN", sheet: "18", snap: "18", db: "18" },
  { outcome: "SHEET_ONLY", sheet: "14", snap: "18", db: "18" },
  { outcome: "DB_ONLY", sheet: "18", snap: "18", db: "21" },
  { outcome: "AUTO_MERGED", sheet: "qty 14", snap: "qty 18 / $379", db: "$359" },
  { outcome: "CONFLICT", sheet: "14", snap: "18", db: "21" },
];

export function GuidePage() {
  return (
    <div className="mx-auto max-w-3xl space-y-10">
      <h1 className="font-serif text-3xl">Guide</h1>

      <section className="space-y-3">
        <h2 className="font-serif text-xl">The three copies of a row</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-xl border border-sheet/30 bg-sheet-dim p-4">
            <div className="text-xs font-medium uppercase tracking-wide text-sheet">Spreadsheet</div>
            <p className="mt-2 text-sm">What operations typed. Click a green cell in the ledger to edit it.</p>
          </div>
          <div className="rounded-xl border border-rule bg-white p-4">
            <div className="text-xs font-medium uppercase tracking-wide text-muted">Last snapshot</div>
            <p className="mt-2 text-sm">The last agreed values, after a reconcile or after you resolved a row.</p>
          </div>
          <div className="rounded-xl border border-db/30 bg-db-dim p-4">
            <div className="text-xs font-medium uppercase tracking-wide text-db">Database</div>
            <p className="mt-2 text-sm">What engineering stored. Click a blue cell in the ledger to edit it.</p>
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="font-serif text-xl">What the row label means</h2>
        <p className="text-sm text-muted">
          Hover a label on the ledger for the same hint. Reconcile applies every row that does not say “Needs a
          choice.”
        </p>
        <div className="overflow-auto rounded-2xl border border-rule bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-paper text-left text-[11px] uppercase tracking-wider text-muted">
              <tr>
                <th className="px-4 py-3">Label</th>
                <th className="px-4 py-3 text-sheet">Spreadsheet</th>
                <th className="px-4 py-3">Last snapshot</th>
                <th className="px-4 py-3 text-db">Database</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.outcome} className="border-t border-rule">
                  <td className="px-4 py-3">
                    <OutcomePill outcome={row.outcome} />
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-sheet">{row.sheet}</td>
                  <td className="px-4 py-3 font-mono text-xs text-muted">{row.snap}</td>
                  <td className="px-4 py-3 font-mono text-xs text-db">{row.db}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="font-serif text-xl">Work a ledger</h2>
        <ol className="list-decimal space-y-2 pl-5 text-sm">
          <li>
            Open a ledger. Each field shows <span className="text-sheet">spreadsheet</span> then{" "}
            <span className="text-db">database</span>.
          </li>
          <li>Click a cell to change one side. The row label updates immediately.</li>
          <li>
            <strong>Run reconciliation</strong> copies one-sided and merged rows. Same-field fights stay put and
            appear in the queue.
          </li>
          <li>
            In the queue, open a row. Three columns: spreadsheet, last snapshot, database. Take one side, or pick
            per field.
          </li>
          <li>Audit log lists every field change with who, when, and which side.</li>
        </ol>
        <Link to="/" className="inline-block rounded-full bg-ink px-4 py-2 text-sm text-paper">
          Open a ledger
        </Link>
      </section>
    </div>
  );
}

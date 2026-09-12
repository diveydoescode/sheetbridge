import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import { ErrorBanner } from "../components/ErrorBanner";
import { SourceLegend } from "../components/SourceLegend";
import { OutcomePill, SyncPill } from "../components/StatusPill";
import { outcomeLabel, outcomeTone } from "../classify";
import type { Mapping, PreviewRow, RowOutcome, SyncRun } from "../types";

const toneRow: Record<string, string> = {
  sheet: "bg-sheet-dim/40",
  db: "bg-db-dim/40",
  conflict: "bg-conflict-dim/60",
  merge: "bg-merge-dim/50",
  clean: "",
};

export function WorkspacePage() {
  const { mappingId } = useParams();
  const [mapping, setMapping] = useState<Mapping | null>(null);
  const [rows, setRows] = useState<PreviewRow[]>([]);
  const [run, setRun] = useState<SyncRun | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<{ side: "sheet" | "db"; rowKey: string; field: string } | null>(null);
  const [draft, setDraft] = useState("");

  const load = useCallback(async () => {
    if (!mappingId) return;
    const [m, preview] = await Promise.all([api.mapping(mappingId), api.preview(mappingId)]);
    setMapping(m);
    setRows(preview);
  }, [mappingId]);

  useEffect(() => {
    load().catch((e: Error) => setError(e.message));
  }, [load]);

  const counts = useMemo(() => {
    const tally: Record<string, number> = {};
    for (const row of rows) tally[row.outcome] = (tally[row.outcome] || 0) + 1;
    return tally;
  }, [rows]);

  async function reconcile() {
    if (!mappingId) return;
    setBusy(true);
    setError(null);
    try {
      const result = await api.sync(mappingId);
      setRun(result);
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function commitEdit() {
    if (!mappingId || !editing || !mapping) return;
    const row = rows.find((r) => r.rowKey === editing.rowKey);
    if (!row) return;
    const payload = { ...(editing.side === "sheet" ? row.sheet : row.db) };
    payload[editing.field] = draft;
    payload[mapping.rowKeyColumn] = editing.rowKey;
    try {
      if (editing.side === "sheet") await api.editSheet(mappingId, editing.rowKey, payload);
      else await api.editDb(mappingId, editing.rowKey, payload);
      setEditing(null);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  if (!mapping) {
    return <ErrorBanner error={error || "Loading mapping…"} />;
  }

  const dataColumns = mapping.columns.filter((c) => c !== mapping.rowKeyColumn);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-serif text-3xl">{mapping.name}</h1>
        </div>
        <div className="flex items-center gap-3">
          <SyncPill status={run?.status ?? mapping.lastRunStatus} />
          <button
            onClick={reconcile}
            disabled={busy}
            className="rounded-full bg-ink px-4 py-2 text-sm text-paper disabled:opacity-50"
          >
            {busy ? "Reconciling…" : "Run reconciliation"}
          </button>
        </div>
      </div>
      <ErrorBanner error={error} />
      <SourceLegend />
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6 text-sm">
        {Object.entries(counts).map(([k, v]) => (
          <div key={k} className="rounded-xl border border-rule bg-white px-3 py-2">
            <div className="text-[11px] text-muted">{outcomeLabel(k as RowOutcome)}</div>
            <div className="font-mono text-lg">{v}</div>
          </div>
        ))}
      </div>
      {run?.stats && (
        <div className="rounded-xl border border-rule bg-white px-4 py-3 text-sm text-muted">
          Last run applied {String(run.stats.processed ?? 0)} rows
          {run.stats.conflicts ? ` · ${run.stats.conflicts} sent to the conflict queue` : ""}
          {run.stats.idempotentSkips ? ` · ${run.stats.idempotentSkips} idempotent skips` : ""}
          .{" "}
          <Link to="/conflicts" className="text-ink underline">
            Open queue
          </Link>
        </div>
      )}
      <div className="overflow-auto rounded-2xl border border-rule bg-white shadow-ledger">
        <table className="min-w-full text-sm">
          <thead className="bg-paper text-left text-[11px] uppercase tracking-wider text-muted">
            <tr>
              <th className="px-3 py-3">Key</th>
              <th className="px-3 py-3">Class</th>
              {dataColumns.map((col) => (
                <th key={col} className="px-3 py-3" colSpan={2}>
                  {col.replaceAll("_", " ")}
                </th>
              ))}
            </tr>
            <tr className="border-t border-rule/70">
              <th />
              <th />
              {dataColumns.map((col) => (
                <th key={`${col}-sides`} colSpan={2} className="px-3 pb-2">
                  <div className="flex gap-6 font-sans normal-case tracking-normal">
                    <span className="text-sheet">Spreadsheet</span>
                    <span className="text-db">Database</span>
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.rowKey} className={`border-t border-rule ${toneRow[outcomeTone(row.outcome)]}`}>
                <td className="px-3 py-2 font-mono text-xs">{row.rowKey}</td>
                <td className="px-3 py-2">
                  <OutcomePill outcome={row.outcome} />
                </td>
                {dataColumns.map((col) => {
                  const sheetVal = row.sheet[col] ?? "";
                  const dbVal = row.db[col] ?? "";
                  const diverge = sheetVal !== dbVal;
                  return (
                    <td key={col} colSpan={2} className="px-3 py-2">
                      <div className="grid grid-cols-2 gap-2">
                        <EditableCell
                          value={sheetVal}
                          active={editing?.side === "sheet" && editing.rowKey === row.rowKey && editing.field === col}
                          draft={draft}
                          diverge={diverge}
                          side="sheet"
                          onStart={() => {
                            setEditing({ side: "sheet", rowKey: row.rowKey, field: col });
                            setDraft(sheetVal);
                          }}
                          onDraft={setDraft}
                          onCommit={commitEdit}
                          onCancel={() => setEditing(null)}
                        />
                        <EditableCell
                          value={dbVal}
                          active={editing?.side === "db" && editing.rowKey === row.rowKey && editing.field === col}
                          draft={draft}
                          diverge={diverge}
                          side="db"
                          onStart={() => {
                            setEditing({ side: "db", rowKey: row.rowKey, field: col });
                            setDraft(dbVal);
                          }}
                          onDraft={setDraft}
                          onCommit={commitEdit}
                          onCancel={() => setEditing(null)}
                        />
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function EditableCell(props: {
  value: string;
  active: boolean;
  draft: string;
  diverge: boolean;
  side: "sheet" | "db";
  onStart: () => void;
  onDraft: (v: string) => void;
  onCommit: () => void;
  onCancel: () => void;
}) {
  if (props.active) {
    return (
      <input
        autoFocus
        value={props.draft}
        onChange={(e) => props.onDraft(e.target.value)}
        onBlur={props.onCommit}
        onKeyDown={(e) => {
          if (e.key === "Enter") props.onCommit();
          if (e.key === "Escape") props.onCancel();
        }}
        className="w-full rounded border border-ink bg-white px-1 py-0.5 font-mono text-xs"
      />
    );
  }
  return (
    <button
      onClick={props.onStart}
      className={`w-full truncate rounded px-1 py-0.5 text-left font-mono text-xs ${
        props.diverge ? (props.side === "sheet" ? "text-sheet" : "text-db") : "text-ink"
      } hover:bg-white`}
    >
      {props.value || <span className="text-muted">∅</span>}
    </button>
  );
}

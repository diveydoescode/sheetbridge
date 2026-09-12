import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { ErrorBanner } from "../components/ErrorBanner";
import type { Conflict, ResolutionChoice } from "../types";

type PickSide = "SHEET" | "DB" | "SNAPSHOT";

export function ResolvePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [conflict, setConflict] = useState<Conflict | null>(null);
  const [picks, setPicks] = useState<Record<string, PickSide>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!id) return;
    api
      .conflict(id)
      .then((c) => {
        setConflict(c);
        const initial: Record<string, PickSide> = {};
        const fields = new Set([...Object.keys(c.sheet), ...Object.keys(c.db), ...Object.keys(c.snapshot)]);
        for (const field of fields) {
          if (c.conflictingFields.includes(field)) initial[field] = "SHEET";
          else if ((c.sheet[field] ?? "") !== (c.snapshot[field] ?? "")) initial[field] = "SHEET";
          else if ((c.db[field] ?? "") !== (c.snapshot[field] ?? "")) initial[field] = "DB";
          else initial[field] = "SHEET";
        }
        setPicks(initial);
      })
      .catch((e: Error) => setError(e.message));
  }, [id]);

  const preview = useMemo(() => {
    if (!conflict) return {};
    const merged: Record<string, string> = {};
    for (const [field, side] of Object.entries(picks)) {
      merged[field] =
        side === "DB" ? conflict.db[field] ?? "" : side === "SNAPSHOT" ? conflict.snapshot[field] ?? "" : conflict.sheet[field] ?? "";
    }
    return merged;
  }, [conflict, picks]);

  async function resolve(choice: ResolutionChoice) {
    if (!id) return;
    setBusy(true);
    setError(null);
    try {
      await api.resolve(id, choice, choice === "CUSTOM" ? picks : undefined);
      navigate("/conflicts");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  if (!conflict) return <ErrorBanner error={error || "Loading conflict…"} />;

  const fields = Object.keys(picks);

  return (
    <div className="space-y-6">
      <div>
        <Link to="/conflicts" className="text-sm text-muted hover:text-ink">
          ← Queue
        </Link>
        <h1 className="mt-2 font-serif text-3xl">
          Resolve <span className="font-mono text-2xl">{conflict.rowKey}</span>
        </h1>
        <p className="mt-1 text-sm text-muted">
          Snapshot is the last agreed truth. Spreadsheet is operations. Database is engineering.
        </p>
      </div>
      <ErrorBanner error={error} />
      <div className="overflow-auto rounded-2xl border border-rule bg-white shadow-ledger">
        <table className="min-w-full text-sm">
          <thead className="bg-paper text-left text-[11px] uppercase tracking-wider text-muted">
            <tr>
              <th className="px-4 py-3">Field</th>
              <th className="px-4 py-3 text-muted">Snapshot</th>
              <th className="px-4 py-3 text-sheet">Spreadsheet</th>
              <th className="px-4 py-3 text-db">Database</th>
              <th className="px-4 py-3">Take</th>
            </tr>
          </thead>
          <tbody>
            {fields.map((field) => {
              const disputed = conflict.conflictingFields.includes(field);
              return (
                <tr key={field} className={`border-t border-rule ${disputed ? "bg-conflict-dim/40" : ""}`}>
                  <td className="px-4 py-3 font-mono text-xs">
                    {field}
                    {disputed && <div className="text-[10px] uppercase tracking-wide text-conflict">disputed</div>}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-muted">{conflict.snapshot[field] || "∅"}</td>
                  <td className="px-4 py-3 font-mono text-xs text-sheet">{conflict.sheet[field] || "∅"}</td>
                  <td className="px-4 py-3 font-mono text-xs text-db">{conflict.db[field] || "∅"}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-1">
                      {(["SHEET", "DB", "SNAPSHOT"] as PickSide[]).map((side) => (
                        <button
                          key={side}
                          onClick={() => setPicks((p) => ({ ...p, [field]: side }))}
                          className={`rounded-full px-2 py-0.5 text-[11px] uppercase ${
                            picks[field] === side ? "bg-ink text-paper" : "bg-paper text-muted"
                          }`}
                        >
                          {side === "SHEET" ? "Sheet" : side === "DB" ? "DB" : "Snap"}
                        </button>
                      ))}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="rounded-2xl border border-rule bg-white p-5">
        <div className="text-[11px] uppercase tracking-[0.16em] text-muted">Resolved row</div>
        <pre className="mt-2 overflow-auto font-mono text-xs">{JSON.stringify(preview, null, 2)}</pre>
      </div>
      <div className="flex flex-wrap gap-3">
        <button
          disabled={busy}
          onClick={() => resolve("SHEET")}
          className="rounded-full bg-sheet px-4 py-2 text-sm text-white"
        >
          Take spreadsheet
        </button>
        <button
          disabled={busy}
          onClick={() => resolve("DB")}
          className="rounded-full bg-db px-4 py-2 text-sm text-white"
        >
          Take database
        </button>
        <button
          disabled={busy}
          onClick={() => resolve("CUSTOM")}
          className="rounded-full bg-ink px-4 py-2 text-sm text-paper"
        >
          Accept field picks
        </button>
      </div>
    </div>
  );
}

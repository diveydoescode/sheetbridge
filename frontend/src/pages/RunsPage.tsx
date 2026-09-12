import { useEffect, useState } from "react";
import { api } from "../api/client";
import { ErrorBanner } from "../components/ErrorBanner";
import { SyncPill } from "../components/StatusPill";
import type { SyncRun } from "../types";

export function RunsPage() {
  const [runs, setRuns] = useState<SyncRun[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  function load() {
    api.runs().then(setRuns).catch((e: Error) => setError(e.message));
  }

  useEffect(() => {
    load();
  }, []);

  async function resume(id: string) {
    setBusy(id);
    setError(null);
    try {
      await api.resume(id);
      load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="font-serif text-3xl">Sync runs</h1>
      <ErrorBanner error={error} />
      <div className="space-y-3">
        {runs.map((run) => (
          <div key={run.id} className="rounded-2xl border border-rule bg-white px-5 py-4 shadow-ledger">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <SyncPill status={run.status} />
                  <span className="font-mono text-xs text-muted">{run.id.slice(0, 8)}</span>
                </div>
                <div className="mt-1 text-sm text-muted">
                  {run.triggerSource} · {run.actor} · attempt {run.attempt}
                </div>
              </div>
              {(run.status === "INTERRUPTED" || run.status === "FAILED") && (
                <button
                  disabled={busy === run.id}
                  onClick={() => resume(run.id)}
                  className="rounded-full bg-ink px-3 py-1.5 text-sm text-paper"
                >
                  {busy === run.id ? "Resuming…" : "Resume from checkpoint"}
                </button>
              )}
            </div>
            <pre className="mt-3 overflow-auto font-mono text-[11px] text-muted">
              {JSON.stringify({ checkpoint: run.checkpoint, stats: run.stats, error: run.errorMessage }, null, 2)}
            </pre>
          </div>
        ))}
        {runs.length === 0 && !error && <p className="text-muted">No sync runs yet.</p>}
      </div>
    </div>
  );
}

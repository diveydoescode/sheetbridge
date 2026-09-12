import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { ErrorBanner } from "../components/ErrorBanner";
import { SyncPill } from "../components/StatusPill";
import type { Mapping } from "../types";

export function DashboardPage() {
  const [mappings, setMappings] = useState<Mapping[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.mappings().then(setMappings).catch((e: Error) => setError(e.message));
  }, []);

  return (
    <div className="space-y-8">
      <h1 className="font-serif text-3xl">Ledger</h1>
      <ErrorBanner error={error} />
      <div className="grid gap-4 md:grid-cols-2">
        {mappings.map((mapping) => (
          <Link
            key={mapping.id}
            to={`/workspace/${mapping.id}`}
            className="rounded-2xl border border-rule bg-white p-6 shadow-ledger transition hover:-translate-y-0.5"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="text-[11px] uppercase tracking-[0.16em] text-muted">
                  {mapping.sheetName} · {mapping.sourceType.toLowerCase()}
                </div>
                <h2 className="mt-1 font-serif text-2xl">{mapping.name}</h2>
              </div>
              <SyncPill status={mapping.lastRunStatus} />
            </div>
            <dl className="mt-6 grid grid-cols-3 gap-3 text-sm">
              <div>
                <dt className="text-muted">Sheet rows</dt>
                <dd className="font-mono text-lg text-sheet">{mapping.sheetRows}</dd>
              </div>
              <div>
                <dt className="text-muted">Database</dt>
                <dd className="font-mono text-lg text-db">{mapping.dbRows}</dd>
              </div>
              <div>
                <dt className="text-muted">Open conflicts</dt>
                <dd className="font-mono text-lg text-conflict">{mapping.openConflicts}</dd>
              </div>
            </dl>
          </Link>
        ))}
      </div>
      {mappings.length === 0 && !error && (
        <p className="text-muted">No mappings yet.</p>
      )}
    </div>
  );
}

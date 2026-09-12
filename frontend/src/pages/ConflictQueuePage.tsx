import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { ErrorBanner } from "../components/ErrorBanner";
import type { Conflict, Mapping } from "../types";

export function ConflictQueuePage() {
  const [conflicts, setConflicts] = useState<Conflict[]>([]);
  const [mappings, setMappings] = useState<Record<string, Mapping>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api.conflicts(), api.mappings()])
      .then(([queue, maps]) => {
        setConflicts(queue);
        setMappings(Object.fromEntries(maps.map((m) => [m.id, m])));
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  return (
    <div className="space-y-6">
      <h1 className="font-serif text-3xl">Conflict queue</h1>
      <ErrorBanner error={error} />
      <div className="space-y-3">
        {conflicts.map((conflict) => (
          <Link
            key={conflict.id}
            to={`/conflicts/${conflict.id}`}
            className="block rounded-2xl border border-rule bg-white px-5 py-4 shadow-ledger hover:border-ink/30"
          >
            <div className="flex items-center justify-between gap-4">
              <div>
                <div className="font-mono text-sm">{conflict.rowKey}</div>
                <div className="text-sm text-muted">{mappings[conflict.mappingId]?.name ?? conflict.mappingId}</div>
              </div>
              <div className="text-right text-sm">
                <div className="text-conflict">
                  {conflict.conflictingFields.length} disputed field
                  {conflict.conflictingFields.length === 1 ? "" : "s"}
                </div>
                <div className="font-mono text-xs text-muted">{conflict.conflictingFields.join(", ")}</div>
              </div>
            </div>
          </Link>
        ))}
        {conflicts.length === 0 && !error && (
          <div className="rounded-2xl border border-dashed border-rule px-5 py-10 text-center text-muted">
            Queue is empty.
          </div>
        )}
      </div>
    </div>
  );
}

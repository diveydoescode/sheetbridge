import { useEffect, useState } from "react";
import { api } from "../api/client";
import { ErrorBanner } from "../components/ErrorBanner";
import type { AuditEntry, Mapping } from "../types";

export function AuditPage() {
  const [mappings, setMappings] = useState<Mapping[]>([]);
  const [mappingId, setMappingId] = useState("");
  const [rowKey, setRowKey] = useState("");
  const [field, setField] = useState("");
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .mappings()
      .then((maps) => {
        setMappings(maps);
        if (maps[0]) setMappingId(maps[0].id);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    if (!mappingId) return;
    api
      .audit(mappingId, rowKey || undefined, field || undefined)
      .then(setEntries)
      .catch((e: Error) => setError(e.message));
  }, [mappingId, rowKey, field]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-serif text-3xl">Append-only audit log</h1>
        <p className="mt-2 text-muted">
          Every field traces back to a person, a time, and a source — spreadsheet, database,
          auto-merge, or a human resolution.
        </p>
      </div>
      <ErrorBanner error={error} />
      <div className="flex flex-wrap gap-3">
        <select
          value={mappingId}
          onChange={(e) => setMappingId(e.target.value)}
          className="rounded-md border border-rule bg-white px-3 py-2 text-sm"
        >
          {mappings.map((m) => (
            <option key={m.id} value={m.id}>
              {m.name}
            </option>
          ))}
        </select>
        <input
          placeholder="Filter row key"
          value={rowKey}
          onChange={(e) => setRowKey(e.target.value)}
          className="rounded-md border border-rule bg-white px-3 py-2 font-mono text-sm"
        />
        <input
          placeholder="Filter field"
          value={field}
          onChange={(e) => setField(e.target.value)}
          className="rounded-md border border-rule bg-white px-3 py-2 font-mono text-sm"
        />
      </div>
      <div className="overflow-auto rounded-2xl border border-rule bg-white shadow-ledger">
        <table className="min-w-full text-sm">
          <thead className="bg-paper text-left text-[11px] uppercase tracking-wider text-muted">
            <tr>
              <th className="px-4 py-3">When</th>
              <th className="px-4 py-3">Row</th>
              <th className="px-4 py-3">Field</th>
              <th className="px-4 py-3">From</th>
              <th className="px-4 py-3">To</th>
              <th className="px-4 py-3">Source</th>
              <th className="px-4 py-3">Actor</th>
              <th className="px-4 py-3">Rev</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.id} className="border-t border-rule">
                <td className="px-4 py-2 font-mono text-xs text-muted">
                  {new Date(entry.createdAt).toISOString().replace("T", " ").slice(0, 19)}
                </td>
                <td className="px-4 py-2 font-mono text-xs">{entry.rowKey}</td>
                <td className="px-4 py-2 font-mono text-xs">{entry.fieldName}</td>
                <td className="px-4 py-2 font-mono text-xs text-muted">{entry.oldValue || "∅"}</td>
                <td className="px-4 py-2 font-mono text-xs">{entry.newValue || "∅"}</td>
                <td className="px-4 py-2 text-xs uppercase tracking-wide">{entry.source}</td>
                <td className="px-4 py-2">{entry.actor}</td>
                <td className="px-4 py-2 font-mono text-xs">{entry.revision}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {entries.length === 0 && <p className="px-4 py-8 text-center text-muted">No audit entries for this filter.</p>}
      </div>
    </div>
  );
}

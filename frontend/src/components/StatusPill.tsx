import { outcomeLabel, outcomeTone } from "../classify";
import type { RowOutcome, SyncStatus } from "../types";

const toneClass = {
  sheet: "bg-sheet-dim text-sheet",
  db: "bg-db-dim text-db",
  conflict: "bg-conflict-dim text-conflict",
  merge: "bg-merge-dim text-merge",
  clean: "bg-rule/70 text-muted",
};

export function OutcomePill({ outcome }: { outcome: RowOutcome }) {
  return <span className={`pill ${toneClass[outcomeTone(outcome)]}`}>{outcomeLabel(outcome)}</span>;
}

export function SyncPill({ status }: { status: SyncStatus | null }) {
  if (!status) return <span className="pill bg-rule/70 text-muted">Never run</span>;
  const map: Record<SyncStatus, string> = {
    PENDING: "bg-rule/70 text-muted",
    RUNNING: "bg-db-dim text-db",
    INTERRUPTED: "bg-merge-dim text-merge",
    COMPLETED: "bg-sheet-dim text-sheet",
    FAILED: "bg-conflict-dim text-conflict",
  };
  return <span className={`pill ${map[status]}`}>{status.toLowerCase()}</span>;
}

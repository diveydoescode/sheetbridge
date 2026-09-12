export function ErrorBanner({ error }: { error: string | null }) {
  if (!error) return null;
  return (
    <div className="rounded-lg border border-conflict/30 bg-conflict-dim px-4 py-3 text-sm text-conflict">
      {error}
    </div>
  );
}

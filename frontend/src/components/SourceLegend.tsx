import { Link } from "react-router-dom";

const items = [
  { label: "Spreadsheet", className: "bg-sheet text-white", meaning: "Operations" },
  { label: "Last snapshot", className: "bg-muted text-white", meaning: "Last agreement" },
  { label: "Database", className: "bg-db text-white", meaning: "Engineering" },
];

export function SourceLegend({ compact = false }: { compact?: boolean }) {
  return (
    <div className="flex flex-wrap items-center gap-3 text-xs">
      {items.map((item) => (
        <span key={item.label} className="inline-flex items-center gap-1.5">
          <span className={`rounded px-1.5 py-0.5 font-medium ${item.className}`}>{item.label}</span>
          {!compact && <span className="text-muted">{item.meaning}</span>}
        </span>
      ))}
      <Link to="/guide" className="ml-auto text-muted underline-offset-2 hover:text-ink hover:underline">
        Guide
      </Link>
    </div>
  );
}

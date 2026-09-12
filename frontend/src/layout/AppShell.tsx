import { BookOpen, GitCompare, LayoutDashboard, ListChecks, ScrollText, SplitSquareHorizontal } from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";
import { getActor, isPagesDemo, resetDemo, setActor } from "../api/client";
import { useEffect, useState } from "react";

const links = [
  { to: "/", label: "Ledger", icon: LayoutDashboard, end: true },
  { to: "/conflicts", label: "Conflict queue", icon: GitCompare },
  { to: "/runs", label: "Sync runs", icon: SplitSquareHorizontal },
  { to: "/audit", label: "Audit log", icon: ScrollText },
  { to: "/guide", label: "Guide", icon: BookOpen },
];

export function AppShell() {
  const [actor, setActorState] = useState(getActor());

  useEffect(() => {
    setActor(actor);
  }, [actor]);

  return (
    <div className="min-h-screen">
      <header className="border-b border-rule bg-white/80 backdrop-blur">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-4 sm:gap-6 sm:px-6">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-ink text-paper">
              <ListChecks size={18} />
            </div>
            <div>
              <div className="font-serif text-xl leading-none tracking-tight">SheetBridge</div>
              <div className="mt-1 text-[11px] uppercase tracking-[0.18em] text-muted">
                Spreadsheet ↔ database
              </div>
            </div>
          </div>
          <nav className="flex flex-wrap items-center gap-1">
            {links.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.end}
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-full px-3 py-1.5 text-sm ${
                    isActive ? "bg-ink text-paper" : "text-muted hover:bg-rule/50 hover:text-ink"
                  }`
                }
              >
                <link.icon size={15} />
                {link.label}
              </NavLink>
            ))}
          </nav>
          <label className="flex items-center gap-2 text-xs text-muted">
            Acting as
            <input
              value={actor}
              onChange={(e) => setActorState(e.target.value)}
              className="w-36 rounded-md border border-rule bg-paper px-2 py-1 font-mono text-ink"
            />
          </label>
        </div>
      </header>
      {isPagesDemo() && (
        <div className="border-b border-rule bg-white/70">
          <div className="mx-auto flex max-w-7xl justify-end px-4 py-2 sm:px-6">
            <button
              type="button"
              onClick={() => {
                resetDemo();
                window.location.reload();
              }}
              className="rounded-full border border-rule bg-paper px-3 py-1 text-xs uppercase tracking-wide text-muted"
            >
              Reset ledger
            </button>
          </div>
        </div>
      )}
      <main className="mx-auto max-w-7xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  );
}

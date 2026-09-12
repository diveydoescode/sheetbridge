import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "./layout/AppShell";
import { AuditPage } from "./pages/AuditPage";
import { ConflictQueuePage } from "./pages/ConflictQueuePage";
import { DashboardPage } from "./pages/DashboardPage";
import { ResolvePage } from "./pages/ResolvePage";
import { GuidePage } from "./pages/GuidePage";
import { RunsPage } from "./pages/RunsPage";
import { WorkspacePage } from "./pages/WorkspacePage";

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/workspace/:mappingId" element={<WorkspacePage />} />
        <Route path="/conflicts" element={<ConflictQueuePage />} />
        <Route path="/conflicts/:id" element={<ResolvePage />} />
        <Route path="/audit" element={<AuditPage />} />
        <Route path="/runs" element={<RunsPage />} />
        <Route path="/guide" element={<GuidePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

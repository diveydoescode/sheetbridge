import type { AuditEntry, Conflict, Mapping, PreviewRow, ResolutionChoice, RowResponse, SyncRun } from "../types";

const ACTOR_KEY = "sheetbridge.actor";

export function getActor(): string {
  return localStorage.getItem(ACTOR_KEY) || "maya.ops";
}

export function setActor(actor: string): void {
  localStorage.setItem(ACTOR_KEY, actor);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("X-Actor", getActor());
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      /* ignore */
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  mappings: () => request<Mapping[]>("/api/mappings"),
  mapping: (id: string) => request<Mapping>(`/api/mappings/${id}`),
  preview: (id: string) => request<PreviewRow[]>(`/api/mappings/${id}/preview`),
  sheetRows: (id: string) => request<RowResponse[]>(`/api/mappings/${id}/sheet-rows`),
  dbRows: (id: string) => request<RowResponse[]>(`/api/mappings/${id}/db-rows`),
  editSheet: (id: string, rowKey: string, payload: Record<string, string>) =>
    request<RowResponse>(`/api/mappings/${id}/sheet-rows/${encodeURIComponent(rowKey)}`, {
      method: "PUT",
      body: JSON.stringify({ payload, deleted: false }),
    }),
  editDb: (id: string, rowKey: string, payload: Record<string, string>) =>
    request<RowResponse>(`/api/mappings/${id}/db-rows/${encodeURIComponent(rowKey)}`, {
      method: "PUT",
      body: JSON.stringify({ payload, deleted: false }),
    }),
  sync: (id: string) =>
    request<SyncRun>(`/api/mappings/${id}/sync`, { method: "POST", body: "{}" }),
  runs: () => request<SyncRun[]>("/api/sync-runs"),
  run: (id: string) => request<SyncRun>(`/api/sync-runs/${id}`),
  resume: (id: string) =>
    request<SyncRun>(`/api/sync-runs/${id}/resume`, { method: "POST", body: "{}" }),
  conflicts: (mappingId?: string, status = "OPEN") => {
    const params = new URLSearchParams({ status });
    if (mappingId) params.set("mappingId", mappingId);
    return request<Conflict[]>(`/api/conflicts?${params}`);
  },
  conflict: (id: string) => request<Conflict>(`/api/conflicts/${id}`),
  resolve: (
    id: string,
    choice: ResolutionChoice,
    fieldPicks?: Record<string, string>,
    customPayload?: Record<string, string>,
  ) =>
    request<Conflict>(`/api/conflicts/${id}/resolve`, {
      method: "POST",
      body: JSON.stringify({ choice, fieldPicks, customPayload }),
    }),
  audit: (mappingId: string, rowKey?: string, field?: string) => {
    const params = new URLSearchParams({ mappingId });
    if (rowKey) params.set("rowKey", rowKey);
    if (field) params.set("field", field);
    return request<AuditEntry[]>(`/api/audit?${params}`);
  },
};

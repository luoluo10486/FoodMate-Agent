/**
 * 管理后台服务 — 当前转发 mock，后续替换为真实 API 调用。
 */
import {
  adminAuditRows,
  adminDeletedRows,
  adminKnowledgeRows,
  adminModelUsageRows,
  adminOperationAuditRows,
  adminOverviewMetrics,
  adminResourceCards,
  adminSqlAuditRows,
  adminToolCallRows,
  adminToolRows,
  adminTraceRows,
  adminUserRows,
  adminUserSessionRows,
} from '../mock/admin';

export type AdminDashboard = {
  overview_metrics: Array<Record<string, string>>;
  runs: Array<Record<string, unknown>>;
  tool_calls: Array<Record<string, unknown>>;
  sql_audits: Array<Record<string, unknown>>;
  tools: Array<Record<string, unknown>>;
  usage: Array<Record<string, unknown>>;
  knowledge: Array<Record<string, unknown>>;
  deleted: Array<Record<string, unknown>>;
  operation_audits: Array<Record<string, unknown>>;
};

export async function loadAdminDashboard(): Promise<AdminDashboard> {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('真实管理后台接口未启用');
  const csrf = document.cookie.split('; ').find((v) => v.startsWith('foodmate_csrf='))?.split('=')[1];
  const response = await fetch(`${baseUrl}/api/admin/dashboard`, { credentials: 'include', headers: csrf ? { 'X-CSRF-Token': csrf } : {} });
  const body = (await response.json()) as { success: boolean; data: AdminDashboard; error?: { message: string } };
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? '管理数据加载失败');
  const map = (row: Record<string, unknown>) => Object.fromEntries(Object.entries(row).map(([key, value]) => [key.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase()), value]));
  const runs = body.data.runs.map(map).map((row) => ({ ...row, runId: row.agentRunId, user: row.username, durationMs: Number(row.durationMs ?? 0) }));
  const toolCalls = body.data.tool_calls.map(map).map((row) => ({ ...row, callId: row.toolCallId, runId: row.agentRunId, toolName: row.toolName }));
  const sqlAudits = body.data.sql_audits.map(map).map((row) => ({ ...row, auditId: row.sqlAuditId, actor: row.actor ?? 'system' }));
  const knowledge = body.data.knowledge.map(map).map((row) => ({ ...row, documentId: row.documentId }));
  const deleted = body.data.deleted.map(map).map((row) => ({ ...row, resourceId: row.resourceId }));
  return {
    overview_metrics: body.data.overview_metrics,
    runs, tool_calls: toolCalls, sql_audits: sqlAudits,
    tools: body.data.tools.map(map), usage: body.data.usage.map(map), knowledge, deleted, operation_audits: body.data.operation_audits.map(map),
  };
}

export async function loadAdminUsers() {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return adminUserRows;
  const csrf = document.cookie.split('; ').find((v) => v.startsWith('foodmate_csrf='))?.split('=')[1];
  const response = await fetch(`${baseUrl}/api/admin/users`, { credentials: 'include', headers: csrf ? { 'X-CSRF-Token': csrf } : {} });
  const body = (await response.json()) as { success: boolean; data: Array<{ user_id: number; username: string; nickname?: string; email: string; role: string; status: string }>; error?: { message: string } };
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? '用户列表加载失败');
  return body.data.map((u) => ({ userId: String(u.user_id), username: u.username, displayName: u.nickname ?? u.username, role: u.role, status: u.status, email: u.email, phone: '-', dietGoal: '-', calorieTarget: 0, loginFailedCount: 0, lockedUntil: '-' }));
}

async function adminWrite<T>(path: string, method: string, payload?: unknown): Promise<T> {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
  const csrf = document.cookie.split('; ').find((v) => v.startsWith('foodmate_csrf='))?.split('=')[1];
  const response = await fetch(`${baseUrl}${path}`, { method, credentials: 'include', headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) }, body: payload === undefined ? undefined : JSON.stringify(payload) });
  const body = (await response.json()) as { success: boolean; data: T; error?: { message?: string } };
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? '管理操作失败');
  return body.data;
}

export const updateAdminUserStatus = (id: string, status: string) => adminWrite(`/api/admin/users/${encodeURIComponent(id)}/status`, 'PATCH', { status });
export const revokeAdminUserSessions = (id: string) => adminWrite(`/api/admin/users/${encodeURIComponent(id)}/sessions/revoke-all`, 'POST');
export const updateAdminToolStatus = (name: string, status: string) => adminWrite(`/api/admin/tools/${encodeURIComponent(name)}/status`, 'PATCH', { status });
export const updateKnowledgeStatus = (id: string, status: string) => adminWrite(`/api/admin/knowledge/${encodeURIComponent(id)}/status`, 'PATCH', { status });
export const restoreAdminResource = (type: string, id: string) => adminWrite(`/api/admin/resources/${encodeURIComponent(type)}/${encodeURIComponent(id)}/restore`, 'POST');
export async function uploadKnowledgeDocument(file: File) {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
  const csrf = document.cookie.split('; ').find((v) => v.startsWith('foodmate_csrf='))?.split('=')[1];
  const form = new FormData(); form.append('file', file);
  const response = await fetch(`${baseUrl}/api/admin/knowledge`, { method: 'POST', credentials: 'include', headers: csrf ? { 'X-CSRF-Token': csrf } : {}, body: form });
  const body = await response.json() as { success: boolean; data: { documentId: number }; error?: { message?: string } };
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? '知识文档上传失败');
  return body.data;
}

export {
  adminAuditRows,
  adminDeletedRows,
  adminKnowledgeRows,
  adminModelUsageRows,
  adminOperationAuditRows,
  adminOverviewMetrics,
  adminResourceCards,
  adminSqlAuditRows,
  adminToolCallRows,
  adminToolRows,
  adminTraceRows,
  adminUserRows,
  adminUserSessionRows,
};

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

export async function loadAdminUsers() {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return adminUserRows;
  const csrf = document.cookie.split('; ').find((v) => v.startsWith('foodmate_csrf='))?.split('=')[1];
  const response = await fetch(`${baseUrl}/api/admin/users`, { credentials: 'include', headers: csrf ? { 'X-CSRF-Token': csrf } : {} });
  const body = (await response.json()) as { success: boolean; data: Array<{ user_id: number; username: string; nickname?: string; email: string; role: string; status: string }>; error?: { message: string } };
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? '用户列表加载失败');
  return body.data.map((u) => ({ userId: String(u.user_id), username: u.username, displayName: u.nickname ?? u.username, role: u.role, status: u.status, email: u.email, phone: '-', dietGoal: '-', calorieTarget: 0, loginFailedCount: 0, lockedUntil: '-' }));
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

/**
 * 会话服务 — 当前转发 mock，后续替换为真实 API 调用。
 */
import { mockSessions, taskCards, recommendedPrompts } from '../mock/sessions';
import type { SessionSummary } from '../types/session';
import type { TaskCardData } from '../types/ui';
import { apiRequest } from './apiClient';

export function getSessions(): SessionSummary[] {
  return mockSessions;
}

export async function loadSessions(): Promise<SessionSummary[]> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockSessions;
  const body = await apiRequest<{ items: Array<{ session_id: number; title?: string; mode?: string; status?: string }>; total: number }>('/api/sessions?size=50');
  return body.items.map((item, index) => ({ id: String(item.session_id), title: item.title ?? '未命名会话', subtitle: item.mode ?? 'agent', active: index === 0, status: item.status as SessionSummary['status'] }));
}

export type RealSession = { session_id: number; title: string; mode: string; status: string; last_message_at?: string };
export type RealMessage = { message_id: number; session_id: number; role: 'user'; content: string; sequence_no: number; created_at: string };
export async function createSession(title?: string): Promise<RealSession> { return apiRequest('/api/sessions', { method: 'POST', body: JSON.stringify({ title: title ?? '', mode: 'chat' }) }); }
export async function loadSessionMessages(sessionId: string): Promise<RealMessage[]> { const page = await apiRequest<{ items: RealMessage[] }>(`/api/sessions/${encodeURIComponent(sessionId)}/messages?size=100`); return page.items; }
export async function sendUserMessage(sessionId: string, content: string): Promise<RealMessage> { return apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}/messages`, { method: 'POST', body: JSON.stringify({ role: 'user', content }) }); }
export async function renameSession(sessionId: string, title: string): Promise<void> { await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}`, { method: 'PATCH', body: JSON.stringify({ title }) }); }
export async function archiveSession(sessionId: string): Promise<void> { await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}/archive`, { method: 'POST' }); }
export async function deleteSession(sessionId: string): Promise<void> { await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' }); }
export async function searchSessions(query: string): Promise<SessionSummary[]> { const rows = await apiRequest<Array<{ session_id: number; title: string; snippet: string }>>(`/api/sessions/search?q=${encodeURIComponent(query)}`); return rows.map((row) => ({ id: String(row.session_id), title: row.title, subtitle: row.snippet })); }

export function getTaskCards(): TaskCardData[] {
  return taskCards;
}

export function getRecommendedPrompts(): string[] {
  return recommendedPrompts;
}

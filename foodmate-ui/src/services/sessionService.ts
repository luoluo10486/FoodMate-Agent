/**
 * 会话服务：real 模式使用后端 API，mock 模式只保留开发期演示数据。
 */
import { mockHomeSessions, mockSessions, taskCards, recommendedPrompts } from '../mock/sessions';
import type { SessionSummary } from '../types/session';
import type { TaskCardData } from '../types/ui';
import { apiRequest } from './apiClient';

export function getSessions(): SessionSummary[] {
  return mockSessions;
}

export function getHomeSessions(): SessionSummary[] {
  return mockHomeSessions;
}

export type RealSession = {
  session_id: string;
  user_id?: string;
  title: string;
  mode: string;
  status: string;
  last_message_at?: string;
};
export type RealMessage = {
  message_id: string;
  session_id: string;
  agent_run_id?: string;
  role: 'user' | 'assistant';
  content: string;
  structured_payload?: string;
  sequence_no: number;
  created_at: string;
};

export type PageResult<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
};

export type SessionListParams = {
  page?: number;
  size?: number;
  query?: string;
  status?: string;
};

export type MessageListParams = {
  page?: number;
  size?: number;
};

function sessionQuery(params: SessionListParams = {}) {
  const search = new URLSearchParams({
    page: String(params.page ?? 1),
    size: String(params.size ?? 50),
  });
  if (params.query?.trim()) search.set('q', params.query.trim());
  if (params.status && params.status !== 'all') search.set('status', params.status);
  return search;
}

function messageQuery(params: MessageListParams = {}) {
  return new URLSearchParams({ page: String(params.page ?? 1), size: String(params.size ?? 100) });
}

function mapSessionSummary(item: RealSession, index: number): SessionSummary {
  return {
    id: String(item.session_id),
    title: item.title ?? '未命名会话',
    subtitle: item.mode ?? 'agent',
    active: index === 0,
    status: item.status as SessionSummary['status'],
  };
}

export async function loadSessionsPage(params: SessionListParams = {}): Promise<PageResult<RealSession>> {
  return apiRequest<PageResult<RealSession>>(`/api/sessions?${sessionQuery(params).toString()}`);
}

// 以下方法统一通过 apiRequest 发送真实会话和消息请求，由 apiRequest 负责 Cookie、CSRF 和错误映射。
export async function createSession(title?: string): Promise<RealSession> {
  return apiRequest('/api/sessions', { method: 'POST', body: JSON.stringify({ title: title ?? '', mode: 'chat' }) });
}

export async function loadSessions(params: SessionListParams = {}): Promise<SessionSummary[]> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockSessions;
  const page = await loadSessionsPage(params);
  return page.items.map(mapSessionSummary);
}

export async function loadSessionMessagesPage(
  sessionId: string,
  params: MessageListParams = {},
): Promise<PageResult<RealMessage>> {
  return apiRequest<PageResult<RealMessage>>(
    `/api/sessions/${encodeURIComponent(sessionId)}/messages?${messageQuery(params).toString()}`,
  );
}

export async function loadSessionMessages(sessionId: string, params: MessageListParams = {}): Promise<RealMessage[]> {
  const page = await loadSessionMessagesPage(sessionId, params);
  return page.items;
}
export async function sendUserMessage(sessionId: string, content: string): Promise<RealMessage> {
  return apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}/messages`, {
    method: 'POST',
    body: JSON.stringify({ role: 'user', content }),
  });
}

export async function updateMessage(sessionId: string, messageId: string, content: string): Promise<RealMessage> {
  return apiRequest<RealMessage>(
    `/api/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}`,
    {
      method: 'PATCH',
      body: JSON.stringify({ content }),
    },
  );
}

export async function deleteMessage(sessionId: string, messageId: string): Promise<void> {
  await apiRequest<void>(`/api/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}`, {
    method: 'DELETE',
  });
}
export async function renameSession(sessionId: string, title: string): Promise<void> {
  await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'PATCH',
    body: JSON.stringify({ title }),
  });
}
export async function archiveSession(sessionId: string): Promise<void> {
  await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}/archive`, { method: 'POST' });
}
export async function unarchiveSession(sessionId: string): Promise<void> {
  await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}/unarchive`, { method: 'POST' });
}
export async function deleteSession(sessionId: string): Promise<void> {
  await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' });
}
export async function loadDeletedSessionsPage(params: MessageListParams = {}): Promise<PageResult<RealSession>> {
  return apiRequest<PageResult<RealSession>>(
    `/api/sessions/deleted?${messageQuery({ ...params, size: params.size ?? 50 }).toString()}`,
  );
}

export async function loadDeletedSessions(params: MessageListParams = {}): Promise<RealSession[]> {
  const page = await loadDeletedSessionsPage(params);
  return page.items;
}
export async function restoreSession(sessionId: string): Promise<void> {
  await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}/restore`, { method: 'POST' });
}
export async function searchSessions(
  query: string,
  params: Pick<SessionListParams, 'page' | 'size'> = {},
): Promise<SessionSummary[]> {
  const search = new URLSearchParams({
    q: query.trim(),
    page: String(params.page ?? 1),
    size: String(params.size ?? 50),
  });
  const rows = await apiRequest<Array<{ session_id: string; title: string; snippet: string }>>(
    `/api/sessions/search?${search.toString()}`,
  );
  return rows.map((row) => ({ id: row.session_id, title: row.title, subtitle: row.snippet }));
}

export function getTaskCards(): TaskCardData[] {
  return taskCards;
}

export function getRecommendedPrompts(): string[] {
  return recommendedPrompts;
}

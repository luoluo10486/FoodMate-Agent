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

function requestInit(signal?: AbortSignal): RequestInit {
  return signal ? { signal } : {};
}

type SearchSession = {
  session_id: string;
  title: string;
  snippet: string;
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

export async function loadSessionSummariesPage(
  params: SessionListParams = {},
  signal?: AbortSignal,
): Promise<PageResult<SessionSummary>> {
  const page = await loadSessionsPage(params, signal);
  return {
    ...page,
    items: page.items.map(mapSessionSummary),
  };
}

export async function loadSessionsPage(
  params: SessionListParams = {},
  signal?: AbortSignal,
): Promise<PageResult<RealSession>> {
  return apiRequest<PageResult<RealSession>>(`/api/sessions?${sessionQuery(params).toString()}`, requestInit(signal));
}

// 以下方法统一通过 apiRequest 发送真实会话和消息请求，由 apiRequest 负责 Cookie、CSRF 和错误映射。
export async function createSession(title?: string, signal?: AbortSignal): Promise<RealSession> {
  return apiRequest('/api/sessions', {
    method: 'POST',
    body: JSON.stringify({ title: title ?? '', mode: 'chat' }),
    ...requestInit(signal),
  });
}

export async function loadSessions(params: SessionListParams = {}, signal?: AbortSignal): Promise<SessionSummary[]> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockSessions;
  const page = await loadSessionsPage(params, signal);
  return page.items.map(mapSessionSummary);
}

export async function loadSessionMessagesPage(
  sessionId: string,
  params: MessageListParams = {},
  signal?: AbortSignal,
): Promise<PageResult<RealMessage>> {
  return apiRequest<PageResult<RealMessage>>(
    `/api/sessions/${encodeURIComponent(sessionId)}/messages?${messageQuery(params).toString()}`,
    requestInit(signal),
  );
}

export async function loadSessionMessages(
  sessionId: string,
  params: MessageListParams = {},
  signal?: AbortSignal,
): Promise<RealMessage[]> {
  const firstPage = await loadSessionMessagesPage(sessionId, params, signal);
  const items = [...firstPage.items];
  const requestedPage = firstPage.page;
  const pageCount = Math.ceil(firstPage.total / firstPage.size);
  if (requestedPage === 1 && pageCount > 1) {
    for (let page = 2; page <= pageCount; page += 1) {
      const nextPage = await loadSessionMessagesPage(sessionId, { page, size: firstPage.size }, signal);
      items.push(...nextPage.items);
    }
  }
  return sortMessages(items);
}
export async function sendUserMessage(sessionId: string, content: string, signal?: AbortSignal): Promise<RealMessage> {
  return apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}/messages`, {
    method: 'POST',
    body: JSON.stringify({ role: 'user', content }),
    ...requestInit(signal),
  });
}

export async function updateMessage(
  sessionId: string,
  messageId: string,
  content: string,
  signal?: AbortSignal,
): Promise<RealMessage> {
  return apiRequest<RealMessage>(
    `/api/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}`,
    {
      method: 'PATCH',
      body: JSON.stringify({ content }),
      ...requestInit(signal),
    },
  );
}

export async function deleteMessage(sessionId: string, messageId: string, signal?: AbortSignal): Promise<void> {
  await apiRequest<void>(`/api/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}`, {
    method: 'DELETE',
    ...requestInit(signal),
  });
}
export async function renameSession(sessionId: string, title: string, signal?: AbortSignal): Promise<void> {
  await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'PATCH',
    body: JSON.stringify({ title }),
    ...requestInit(signal),
  });
}
export async function archiveSession(sessionId: string, signal?: AbortSignal): Promise<void> {
  await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}/archive`, {
    method: 'POST',
    ...requestInit(signal),
  });
}
export async function unarchiveSession(sessionId: string, signal?: AbortSignal): Promise<void> {
  await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}/unarchive`, {
    method: 'POST',
    ...requestInit(signal),
  });
}
export async function deleteSession(sessionId: string, signal?: AbortSignal): Promise<void> {
  await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE', ...requestInit(signal) });
}
export async function loadDeletedSessionsPage(
  params: MessageListParams = {},
  signal?: AbortSignal,
): Promise<PageResult<RealSession>> {
  return apiRequest<PageResult<RealSession>>(
    `/api/sessions/deleted?${messageQuery({ ...params, size: params.size ?? 50 }).toString()}`,
    requestInit(signal),
  );
}

export async function loadDeletedSessions(
  params: MessageListParams = {},
  signal?: AbortSignal,
): Promise<RealSession[]> {
  const firstPage = await loadDeletedSessionsPage(params, signal);
  const items = [...firstPage.items];
  const pageCount = Math.ceil(firstPage.total / firstPage.size);
  if (firstPage.page === 1 && pageCount > 1) {
    for (let page = 2; page <= pageCount; page += 1) {
      const nextPage = await loadDeletedSessionsPage({ page, size: firstPage.size }, signal);
      items.push(...nextPage.items);
    }
  }
  return items;
}
export async function restoreSession(sessionId: string, signal?: AbortSignal): Promise<void> {
  await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}/restore`, {
    method: 'POST',
    ...requestInit(signal),
  });
}
export async function searchSessions(
  query: string,
  params: Pick<SessionListParams, 'page' | 'size'> = {},
  signal?: AbortSignal,
): Promise<PageResult<SessionSummary>> {
  const search = new URLSearchParams({
    q: query.trim(),
    page: String(params.page ?? 1),
    size: String(params.size ?? 50),
  });
  const result = await apiRequest<PageResult<SearchSession>>(
    `/api/sessions/search?${search.toString()}`,
    requestInit(signal),
  );
  return {
    ...result,
    items: result.items.map((row) => ({ id: row.session_id, title: row.title, subtitle: row.snippet })),
  };
}

function sortMessages(items: RealMessage[]) {
  return [...items].sort((left, right) => {
    if (left.sequence_no !== right.sequence_no) return left.sequence_no - right.sequence_no;
    const createdAtOrder = left.created_at.localeCompare(right.created_at);
    if (createdAtOrder !== 0) return createdAtOrder;
    return left.message_id.localeCompare(right.message_id);
  });
}

export function getTaskCards(): TaskCardData[] {
  return taskCards;
}

export function getRecommendedPrompts(): string[] {
  return recommendedPrompts;
}

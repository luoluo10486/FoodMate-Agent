/**
 * 会话服务 — 当前转发 mock，后续替换为真实 API 调用。
 */
import { mockSessions, taskCards, recommendedPrompts } from '../mock/sessions';
import type { SessionSummary } from '../types/session';
import type { TaskCardData } from '../types/ui';
import type { ApiResponse } from './types';

export function getSessions(): SessionSummary[] {
  return mockSessions;
}

export async function loadSessions(): Promise<SessionSummary[]> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockSessions;
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
  const response = await fetch(`${baseUrl}/api/sessions`, { credentials: 'include' });
  const body = (await response.json()) as ApiResponse<Array<{ session_id: number; title?: string; mode?: string; status?: string }>>;
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? '会话加载失败');
  return body.data.map((item, index) => ({ id: String(item.session_id), title: item.title ?? '未命名会话', subtitle: item.mode ?? 'agent', active: index === 0, status: item.status as SessionSummary['status'] }));
}

export function getTaskCards(): TaskCardData[] {
  return taskCards;
}

export function getRecommendedPrompts(): string[] {
  return recommendedPrompts;
}

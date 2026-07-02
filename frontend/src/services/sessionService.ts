/**
 * 会话服务 — 当前转发 mock，后续替换为真实 API 调用。
 */
import { mockSessions, taskCards, recommendedPrompts } from '../mock/sessions';
import type { SessionSummary } from '../types/session';
import type { TaskCardData } from '../types/ui';

export function getSessions(): SessionSummary[] {
  return mockSessions;
}

export function getTaskCards(): TaskCardData[] {
  return taskCards;
}

export function getRecommendedPrompts(): string[] {
  return recommendedPrompts;
}

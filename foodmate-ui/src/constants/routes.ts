export const ROUTES = {
  HOME: '/',
  CHAT: '/chat/:session_id?',
  ANALYSIS: '/analysis',
  PLANNING: '/planning',
  KNOWLEDGE: '/knowledge',
  LOGIN: '/login',
  PROFILE: '/profile',
  ADMIN: '/admin',
} as const;

export function buildChatPath(sessionId?: string): string {
  return sessionId ? `/chat/${sessionId}` : '/chat';
}

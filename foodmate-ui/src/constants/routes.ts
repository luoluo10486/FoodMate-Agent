export const ROUTES = {
  HOME: '/',
  CHAT: '/chat/:session_id?',
  ANALYSIS: '/analysis',
  PLANNING: '/planning',
  KNOWLEDGE: '/knowledge',
  LOGIN: '/login',
  PROFILE: '/profile',
  PROFILE_MEMORIES: '/profile/memories',
  PROFILE_SECURITY: '/profile/security',
  PROFILE_DATA: '/profile/data',
  ADMIN: '/admin',
} as const;

export function buildChatPath(sessionId?: string): string {
  return sessionId ? `/chat/${sessionId}` : '/chat';
}

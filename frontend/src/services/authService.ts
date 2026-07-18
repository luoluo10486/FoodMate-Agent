import { mockAuthStatus, mockAuthUser, mockLoginDefaults, mockAuthScenarios } from '../mock/auth';
import type { AuthUser, LoginFormValues } from '../mock/auth';

export type AuthStatus = 'anonymous' | 'authenticated' | 'expired' | 'disabled' | 'forbidden';

type AuthResponse = { access_token: string; username: string; role: string; user_id: number };
type ApiResponse<T> = { success: boolean; data: T; error?: { message: string } };
const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
const tokenKey = 'foodmate.access-token';

export function accessToken(): string | undefined {
  return sessionStorage.getItem(tokenKey) ?? undefined;
}
export function getAuthStatus(): AuthStatus {
  return accessToken() ? 'authenticated' : mockAuthStatus;
}
export function getAuthUser(): AuthUser {
  return mockAuthUser;
}
export function getLoginDefaults(): LoginFormValues {
  return mockLoginDefaults;
}
export function getAuthScenarios() {
  return mockAuthScenarios;
}

export async function login(credentials: LoginFormValues): Promise<AuthUser> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockAuthUser;
  const response = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username_or_email: credentials.username, password: credentials.password }),
  });
  const body = (await response.json()) as ApiResponse<AuthResponse>;
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? '登录失败');
  sessionStorage.setItem(tokenKey, body.data.access_token);
  return {
    ...mockAuthUser,
    id: String(body.data.user_id),
    username: body.data.username,
    displayName: body.data.username,
    role: body.data.role as AuthUser['role'],
  };
}

export async function logout(): Promise<void> {
  const token = accessToken();
  if (import.meta.env.VITE_AGENT_MODE === 'real')
    await fetch(`${baseUrl}/api/auth/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
  sessionStorage.removeItem(tokenKey);
}

import { mockAuthStatus, mockAuthUser, mockLoginDefaults, mockAuthScenarios } from '../mock/auth';
import type { AuthUser, LoginFormValues } from '../mock/auth';

export type AuthStatus = 'anonymous' | 'authenticated' | 'expired' | 'disabled' | 'forbidden';

type ApiResponse<T> = { success: boolean; data: T; error?: { message: string } };
const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

type AuthResponse = { username: string; role: string; user_id: number; session_expires_at: string };

export function csrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find((value) => value.startsWith('foodmate_csrf='))
    ?.split('=')[1];
}
export function getAuthStatus(): AuthStatus {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockAuthStatus;
  return localStorage.getItem('foodmate_auth_user') ? 'authenticated' : 'anonymous';
}
export function getAuthUser(): AuthUser {
  if (import.meta.env.VITE_AGENT_MODE === 'real') {
    const saved = localStorage.getItem('foodmate_auth_user');
    if (saved) return JSON.parse(saved) as AuthUser;
  }
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
  const user = {
    ...mockAuthUser,
    id: String(body.data.user_id),
    username: body.data.username,
    displayName: body.data.username,
    role: body.data.role as AuthUser['role'],
  };
  localStorage.setItem('foodmate_auth_user', JSON.stringify(user));
  return user;
}

export async function logout(): Promise<void> {
  if (import.meta.env.VITE_AGENT_MODE === 'real')
    await fetch(`${baseUrl}/api/auth/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: csrfToken() ? { 'X-CSRF-Token': csrfToken()! } : {},
    });
  localStorage.removeItem('foodmate_auth_user');
}

export async function requestPasswordReset(email: string): Promise<void> {
  const response = await fetch(`${baseUrl}/api/auth/password-reset/request`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email }) });
  const body = (await response.json()) as ApiResponse<unknown>;
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? '密码重置请求失败');
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  const response = await fetch(`${baseUrl}/api/auth/password-reset/confirm`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, new_password: newPassword }) });
  const body = (await response.json()) as ApiResponse<unknown>;
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? '密码重置失败');
}

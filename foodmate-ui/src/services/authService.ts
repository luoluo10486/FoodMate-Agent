import { mockAuthStatus, mockAuthUser, mockLoginDefaults, mockAuthScenarios } from '../mock/auth';
import type { AuthUser, LoginFormValues } from '../mock/auth';
import { apiRequest } from './apiClient';

export type AuthStatus = 'anonymous' | 'authenticated' | 'expired' | 'disabled' | 'forbidden';
type AuthResponse = { username: string; role: string; user_id: number; session_expires_at: string };
type CurrentUserResponse = {
  user_id: number;
  username: string;
  email: string;
  nickname?: string;
  role: string;
  status: AuthUser['status'];
};

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

function toAuthUser(data: AuthResponse | CurrentUserResponse): AuthUser {
  return {
    ...mockAuthUser,
    id: String(data.user_id),
    username: data.username,
    displayName: ('nickname' in data && data.nickname) || data.username,
    email: 'email' in data ? data.email : mockAuthUser.email,
    role: data.role as AuthUser['role'],
    status: 'status' in data ? data.status : 'active',
  };
}

export async function loadCurrentUser(): Promise<AuthUser> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockAuthUser;
  const user = toAuthUser(await apiRequest<CurrentUserResponse>('/api/users/me'));
  localStorage.setItem('foodmate_auth_user', JSON.stringify(user));
  return user;
}

export function getLoginDefaults(): LoginFormValues {
  return mockLoginDefaults;
}
export function getAuthScenarios() {
  return mockAuthScenarios;
}

export async function login(credentials: LoginFormValues): Promise<AuthUser> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockAuthUser;
  const data = await apiRequest<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username_or_email: credentials.username, password: credentials.password }),
  });
  const user = toAuthUser(data);
  localStorage.setItem('foodmate_auth_user', JSON.stringify(user));
  return user;
}

export async function register(credentials: { username: string; email: string; password: string }): Promise<AuthUser> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockAuthUser;
  const data = await apiRequest<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(credentials),
  });
  const user = toAuthUser(data);
  localStorage.setItem('foodmate_auth_user', JSON.stringify(user));
  return user;
}

export async function logout(): Promise<void> {
  if (import.meta.env.VITE_AGENT_MODE === 'real') await apiRequest<void>('/api/auth/logout', { method: 'POST' });
  localStorage.removeItem('foodmate_auth_user');
}

export async function requestPasswordReset(email: string): Promise<void> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return;
  await apiRequest<void>('/api/auth/password-reset/request', { method: 'POST', body: JSON.stringify({ email }) });
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return;
  await apiRequest<void>('/api/auth/password-reset/confirm', {
    method: 'POST',
    body: JSON.stringify({ token, new_password: newPassword }),
  });
}

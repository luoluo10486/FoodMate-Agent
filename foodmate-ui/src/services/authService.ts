import { mockAuthStatus, mockAuthUser, mockLoginDefaults, mockAuthScenarios } from '../mock/auth';
import type { AuthUser, LoginFormValues } from '../mock/auth';
import { resolvePersistedAvatarUrl } from '../lib/avatar';
import { apiRequest } from './apiClient';

export type AuthStatus = 'anonymous' | 'authenticated' | 'expired' | 'disabled' | 'locked' | 'forbidden';
type AuthResponse = {
  username: string;
  role: string;
  user_id: number;
  session_expires_at: string;
  gender?: string;
};
type CurrentUserResponse = {
  user_id: number;
  username: string;
  email: string;
  nickname?: string;
  role: string;
  status: AuthUser['status'];
  gender?: string;
  avatar_url?: string;
};

const AUTH_USER_STORAGE_KEY = 'foodmate_auth_user';

function notifyAuthChanged() {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('foodmate:auth-changed'));
}

function persistAuthUser(user: AuthUser, notify = true) {
  localStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(user));
  if (notify) notifyAuthChanged();
}

export function csrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find((value) => value.startsWith('foodmate_csrf='))
    ?.split('=')[1];
}

function persistedAuthStatus(): AuthStatus {
  const saved = localStorage.getItem(AUTH_USER_STORAGE_KEY);
  if (!saved) return 'anonymous';
  try {
    const parsed: unknown = JSON.parse(saved);
    if (!parsed || typeof parsed !== 'object') return 'expired';
    const status = (parsed as { status?: unknown }).status;
    if (status === 'active') return 'authenticated';
    if (status === 'disabled') return 'disabled';
    if (status === 'locked') return 'locked';
    // 缺少明确状态的旧缓存不能作为已认证凭据使用。
    return 'expired';
  } catch {
    localStorage.removeItem(AUTH_USER_STORAGE_KEY);
    return 'anonymous';
  }
}

export function getAuthStatus(): AuthStatus {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockAuthStatus;
  return persistedAuthStatus();
}

export function getAuthUser(): AuthUser {
  if (import.meta.env.VITE_AGENT_MODE === 'real') {
    const saved = localStorage.getItem(AUTH_USER_STORAGE_KEY);
    if (saved) {
      try {
        const user = JSON.parse(saved) as AuthUser;
        // 本地缓存可能来自旧版本 Fixture，读取时也必须经过统一头像解析层。
        const normalizedUser = { ...user, avatarUrl: resolvePersistedAvatarUrl(user.avatarUrl, user.gender) };
        // 归一化后回写缓存，避免旧人物地址在后续页面切换中再次进入头像参数。
        if (normalizedUser.avatarUrl !== user.avatarUrl) persistAuthUser(normalizedUser, false);
        return normalizedUser;
      } catch {
        localStorage.removeItem(AUTH_USER_STORAGE_KEY);
      }
    }
  }
  return mockAuthUser;
}

function toAuthUser(data: AuthResponse | CurrentUserResponse): AuthUser {
  const gender = data.gender ?? mockAuthUser.gender;
  return {
    ...mockAuthUser,
    id: String(data.user_id),
    username: data.username,
    displayName: ('nickname' in data && data.nickname) || data.username,
    email: 'email' in data ? data.email : mockAuthUser.email,
    role: data.role as AuthUser['role'],
    status: 'status' in data ? data.status : 'active',
    gender,
    avatarUrl: resolvePersistedAvatarUrl('avatar_url' in data ? data.avatar_url : mockAuthUser.avatarUrl, gender),
  };
}

export async function loadCurrentUser(signal?: AbortSignal): Promise<AuthUser> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockAuthUser;
  const user = toAuthUser(await apiRequest<CurrentUserResponse>('/api/users/me', signal ? { signal } : {}));
  persistAuthUser(user);
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
  await apiRequest<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username_or_email: credentials.username, password: credentials.password }),
  });
  // 登录响应只包含认证信息，必须再读取当前用户资料，避免把 Fixture 用户资料写入真实模式。
  return loadCurrentUser();
}

export async function register(credentials: { username: string; email: string; password: string }): Promise<AuthUser> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return mockAuthUser;
  await apiRequest<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(credentials),
  });
  // 注册响应同样不包含完整资料，统一通过当前用户接口建立真实缓存。
  return loadCurrentUser();
}

export async function logout(options: { skipRemote?: boolean } = {}): Promise<void> {
  if (import.meta.env.VITE_AGENT_MODE === 'real' && !options.skipRemote)
    await apiRequest<void>('/api/auth/logout', { method: 'POST' });
  localStorage.removeItem(AUTH_USER_STORAGE_KEY);
  notifyAuthChanged();
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

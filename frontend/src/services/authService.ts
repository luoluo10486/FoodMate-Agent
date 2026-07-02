/**
 * 认证服务 — 当前转发 mock，后续替换为真实 API 调用。
 */
import { mockAuthStatus, mockAuthUser, mockLoginDefaults, mockAuthScenarios } from '../mock/auth';
import type { AuthUser, LoginFormValues } from '../mock/auth';

export type AuthStatus = 'anonymous' | 'authenticated' | 'expired' | 'disabled' | 'forbidden';

export function getAuthStatus(): AuthStatus {
  return mockAuthStatus;
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

export async function login(_credentials: LoginFormValues): Promise<AuthUser> {
  // TODO: 替换为真实 API
  return mockAuthUser;
}

export async function logout(): Promise<void> {
  // TODO: 替换为真实 API
}

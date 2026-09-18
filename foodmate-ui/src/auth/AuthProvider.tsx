import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { Button } from '../components/ui/button';
import { ApiError, isAbortError } from '../services/apiClient';
import { getAuthStatus, getAuthUser, loadCurrentUser } from '../services/authService';
import {
  AuthContext,
  isAdminRole,
  useAuth,
  type AuthContextValue,
  type AuthLoadError,
  type AuthProviderStatus,
} from './AuthContext';
import type { AuthUser } from '../mock/auth';

function statusForUser(user: AuthUser): AuthProviderStatus {
  if (user.status === 'disabled') return 'disabled';
  if (user.status === 'locked') return 'locked';
  return 'authenticated';
}

function toAuthLoadError(error: unknown): AuthLoadError {
  if (error instanceof ApiError) {
    return {
      code: error.code,
      message: error.message || '登录状态验证失败，请重试。',
      retryable: error.code === 'NETWORK_ERROR' || (error.status !== undefined && error.status >= 500),
    };
  }
  return {
    code: 'AUTH_STATE_UNAVAILABLE',
    message: error instanceof Error ? error.message : '登录状态验证失败，请重试。',
    retryable: true,
  };
}

function authRedirect(location: { pathname: string; search: string; hash: string }) {
  return `${location.pathname}${location.search}${location.hash}`;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const realMode = import.meta.env.VITE_AGENT_MODE === 'real';
  const [status, setStatus] = useState<AuthProviderStatus>(() => (realMode ? 'checking' : 'authenticated'));
  const [user, setUser] = useState<AuthUser | null>(() => (realMode ? null : getAuthUser()));
  const [error, setError] = useState<AuthLoadError>();
  const [retryNonce, setRetryNonce] = useState(0);
  const retry = useCallback(() => {
    setStatus('checking');
    setError(undefined);
    setRetryNonce((current) => current + 1);
  }, []);

  useEffect(() => {
    const syncAuthState = () => {
      const nextStatus = getAuthStatus();
      if (nextStatus === 'authenticated') {
        const nextUser = getAuthUser();
        setUser(nextUser);
        setStatus(statusForUser(nextUser));
        setError(undefined);
        return;
      }
      setUser(null);
      setStatus(nextStatus);
      setError(undefined);
    };

    window.addEventListener('foodmate:auth-changed', syncAuthState);
    return () => window.removeEventListener('foodmate:auth-changed', syncAuthState);
  }, []);

  useEffect(() => {
    if (!realMode) return;

    const controller = new AbortController();
    let active = true;

    void loadCurrentUser(controller.signal)
      .then((nextUser) => {
        if (!active || controller.signal.aborted) return;
        setUser(nextUser);
        setStatus(statusForUser(nextUser));
      })
      .catch((cause) => {
        if (!active || controller.signal.aborted || isAbortError(cause)) return;
        const apiError = cause instanceof ApiError ? cause : undefined;
        if (apiError?.code === 'AUTH_ACCOUNT_DISABLED') {
          setUser(null);
          setStatus('disabled');
          return;
        }
        if (apiError?.code === 'AUTH_ACCOUNT_LOCKED') {
          setUser(null);
          setStatus('locked');
          return;
        }
        if (apiError?.code === 'FORBIDDEN' || apiError?.status === 403) {
          setUser(null);
          setStatus('forbidden');
          return;
        }
        if (apiError?.code === 'AUTH_REQUIRED' || apiError?.status === 401) {
          setUser(null);
          setStatus('anonymous');
          return;
        }
        setUser(null);
        setStatus('unavailable');
        setError(toAuthLoadError(cause));
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [realMode, retryNonce]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      error,
      isLoading: status === 'checking',
      isAuthenticated: status === 'authenticated',
      isAdmin: status === 'authenticated' && user !== null && isAdminRole(user.role),
      retry,
    }),
    [error, retry, status, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function AuthLoadingNotice() {
  return (
    <div role="status" aria-live="polite" data-auth-gate-state="checking">
      正在验证登录状态...
    </div>
  );
}

function AuthUnavailableNotice({ error, onRetry }: { error?: AuthLoadError; onRetry: () => void }) {
  return (
    <div role="alert" data-auth-gate-state="unavailable">
      <p>{error?.message ?? '暂时无法验证登录状态。'}</p>
      {error?.retryable !== false ? (
        <Button type="button" onClick={onRetry}>
          重试
        </Button>
      ) : null}
    </div>
  );
}

function AuthStateNotice({ status, admin }: { status: AuthProviderStatus; admin: boolean }) {
  const title = admin ? '无权访问管理后台' : status === 'disabled' ? '账号已禁用' : '账号已锁定';
  const message = admin
    ? '当前账号没有进入管理后台的权限。'
    : status === 'disabled'
      ? '当前账号已被禁用，请联系管理员。'
      : '当前账号已被锁定，请稍后重试或联系管理员。';
  return (
    <div role="alert" data-auth-gate-state={admin ? 'forbidden' : status}>
      <h1>{title}</h1>
      <p>{message}</p>
      <Button asChild variant="outline">
        <Link to={admin ? '/' : '/login'}>{admin ? '返回工作台' : '返回登录'}</Link>
      </Button>
    </div>
  );
}

function AuthGate({ children, admin }: { children: ReactNode; admin: boolean }) {
  const auth = useAuth();
  const location = useLocation();

  if (auth.status === 'checking') return <AuthLoadingNotice />;
  if (auth.status === 'unavailable') return <AuthUnavailableNotice error={auth.error} onRetry={auth.retry} />;
  if (auth.status === 'disabled' || auth.status === 'locked') {
    return <AuthStateNotice status={auth.status} admin={false} />;
  }
  if (auth.status === 'forbidden' && admin) return <AuthStateNotice status={auth.status} admin />;
  if (auth.status !== 'authenticated') {
    const redirect = authRedirect(location);
    return <Navigate to={`/login?redirect=${encodeURIComponent(redirect)}`} replace />;
  }
  if (admin && !auth.isAdmin) return <AuthStateNotice status="forbidden" admin />;
  return <>{children}</>;
}

export function RequireAuth({ children }: { children: ReactNode }) {
  return <AuthGate admin={false}>{children}</AuthGate>;
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  return <AuthGate admin>{children}</AuthGate>;
}

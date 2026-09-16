import { createContext, useContext } from 'react';
import { getAuthStatus, getAuthUser, type AuthStatus } from '../services/authService';
import type { AuthUser } from '../mock/auth';

export type AuthProviderStatus = 'checking' | AuthStatus | 'unavailable';

export type AuthLoadError = {
  code: string;
  message: string;
  retryable: boolean;
};

export type AuthContextValue = {
  status: AuthProviderStatus;
  user: AuthUser | null;
  error?: AuthLoadError;
  isLoading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  retry: () => void;
};

const adminRoles = new Set(['admin', 'operator', 'superadmin']);

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function useAuthContext(): AuthContextValue | undefined {
  return useContext(AuthContext);
}

export function useAuth(): AuthContextValue {
  const context = useAuthContext();
  if (context) return context;

  const fallbackStatus = import.meta.env.VITE_AGENT_MODE === 'real' ? getAuthStatus() : 'authenticated';
  const fallbackUser = fallbackStatus === 'authenticated' ? getAuthUser() : null;
  return {
    status: fallbackStatus,
    user: fallbackUser,
    isLoading: false,
    isAuthenticated: fallbackStatus === 'authenticated',
    isAdmin: fallbackStatus === 'authenticated' && fallbackUser !== null && adminRoles.has(fallbackUser.role),
    retry: () => undefined,
  };
}

export function isAdminRole(role: string | undefined): boolean {
  return role !== undefined && adminRoles.has(role);
}

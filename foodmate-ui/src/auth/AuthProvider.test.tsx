import userEvent from '@testing-library/user-event';
import { render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../services/apiClient';
import { loadCurrentUser } from '../services/authService';
import { mockAuthUser } from '../mock/auth';
import { AuthProvider, RequireAdmin, RequireAuth } from './AuthProvider';
import { useAuth } from './AuthContext';
import { useAdminAccess } from '../pages/AdminPage/tabs/AdminShared';

vi.mock('../services/authService', async () => {
  const actual = await vi.importActual<typeof import('../services/authService')>('../services/authService');
  return { ...actual, loadCurrentUser: vi.fn() };
});

function AuthProbe() {
  const auth = useAuth();
  return (
    <output data-auth-status={auth.status} data-auth-user={auth.user?.username ?? ''}>
      {auth.status}
    </output>
  );
}

function AdminAccessProbe() {
  const access = useAdminAccess();
  return <output data-can-access={String(access.canAccess)} data-can-manage={String(access.canManage)} />;
}

function authOutput(status: string) {
  return document.querySelector(`output[data-auth-status="${status}"]`);
}

function LocationProbe() {
  const location = useLocation();
  return <output>当前路径：{location.pathname + location.search + location.hash}</output>;
}

function renderWithAuth(children: ReactNode, initialEntry = '/') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>{children}</AuthProvider>
    </MemoryRouter>,
  );
}

describe('AuthProvider', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    localStorage.clear();
  });

  it('在 fixture 模式保持已有的已登录身份，不请求真实用户接口', () => {
    renderWithAuth(<AuthProbe />);

    expect(authOutput('authenticated')).toHaveAttribute('data-auth-user', mockAuthUser.username);
    expect(loadCurrentUser).not.toHaveBeenCalled();
  });

  it('真实模式先显示检查状态，再只使用当前用户接口返回的身份', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    const realUser = { ...mockAuthUser, username: 'real-user', displayName: '真实用户', role: 'user' as const };
    vi.mocked(loadCurrentUser).mockResolvedValue(realUser);

    renderWithAuth(<AuthProbe />);

    expect(authOutput('checking')).toBeInTheDocument();
    await waitFor(() => expect(authOutput('authenticated')).toHaveAttribute('data-auth-user', 'real-user'));
    expect(loadCurrentUser).toHaveBeenCalledTimes(1);
  });

  it('未登录访问受保护页面时保留原始路径、查询参数和 hash', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.mocked(loadCurrentUser).mockRejectedValue(new ApiError('AUTH_REQUIRED', '登录已失效', 401));

    renderWithAuth(
      <Routes>
        <Route
          path="/chat"
          element={
            <RequireAuth>
              <div>受保护内容</div>
            </RequireAuth>
          }
        />
        <Route path="/login" element={<LocationProbe />} />
      </Routes>,
      '/chat?tab=records#summary',
    );

    expect(await screen.findByText('当前路径：/login?redirect=%2Fchat%3Ftab%3Drecords%23summary')).toBeInTheDocument();
    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
  });

  it('网络错误展示重试状态，重试成功后继续渲染受保护内容', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.mocked(loadCurrentUser)
      .mockRejectedValueOnce(new ApiError('NETWORK_ERROR', '网络连接失败'))
      .mockResolvedValueOnce({ ...mockAuthUser, username: 'retry-user' });

    renderWithAuth(
      <RequireAuth>
        <div>受保护内容</div>
      </RequireAuth>,
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('网络连接失败');
    await userEvent.click(screen.getByRole('button', { name: '重试' }));
    await waitFor(() => expect(screen.getByText('受保护内容')).toBeInTheDocument());
    expect(loadCurrentUser).toHaveBeenCalledTimes(2);
  });

  it('普通用户不能进入 Admin 且不会渲染管理页面', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.mocked(loadCurrentUser).mockResolvedValue({ ...mockAuthUser, role: 'user' });

    renderWithAuth(
      <Routes>
        <Route
          path="/admin/*"
          element={
            <RequireAdmin>
              <div>管理数据</div>
            </RequireAdmin>
          }
        />
      </Routes>,
      '/admin/users',
    );

    expect(await screen.findByRole('heading', { name: '无权访问管理后台' })).toBeInTheDocument();
    expect(screen.queryByText('管理数据')).not.toBeInTheDocument();
  });

  it('认证事件会同步登出后的匿名状态', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.mocked(loadCurrentUser).mockResolvedValue(mockAuthUser);

    renderWithAuth(<AuthProbe />);
    await waitFor(() => expect(authOutput('authenticated')).toBeInTheDocument());

    localStorage.removeItem('foodmate_auth_user');
    window.dispatchEvent(new Event('foodmate:auth-changed'));

    await waitFor(() => expect(authOutput('anonymous')).toBeInTheDocument());
  });

  it('认证角色变化后 Admin 权限会实时更新', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.mocked(loadCurrentUser).mockResolvedValue({ ...mockAuthUser, role: 'admin' });

    renderWithAuth(<AdminAccessProbe />);
    await waitFor(() => expect(document.querySelector('[data-can-manage="true"]')).toBeInTheDocument());

    localStorage.setItem('foodmate_auth_user', JSON.stringify({ ...mockAuthUser, role: 'operator', status: 'active' }));
    window.dispatchEvent(new Event('foodmate:auth-changed'));

    await waitFor(() => expect(document.querySelector('[data-can-manage="false"]')).toBeInTheDocument());
    expect(document.querySelector('[data-can-access="true"]')).toBeInTheDocument();
  });

  it('卸载 Provider 时取消当前用户请求', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    let signal: AbortSignal | undefined;
    vi.mocked(loadCurrentUser).mockImplementation((requestSignal) => {
      signal = requestSignal;
      return new Promise<typeof mockAuthUser>(() => undefined);
    });

    const view = renderWithAuth(<AuthProbe />);
    await waitFor(() => expect(signal).toBeDefined());

    view.unmount();

    expect(signal?.aborted).toBe(true);
  });
});

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProfilePage } from './ProfilePage';
import {
  changePassword,
  downloadDataExport,
  getAuthSessions,
  getDataExport,
  getProfile,
  requestDataExport,
  requestAccountDeletion,
  revokeAuthSession,
  updateProfile,
} from '../../services/accountService';

vi.mock('../../services/accountService', () => ({
  changePassword: vi.fn(),
  deleteAvatar: vi.fn(),
  downloadDataExport: vi.fn(),
  getAuthSessions: vi.fn(),
  getDataExport: vi.fn(),
  getProfile: vi.fn(),
  requestAccountDeletion: vi.fn(),
  requestDataExport: vi.fn(),
  revokeAllAuthSessions: vi.fn(),
  revokeAuthSession: vi.fn(),
  updateProfile: vi.fn(),
  uploadAvatar: vi.fn(),
}));

vi.mock('../../services/sessionService', () => ({
  archiveSession: vi.fn(),
  createSession: vi.fn(),
  deleteSession: vi.fn(),
  loadDeletedSessions: vi.fn().mockResolvedValue([]),
  loadSessionSummariesPage: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, size: 50 }),
  renameSession: vi.fn(),
  restoreSession: vi.fn(),
  searchSessions: vi.fn().mockResolvedValue([]),
  unarchiveSession: vi.fn(),
}));

vi.mock('../../services/memoryService', () => ({
  confirmMemory: vi.fn(),
  deleteMemory: vi.fn(),
  loadMemories: vi.fn().mockResolvedValue([]),
  updateMemory: vi.fn(),
}));

vi.mock('../../services/authService', () => ({
  getAuthScenarios: () => [
    { status: 'authenticated', title: '已登录', description: '', code: 'OK' },
    { status: 'anonymous', title: '未登录', description: '', code: 'AUTH_REQUIRED' },
  ],
  getAuthStatus: () => 'authenticated',
  getAuthUser: () => ({
    id: '7',
    username: 'real-user',
    displayName: '真实用户',
    role: 'user',
    status: 'active',
    email: 'real@example.com',
    gender: '女',
    avatarUrl: '',
    lastLoginAt: '',
    profile: {
      heightCm: 170,
      weightKg: 60,
      activityLevel: 'Moderately Active (3-5d/wk)',
      dietGoal: '维持健康',
      proteinMultiplierRange: [1.2, 1.6],
      proteinTargetRange: [72, 96],
      calorieTarget: 1800,
      proteinTarget: 84,
      preference: '',
      allergens: [],
      dislikes: [],
      preferredUnits: { weight: 'g', energy: 'kcal' },
    },
    permissions: [],
    security: { tokenStrategy: '', accessTokenTtl: '', refreshTokenMode: '' },
  }),
  loadCurrentUser: vi.fn().mockResolvedValue({}),
  logout: vi.fn(),
}));

function renderPage(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/profile/*" element={<ProfilePage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProfilePage real account states', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.mocked(getProfile).mockRejectedValue(new Error('资料接口不可用'));
    vi.mocked(getAuthSessions).mockRejectedValue(new Error('设备接口不可用'));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.clearAllMocks();
  });

  it('shows a retryable profile error instead of the fixture form', async () => {
    renderPage('/profile');

    expect(await screen.findByRole('alert')).toHaveTextContent('资料接口不可用');
    expect(screen.queryByText('March 14, 2024')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument();
  });

  it('shows a retryable session error without fixture devices', async () => {
    renderPage('/profile/security');

    expect(await screen.findByRole('alert')).toHaveTextContent('设备接口不可用');
    expect(screen.queryByText('MacBook Pro 16" · macOS')).not.toBeInTheDocument();
  });

  it('retries device loading with a fresh request and keeps the failed state empty', async () => {
    const user = userEvent.setup();
    const getAuthSessionsMock = vi.mocked(getAuthSessions);
    getAuthSessionsMock
      .mockRejectedValueOnce(new Error('设备接口不可用'))
      .mockRejectedValueOnce(new Error('设备接口再次不可用'));

    renderPage('/profile/security');

    expect(await screen.findByRole('alert')).toHaveTextContent('设备接口不可用');
    await user.click(screen.getByRole('button', { name: '重试' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('设备接口再次不可用'));
    expect(screen.queryByText('MacBook Pro 16" · macOS')).not.toBeInTheDocument();
    expect(getAuthSessionsMock).toHaveBeenCalledTimes(2);
    expect(getAuthSessionsMock.mock.calls[0][0]).not.toBe(getAuthSessionsMock.mock.calls[1][0]);
  });

  it('aborts the device request when the security tab unmounts', async () => {
    let capturedSignal: AbortSignal | undefined;
    vi.mocked(getAuthSessions).mockImplementation((signal) => {
      capturedSignal = signal;
      return new Promise(() => undefined);
    });

    const view = renderPage('/profile/security');
    await waitFor(() => expect(capturedSignal).toBeDefined());
    view.unmount();

    expect(capturedSignal?.aborted).toBe(true);
  });

  it('aborts a profile save when the basic tab unmounts', async () => {
    const user = userEvent.setup();
    let capturedSignal: AbortSignal | undefined;
    vi.mocked(getProfile).mockResolvedValue({
      user_id: 7,
      display_name: '真实用户的工作区',
      gender: '女',
      height_cm: 170,
      weight_kg: 60,
      activity_level: 'Moderately Active (3-5d/wk)',
      diet_goal: '维持健康',
      calorie_target: 1800,
      protein_target: 84,
      allergens: '[]',
      dislikes: '[]',
    });
    vi.mocked(updateProfile).mockImplementation((_profile, signal) => {
      capturedSignal = signal;
      return new Promise(() => undefined);
    });

    const view = renderPage('/profile');
    await screen.findByRole('button', { name: '保存资料' });
    await user.click(screen.getByRole('button', { name: '保存资料' }));
    await waitFor(() => expect(capturedSignal).toBeDefined());

    view.unmount();

    expect(capturedSignal?.aborted).toBe(true);
  });

  it('aborts a password update when the security tab unmounts', async () => {
    const user = userEvent.setup();
    let capturedSignal: AbortSignal | undefined;
    vi.mocked(getAuthSessions).mockResolvedValue([]);
    vi.mocked(changePassword).mockImplementation((_current, _next, signal) => {
      capturedSignal = signal;
      return new Promise(() => undefined);
    });

    const view = renderPage('/profile/security');
    await screen.findByRole('button', { name: '更新密码' });
    await user.type(screen.getByLabelText('当前密码'), 'current-password');
    await user.type(screen.getByLabelText('新密码'), 'StrongPass99!');
    await user.type(screen.getByLabelText('确认密码'), 'StrongPass99!');
    await user.click(screen.getByRole('button', { name: '更新密码' }));
    await waitFor(() => expect(capturedSignal).toBeDefined());

    view.unmount();

    expect(capturedSignal?.aborted).toBe(true);
  });

  it('aborts an individual session revocation when the security tab unmounts', async () => {
    const user = userEvent.setup();
    let capturedSignal: AbortSignal | undefined;
    vi.mocked(getAuthSessions).mockResolvedValue([
      {
        auth_session_id: 1,
        current: true,
        user_agent: '当前浏览器',
        expires_at: '2026-10-01',
      },
      {
        auth_session_id: 2,
        current: false,
        device_id: 'other-browser',
        user_agent: '其它浏览器',
        expires_at: '2026-10-01',
      },
    ]);
    vi.mocked(revokeAuthSession).mockImplementation((_id, signal) => {
      capturedSignal = signal;
      return new Promise(() => undefined);
    });

    const view = renderPage('/profile/security');
    await screen.findByText('其它浏览器');
    await user.click(screen.getByRole('button', { name: '退出登录' }));
    await user.click(screen.getByRole('button', { name: '确认退出' }));
    await waitFor(() => expect(capturedSignal).toBeDefined());

    view.unmount();

    expect(capturedSignal?.aborted).toBe(true);
  });

  it('uses the backend current flag instead of the device identifier', async () => {
    vi.mocked(getAuthSessions).mockResolvedValue([
      {
        auth_session_id: 1,
        device_id: 'browser-a',
        current: true,
        user_agent: '当前浏览器',
        expires_at: '2026-10-01',
      },
      {
        auth_session_id: 2,
        device_id: 'current',
        current: false,
        user_agent: '其它浏览器',
        expires_at: '2026-10-01',
      },
    ]);

    renderPage('/profile/security');

    expect(await screen.findByText('当前浏览器')).toBeInTheDocument();
    expect(screen.getByText('当前设备')).toBeInTheDocument();
    expect(screen.getByText('其它浏览器')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '退出登录' })).toHaveLength(1);
  });

  it('reloads sessions from the backend after revoking an individual device', async () => {
    const user = userEvent.setup();
    vi.mocked(getAuthSessions)
      .mockResolvedValueOnce([
        {
          auth_session_id: 1,
          current: true,
          user_agent: '当前浏览器',
          expires_at: '2026-10-01',
        },
        {
          auth_session_id: 2,
          current: false,
          user_agent: '其它浏览器',
          expires_at: '2026-10-01',
        },
      ])
      .mockResolvedValueOnce([
        {
          auth_session_id: 1,
          current: true,
          user_agent: '当前浏览器',
          expires_at: '2026-10-01',
        },
      ]);
    vi.mocked(revokeAuthSession).mockResolvedValue(undefined);

    renderPage('/profile/security');

    await user.click(await screen.findByRole('button', { name: '退出登录' }));
    await user.click(screen.getByRole('button', { name: '确认退出' }));

    await waitFor(() => expect(getAuthSessions).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByText('其它浏览器')).not.toBeInTheDocument());
    expect(revokeAuthSession).toHaveBeenCalledWith(2, expect.any(AbortSignal));
  });

  it('aborts export creation when the privacy tab unmounts', async () => {
    const user = userEvent.setup();
    let capturedSignal: AbortSignal | undefined;
    vi.mocked(requestDataExport).mockImplementation((signal) => {
      capturedSignal = signal;
      return new Promise(() => undefined);
    });

    const view = renderPage('/profile/data');
    await user.click(await screen.findByRole('button', { name: '创建数据导出' }));
    await waitFor(() => expect(capturedSignal).toBeDefined());

    view.unmount();

    expect(capturedSignal?.aborted).toBe(true);
  });

  it('aborts account deletion when the privacy tab unmounts', async () => {
    const user = userEvent.setup();
    let capturedSignal: AbortSignal | undefined;
    vi.mocked(requestAccountDeletion).mockImplementation((_confirmation, _password, signal) => {
      capturedSignal = signal;
      return new Promise(() => undefined);
    });

    const view = renderPage('/profile/data');
    await user.click(await screen.findByRole('button', { name: '申请注销账号' }));
    await user.type(screen.getByLabelText('当前密码'), 'current-password');
    await user.type(screen.getByPlaceholderText('DELETE_MY_ACCOUNT'), 'DELETE_MY_ACCOUNT');
    await user.click(screen.getByRole('button', { name: '确认注销' }));
    await waitFor(() => expect(capturedSignal).toBeDefined());

    view.unmount();

    expect(capturedSignal?.aborted).toBe(true);
  });

  it('ignores a profile fixture query in real mode', async () => {
    renderPage('/profile?state=security-password-success');

    expect(await screen.findByRole('alert')).toHaveTextContent('资料接口不可用');
    expect(screen.queryByText('密码已更新')).not.toBeInTheDocument();
    expect(screen.queryByText('March 14, 2024')).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('submits edited profile lists to the real API and parses JSON list responses', async () => {
    const user = userEvent.setup();
    vi.mocked(getProfile).mockResolvedValue({
      user_id: 7,
      display_name: '真实用户的工作区',
      gender: '女',
      height_cm: 170,
      weight_kg: 60,
      activity_level: 'Moderately Active (3-5d/wk)',
      diet_goal: '维持健康',
      calorie_target: 1800,
      protein_target: 84,
      allergens: '["乳糖"]',
      dislikes: '["香菜"]',
      preferred_units: '{"weight":"g","energy":"kcal"}',
    });
    vi.mocked(updateProfile).mockResolvedValue({
      user_id: 7,
      display_name: '真实用户的工作区',
      gender: '女',
      allergens: '["乳糖"]',
      dislikes: '["香菜"]',
    });

    renderPage('/profile');

    expect(await screen.findByRole('button', { name: '乳糖' })).toBeInTheDocument();
    const allergenInput = screen.getByRole('textbox', { name: '添加过敏原' });
    await user.type(allergenInput, '花生');
    await user.keyboard('{Enter}');
    await user.click(screen.getByRole('button', { name: '保存资料' }));

    await waitFor(() =>
      expect(updateProfile).toHaveBeenCalledWith(
        expect.objectContaining({ allergens: ['乳糖', '花生'], dislikes: ['香菜'] }),
        expect.any(AbortSignal),
      ),
    );
    await waitFor(() => expect(screen.queryByRole('button', { name: '花生' })).not.toBeInTheDocument());
  });

  it('真实导出只展示后端状态，不虚构进度和文件大小', async () => {
    vi.mocked(requestDataExport).mockResolvedValue({ export_job_id: 42 });
    vi.mocked(getDataExport).mockResolvedValue({ export_job_id: 42, status: 'RUNNING' });
    const intervalSpy = vi.spyOn(window, 'setInterval').mockImplementation((handler) => {
      queueMicrotask(() => {
        if (typeof handler === 'function') handler();
      });
      return 1 as unknown as ReturnType<typeof window.setInterval>;
    });

    try {
      renderPage('/profile/data');
      const exportButton = await screen.findByRole('button', { name: '创建数据导出' });
      fireEvent.click(exportButton);

      await waitFor(() => expect(requestDataExport).toHaveBeenCalledTimes(1));
      await waitFor(() => expect(getDataExport).toHaveBeenCalledWith(42, expect.any(AbortSignal)));
      expect(screen.getAllByText(/生成中/).length).toBeGreaterThan(0);
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
      expect(screen.queryByText('142 MB')).not.toBeInTheDocument();
    } finally {
      intervalSpy.mockRestore();
    }
  });

  it('marks a personal export as consumed after the one-time download succeeds', async () => {
    vi.mocked(requestDataExport).mockResolvedValue({ export_job_id: 42 });
    vi.mocked(getDataExport).mockResolvedValue({ export_job_id: 42, status: 'COMPLETED' });
    vi.mocked(downloadDataExport).mockResolvedValue({ download_url: 'https://example.com/export.zip' });
    const intervalSpy = vi.spyOn(window, 'setInterval').mockImplementation((handler) => {
      queueMicrotask(() => {
        if (typeof handler === 'function') handler();
      });
      return 1 as unknown as ReturnType<typeof window.setInterval>;
    });
    const openSpy = vi.spyOn(window, 'open').mockReturnValue(null);

    try {
      renderPage('/profile/data');
      fireEvent.click(await screen.findByRole('button', { name: '创建数据导出' }));

      const downloadButton = await screen.findByRole('button', { name: /下载归档/ });
      await userEvent.setup().click(downloadButton);

      await waitFor(() => expect(downloadDataExport).toHaveBeenCalledWith(42, expect.any(AbortSignal)));
      expect(openSpy).toHaveBeenCalledWith('https://example.com/export.zip', '_blank', 'noopener,noreferrer');
      expect(await screen.findByText('下载资格已消费')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /下载归档/ })).not.toBeInTheDocument();
    } finally {
      intervalSpy.mockRestore();
      openSpy.mockRestore();
    }
  });
});

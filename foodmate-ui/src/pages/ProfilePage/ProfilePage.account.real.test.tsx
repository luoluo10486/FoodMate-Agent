import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProfilePage } from './ProfilePage';
import { getAuthSessions, getProfile } from '../../services/accountService';

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
});

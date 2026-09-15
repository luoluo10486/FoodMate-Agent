import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProfilePage } from './ProfilePage';
import { confirmMemory, loadMemories, updateMemory } from '../../services/memoryService';

vi.mock('../../services/memoryService', () => ({
  confirmMemory: vi.fn(),
  deleteMemory: vi.fn(),
  loadMemories: vi.fn(),
  updateMemory: vi.fn(),
}));

vi.mock('../../services/authService', async () => {
  const actual = await vi.importActual<typeof import('../../services/authService')>('../../services/authService');
  return {
    ...actual,
    getAuthStatus: () => 'authenticated',
    getAuthUser: () => ({
      id: '7',
      username: 'tester',
      displayName: 'Tester',
      email: 'tester@example.com',
      role: 'user',
      status: 'active',
    }),
  };
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/profile/memories']}>
      <Routes>
        <Route path="/profile/memories" element={<ProfilePage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProfilePage real memory status', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.mocked(loadMemories).mockResolvedValue([
      {
        memory_id: 11,
        memory_type: 'preference',
        memory_value: JSON.stringify('偏好燕麦'),
        confirmation_status: 'confirmed',
        updated_at: '2026-09-06T08:00:00Z',
      },
      {
        memory_id: 12,
        memory_type: 'constraint',
        memory_value: JSON.stringify('避免花生'),
        confirmation_status: 'conflict',
        updated_at: '2026-09-06T08:01:00Z',
      },
    ]);
    vi.mocked(confirmMemory).mockResolvedValue({
      memory_id: 12,
      memory_type: 'constraint',
      memory_value: JSON.stringify('避免花生'),
      confirmation_status: 'confirmed',
      updated_at: '2026-09-06T08:02:00Z',
    });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.clearAllMocks();
  });

  it('distinguishes conflict memories and confirms them as replacements', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('存在冲突')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认并替换' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '待处理 (1)' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '已确认 (1)' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '确认并替换' }));

    await waitFor(() => expect(confirmMemory).toHaveBeenCalledWith(12, expect.any(AbortSignal)));
    expect(loadMemories).toHaveBeenCalledTimes(2);
  });

  it('clears stale memories and exposes a retry when the real read fails', async () => {
    const user = userEvent.setup();
    vi.mocked(loadMemories)
      .mockResolvedValueOnce([
        {
          memory_id: 11,
          memory_type: 'preference',
          memory_value: JSON.stringify('偏好燕麦'),
          confirmation_status: 'confirmed',
        },
      ])
      .mockRejectedValueOnce(new Error('记忆接口不可用'))
      .mockResolvedValueOnce([
        {
          memory_id: 13,
          memory_type: 'goal',
          memory_value: JSON.stringify('增加蛋白质'),
          confirmation_status: 'confirmed',
        },
      ]);

    renderPage();
    expect(await screen.findByText(/偏好燕麦/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '刷新' }));
    const memoryAlert = await screen.findByText('记忆接口不可用');
    expect(memoryAlert).toBeInTheDocument();
    expect(screen.queryByText(/偏好燕麦/)).not.toBeInTheDocument();

    const memoryErrorPanel = memoryAlert.closest('[role="alert"]');
    expect(memoryErrorPanel).not.toBeNull();
    await user.click(within(memoryErrorPanel as HTMLElement).getByRole('button', { name: '重试' }));
    expect(await screen.findByText(/增加蛋白质/)).toBeInTheDocument();
    expect(screen.queryByText('记忆接口不可用')).not.toBeInTheDocument();
  });

  it('aborts the real memory request when the tab unmounts', async () => {
    let capturedSignal: AbortSignal | undefined;
    vi.mocked(loadMemories).mockImplementation((signal) => {
      capturedSignal = signal;
      return new Promise(() => undefined);
    });

    const view = renderPage();
    await waitFor(() => expect(capturedSignal).toBeDefined());
    view.unmount();

    expect(capturedSignal?.aborted).toBe(true);
  });

  it('updates a memory and reloads the server value after editing', async () => {
    const user = userEvent.setup();
    vi.mocked(updateMemory).mockResolvedValue({
      memory_id: 11,
      memory_type: 'preference',
      memory_value: JSON.stringify({ value: '偏好黑麦' }),
      confirmation_status: 'confirmed',
      updated_at: '2026-09-06T08:03:00Z',
    });
    vi.mocked(loadMemories)
      .mockResolvedValueOnce([
        {
          memory_id: 11,
          memory_type: 'preference',
          memory_value: JSON.stringify({ value: '偏好燕麦' }),
          confirmation_status: 'confirmed',
        },
      ])
      .mockResolvedValueOnce([
        {
          memory_id: 11,
          memory_type: 'preference',
          memory_value: JSON.stringify({ value: '偏好黑麦' }),
          confirmation_status: 'confirmed',
        },
      ]);

    renderPage();
    expect(await screen.findByText(/偏好燕麦/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '编辑记忆' }));

    const editor = screen.getByRole('textbox');
    await user.clear(editor);
    await user.type(editor, '偏好黑麦');
    await user.click(screen.getByRole('button', { name: '保存记忆' }));

    await waitFor(() => expect(updateMemory).toHaveBeenCalledWith(11, '偏好黑麦', undefined, expect.any(AbortSignal)));
    expect(await screen.findByText(/偏好黑麦/)).toBeInTheDocument();
    expect(loadMemories).toHaveBeenCalledTimes(2);
  });
});

import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatPage } from './ChatPage';

const { loadSessionMessages, loadSessionSummariesPage } = vi.hoisted(() => ({
  loadSessionMessages: vi.fn(),
  loadSessionSummariesPage: vi.fn(),
}));

vi.mock('../../services/sessionService', async () => {
  const actual = await vi.importActual<typeof import('../../services/sessionService')>('../../services/sessionService');
  return { ...actual, loadSessionMessages, loadSessionSummariesPage };
});

vi.mock('../../services/authService', async () => {
  const actual = await vi.importActual<typeof import('../../services/authService')>('../../services/authService');
  return {
    ...actual,
    getAuthStatus: () => 'authenticated',
    getAuthUser: () => ({
      id: '7',
      username: 'user@foodmate.local',
      displayName: 'FoodMate 用户',
      email: 'user@foodmate.local',
      role: 'user',
      status: 'active',
    }),
    loadCurrentUser: async () => ({
      id: '7',
      username: 'user@foodmate.local',
      displayName: 'FoodMate 用户',
      email: 'user@foodmate.local',
      role: 'user',
      status: 'active',
    }),
  };
});

describe('ChatPage ChatRun 兼容入口', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    loadSessionMessages.mockReset();
    loadSessionSummariesPage.mockReset();
    loadSessionMessages.mockResolvedValue([]);
    loadSessionSummariesPage.mockResolvedValue({ items: [], total: 0, page: 1, size: 50 });
  });

  it('真实模式通过 transport 查询参数渲染 ChatRun 页面，而不是 Fixture 页面', async () => {
    render(
      <MemoryRouter initialEntries={['/chat?transport=chat-run']}>
        <Routes>
          <Route path="/chat/:session_id?" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText('暂无消息，发送第一条内容开始会话。')).toBeInTheDocument());
    expect(screen.getByText('ChatRun 兼容模式')).toHaveAttribute('data-chat-transport', 'chat-run');
  });
});

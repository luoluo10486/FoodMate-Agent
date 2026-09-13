import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatPage } from './ChatPage';

const { cancelAgentRun, confirmAgentWrite, executeAgentWrite, extendAgentRunBudget, rejectAgentWrite, retryAgentRun } =
  vi.hoisted(() => ({
    cancelAgentRun: vi.fn(),
    confirmAgentWrite: vi.fn(),
    executeAgentWrite: vi.fn(),
    extendAgentRunBudget: vi.fn(),
    rejectAgentWrite: vi.fn(),
    retryAgentRun: vi.fn(),
  }));

vi.mock('../../services/agentRunService', async () => {
  const actual = await vi.importActual<typeof import('../../services/agentRunService')>(
    '../../services/agentRunService',
  );
  return {
    ...actual,
    cancelAgentRun,
    confirmAgentWrite,
    executeAgentWrite,
    extendAgentRunBudget,
    rejectAgentWrite,
    retryAgentRun,
  };
});

vi.mock('../../services/authService', async () => {
  const actual = await vi.importActual<typeof import('../../services/authService')>('../../services/authService');
  const user = {
    id: '7',
    username: 'anddy@foodmate.local',
    displayName: 'Anddy',
    email: 'anddy@foodmate.local',
    role: 'user',
    status: 'active',
  };
  return {
    ...actual,
    getAuthStatus: () => 'authenticated',
    getAuthUser: () => user,
    loadCurrentUser: async () => user,
  };
});

vi.mock('../../services/sessionService', async () => {
  const actual = await vi.importActual<typeof import('../../services/sessionService')>('../../services/sessionService');
  return {
    ...actual,
    loadSessionSummariesPage: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, size: 50 }),
  };
});

function renderState(state: string, query = '') {
  const suffix = query ? `&${query}` : '';
  render(
    <MemoryRouter initialEntries={[`/chat?state=${state}${suffix}`]}>
      <Routes>
        <Route path="/chat/:session_id?" element={<ChatPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ChatPage Agent 状态真实动作', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    cancelAgentRun.mockReset();
    confirmAgentWrite.mockReset();
    executeAgentWrite.mockReset();
    extendAgentRunBudget.mockReset();
    rejectAgentWrite.mockReset();
    retryAgentRun.mockReset();
    cancelAgentRun.mockResolvedValue({ run_id: '42', status: 'cancel_requested', terminal: false });
    confirmAgentWrite.mockResolvedValue({ status: 'confirmed' });
    executeAgentWrite.mockResolvedValue({ approval_request_id: '8', operation: 'food_log.create', status: 'executed' });
    extendAgentRunBudget.mockResolvedValue({
      run_id: '42',
      dispatch_id: 'dispatch-2',
      attempt: 2,
      budget_revision: 2,
      status: 'queued',
    });
    rejectAgentWrite.mockResolvedValue({ status: 'rejected' });
    retryAgentRun.mockResolvedValue({ run_id: '42', dispatch_id: 'dispatch-2', attempt: 2, status: 'queued' });
  });

  it('确认写入先确认提案，再执行真实写入', async () => {
    const user = userEvent.setup();
    renderState('write-confirmation', 'approval_id=8');

    await user.click(await screen.findByRole('button', { name: '确认写入' }));

    await waitFor(() => expect(confirmAgentWrite).toHaveBeenCalledWith('8', { source: 'figma-write-confirmation' }));
    expect(executeAgentWrite).toHaveBeenCalledWith('8', { source: 'figma-write-confirmation' });
    expect(screen.getByRole('status')).toHaveTextContent('后端已返回写入状态：executed');
  });

  it('取消写入只调用拒绝接口，并展示后端返回状态', async () => {
    const user = userEvent.setup();
    renderState('write-confirmation', 'approval_id=8');

    await user.click(await screen.findByRole('button', { name: '取消' }));

    await waitFor(() => expect(rejectAgentWrite).toHaveBeenCalledWith('8', { source: 'figma-write-confirmation' }));
    expect(executeAgentWrite).not.toHaveBeenCalled();
    expect(screen.getByRole('status')).toHaveTextContent('后端已返回审批状态：rejected');
  });

  it('预算追加和结束会话使用当前 Run 的真实接口', async () => {
    const user = userEvent.setup();
    renderState('budget-limit', 'run_id=42');

    await user.click(await screen.findByRole('button', { name: '追加 20,000 tokens' }));
    await waitFor(() => expect(extendAgentRunBudget).toHaveBeenCalledWith('42', 20000, '0.15'));
    expect(await screen.findByRole('status')).toHaveTextContent('未创建新会话');

    await user.click(await screen.findByRole('button', { name: '结束会话' }));
    await waitFor(() => expect(cancelAgentRun).toHaveBeenCalledWith('42'));
  });

  it('工具重试使用专用 retry 接口，跳过步骤没有伪造成功', async () => {
    const user = userEvent.setup();
    renderState('tool-failed-retryable', 'run_id=42');

    await user.click(await screen.findByRole('button', { name: '重试' }));
    await waitFor(() => expect(retryAgentRun).toHaveBeenCalledWith('42'));
    expect(await screen.findByRole('status')).toHaveTextContent('后端已返回重试状态：queued');

    await user.click(await screen.findByRole('button', { name: '跳过此步骤' }));
    expect(await screen.findByRole('status')).toHaveTextContent('当前后端未提供跳过此步骤接口');
    expect(retryAgentRun).toHaveBeenCalledTimes(1);
  });
});

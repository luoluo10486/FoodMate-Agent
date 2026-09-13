import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AgentRunEvent, AgentStreamOptions } from '../../services/agentRunService';
import type { AgentStreamConnection, AgentStreamHandle } from '../../types/agent';
import { ChatPage } from './ChatPage';

const {
  cancelAgentRun,
  createApprovalProposal,
  confirmAgentWrite,
  executeAgentWrite,
  extendAgentRunBudget,
  loadAgentRun,
  loadApprovalProposal,
  openAgentRunStream,
  recoverAgentRun,
  recoverAgentRunFromCheckpoint,
  rejectAgentWrite,
  retryAgentRun,
} = vi.hoisted(() => ({
  cancelAgentRun: vi.fn(),
  createApprovalProposal: vi.fn(),
  confirmAgentWrite: vi.fn(),
  executeAgentWrite: vi.fn(),
  extendAgentRunBudget: vi.fn(),
  loadAgentRun: vi.fn(),
  loadApprovalProposal: vi.fn(),
  openAgentRunStream: vi.fn(),
  recoverAgentRun: vi.fn(),
  recoverAgentRunFromCheckpoint: vi.fn(),
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
    createApprovalProposal,
    confirmAgentWrite,
    executeAgentWrite,
    extendAgentRunBudget,
    loadAgentRun,
    loadApprovalProposal,
    openAgentRunStream,
    recoverAgentRun,
    recoverAgentRunFromCheckpoint,
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

let emitStreamEvent: ((eventType: string, payload: AgentRunEvent, eventId?: string) => void) | undefined;
let emitConnection: ((connection: AgentStreamConnection) => void) | undefined;

const proposalParameters = {
  meal_time: '2026-09-14',
  meal_type: '午餐',
  notes: null,
  items: [{ name: '野生三文鱼', amount: 150, unit: 'g' }],
};

function pendingApproval(status = 'pending') {
  return {
    approval_request_id: '8',
    operation: 'food_log.create',
    resource_type: 'food_log',
    resource_id: null,
    parameters_digest: 'sha256:proposal',
    status,
    expires_at: '2026-09-14T13:00:00Z',
    confirmed_at: null,
    executed_at: null,
  };
}

describe('ChatPage Agent 状态真实动作', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    cancelAgentRun.mockReset();
    createApprovalProposal.mockReset();
    confirmAgentWrite.mockReset();
    executeAgentWrite.mockReset();
    extendAgentRunBudget.mockReset();
    loadAgentRun.mockReset();
    loadApprovalProposal.mockReset();
    openAgentRunStream.mockReset();
    recoverAgentRun.mockReset();
    recoverAgentRunFromCheckpoint.mockReset();
    rejectAgentWrite.mockReset();
    retryAgentRun.mockReset();
    emitStreamEvent = undefined;
    emitConnection = undefined;

    cancelAgentRun.mockResolvedValue({ run_id: '42', status: 'cancel_requested', terminal: false });
    createApprovalProposal.mockResolvedValue({ ...pendingApproval(), approval_request_id: '9' });
    confirmAgentWrite.mockResolvedValue(pendingApproval('confirmed'));
    executeAgentWrite.mockResolvedValue({
      approval_request_id: '8',
      operation: 'food_log.create',
      status: 'executed',
      resource_id: 101,
    });
    extendAgentRunBudget.mockResolvedValue({
      run_id: '42',
      dispatch_id: 'dispatch-2',
      attempt: 2,
      budget_revision: 2,
      status: 'queued',
    });
    loadAgentRun.mockResolvedValue({ run_id: '42', status: 'running', accepted_event_count: 0 });
    loadApprovalProposal.mockResolvedValue(pendingApproval());
    recoverAgentRun.mockResolvedValue({
      run_id: '42',
      dispatch_id: 'dispatch-explicit',
      attempt: 2,
      status: 'queued',
    });
    recoverAgentRunFromCheckpoint.mockResolvedValue({
      run_id: '42',
      dispatch_id: 'dispatch-recovered',
      attempt: 2,
      status: 'queued',
    });
    rejectAgentWrite.mockResolvedValue(pendingApproval('rejected'));
    retryAgentRun.mockResolvedValue({ run_id: '42', dispatch_id: 'dispatch-2', attempt: 2, status: 'queued' });
    openAgentRunStream.mockImplementation(
      (
        _runId: string,
        onEvent: (eventType: string, payload: AgentRunEvent, eventId: string) => void,
        options: AgentStreamOptions = {},
      ): AgentStreamHandle => {
        emitStreamEvent = (eventType, payload, eventId = '') => onEvent(eventType, payload, eventId);
        emitConnection = options.onStateChange;
        options.onStateChange?.({ state: 'connecting', attempt: 1, maxAttempts: 5 });
        const connection: AgentStreamConnection = { state: 'connecting', attempt: 1, maxAttempts: 5 };
        return { close: vi.fn(), getConnection: () => connection };
      },
    );
  });

  it('真实 Approval 事件到达后，确认会依次调用确认和执行接口', async () => {
    const user = userEvent.setup();
    const query = new URLSearchParams({
      approval_id: '8',
      operation: 'food_log.create',
      resource_type: 'food_log',
      parameters: JSON.stringify(proposalParameters),
    }).toString();
    renderState('write-confirmation', query);

    await user.click(await screen.findByRole('button', { name: '确认并执行' }));

    await waitFor(() => expect(confirmAgentWrite).toHaveBeenCalledWith('8', proposalParameters));
    expect(executeAgentWrite).toHaveBeenCalledWith('8', proposalParameters);
    expect(await screen.findByText(/后端已返回执行状态：executed/)).toBeInTheDocument();
  });

  it('拒绝写入只调用拒绝接口，并展示后端返回状态', async () => {
    const user = userEvent.setup();
    const query = new URLSearchParams({
      approval_id: '8',
      operation: 'food_log.create',
      resource_type: 'food_log',
      parameters: JSON.stringify(proposalParameters),
    }).toString();
    renderState('write-confirmation', query);

    await user.click(await screen.findByRole('button', { name: '拒绝写入' }));

    await waitFor(() => expect(rejectAgentWrite).toHaveBeenCalledWith('8', proposalParameters));
    expect(executeAgentWrite).not.toHaveBeenCalled();
    expect(await screen.findByText(/后端已返回审批状态：rejected/)).toBeInTheDocument();
  });

  it('只有显式真实提案参数存在时才调用后端创建 Proposal', async () => {
    const user = userEvent.setup();
    const query = new URLSearchParams({
      run_id: '42',
      operation: 'food_log.create',
      resource_type: 'food_log',
      parameters: JSON.stringify(proposalParameters),
    }).toString();
    renderState('write-confirmation', query);

    await user.click(await screen.findByRole('button', { name: '创建写入提案' }));

    await waitFor(() =>
      expect(createApprovalProposal).toHaveBeenCalledWith({
        agentRunId: 42,
        operation: 'food_log.create',
        resourceType: 'food_log',
        parameters: proposalParameters,
        idempotencyKey: expect.stringContaining('real-agent-state-42'),
        resourceId: undefined,
        sessionId: undefined,
      }),
    );
    expect(await screen.findByText(/提案已创建：9/)).toBeInTheDocument();
  });

  it('预算事件返回真实额度后追加预算，并可结束当前 Run', async () => {
    const user = userEvent.setup();
    renderState('budget-limit', 'run_id=42');
    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalledWith('42', expect.any(Function), expect.anything()));

    await act(async () => {
      emitStreamEvent?.(
        'run.model_usage',
        {
          event_type: 'run.model_usage',
          usage: { tokens: 50000, cost_cny: '0.32' },
          budget: { max_tokens: 50000, max_cost: '0.5' },
          budget_actions: { additional_tokens: 20000, additional_cost_cny: '0.15' },
        } as unknown as AgentRunEvent,
        'budget-event',
      );
    });

    await user.click(await screen.findByRole('button', { name: '追加 20,000 tokens' }));
    await waitFor(() => expect(extendAgentRunBudget).toHaveBeenCalledWith('42', 20000, '0.15'));
    expect(await screen.findByText(/当前 Run 已返回预算追加状态/)).toBeInTheDocument();

    await user.click(await screen.findByRole('button', { name: '结束当前 Run' }));
    await waitFor(() => expect(cancelAgentRun).toHaveBeenCalledWith('42'));
  });

  it('真实状态页取消时先关闭旧 SSE，再使用原游标续接当前 Run', async () => {
    const user = userEvent.setup();
    const firstClose = vi.fn();
    let streamCount = 0;
    openAgentRunStream.mockImplementation(
      (
        _runId: string,
        _onEvent: (eventType: string, payload: AgentRunEvent, eventId?: string) => void,
        options: AgentStreamOptions = {},
      ): AgentStreamHandle => {
        streamCount += 1;
        const connection: AgentStreamConnection = {
          state: 'connected',
          attempt: streamCount,
          maxAttempts: 5,
          lastEventId: 'event-17',
        };
        options.onStateChange?.(connection);
        return {
          close: streamCount === 1 ? firstClose : vi.fn(),
          getConnection: () => connection,
        };
      },
    );
    renderState('user-cancelled', 'run_id=42');

    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalledTimes(1));
    await user.click(await screen.findByRole('button', { name: '停止生成' }));

    await waitFor(() => expect(cancelAgentRun).toHaveBeenCalledWith('42'));
    // React effect 清理和主动切换都可能调用同一个幂等关闭句柄，验证连接确实已关闭即可。
    expect(firstClose).toHaveBeenCalled();
    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalledTimes(2));
    expect(openAgentRunStream.mock.calls[1][2]).toMatchObject({ lastEventId: 'event-17' });
  });

  it('只有后端明确标记 retryable=true 时显示重试，不显示虚构的跳过接口', async () => {
    const user = userEvent.setup();
    renderState('tool-failed-retryable', 'run_id=42');
    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalledWith('42', expect.any(Function), expect.anything()));

    await act(async () => {
      emitStreamEvent?.(
        'run.failed',
        { event_type: 'run.failed', code: 'TOOL_TIMEOUT_001', retryable: true } as AgentRunEvent,
        'failed-event',
      );
    });

    expect(screen.queryByRole('button', { name: '跳过此步骤' })).not.toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: '重试当前 Run' }));
    await waitFor(() => expect(retryAgentRun).toHaveBeenCalledWith('42'));
    expect(await screen.findByText(/后端已返回重试状态：queued/)).toBeInTheDocument();
  });

  it('真实状态页缺少标识时不渲染 Fixture，也不调用真实写入接口', async () => {
    renderState('write-confirmation');

    expect(await screen.findByText('真实状态页缺少运行标识')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '确认写入' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '确认并执行' })).not.toBeInTheDocument();
    expect(createApprovalProposal).not.toHaveBeenCalled();
    expect(confirmAgentWrite).not.toHaveBeenCalled();
    expect(executeAgentWrite).not.toHaveBeenCalled();
  });

  it('checkpoint 事件显示服务端恢复入口，并调用持久化 checkpoint 接口', async () => {
    const user = userEvent.setup();
    renderState('safety-degraded', 'run_id=42');
    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalledWith('42', expect.any(Function), expect.anything()));

    await act(async () => {
      emitStreamEvent?.(
        'run.checkpoint_saved',
        {
          event_type: 'run.checkpoint_saved',
          current_node: 'tool_wait',
        },
        'checkpoint-event',
      );
    });

    await user.click(await screen.findByRole('button', { name: '从 checkpoint 恢复' }));
    await waitFor(() => expect(recoverAgentRunFromCheckpoint).toHaveBeenCalledWith('42'));
    expect(await screen.findByText(/后端已返回恢复状态：queued/)).toBeInTheDocument();
  });

  it('checkpoint 元数据完整时提交显式恢复请求，不提交 checkpoint 内容', async () => {
    const user = userEvent.setup();
    renderState(
      'sse-reconnecting',
      `run_id=42&checkpoint_version=4&checkpoint_digest=${encodeURIComponent('sha256:checkpoint')}&completed_invocation_ids=${encodeURIComponent(JSON.stringify(['inv-1']))}`,
    );

    await user.click(await screen.findByRole('button', { name: '提交恢复请求' }));

    await waitFor(() =>
      expect(recoverAgentRun).toHaveBeenCalledWith('42', {
        checkpointVersion: 4,
        checkpointDigest: 'sha256:checkpoint',
        completedInvocationIds: ['inv-1'],
      }),
    );
    expect(recoverAgentRunFromCheckpoint).not.toHaveBeenCalled();
    expect(await screen.findByText(/后端已返回恢复状态：queued/)).toBeInTheDocument();
  });

  it('缺少 checkpoint 元数据时不提交虚构的显式恢复请求', async () => {
    renderState('sse-reconnecting', 'run_id=42&checkpoint_version=4');

    expect(screen.queryByRole('button', { name: '提交恢复请求' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '从 checkpoint 恢复' })).not.toBeInTheDocument();
    expect(recoverAgentRun).not.toHaveBeenCalled();
    expect(recoverAgentRunFromCheckpoint).not.toHaveBeenCalled();
  });

  it('SSE 重连状态显示次数和游标', async () => {
    renderState('sse-reconnecting', 'run_id=42');
    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalledWith('42', expect.any(Function), expect.anything()));

    await act(async () => {
      emitConnection?.({ state: 'reconnecting', attempt: 2, maxAttempts: 5, lastEventId: 'event-17' });
    });

    expect(await screen.findByText('连接已中断，正在重新连接...')).toBeInTheDocument();
    expect(screen.getByText('第 2 / 5 次尝试 · 最近事件 event-17')).toBeInTheDocument();
  });
});

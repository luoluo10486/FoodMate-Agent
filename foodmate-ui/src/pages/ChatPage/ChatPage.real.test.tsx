import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../services/apiClient';
import { ChatPage } from './ChatPage';

const {
  cancelAgentRun,
  createSession,
  deleteMessage,
  extendAgentRunBudget,
  loadSessionMessages,
  loadSessionSummariesPage,
  openAgentRunStream,
  retryAgentRun,
  sendUserMessage,
  updateMessage,
} = vi.hoisted(() => ({
  cancelAgentRun: vi.fn(),
  createSession: vi.fn(),
  deleteMessage: vi.fn(),
  extendAgentRunBudget: vi.fn(),
  loadSessionMessages: vi.fn(),
  loadSessionSummariesPage: vi.fn(),
  loadSessionsPage: vi.fn(),
  openAgentRunStream: vi.fn(),
  retryAgentRun: vi.fn(),
  sendUserMessage: vi.fn(),
  updateMessage: vi.fn(),
}));

vi.mock('../../services/sessionService', async () => {
  const actual = await vi.importActual<typeof import('../../services/sessionService')>('../../services/sessionService');
  return {
    ...actual,
    createSession,
    deleteMessage,
    loadSessionMessages,
    loadSessionSummariesPage,
    sendUserMessage,
    updateMessage,
  };
});

vi.mock('../../services/agentRunService', async () => {
  const actual = await vi.importActual<typeof import('../../services/agentRunService')>(
    '../../services/agentRunService',
  );
  return { ...actual, cancelAgentRun, extendAgentRunBudget, openAgentRunStream, retryAgentRun };
});

vi.mock('../../services/authService', async () => {
  const actual = await vi.importActual<typeof import('../../services/authService')>('../../services/authService');
  return {
    ...actual,
    getAuthStatus: () => 'authenticated',
    getAuthUser: () => ({
      id: '7',
      username: 'admin@foodmate.local',
      displayName: '管理员',
      email: 'admin@foodmate.local',
      role: 'admin',
      status: 'active',
    }),
    loadCurrentUser: async () => ({
      id: '7',
      username: 'admin@foodmate.local',
      displayName: '管理员',
      email: 'admin@foodmate.local',
      role: 'admin',
      status: 'active',
    }),
  };
});

describe('ChatPage 真实历史会话回放', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    cancelAgentRun.mockReset();
    deleteMessage.mockReset();
    extendAgentRunBudget.mockReset();
    loadSessionMessages.mockReset();
    loadSessionSummariesPage.mockReset();
    openAgentRunStream.mockReset();
    retryAgentRun.mockReset();
    sendUserMessage.mockReset();
    updateMessage.mockReset();
    loadSessionSummariesPage.mockResolvedValue({ items: [], total: 0, page: 1, size: 50 });
    openAgentRunStream.mockImplementation(
      (_runId: string, onEvent: (type: string, payload: unknown, eventId?: string) => void) => {
        onEvent('run.completed', {
          event_type: 'run.completed',
          answer: '基于已发布公共知识库完成回答。',
          citations: [
            {
              citation_id: 'citation-1',
              document_id: 'document-1',
              title: '公共营养指南',
              version: 'v1',
              section_path: '健康饮食',
              snippet: '优先选择多样化且少加工的食物。',
            },
          ],
        });
        return { close: vi.fn(), getConnection: () => ({ state: 'closed', attempt: 1, maxAttempts: 5 }) };
      },
    );
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('新会话先提交首条消息再切换路由，并从服务端恢复真实 Run', async () => {
    const user = userEvent.setup();
    const saved = {
      message_id: 'message-new',
      session_id: 'session-new',
      agent_run_id: 'run-new',
      role: 'user' as const,
      content: '分析我的午餐',
      sequence_no: 1,
      created_at: '2026-09-14T10:00:00Z',
    };
    createSession.mockResolvedValue({
      session_id: 'session-new',
      title: '分析我的午餐',
      mode: 'chat',
      status: 'active',
    });
    sendUserMessage.mockResolvedValue(saved);
    loadSessionMessages.mockResolvedValue([saved]);

    render(
      <MemoryRouter initialEntries={['/chat']}>
        <Routes>
          <Route path="/chat/:session_id?" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    const composer = await screen.findByPlaceholderText('追问或添加自定义指令...');
    await user.type(composer, '分析我的午餐');
    await user.click(screen.getByRole('button', { name: '发送消息' }));

    await waitFor(() => expect(createSession).toHaveBeenCalledWith('分析我的午餐', expect.any(AbortSignal)));
    await waitFor(() =>
      expect(sendUserMessage).toHaveBeenCalledWith('session-new', '分析我的午餐', expect.any(AbortSignal)),
    );
    expect(createSession.mock.invocationCallOrder[0]).toBeLessThan(sendUserMessage.mock.invocationCallOrder[0]);
    await waitFor(() =>
      expect(openAgentRunStream).toHaveBeenCalledWith('run-new', expect.any(Function), expect.anything()),
    );
    expect(screen.getByPlaceholderText('追问或添加自定义指令...')).toHaveValue('');
  });

  it('新会话首条消息失败时保留错误和输入，并允许在同一会话重试', async () => {
    const user = userEvent.setup();
    const saved = {
      message_id: 'message-retry',
      session_id: 'session-retry',
      agent_run_id: 'run-retry',
      role: 'user' as const,
      content: '重试我的午餐分析',
      sequence_no: 1,
      created_at: '2026-09-14T10:01:00Z',
    };
    createSession.mockResolvedValue({
      session_id: 'session-retry',
      title: '重试我的午餐分析',
      mode: 'chat',
      status: 'active',
    });
    sendUserMessage.mockRejectedValueOnce(new Error('消息服务暂时不可用')).mockResolvedValueOnce(saved);
    loadSessionMessages.mockResolvedValue([]);

    render(
      <MemoryRouter initialEntries={['/chat']}>
        <Routes>
          <Route path="/chat/:session_id?" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    const composer = await screen.findByPlaceholderText('追问或添加自定义指令...');
    await user.type(composer, '重试我的午餐分析');
    await user.click(screen.getByRole('button', { name: '发送消息' }));

    expect(await screen.findByText('消息服务暂时不可用')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('追问或添加自定义指令...')).toHaveValue('重试我的午餐分析');

    await user.click(screen.getByRole('button', { name: '发送消息' }));
    await waitFor(() => expect(sendUserMessage).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(openAgentRunStream).toHaveBeenCalledWith('run-retry', expect.any(Function), expect.anything()),
    );
  });

  it('真实模式不会把辅助 Fixture 状态渲染成静态页面', async () => {
    const fixtureStates = [
      'empty',
      'planning',
      'tool-executing',
      'awaiting-clarification',
      'completed-with-citations',
      'redesign-default',
      'session-actions',
      'running-stop',
    ];

    for (const state of fixtureStates) {
      const view = render(
        <MemoryRouter initialEntries={[`/chat?state=${state}`]}>
          <Routes>
            <Route path="/chat/:session_id?" element={<ChatPage />} />
          </Routes>
        </MemoryRouter>,
      );

      await waitFor(() => expect(screen.getByPlaceholderText('追问或添加自定义指令...')).toBeInTheDocument());
      expect(screen.queryByRole('heading', { name: '开始新的对话' })).not.toBeInTheDocument();
      expect(screen.queryByText('Planning...')).not.toBeInTheDocument();
      expect(screen.queryByText('Executing Tools...')).not.toBeInTheDocument();
      expect(screen.queryByText('分析已完成，以下内容包含可追溯引用。')).not.toBeInTheDocument();
      view.unmount();
    }
  });

  it('从历史消息恢复最近 Run 并回放安全引用', async () => {
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        role: 'user',
        content: '请解释公共营养指南。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
      },
      {
        message_id: 'message-2',
        session_id: 'session-1',
        agent_run_id: 'run-1',
        role: 'assistant',
        content: '基于已发布公共知识库完成回答。',
        sequence_no: 2,
        created_at: '2026-09-06T10:00:01Z',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(openAgentRunStream).toHaveBeenCalledWith('run-1', expect.any(Function), expect.anything()),
    );
    expect(await screen.findByText('公共营养指南')).toBeInTheDocument();
    expect(screen.getByLabelText('知识库引用')).toBeInTheDocument();
    expect(screen.getAllByText('基于已发布公共知识库完成回答。')).toHaveLength(1);
    expect(screen.getByText('优先选择多样化且少加工的食物。')).not.toBeVisible();
  });

  it('只有 retryable 失败才显示重试并调用专用接口', async () => {
    const user = userEvent.setup();
    const close = vi.fn();
    let streamCount = 0;
    openAgentRunStream.mockImplementation(
      (
        _runId: string,
        onEvent: (type: string, payload: unknown, eventId: string) => void,
        options: {
          onStateChange?: (connection: {
            state: string;
            attempt: number;
            maxAttempts: number;
            lastEventId?: string;
          }) => void;
        },
      ) => {
        streamCount += 1;
        options.onStateChange?.({
          state: 'connected',
          attempt: streamCount,
          maxAttempts: 5,
          lastEventId: 'failed-event',
        });
        if (streamCount === 1) {
          onEvent(
            'run.failed',
            { event_type: 'run.failed', code: 'TOOL_RESULT_TIMEOUT', retryable: true },
            'failed-event',
          );
        }
        return {
          close,
          getConnection: () => ({ state: 'closed', attempt: streamCount, maxAttempts: 5, lastEventId: 'failed-event' }),
        };
      },
    );
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        agent_run_id: 'run-1',
        role: 'user',
        content: '请重试工具调用。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
      },
    ]);
    retryAgentRun.mockResolvedValue({ run_id: 'run-1', dispatch_id: 'dsp-retry', attempt: 2, status: 'queued' });

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    const retryButton = await screen.findByRole('button', { name: '重试' });
    await user.click(retryButton);
    await waitFor(() => expect(retryAgentRun).toHaveBeenCalledWith('run-1', expect.any(AbortSignal)));
    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalledTimes(2));
    expect(openAgentRunStream.mock.calls[1][2]).toMatchObject({ lastEventId: 'failed-event' });
  });

  it('非 retryable 失败不显示重试入口', async () => {
    openAgentRunStream.mockImplementation(
      (_runId: string, onEvent: (type: string, payload: unknown, eventId?: string) => void) => {
        onEvent('run.failed', { event_type: 'run.failed', code: 'INVALID_REQUEST', retryable: false }, 'failed-event');
        return { close: vi.fn(), getConnection: () => ({ state: 'closed', attempt: 1, maxAttempts: 5 }) };
      },
    );
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        agent_run_id: 'run-1',
        role: 'user',
        content: '请求失败。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('运行失败（错误码：INVALID_REQUEST）。');
    expect(screen.queryByRole('button', { name: '重试' })).not.toBeInTheDocument();
  });

  it('兼容 run.event 内层终态并将错误映射到真实 Chat 页面', async () => {
    openAgentRunStream.mockImplementation(
      (_runId: string, onEvent: (type: string, payload: unknown, eventId?: string) => void) => {
        onEvent(
          'run.event',
          {
            event_type: 'run.event',
            state: 'RUNNING',
            payload: { status: 'FAILED', error_message: '兼容流工具查询超时', retryable: true },
          },
          'generic-failed-event',
        );
        return { close: vi.fn(), getConnection: () => ({ state: 'closed', attempt: 1, maxAttempts: 5 }) };
      },
    );
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        role: 'user',
        content: '查询今天晚餐',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
        agent_run_id: 'run-1',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('兼容流工具查询超时')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument();
  });

  it('安全降级结果保留追问入口但隐藏完整引用', async () => {
    openAgentRunStream.mockImplementation((_runId: string, onEvent: (type: string, payload: unknown) => void) => {
      onEvent('run.completed', {
        event_type: 'run.completed',
        answer: '这是基于有限数据的安全降级回答。',
        result_type: 'safety_degraded',
        citations: [
          {
            citation_id: 'citation-1',
            document_id: 'document-1',
            title: '不应展示的完整引用',
            version: 'v1',
            snippet: '不应展示的引用片段',
          },
        ],
      });
      return { close: vi.fn(), getConnection: () => ({ state: 'closed', attempt: 1, maxAttempts: 5 }) };
    });
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        agent_run_id: 'run-1',
        role: 'user',
        content: '请分析我的饮食。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('安全降级提示')).toBeInTheDocument();
    expect(screen.getByText('这是基于有限数据的安全降级回答。')).toBeInTheDocument();
    expect(screen.queryByLabelText('知识库引用')).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText('追问或添加自定义指令...')).toBeInTheDocument();
  });

  it('真实 Chat 预算卡只使用服务端额度，并在追加后调用当前 Run 接口', async () => {
    const user = userEvent.setup();
    extendAgentRunBudget.mockResolvedValue({
      run_id: 'run-1',
      dispatch_id: 'dispatch-2',
      attempt: 2,
      budget_revision: 2,
      status: 'queued',
    });
    openAgentRunStream.mockImplementation(
      (_runId: string, onEvent: (type: string, payload: unknown, eventId?: string) => void) => {
        onEvent(
          'run.completed',
          {
            event_type: 'run.completed',
            answer: '预算已达到当前上限。',
            requires_confirmation: true,
            usage: { tokens: 50000, cost_cny: '0.32' },
            budget: { max_tokens: 50000, max_cost: '0.50' },
            budget_actions: { requires_confirmation: true, additional_tokens: 20000, additional_cost_cny: '0.15' },
          },
          'budget-event',
        );
        return { close: vi.fn(), getConnection: () => ({ state: 'closed', attempt: 1, maxAttempts: 5 }) };
      },
    );
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        agent_run_id: 'run-1',
        role: 'user',
        content: '分析全年趋势。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findAllByText('50,000')).toHaveLength(2);
    expect(screen.getByText('20,000')).toBeInTheDocument();
    expect(screen.queryByText('30,000')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '追加预算' }));
    await waitFor(() =>
      expect(extendAgentRunBudget).toHaveBeenCalledWith('run-1', 20000, '0.15', undefined, expect.any(AbortSignal)),
    );
  });

  it('流式回答处于 composing 状态而不是 validating 状态', async () => {
    openAgentRunStream.mockImplementation(
      (_runId: string, onEvent: (type: string, payload: unknown, eventId?: string) => void) => {
        onEvent('run.answer_stream', { event_type: 'run.answer_stream', text: '正在生成回答。' }, 'answer-event');
        return { close: vi.fn(), getConnection: () => ({ state: 'connected', attempt: 1, maxAttempts: 5 }) };
      },
    );
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        agent_run_id: 'run-1',
        role: 'user',
        content: '请生成分析。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    const composingLabel = await screen.findByText('Composing', { exact: false });
    const composingStep = composingLabel.closest('[role="listitem"]');
    expect(composingStep).not.toBeNull();
    expect(composingStep).toHaveTextContent('●');
    expect(composingStep).not.toHaveClass('validating');
  });

  it('根据真实运行事件展示路由意图、工具名称和执行耗时', async () => {
    openAgentRunStream.mockImplementation(
      (_runId: string, onEvent: (type: string, payload: unknown, eventId: string) => void) => {
        onEvent('run.routed', { event_type: 'run.routed', intent: 'analysis' }, 'event-1');
        onEvent(
          'run.tool_started',
          { event_type: 'run.tool_started', proposal_id: 'proposal-1', tool_name: 'database_query' },
          'event-2',
        );
        onEvent(
          'run.tool_finished',
          {
            event_type: 'run.tool_finished',
            proposal_id: 'proposal-1',
            tool_name: 'database_query',
            status: 'succeeded',
            latency_ms: 24,
          },
          'event-3',
        );
        onEvent('run.completed', { event_type: 'run.completed', answer: '分析已完成。' }, 'event-4');
        return { close: vi.fn(), getConnection: () => ({ state: 'closed', attempt: 1, maxAttempts: 5 }) };
      },
    );
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        role: 'user',
        content: '帮我分析饮食。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
        agent_run_id: 'run-1',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('饮食数据查询')).toBeInTheDocument();
    expect(screen.getByText('24ms')).toBeInTheDocument();
    expect(screen.getByText('RUN ID: run-1')).toBeInTheDocument();
  });

  it('按 meal_plan 审批类型展示计划摘要而不是饮食记录字段', async () => {
    openAgentRunStream.mockImplementation(
      (_runId: string, onEvent: (type: string, payload: unknown, eventId: string) => void) => {
        onEvent(
          'run.clarification_requested',
          {
            event_type: 'run.clarification_requested',
            approval_request_id: 'approval-1',
            operation: 'save_plan',
            resource_type: 'meal_plan',
            tool_name: 'meal_plan.save_plan',
            details: {
              resource_type: 'meal_plan',
              plan: {
                plan_name: '高蛋白工作日计划',
                people: 1,
                days: 3,
                calorie_target: 2100,
                protein_target: 140,
                budget: 120,
                allergens: ['花生'],
                dislikes: ['香菜'],
              },
            },
          },
          'event-approval',
        );
        return { close: vi.fn(), getConnection: () => ({ state: 'connected', attempt: 1, maxAttempts: 5 }) };
      },
    );
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        role: 'user',
        content: '帮我生成三天高蛋白计划。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
        agent_run_id: 'run-1',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('请确认保存餐食计划')).toBeInTheDocument();
    expect(screen.getByText('高蛋白工作日计划')).toBeInTheDocument();
    expect(screen.getByText('3 天 · 1 人')).toBeInTheDocument();
    expect(screen.queryByText('食物')).not.toBeInTheDocument();
  });

  it('卸载真实会话页面时关闭当前 SSE 订阅', async () => {
    const close = vi.fn();
    openAgentRunStream.mockImplementation((_runId: string, onEvent: (type: string, payload: unknown) => void) => {
      onEvent('run.answer_stream', { event_type: 'run.answer_stream', text: '已接收部分回答' });
      return { close, getConnection: () => ({ state: 'connected', attempt: 1, maxAttempts: 5 }) };
    });
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        role: 'user',
        content: '请继续分析。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
      },
      {
        message_id: 'message-2',
        session_id: 'session-1',
        agent_run_id: 'run-1',
        role: 'assistant',
        content: '已接收部分回答',
        sequence_no: 2,
        created_at: '2026-09-06T10:00:01Z',
      },
    ]);

    const view = render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(openAgentRunStream).toHaveBeenCalledWith('run-1', expect.any(Function), expect.anything()),
    );
    view.unmount();

    expect(close).toHaveBeenCalledTimes(1);
  });

  it('完成终态回放后不会因为刷新消息重新建立同一条 SSE 订阅', async () => {
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        role: 'user',
        content: '请继续分析。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
        agent_run_id: 'run-1',
      },
    ]);
    openAgentRunStream.mockImplementation((_runId: string, onEvent: (type: string, payload: unknown) => void) => {
      onEvent('run.completed', { event_type: 'run.completed', answer: '已完成分析。' });
      return { close: vi.fn(), getConnection: () => ({ state: 'closed', attempt: 1, maxAttempts: 5 }) };
    });

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(loadSessionMessages).toHaveBeenCalledTimes(2));
    expect(openAgentRunStream).toHaveBeenCalledTimes(1);
  });

  it('完成后刷新消息失败时显示真实错误，并在卸载后忽略迟到响应', async () => {
    loadSessionMessages
      .mockResolvedValueOnce([
        {
          message_id: 'message-1',
          session_id: 'session-1',
          role: 'user',
          content: '请继续分析。',
          sequence_no: 1,
          created_at: '2026-09-06T10:00:00Z',
          agent_run_id: 'run-1',
        },
      ])
      .mockRejectedValueOnce(new Error('消息刷新服务不可用'));
    openAgentRunStream.mockImplementation((_runId: string, onEvent: (type: string, payload: unknown) => void) => {
      onEvent('run.completed', { event_type: 'run.completed', answer: '已完成分析。' });
      return { close: vi.fn(), getConnection: () => ({ state: 'closed', attempt: 1, maxAttempts: 5 }) };
    });

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('消息刷新服务不可用')).toBeInTheDocument();
  });

  it('用户停止真实运行时保留文本，使用原游标等待取消终态', async () => {
    const user = userEvent.setup();
    const firstClose = vi.fn();
    const secondClose = vi.fn();
    const eventHandlers: Array<(type: string, payload: unknown, eventId?: string) => void> = [];
    cancelAgentRun.mockResolvedValue(undefined);
    extendAgentRunBudget.mockResolvedValue({
      run_id: 'run-1',
      dispatch_id: 'dispatch-2',
      attempt: 2,
      budget_revision: 2,
      status: 'queued',
    });
    openAgentRunStream.mockImplementation(
      (
        _runId: string,
        onEvent: (type: string, payload: unknown, eventId?: string) => void,
        options: {
          lastEventId?: string;
          onStateChange?: (connection: {
            state: string;
            attempt: number;
            maxAttempts: number;
            lastEventId?: string;
          }) => void;
        },
      ) => {
        const streamIndex = eventHandlers.length;
        eventHandlers.push(onEvent);
        const cursor = options.lastEventId ?? 'event-1';
        options.onStateChange?.({
          state: 'connected',
          attempt: streamIndex + 1,
          maxAttempts: 5,
          lastEventId: cursor,
        });
        if (streamIndex === 0) {
          onEvent('run.answer_stream', { event_type: 'run.answer_stream', text: '已接收部分回答' }, 'event-1');
        }
        return {
          close: streamIndex === 0 ? firstClose : secondClose,
          getConnection: () => ({
            state: 'connected',
            attempt: streamIndex + 1,
            maxAttempts: 5,
            lastEventId: cursor,
          }),
        };
      },
    );
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        role: 'user',
        content: '请继续分析。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
        agent_run_id: 'run-1',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByRole('button', { name: '停止生成' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '停止生成' }));

    expect(firstClose).toHaveBeenCalled();
    await waitFor(() =>
      expect(cancelAgentRun).toHaveBeenCalledWith('run-1', 'user_requested', expect.any(AbortSignal)),
    );
    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalledTimes(2));
    expect(openAgentRunStream.mock.calls[1][2]).toMatchObject({ lastEventId: 'event-1' });
    expect(screen.getByText('正在取消当前运行...')).toBeInTheDocument();
    expect(screen.getByText('已接收部分回答')).toBeInTheDocument();

    await act(async () => {
      eventHandlers[1]('run.cancel_acknowledged', { event_type: 'run.cancel_acknowledged' }, 'event-2');
    });
    expect(await screen.findByText('取消请求已确认，等待运行终态...')).toBeInTheDocument();

    await act(async () => {
      eventHandlers[1]('run.cancelled', { event_type: 'run.cancelled', reason: 'user_requested' }, 'event-3');
    });
    await waitFor(() => expect(screen.queryByText('取消请求已确认，等待运行终态...')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: '发送消息' })).toBeInTheDocument();
  });

  it('SSE 重连耗尽后展示稳定错误且不再保持生成态', async () => {
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        role: 'user',
        content: '请继续分析。',
        sequence_no: 1,
        created_at: '2026-09-06T10:00:00Z',
        agent_run_id: 'run-1',
      },
    ]);
    openAgentRunStream.mockImplementation(
      (
        _runId: string,
        _onEvent: (type: string, payload: unknown) => void,
        options: {
          onStateChange?: (connection: { state: string; attempt: number; maxAttempts: number }) => void;
          onError?: (connection: { state: string; attempt: number; maxAttempts: number }) => void;
        },
      ) => {
        const exhausted = { state: 'exhausted', attempt: 5, maxAttempts: 5 } as const;
        options.onStateChange?.(exhausted);
        options.onError?.(exhausted);
        return { close: vi.fn(), getConnection: () => exhausted };
      },
    );

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(openAgentRunStream).toHaveBeenCalled());
    const exhaustedNotice = screen.getByText('连接重试已耗尽').closest('[role="alert"]');
    expect(exhaustedNotice).not.toBeNull();
    expect(exhaustedNotice as HTMLElement).toHaveTextContent('连接重试已耗尽');
    expect(screen.getByRole('button', { name: '发送消息' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '停止生成' })).not.toBeInTheDocument();
  });

  it('真实模式可以编辑用户消息，并在服务端成功后刷新列表', async () => {
    const user = userEvent.setup();
    const original = {
      message_id: 'message-1',
      session_id: 'session-1',
      role: 'user' as const,
      content: '原始消息',
      sequence_no: 1,
      created_at: '2026-09-06T10:00:00Z',
    };
    const updated = { ...original, content: '更新后的消息' };
    loadSessionMessages.mockResolvedValueOnce([original]).mockResolvedValueOnce([updated]);
    updateMessage.mockResolvedValue(updated);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('原始消息');
    await user.click(screen.getByRole('button', { name: '编辑消息' }));
    const editor = await screen.findByRole('textbox', { name: '编辑消息内容' });
    await user.clear(editor);
    await user.type(editor, '更新后的消息');
    await user.click(screen.getByRole('button', { name: '保存消息' }));

    await waitFor(() =>
      expect(updateMessage).toHaveBeenCalledWith('session-1', 'message-1', '更新后的消息', expect.any(AbortSignal)),
    );
    await waitFor(() => expect(loadSessionMessages).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('更新后的消息')).toBeInTheDocument();
    expect(screen.queryByText('原始消息')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('消息已更新。');
  });

  it('消息编辑冲突时恢复原文并保留编辑入口', async () => {
    const user = userEvent.setup();
    const original = {
      message_id: 'message-1',
      session_id: 'session-1',
      role: 'user' as const,
      content: '原始消息',
      sequence_no: 1,
      created_at: '2026-09-06T10:00:00Z',
    };
    loadSessionMessages.mockResolvedValue([original]);
    updateMessage.mockRejectedValue(new ApiError('CONFLICT', 'revision conflict', 409));

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('原始消息');
    await user.click(screen.getByRole('button', { name: '编辑消息' }));
    const editor = await screen.findByRole('textbox', { name: '编辑消息内容' });
    await user.clear(editor);
    await user.type(editor, '本地草稿');
    await user.click(screen.getByRole('button', { name: '保存消息' }));

    await waitFor(() =>
      expect(updateMessage).toHaveBeenCalledWith('session-1', 'message-1', '本地草稿', expect.any(AbortSignal)),
    );
    expect(await screen.findByText(/这条消息已被其他操作更新/)).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '编辑消息内容' })).toHaveValue('原始消息');
    expect(screen.getByRole('button', { name: '取消编辑消息' })).toBeInTheDocument();
  });

  it('确认删除用户消息后调用真实接口并刷新列表', async () => {
    const user = userEvent.setup();
    const original = {
      message_id: 'message-1',
      session_id: 'session-1',
      role: 'user' as const,
      content: '需要删除的消息',
      sequence_no: 1,
      created_at: '2026-09-06T10:00:00Z',
    };
    loadSessionMessages.mockResolvedValueOnce([original]).mockResolvedValueOnce([]);
    deleteMessage.mockResolvedValue(undefined);

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('需要删除的消息');
    await user.click(screen.getByRole('button', { name: '删除消息' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('需要删除的消息');
    await user.click(within(dialog).getByRole('button', { name: '删除消息' }));

    await waitFor(() => expect(deleteMessage).toHaveBeenCalledWith('session-1', 'message-1', expect.any(AbortSignal)));
    await waitFor(() => expect(loadSessionMessages).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('消息已删除。')).toBeInTheDocument();
    expect(screen.queryByText('需要删除的消息')).not.toBeInTheDocument();
  });

  it('删除失败时保留消息并显示错误，不伪造删除成功', async () => {
    const user = userEvent.setup();
    const original = {
      message_id: 'message-1',
      session_id: 'session-1',
      role: 'user' as const,
      content: '不能删除的消息',
      sequence_no: 1,
      created_at: '2026-09-06T10:00:00Z',
    };
    loadSessionMessages.mockResolvedValue([original]);
    deleteMessage.mockRejectedValue(new ApiError('FORBIDDEN', 'forbidden', 403));

    render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('不能删除的消息');
    await user.click(screen.getByRole('button', { name: '删除消息' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: '删除消息' }));

    expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('当前账号无权操作这条消息');
    expect(screen.getAllByText('不能删除的消息')).toHaveLength(2);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('卸载真实会话页面时取消未完成的消息历史请求', async () => {
    let requestSignal: AbortSignal | undefined;
    loadSessionMessages.mockImplementation(
      (_sessionId: string, _params: unknown, signal?: AbortSignal) =>
        new Promise((_resolve, reject) => {
          requestSignal = signal;
          signal?.addEventListener(
            'abort',
            () => reject(Object.assign(new Error('消息读取已取消'), { name: 'AbortError' })),
            { once: true },
          );
        }),
    );

    const view = render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(requestSignal).toBeDefined());
    view.unmount();

    expect(requestSignal?.aborted).toBe(true);
  });

  it('完成终态刷新消息时传递取消信号，并在卸载时中止刷新', async () => {
    let refreshSignal: AbortSignal | undefined;
    loadSessionMessages
      .mockResolvedValueOnce([
        {
          message_id: 'message-1',
          session_id: 'session-1',
          role: 'user',
          content: '完成后刷新消息',
          sequence_no: 1,
          created_at: '2026-09-06T10:00:00Z',
          agent_run_id: 'run-1',
        },
      ])
      .mockImplementationOnce((_sessionId: string, _params: unknown, signal?: AbortSignal) => {
        refreshSignal = signal;
        return new Promise((_resolve, reject) => {
          signal?.addEventListener(
            'abort',
            () => reject(Object.assign(new Error('完成刷新已取消'), { name: 'AbortError' })),
            { once: true },
          );
        });
      });
    openAgentRunStream.mockImplementation(
      (_runId: string, onEvent: (type: string, payload: unknown, eventId?: string) => void) => {
        onEvent('run.completed', { event_type: 'run.completed', answer: '完成回答' }, 'completed-event');
        return { close: vi.fn(), getConnection: () => ({ state: 'closed', attempt: 1, maxAttempts: 5 }) };
      },
    );

    const view = render(
      <MemoryRouter initialEntries={['/chat/session-1']}>
        <Routes>
          <Route path="/chat/:session_id" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(loadSessionMessages).toHaveBeenCalledTimes(2));
    expect(refreshSignal).toBeDefined();
    view.unmount();

    expect(refreshSignal?.aborted).toBe(true);
  });

  it('卸载真实 Chat 页面时取消未完成的新会话发送请求', async () => {
    const user = userEvent.setup();
    let sendSignal: AbortSignal | undefined;
    createSession.mockImplementation(
      (_title: string, signal?: AbortSignal) =>
        new Promise((_resolve, reject) => {
          sendSignal = signal;
          signal?.addEventListener(
            'abort',
            () => reject(Object.assign(new Error('会话创建已取消'), { name: 'AbortError' })),
            { once: true },
          );
        }),
    );

    const view = render(
      <MemoryRouter initialEntries={['/chat']}>
        <Routes>
          <Route path="/chat/:session_id?" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    const composer = await screen.findByPlaceholderText('追问或添加自定义指令...');
    await user.type(composer, '取消中的新消息');
    await user.click(screen.getByRole('button', { name: '发送消息' }));
    await waitFor(() => expect(sendSignal).toBeDefined());

    view.unmount();

    expect(sendSignal?.aborted).toBe(true);
  });
});

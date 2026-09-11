import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatPage } from './ChatPage';

const { cancelAgentRun, loadSessionMessages, openAgentRunStream } = vi.hoisted(() => ({
  cancelAgentRun: vi.fn(),
  loadSessionMessages: vi.fn(),
  openAgentRunStream: vi.fn(),
}));

vi.mock('../../services/sessionService', async () => {
  const actual = await vi.importActual<typeof import('../../services/sessionService')>('../../services/sessionService');
  return { ...actual, loadSessionMessages };
});

vi.mock('../../services/agentRunService', async () => {
  const actual = await vi.importActual<typeof import('../../services/agentRunService')>(
    '../../services/agentRunService',
  );
  return { ...actual, cancelAgentRun, openAgentRunStream };
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
    loadSessionMessages.mockReset();
    openAgentRunStream.mockReset();
    openAgentRunStream.mockImplementation((_runId: string, onEvent: (type: string, payload: unknown) => void) => {
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
    });
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

  it('用户停止真实运行时先关闭 SSE，取消接口成功后再展示 cancelled 状态', async () => {
    const close = vi.fn();
    cancelAgentRun.mockResolvedValue(undefined);
    openAgentRunStream.mockImplementation(
      (
        _runId: string,
        onEvent: (type: string, payload: unknown) => void,
        options: { onStateChange?: (connection: { state: string; attempt: number; maxAttempts: number }) => void },
      ) => {
        options.onStateChange?.({ state: 'connected', attempt: 1, maxAttempts: 5 });
        onEvent('run.answer_stream', { event_type: 'run.answer_stream', text: '已接收部分回答' });
        return { close, getConnection: () => ({ state: 'connected', attempt: 1, maxAttempts: 5 }) };
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
    screen.getByRole('button', { name: '停止生成' }).click();

    expect(close).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(cancelAgentRun).toHaveBeenCalledWith('run-1'));
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
    expect(await screen.findByRole('alert')).toHaveTextContent('连接重试已耗尽');
    expect(screen.getByRole('button', { name: '发送消息' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '停止生成' })).not.toBeInTheDocument();
  });
});

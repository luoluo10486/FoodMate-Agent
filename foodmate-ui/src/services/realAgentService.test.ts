import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AgentStreamConnection, AgentStreamHandle } from '../types/agent';
import type { ChatRunEvent } from './chatApi';
import { useRealAgentReplay } from './realAgentService';

const { cancelChatRun, createChatRun, getChatRun, getChatRunEvents, loadSessionMessages, streamChatRun } = vi.hoisted(
  () => ({
    cancelChatRun: vi.fn(),
    createChatRun: vi.fn(),
    getChatRun: vi.fn(),
    getChatRunEvents: vi.fn(),
    loadSessionMessages: vi.fn(),
    streamChatRun: vi.fn(),
  }),
);

vi.mock('./chatApi', async () => {
  const actual = await vi.importActual<typeof import('./chatApi')>('./chatApi');
  return { ...actual, cancelChatRun, createChatRun, getChatRun, getChatRunEvents, streamChatRun };
});

vi.mock('./sessionService', async () => {
  const actual = await vi.importActual<typeof import('./sessionService')>('./sessionService');
  return { ...actual, loadSessionMessages };
});

type StreamSubscription = {
  onEvent: (event: ChatRunEvent) => void;
  close: ReturnType<typeof vi.fn>;
  connection: AgentStreamConnection;
};

function event(
  eventType: string,
  payload: Record<string, unknown> = {},
  eventId = eventType,
  state = 'RUNNING',
): ChatRunEvent {
  return {
    event_id: eventId,
    run_id: '42',
    event_seq: Number(eventId.replace(/\D/g, '')) || 1,
    state,
    payload,
    occurred_at: '2026-09-13T00:00:00Z',
    event_type: eventType,
    sse_event_id: eventId,
  };
}

describe('useRealAgentReplay ChatRun 兼容入口', () => {
  const subscriptions: StreamSubscription[] = [];

  beforeEach(() => {
    cancelChatRun.mockReset();
    createChatRun.mockReset();
    getChatRun.mockReset();
    getChatRunEvents.mockReset();
    loadSessionMessages.mockReset();
    streamChatRun.mockReset();
    subscriptions.length = 0;
    loadSessionMessages.mockResolvedValue([]);
    getChatRun.mockResolvedValue({ run_id: '42', status: 'DISPATCHED' });
    getChatRunEvents.mockResolvedValue([]);
    createChatRun.mockResolvedValue({
      run_id: '42',
      dispatch_id: 'dispatch-1',
      status: 'DISPATCHED',
      duplicate: false,
      session_id: 'session-1',
      user_message_id: 'message-1',
    });
    streamChatRun.mockImplementation(
      (
        _runId: string,
        onEvent: (event: ChatRunEvent) => void,
        lastEventId: string | undefined,
        options: { onStateChange?: (connection: AgentStreamConnection) => void },
      ): AgentStreamHandle => {
        const connection: AgentStreamConnection = {
          state: 'connected',
          attempt: 1,
          maxAttempts: 5,
          lastEventId,
        };
        const close = vi.fn();
        subscriptions.push({ onEvent, close, connection });
        options.onStateChange?.(connection);
        return { close, getConnection: () => connection };
      },
    );
  });

  it('创建后读取状态并通过 SSE 去重文本、识别完成终态', async () => {
    const { result } = renderHook(() => useRealAgentReplay(true, 'session-1'));

    act(() => result.current.setInput('分析本周饮食'));
    await act(async () => {
      await result.current.send();
    });
    await waitFor(() => expect(streamChatRun).toHaveBeenCalledTimes(1));

    act(() => subscriptions[0].onEvent(event('run.answer_stream', { text: '第一段' }, 'event-1')));
    act(() => subscriptions[0].onEvent(event('run.answer_stream', { text: '重复文本' }, 'event-1')));
    act(() => subscriptions[0].onEvent(event('run.completed', { answer: '完整回答' }, 'event-2', 'SUCCEEDED')));

    expect(result.current.events).toHaveLength(2);
    expect(result.current.assistantText).toBe('完整回答');
    expect(result.current.run.status).toBe('completed');
    expect(result.current.running).toBe(false);
    expect(subscriptions[0].close).not.toHaveBeenCalled();
  });

  it('从历史事件返回的 SSE 游标继续订阅，而不是使用 Runtime 事件 ID', async () => {
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'message-1',
        session_id: 'session-1',
        agent_run_id: '42',
        role: 'user',
        content: '继续分析',
        sequence_no: 1,
        created_at: '2026-09-16T00:00:00Z',
      },
    ]);
    getChatRun.mockResolvedValue({ run_id: '42', status: 'RUNNING' });
    getChatRunEvents.mockResolvedValue([
      {
        event_id: 'runtime-event-7',
        sse_event_id: 'sse-19',
        run_id: '42',
        event_seq: 7,
        state: 'RUNNING',
        payload: {},
        occurred_at: '2026-09-16T00:00:01Z',
        event_type: 'run.planned',
      },
    ]);

    renderHook(() => useRealAgentReplay(true, 'session-1'));

    await waitFor(() => expect(streamChatRun).toHaveBeenCalledTimes(1));
    expect(streamChatRun.mock.calls[0][2]).toBe('sse-19');
  });

  it('将兼容 run.event 的外层终态映射为失败并保留可重试错误', async () => {
    const { result } = renderHook(() => useRealAgentReplay(true, 'session-1'));

    act(() => result.current.setInput('查询晚餐'));
    await act(async () => {
      await result.current.send();
    });
    await waitFor(() => expect(streamChatRun).toHaveBeenCalledTimes(1));

    act(() =>
      subscriptions[0].onEvent(
        event('run.event', { error_message: '工具查询超时', retryable: true }, 'failed-event', 'FAILED'),
      ),
    );

    expect(result.current.run.status).toBe('failed');
    expect(result.current.running).toBe(false);
    expect(result.current.error).toBe('工具查询超时');
    expect(result.current.card).toEqual({ type: 'error', message: '工具查询超时' });
  });

  it('取消请求接受后仍等待取消事件，并保留已经接收的文本', async () => {
    cancelChatRun.mockResolvedValue({ run_id: '42', status: 'accepted', terminal: false });
    const { result } = renderHook(() => useRealAgentReplay(true, 'session-1'));

    act(() => result.current.setInput('继续分析'));
    await act(async () => {
      await result.current.send();
    });
    await waitFor(() => expect(subscriptions).toHaveLength(1));
    act(() => subscriptions[0].onEvent(event('run.answer_stream', { text: '已接收部分回答' }, 'event-1')));

    act(() => result.current.stop());
    await waitFor(() => expect(cancelChatRun).toHaveBeenCalledWith('42', expect.any(AbortSignal)));
    await waitFor(() => expect(subscriptions).toHaveLength(2));

    expect(result.current.cancelling).toBe(true);
    expect(result.current.running).toBe(false);
    expect(subscriptions[1].connection.lastEventId).toBe('event-1');
    act(() => subscriptions[1].onEvent(event('run.cancel_acknowledged', {}, 'event-2')));
    expect(result.current.cancelling).toBe(true);
    expect(result.current.cancelAcknowledged).toBe(true);
    act(() => subscriptions[1].onEvent(event('run.cancelled', {}, 'event-3', 'CANCELED')));

    expect(result.current.assistantText).toBe('已接收部分回答');
    expect(result.current.cancelling).toBe(false);
    expect(result.current.run.status).toBe('cancelled');
  });

  it('连接耗尽后停止生成并允许从最近游标手动重连', async () => {
    let streamCount = 0;
    streamChatRun.mockImplementation(
      (
        _runId: string,
        _onEvent: (event: ChatRunEvent) => void,
        lastEventId: string | undefined,
        options: { onStateChange?: (connection: AgentStreamConnection) => void },
      ): AgentStreamHandle => {
        streamCount += 1;
        const connection: AgentStreamConnection = {
          state: streamCount === 1 ? 'exhausted' : 'connected',
          attempt: streamCount === 1 ? 5 : 1,
          maxAttempts: 5,
          lastEventId,
        };
        const close = vi.fn();
        subscriptions.push({ onEvent: _onEvent, close, connection });
        options.onStateChange?.(connection);
        return { close, getConnection: () => connection };
      },
    );
    const { result } = renderHook(() => useRealAgentReplay(true, 'session-1'));

    act(() => result.current.setInput('检查连接'));
    await act(async () => {
      await result.current.send();
    });
    await waitFor(() => expect(result.current.run.connection?.state).toBe('exhausted'));
    expect(result.current.running).toBe(false);
    expect(result.current.error).toContain('重试已耗尽');

    act(() => result.current.reconnect());
    await waitFor(() => expect(streamChatRun).toHaveBeenCalledTimes(2));
    expect(result.current.run.connection?.state).toBe('connected');
  });

  it('旧 Run 已有回答时，新 Run 仍能接收未持久化的流式回答', async () => {
    loadSessionMessages.mockResolvedValue([
      {
        message_id: 'old-user',
        session_id: 'session-1',
        agent_run_id: '41',
        role: 'user',
        content: '上一轮问题',
        sequence_no: 1,
        created_at: '2026-09-15T10:00:00Z',
      },
      {
        message_id: 'old-assistant',
        session_id: 'session-1',
        agent_run_id: '41',
        role: 'assistant',
        content: '上一轮已保存回答',
        sequence_no: 2,
        created_at: '2026-09-15T10:00:01Z',
      },
      {
        message_id: 'new-user',
        session_id: 'session-1',
        agent_run_id: '42',
        role: 'user',
        content: '新一轮问题',
        sequence_no: 3,
        created_at: '2026-09-15T10:01:00Z',
      },
    ]);

    const { result } = renderHook(() => useRealAgentReplay(true, 'session-1'));

    await waitFor(() =>
      expect(streamChatRun).toHaveBeenCalledWith('42', expect.any(Function), undefined, expect.anything()),
    );
    act(() => subscriptions[0].onEvent(event('run.answer_stream', { text: '新一轮流式回答' }, 'event-42')));

    expect(result.current.activeRunId).toBe('42');
    expect(result.current.assistantText).toBe('新一轮流式回答');
  });

  it('卸载时取消 ChatRun 的历史和状态请求', async () => {
    const messageSignals: AbortSignal[] = [];
    const statusSignals: AbortSignal[] = [];
    const eventSignals: AbortSignal[] = [];
    loadSessionMessages.mockImplementation((_sessionId: string, _params: unknown, signal?: AbortSignal) => {
      if (signal) messageSignals.push(signal);
      return Promise.resolve([
        {
          message_id: 'message-1',
          role: 'assistant',
          content: '已保存的回答',
          created_at: '2026-09-15T00:00:00Z',
          sequence_no: 1,
          agent_run_id: '42',
        },
      ]);
    });
    getChatRun.mockImplementation((_runId: string, signal?: AbortSignal) => {
      if (signal) statusSignals.push(signal);
      return new Promise(() => undefined);
    });
    getChatRunEvents.mockImplementation((_runId: string, signal?: AbortSignal) => {
      if (signal) eventSignals.push(signal);
      return new Promise(() => undefined);
    });

    const { unmount } = renderHook(() => useRealAgentReplay(true, 'session-1'));
    await waitFor(() => expect(statusSignals).toHaveLength(1));

    unmount();

    expect(messageSignals[0]?.aborted).toBe(true);
    expect(statusSignals[0]?.aborted).toBe(true);
    expect(eventSignals[0]?.aborted).toBe(true);
  });

  it('切换会话时关闭旧 SSE 并忽略迟到事件', async () => {
    loadSessionMessages.mockImplementation((sessionId: string) =>
      Promise.resolve(
        sessionId === 'session-1'
          ? [
              {
                message_id: 'message-1',
                role: 'user',
                content: '旧会话消息',
                created_at: '2026-09-15T00:00:00Z',
                sequence_no: 1,
                agent_run_id: '42',
              },
            ]
          : [],
      ),
    );

    const view = renderHook(({ sessionId }: { sessionId: string }) => useRealAgentReplay(true, sessionId), {
      initialProps: { sessionId: 'session-1' },
    });
    await waitFor(() => expect(subscriptions).toHaveLength(1));

    const oldSubscription = subscriptions[0];
    act(() => oldSubscription.onEvent(event('run.answer_stream', { text: '旧会话回答' }, 'old-event')));
    expect(view.result.current.assistantText).toBe('旧会话回答');

    view.rerender({ sessionId: 'session-2' });
    await waitFor(() => expect(oldSubscription.close).toHaveBeenCalled());

    act(() => oldSubscription.onEvent(event('run.answer_stream', { text: '迟到回答' }, 'late-event')));
    expect(view.result.current.assistantText).toBe('');
    expect(view.result.current.activeRunId).toBeUndefined();

    view.unmount();
  });
});

import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  cancelChatRun,
  createChatRun,
  getChatRun,
  getChatRunEvents,
  streamChatRun,
  type ChatRunEvent,
} from './chatApi';

function ok(data: unknown) {
  return new Response(JSON.stringify({ success: true, data }), { status: 200 });
}

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  readonly listeners = new Map<string, Array<(event: Event) => void>>();
  readonly url: string;
  closed = false;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: Event) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close() {
    this.closed = true;
  }

  fail() {
    this.onerror?.();
  }

  emit(type: string, payload: object, lastEventId = '') {
    const event = new MessageEvent(type, { data: JSON.stringify(payload), lastEventId });
    for (const listener of this.listeners.get(type) ?? []) listener(event);
  }
}

describe('chatApi HTTP contract', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    FakeEventSource.instances = [];
  });

  it('normalizes the two backend naming styles used by Chat cancellation', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(ok({ run_id: '42', dispatch_id: 'dsp-1', status: 'DISPATCHED', duplicate: false }))
      .mockResolvedValueOnce(ok({ runId: '42', status: 'requested', terminal: false }))
      .mockResolvedValueOnce(ok({ commandId: 'cmd-1', runId: '42', status: 'CANCELED', duplicate: true }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(createChatRun('记录午餐')).resolves.toMatchObject({ run_id: '42' });
    await expect(cancelChatRun('42')).resolves.toEqual({ run_id: '42', status: 'requested', terminal: false });
    await expect(cancelChatRun('42')).resolves.toEqual({
      run_id: '42',
      status: 'CANCELED',
      command_id: 'cmd-1',
      duplicate: true,
    });

    expect(JSON.parse(String(fetchMock.mock.calls[1][1].body))).toEqual({ reason: 'user_cancelled' });
  });

  it('forwards the cancellation signal to ChatRun reads and mutations', async () => {
    const controller = new AbortController();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(ok({ run_id: '42', dispatch_id: 'dsp-1', status: 'DISPATCHED', duplicate: false }))
      .mockResolvedValueOnce(ok({ run_id: '42', status: 'RUNNING' }))
      .mockResolvedValueOnce(ok([]))
      .mockResolvedValueOnce(ok({ run_id: '42', status: 'accepted', terminal: false }));
    vi.stubGlobal('fetch', fetchMock);

    await createChatRun('记录午餐', 'session-1', controller.signal);
    await getChatRun('42', controller.signal);
    await getChatRunEvents('42', controller.signal);
    await cancelChatRun('42', controller.signal);

    expect(fetchMock.mock.calls.every(([, init]) => init?.signal === controller.signal)).toBe(true);
  });

  it('保留后端返回的持久化 SSE 游标，供历史 Run 续接使用', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        ok([
          {
            event_id: 'runtime-event-7',
            sse_event_id: 'sse-19',
            run_id: '42',
            event_seq: 7,
            state: 'RUNNING',
            payload: {},
            occurred_at: '2026-09-16T00:00:00Z',
          },
        ]),
      ),
    );

    await expect(getChatRunEvents('42')).resolves.toMatchObject([
      { event_id: 'runtime-event-7', sse_event_id: 'sse-19' },
    ]);
  });

  it('reconnects the Chat stream, resumes the cursor, deduplicates events and closes on terminal state', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const received: ChatRunEvent[] = [];
    const states: string[] = [];
    const stream = streamChatRun('42', (event) => received.push(event), undefined, {
      reconnectDelayMs: 10,
      maxAttempts: 3,
      onStateChange: (connection) => states.push(`${connection.state}:${connection.attempt}`),
    });

    const first = FakeEventSource.instances[0];
    first.emit('run.event', { event_id: 'event-1', event_seq: 1, state: 'RUNNING', payload: { text: '第一段' } });
    first.fail();
    vi.advanceTimersByTime(10);

    const second = FakeEventSource.instances[1];
    expect(second.url).toContain('lastEventId=event-1');
    second.emit('run.event', { event_id: 'event-1', event_seq: 1, state: 'RUNNING', payload: { text: '重复' } });
    second.emit('run.completed', { event_seq: 2, answer: '完成' }, 'event-2');

    expect(received).toHaveLength(2);
    expect(received[0]).toMatchObject({ event_id: 'event-1', event_seq: 1, state: 'RUNNING' });
    expect(received[1]).toMatchObject({ event_id: 'event-2', event_seq: 2, state: 'SUCCEEDED' });
    expect(second.closed).toBe(true);
    expect(stream.getConnection()).toMatchObject({ state: 'closed', lastEventId: 'event-2' });
    expect(states).toContain('reconnecting:2');
  });

  it('enters exhausted state after bounded Chat stream failures', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const states: string[] = [];
    streamChatRun('42', () => undefined, undefined, {
      reconnectDelayMs: 5,
      maxAttempts: 2,
      onStateChange: (connection) => states.push(connection.state),
    });

    FakeEventSource.instances[0].fail();
    vi.advanceTimersByTime(5);
    FakeEventSource.instances[1].fail();

    expect(states.at(-1)).toBe('exhausted');
  });

  it('deduplicates Chat events by payload ids even when the SSE message id changes', () => {
    vi.stubGlobal('EventSource', FakeEventSource);
    const received: ChatRunEvent[] = [];
    const stream = streamChatRun('42', (event) => received.push(event));
    const source = FakeEventSource.instances[0];

    source.emit('run.event', { event_id: 'business-id', sse_event_id: 'stream-id', state: 'RUNNING' }, 'message-1');
    source.emit('run.event', { event_id: 'business-id', sse_event_id: 'stream-id', state: 'RUNNING' }, 'message-2');

    expect(received).toHaveLength(1);
    expect(stream.getConnection().lastEventId).toBe('message-2');
    stream.close();
  });

  it('closes the Chat stream and cancels pending reconnect when the lifecycle signal aborts', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const controller = new AbortController();
    const stream = streamChatRun('42', () => undefined, undefined, {
      signal: controller.signal,
      reconnectDelayMs: 10,
    });

    const source = FakeEventSource.instances[0];
    source.fail();
    controller.abort();
    vi.advanceTimersByTime(20);

    expect(source.closed).toBe(true);
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(stream.getConnection().state).toBe('closed');
  });
});
